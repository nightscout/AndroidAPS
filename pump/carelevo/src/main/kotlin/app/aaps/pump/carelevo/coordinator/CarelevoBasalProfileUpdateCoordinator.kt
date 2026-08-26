package app.aaps.pump.carelevo.coordinator

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.pump.carelevo.R
import app.aaps.pump.carelevo.ble.CarelevoBleSession
import app.aaps.pump.carelevo.common.CarelevoPatch
import app.aaps.pump.carelevo.domain.model.ResponseResult
import app.aaps.pump.carelevo.domain.model.infusion.CarelevoInfusionInfoDomainModel
import app.aaps.pump.carelevo.domain.model.result.ResultSuccess
import app.aaps.pump.carelevo.domain.usecase.CarelevoUseCaseResponse
import app.aaps.pump.carelevo.domain.usecase.basal.CarelevoSetBasalProgramUseCase
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Single
import kotlinx.coroutines.runBlocking
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlin.jvm.optionals.getOrNull

@Singleton
class CarelevoBasalProfileUpdateCoordinator @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val rh: ResourceHelper,
    private val pumpEnactResultProvider: Provider<PumpEnactResult>,
    private val carelevoPatch: CarelevoPatch,
    private val bleSession: CarelevoBleSession,
    private val setBasalProgramUseCase: CarelevoSetBasalProgramUseCase
) {

    private companion object {

        // Fresh-session worst case: 1 s settle + ≤20 s connect handshake + ≤30 s for the three program
        // writes — the session's own withTimeouts fire first, this is only the outer backstop.
        private const val BASAL_PROGRAM_TIMEOUT_SEC = 60L
    }

    private var lastProfileUpdateAttemptMs: Long = 0

    fun updateBasalProfile(
        profile: Profile,
        cancelExtendedBolus: () -> PumpEnactResult,
        cancelTempBasal: () -> PumpEnactResult,
        onProfileUpdated: (Profile) -> Unit
    ): PumpEnactResult {
        aapsLogger.debug(LTag.PUMPCOMM, "execute.start profile=$profile")

        val result = pumpEnactResultProvider.get()
        val now = System.currentTimeMillis()
        if (now - lastProfileUpdateAttemptMs < 30_000) {
            // Benign debounce: a repeated profile-set within 30s is skipped, not a real failure.
            // Do not post FAILED_UPDATE_PROFILE here — dev reclassified it to URGENT, so posting it
            // for this non-event would ring the alarm. Log only; the real failure branches below post.
            aapsLogger.debug(LTag.PUMPCOMM, "execute.skip tooSoon=true")
            return result
                .success(true)
                .enacted(false)
                .comment(rh.gs(R.string.carelevo_profile_update_skip_comment))
        }

        val infusionInfo = carelevoPatch.infusionInfo.value?.getOrNull()
        val shouldUseSetBasalProgram = infusionInfo?.basalInfusionInfo == null

        val response = cancelExtendedBolusRx(infusionInfo, cancelExtendedBolus)
            .timeout(20, TimeUnit.SECONDS)
            .retryCancelWithLog("cancelExtendedBolus")
            .flatMap {
                if (!it.success) throw IllegalStateException("cancelExtendedBolus failed")
                cancelTempBasalRx(infusionInfo, cancelTempBasal)
                    .timeout(20, TimeUnit.SECONDS)
                    .retryCancelWithLog("cancelTempBasal")
            }
            .flatMap {
                if (!it.success) throw IllegalStateException("cancelTempBasal failed")
                // The program opens a fresh session (connect ≤20 s + three writes ≤30 s); its own internal
                // timeouts fire first, this outer one is only a safety net.
                executeBasalProgram(profile, shouldUseSetBasalProgram).timeout(BASAL_PROGRAM_TIMEOUT_SEC, TimeUnit.SECONDS)
            }
            .onErrorReturn { ResponseResult.Error(it) }
            .blockingGet()

        // PROFILE_SET_OK / FAILED_UPDATE_PROFILE are now posted centrally by the CommandQueue from the
        // returned success/enacted (success && enacted → PROFILE_SET_OK; !success → FAILED_UPDATE_PROFILE),
        // unified across all pumps — do not post them here.
        return when (response) {
            is ResponseResult.Success -> {
                aapsLogger.debug(LTag.PUMPCOMM, "execute.success")
                onProfileUpdated(profile)
                lastProfileUpdateAttemptMs = System.currentTimeMillis()
                result.success(true).enacted(true)
            }

            is ResponseResult.Error   -> {
                aapsLogger.error(LTag.PUMPCOMM, "execute.error error=${response.e}", response.e)
                lastProfileUpdateAttemptMs = System.currentTimeMillis()
                result.success(false).enacted(false)
            }

            is ResponseResult.Failure -> {
                aapsLogger.error(LTag.PUMPCOMM, "execute.failure unknownResponse=$response")
                lastProfileUpdateAttemptMs = System.currentTimeMillis()
                result.success(false).enacted(false)
            }
        }
    }

    private fun <T : Any> Single<T>.retryCancelWithLog(
        tag: String,
        maxRetry: Int = 3,
        delayMs: Long = 300L
    ): Single<T> {
        return retryWhen { errors ->
            errors
                .zipWith(Flowable.range(1, maxRetry)) { error, retryCount ->
                    if (retryCount < maxRetry) {
                        aapsLogger.warn(LTag.PUMPCOMM, "$tag.retry attempt=$retryCount/$maxRetry reason=${error.message}")
                        retryCount
                    } else {
                        aapsLogger.error(LTag.PUMPCOMM, "$tag.retry.exhausted max=$maxRetry reason=${error.message}")
                        throw error
                    }
                }
                .flatMap { Flowable.timer(delayMs, TimeUnit.MILLISECONDS) }
        }
    }

    private fun executeBasalProgram(
        profile: Profile,
        shouldUseSetBasalProgram: Boolean
    ): Single<ResponseResult<CarelevoUseCaseResponse>> {
        aapsLogger.debug(LTag.PUMPCOMM, "executeBasalProgram mode=${if (shouldUseSetBasalProgram) "SET" else "UPDATE"}")
        return Single.fromCallable { executeBasalProgramInternal(profile, isUpdate = !shouldUseSetBasalProgram) }
    }

    /**
     * Basal (re)program core (**delivery-critical**). Same 3-write single-session plan as the activation
     * set-basal in `CarelevoActivationExecutor.runSetBasal`, with [isUpdate] selecting the change opcode
     * (0x21) instead of the set opcode (0x13). The V2 update's persist is byte-identical to set's, so both
     * modes reuse [CarelevoSetBasalProgramUseCase]'s `buildBasalProgramPlan` + `persistBasalProgram`
     * (mode=1 + basal infusion-info).
     */
    private fun executeBasalProgramInternal(profile: Profile, isUpdate: Boolean): ResponseResult<CarelevoUseCaseResponse> = runCatching<CarelevoUseCaseResponse> {
        val address = carelevoPatch.getPatchInfoAddress()
            ?: throw IllegalStateException("no patch address")
        val plan = setBasalProgramUseCase.buildBasalProgramPlan(profile)
        val programmed = runBlocking { bleSession.runBasalProgram(address, plan.programs, isUpdate) }
        if (!programmed) throw IllegalStateException("basal program write failed")
        val persisted = setBasalProgramUseCase.persistBasalProgram(plan.segments)
        aapsLogger.info(LTag.PUMPCOMM, "newBle.basalProfile isUpdate=$isUpdate programmed=true persisted=$persisted")
        if (!persisted) throw IllegalStateException("basal program persist failed")
        ResultSuccess
    }.fold(
        onSuccess = { ResponseResult.Success(it) },
        onFailure = {
            aapsLogger.error(LTag.PUMPCOMM, "newBle.basalProfile FAILED isUpdate=$isUpdate", it)
            ResponseResult.Error(it)
        }
    )

    private fun cancelExtendedBolusRx(
        infusionInfo: CarelevoInfusionInfoDomainModel?,
        cancelExtendedBolus: () -> PumpEnactResult
    ): Single<PumpEnactResult> {
        aapsLogger.debug(LTag.PUMPCOMM, "cancelExtendedBolus.start hasExtended=${infusionInfo?.extendBolusInfusionInfo != null}")
        return if (infusionInfo?.extendBolusInfusionInfo != null) {
            Single.fromCallable { cancelExtendedBolus() }
                .flatMap { cancelResult ->
                    if (cancelResult.success) {
                        Single.just(cancelResult)
                    } else {
                        Single.error(IllegalStateException("cancelExtendedBolus returned success=false"))
                    }
                }
                .doOnError { aapsLogger.error(LTag.PUMPCOMM, "cancelExtendedBolus.error", it) }
        } else {
            Single.just(pumpEnactResultProvider.get().success(true).enacted(false))
        }
    }

    private fun cancelTempBasalRx(
        infusionInfo: CarelevoInfusionInfoDomainModel?,
        cancelTempBasal: () -> PumpEnactResult
    ): Single<PumpEnactResult> {
        aapsLogger.debug(LTag.PUMPCOMM, "cancelTempBasal.start hasTempBasal=${infusionInfo?.tempBasalInfusionInfo != null}")
        return if (infusionInfo?.tempBasalInfusionInfo != null) {
            Single.fromCallable { cancelTempBasal() }
                .flatMap { cancelResult ->
                    if (cancelResult.success) {
                        Single.just(cancelResult)
                    } else {
                        Single.error(IllegalStateException("cancelTempBasal returned success=false"))
                    }
                }
                .doOnError { aapsLogger.error(LTag.PUMPCOMM, "cancelTempBasal.error", it) }
        } else {
            Single.just(pumpEnactResultProvider.get().success(true).enacted(false))
        }
    }
}

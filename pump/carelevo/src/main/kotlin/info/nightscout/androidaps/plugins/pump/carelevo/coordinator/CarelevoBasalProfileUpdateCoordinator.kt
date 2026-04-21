package info.nightscout.androidaps.plugins.pump.carelevo.coordinator

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.resources.ResourceHelper
import info.nightscout.androidaps.plugins.pump.carelevo.R
import info.nightscout.androidaps.plugins.pump.carelevo.common.CarelevoPatch
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.ResponseResult
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.infusion.CarelevoInfusionInfoDomainModel
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.CarelevoUseCaseResponse
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.basal.CarelevoSetBasalProgramUseCase
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.basal.CarelevoUpdateBasalProgramUseCase
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.basal.model.SetBasalProgramRequestModel
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Single
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlin.jvm.optionals.getOrNull

@Singleton
class CarelevoBasalProfileUpdateCoordinator @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val rh: ResourceHelper,
    private val notificationManager: NotificationManager,
    private val pumpEnactResultProvider: Provider<PumpEnactResult>,
    private val carelevoPatch: CarelevoPatch,
    private val setBasalProgramUseCase: CarelevoSetBasalProgramUseCase,
    private val updateBasalProgramUseCase: CarelevoUpdateBasalProgramUseCase
) {

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
            notificationManager.post(
                NotificationId.FAILED_UPDATE_PROFILE,
                rh.gs(R.string.carelevo_profile_update_skip_too_soon),
                validMinutes = 1
            )
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
                executeBasalProgram(profile, shouldUseSetBasalProgram).timeout(20, TimeUnit.SECONDS)
            }
            .onErrorReturn { ResponseResult.Error(it) }
            .blockingGet()

        return when (response) {
            is ResponseResult.Success -> {
                aapsLogger.debug(LTag.PUMPCOMM, "execute.success")
                onProfileUpdated(profile)
                lastProfileUpdateAttemptMs = System.currentTimeMillis()
                notificationManager.post(
                    NotificationId.PROFILE_SET_OK,
                    app.aaps.core.ui.R.string.profile_set_ok,
                    validMinutes = 60
                )
                result.success(true).enacted(true)
            }

            is ResponseResult.Error -> {
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
        val request = SetBasalProgramRequestModel(profile)

        return if (shouldUseSetBasalProgram) {
            aapsLogger.debug(LTag.PUMPCOMM, "executeBasalProgram mode=SET")
            setBasalProgramUseCase.execute(request)
        } else {
            aapsLogger.debug(LTag.PUMPCOMM, "executeBasalProgram mode=UPDATE")
            updateBasalProgramUseCase.execute(request)
        }
    }

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

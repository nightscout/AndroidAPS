package app.aaps.pump.carelevo.coordinator

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.pump.carelevo.CarelevoStrings
import app.aaps.pump.carelevo.ble.CarelevoBleSession
import app.aaps.pump.carelevo.common.CarelevoPatch
import app.aaps.pump.carelevo.common.latest
import app.aaps.pump.carelevo.domain.model.ResponseResult
import app.aaps.pump.carelevo.domain.model.infusion.CarelevoBasalSegmentInfusionInfoDomainModel
import app.aaps.pump.carelevo.domain.model.infusion.CarelevoInfusionInfoDomainModel
import app.aaps.pump.carelevo.domain.model.result.ResultSuccess
import app.aaps.pump.carelevo.domain.usecase.CarelevoUseCaseResponse
import app.aaps.pump.carelevo.domain.usecase.basal.CarelevoSetBasalProgramUseCase
import app.aaps.pump.carelevo.ext.sleepBlocking
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Provider
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@SingleIn(AppScope::class)
class CarelevoBasalProfileUpdateCoordinator @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val rh: TextResolver,
    private val pumpEnactResultProvider: Provider<PumpEnactResult>,
    private val carelevoPatch: CarelevoPatch,
    private val bleSession: CarelevoBleSession,
    private val setBasalProgramUseCase: CarelevoSetBasalProgramUseCase
) {

    private companion object {

        // Fresh-session worst case: 1 s settle + ≤20 s connect handshake + ≤30 s for the three program
        // writes — the session's own withTimeouts fire first, this is only the outer backstop.
        val BASAL_PROGRAM_TIMEOUT = 60.seconds

        /** A cancel before the program: tries in all, the wait between them and the limit of each. */
        const val CANCEL_TRIES = 3
        val CANCEL_RETRY_DELAY = 300.milliseconds
        val CANCEL_TIMEOUT = 20.seconds

        /** The least time between two profile updates of the patch. */
        private const val PROFILE_UPDATE_SPACING_MS = 30_000L
    }

    private var lastProfileUpdateAttemptMs: Long = 0

    /** The clock and the wait of the spacing between updates, replaced in unit tests. */
    internal var clock: () -> Long = { Clock.System.now().toEpochMilliseconds() }
    internal var sleep: (Long) -> Unit = { sleepBlocking(it) }

    /** The hourly segments of the program the patch accepted, see [CarelevoSetBasalProgramUseCase.acceptedBasalSegments]. */
    fun acceptedBasalSegments(): List<CarelevoBasalSegmentInfusionInfoDomainModel>? = setBasalProgramUseCase.acceptedBasalSegments()

    /** Whether the patch runs [profile], see [CarelevoSetBasalProgramUseCase.isAccepted]. */
    fun isAccepted(profile: Profile): Boolean? = setBasalProgramUseCase.isAccepted(profile)

    suspend fun updateBasalProfile(
        profile: Profile,
        cancelExtendedBolus: () -> PumpEnactResult,
        cancelTempBasal: () -> PumpEnactResult,
        onProfileUpdated: (Profile) -> Unit
    ): PumpEnactResult {
        aapsLogger.debug(LTag.PUMPCOMM, "execute.start profile=$profile")

        val result = pumpEnactResultProvider()
        val sinceLast = clock() - lastProfileUpdateAttemptMs
        if (sinceLast < PROFILE_UPDATE_SPACING_MS) {
            if (setBasalProgramUseCase.isAccepted(profile) == true) {
                // A repeated profile-set of what the patch already runs: nothing to do, and not a failure.
                aapsLogger.debug(LTag.PUMPCOMM, "execute.skip sameProfile=true")
                return result
                    .success(true)
                    .enacted(false)
                    .comment(rh.gs(CarelevoStrings.carelevo_profile_update_skip_comment))
            }
            // Another profile, or a try again after a failure, waits for its turn. It was skipped and reported as set,
            // so AAPS recorded a profile the patch did not run.
            val wait = PROFILE_UPDATE_SPACING_MS - sinceLast
            aapsLogger.debug(LTag.PUMPCOMM, "execute.wait ${wait}ms")
            sleep(wait)
        }

        val infusionInfo = carelevoPatch.infusionInfo.latest
        val shouldUseSetBasalProgram = infusionInfo?.basalInfusionInfo == null

        val response: ResponseResult<CarelevoUseCaseResponse> = try {
            val extendedEnded = cancelIfRunning("cancelExtendedBolus", infusionInfo?.extendBolusInfusionInfo != null, cancelExtendedBolus)
            if (!extendedEnded.success) throw IllegalStateException("cancelExtendedBolus failed")
            val tempBasalEnded = cancelIfRunning("cancelTempBasal", infusionInfo?.tempBasalInfusionInfo != null, cancelTempBasal)
            if (!tempBasalEnded.success) throw IllegalStateException("cancelTempBasal failed")
            // The program opens a fresh session (connect ≤20 s + three writes ≤30 s); its own internal
            // timeouts fire first, this outer one is only a safety net.
            withTimeoutOrNull(BASAL_PROGRAM_TIMEOUT) { executeBasalProgramInternal(profile, isUpdate = !shouldUseSetBasalProgram) }
                ?: throw IllegalStateException("basal program did not end in $BASAL_PROGRAM_TIMEOUT")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ResponseResult.Error(e)
        }

        // PROFILE_SET_OK / FAILED_UPDATE_PROFILE are now posted centrally by the CommandQueue from the
        // returned success/enacted (success && enacted → PROFILE_SET_OK; !success → FAILED_UPDATE_PROFILE),
        // unified across all pumps — do not post them here.
        return when (response) {
            is ResponseResult.Success -> {
                aapsLogger.debug(LTag.PUMPCOMM, "execute.success")
                onProfileUpdated(profile)
                lastProfileUpdateAttemptMs = clock()
                result.success(true).enacted(true)
            }

            is ResponseResult.Error   -> {
                aapsLogger.error(LTag.PUMPCOMM, "execute.error error=${response.e}", response.e)
                lastProfileUpdateAttemptMs = clock()
                result.success(false).enacted(false)
            }

            is ResponseResult.Failure -> {
                aapsLogger.error(LTag.PUMPCOMM, "execute.failure unknownResponse=$response")
                lastProfileUpdateAttemptMs = clock()
                result.success(false).enacted(false)
            }
        }
    }

    /**
     * Ends a running extended bolus or temp basal before the program write. Nothing to end is a success that changed
     * nothing. A refusal or a failure is tried [CANCEL_TRIES] times in all, [CANCEL_RETRY_DELAY] apart, each try
     * limited to [CANCEL_TIMEOUT]; then the last failure is thrown.
     */
    private suspend fun cancelIfRunning(tag: String, running: Boolean, cancel: () -> PumpEnactResult): PumpEnactResult {
        aapsLogger.debug(LTag.PUMPCOMM, "$tag.start running=$running")
        if (!running) return pumpEnactResultProvider().success(true).enacted(false)
        var tryNumber = 1
        while (true) {
            try {
                return withTimeoutOrNull(CANCEL_TIMEOUT) {
                    try {
                        cancel().also { if (!it.success) throw IllegalStateException("$tag returned success=false") }
                    } catch (e: CancellationException) {
                        // A time limit inside the cancel throws this too. It is a failed try, unless this caller is cancelled.
                        currentCoroutineContext().ensureActive()
                        aapsLogger.error(LTag.PUMPCOMM, "$tag.error", e)
                        throw IllegalStateException("$tag was ended by a time limit", e)
                    } catch (e: Exception) {
                        aapsLogger.error(LTag.PUMPCOMM, "$tag.error", e)
                        throw e
                    }
                } ?: throw IllegalStateException("$tag did not end in $CANCEL_TIMEOUT")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (tryNumber >= CANCEL_TRIES) {
                    aapsLogger.error(LTag.PUMPCOMM, "$tag.retry.exhausted max=$CANCEL_TRIES reason=${e.message}")
                    throw e
                }
                aapsLogger.warn(LTag.PUMPCOMM, "$tag.retry attempt=$tryNumber/$CANCEL_TRIES reason=${e.message}")
                tryNumber++
                delay(CANCEL_RETRY_DELAY)
            }
        }
    }

    /**
     * Basal (re)program core (**delivery-critical**). Same 3-write single-session plan as the activation
     * set-basal in `CarelevoActivationExecutor.runSetBasal`, with [isUpdate] selecting the change opcode
     * (0x21) instead of the set opcode (0x13). The V2 update's persist is byte-identical to set's, so both
     * modes reuse [CarelevoSetBasalProgramUseCase]'s `buildBasalProgramPlan` + `persistBasalProgram`
     * (mode=1 + basal infusion-info).
     */
    private suspend fun executeBasalProgramInternal(profile: Profile, isUpdate: Boolean): ResponseResult<CarelevoUseCaseResponse> {
        aapsLogger.debug(LTag.PUMPCOMM, "executeBasalProgram mode=${if (isUpdate) "UPDATE" else "SET"}")
        return try {
            val address = carelevoPatch.getPatchInfoAddress()
                ?: throw IllegalStateException("no patch address")
            val plan = setBasalProgramUseCase.buildBasalProgramPlan(profile)
            val programmed = bleSession.runBasalProgram(address, plan.programs, isUpdate)
            if (!programmed) throw IllegalStateException("basal program write failed")
            val persisted = setBasalProgramUseCase.persistBasalProgram(plan.segments)
            aapsLogger.info(LTag.PUMPCOMM, "newBle.basalProfile isUpdate=$isUpdate programmed=true persisted=$persisted")
            if (!persisted) throw IllegalStateException("basal program persist failed")
            ResponseResult.Success(ResultSuccess)
        } catch (e: CancellationException) {
            // The session ends a write that gets no answer with its own time limit, which throws this too. That is a
            // failed write, as it was before (the patch may run the program: a later status read tells). Only a
            // cancel of this caller goes on as a cancel.
            currentCoroutineContext().ensureActive()
            aapsLogger.error(LTag.PUMPCOMM, "newBle.basalProfile FAILED isUpdate=$isUpdate", e)
            ResponseResult.Error(e)
        } catch (e: Exception) {
            aapsLogger.error(LTag.PUMPCOMM, "newBle.basalProfile FAILED isUpdate=$isUpdate", e)
            ResponseResult.Error(e)
        }
    }
}

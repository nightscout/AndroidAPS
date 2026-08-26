package app.aaps.pump.carelevo.command

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.queue.CustomCommand
import app.aaps.pump.carelevo.ble.BleCommand
import app.aaps.pump.carelevo.ble.CarelevoBleSession
import app.aaps.pump.carelevo.ble.commands.AdditionalPrimingCommand
import app.aaps.pump.carelevo.ble.commands.AlarmClearCommand
import app.aaps.pump.carelevo.ble.commands.BuzzModeCommand
import app.aaps.pump.carelevo.ble.commands.InfusionThresholdCommand
import app.aaps.pump.carelevo.ble.commands.NeedleStatusCommand
import app.aaps.pump.carelevo.ble.commands.NoticeThresholdCommand
import app.aaps.pump.carelevo.ble.commands.PatchDiscardCommand
import app.aaps.pump.carelevo.ble.commands.PumpResumeCommand
import app.aaps.pump.carelevo.ble.commands.PumpStopCommand
import app.aaps.pump.carelevo.ble.commands.SafetyCheckCommand
import app.aaps.pump.carelevo.ble.commands.SetTimeCommand
import app.aaps.pump.carelevo.ble.commands.SimpleResultResponse
import app.aaps.pump.carelevo.common.CarelevoPatch
import app.aaps.pump.carelevo.domain.model.bt.SafetyCheckResult
import app.aaps.pump.carelevo.domain.model.bt.SafetyCheckResultModel
import app.aaps.pump.carelevo.domain.type.SafetyProgress
import app.aaps.pump.carelevo.domain.usecase.alarm.AlarmClearPatchDiscardUseCase
import app.aaps.pump.carelevo.domain.usecase.alarm.AlarmClearRequestUseCase
import app.aaps.pump.carelevo.domain.usecase.basal.CarelevoSetBasalProgramUseCase
import app.aaps.pump.carelevo.domain.usecase.infusion.CarelevoPumpResumeUseCase
import app.aaps.pump.carelevo.domain.usecase.infusion.CarelevoPumpStopUseCase
import app.aaps.pump.carelevo.domain.usecase.patch.CarelevoPatchNeedleInsertionCheckUseCase
import app.aaps.pump.carelevo.domain.usecase.patch.CarelevoPatchSafetyCheckUseCase
import app.aaps.pump.carelevo.domain.usecase.userSetting.CarelevoUpdateLowInsulinNoticeAmountUseCase
import app.aaps.pump.carelevo.domain.usecase.userSetting.CarelevoUpdateMaxBolusDoseUseCase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.runBlocking
import org.joda.time.DateTime
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlin.jvm.optionals.getOrNull

/**
 * Runs activation operations that are routed through the AAPS [app.aaps.core.interfaces.queue.CommandQueue]
 * as custom commands. The plugin's `executeCustomCommand` delegates here; it is invoked on the queue
 * worker thread. Each op runs BLOCKING over the coroutine [CarelevoBleSession] (its own per-op
 * connection session — connect → exchange → close) and returns a [PumpEnactResult]. The use cases are
 * persistence-only seams (`persistX`/`buildX`); all BLE goes through [CarelevoBleSession].
 *
 * The safety check streams progress (`Progress` → `Success`/`Error`); since a custom command returns a
 * single result, that progress is republished on [safetyProgress] so the wizard can drive its countdown.
 */
@Singleton
class CarelevoActivationExecutor @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val pumpEnactResultProvider: Provider<PumpEnactResult>,
    private val carelevoPatch: CarelevoPatch,
    private val safetyCheckUseCase: CarelevoPatchSafetyCheckUseCase,
    private val needleCheckUseCase: CarelevoPatchNeedleInsertionCheckUseCase,
    private val setBasalUseCase: CarelevoSetBasalProgramUseCase,
    private val pumpStopUseCase: CarelevoPumpStopUseCase,
    private val pumpResumeUseCase: CarelevoPumpResumeUseCase,
    private val updateMaxBolusDoseUseCase: CarelevoUpdateMaxBolusDoseUseCase,
    private val updateLowInsulinNoticeAmountUseCase: CarelevoUpdateLowInsulinNoticeAmountUseCase,
    private val alarmClearRequestUseCase: AlarmClearRequestUseCase,
    private val alarmClearPatchDiscardUseCase: AlarmClearPatchDiscardUseCase,
    private val bleSession: CarelevoBleSession
) {

    private companion object {

        private const val RESULT_SUCCESS = 0 // pump result byte 0 = SUCCESS / BY_REQ
        private const val STOP_RESUME_SUB_ID = 0 // stop/resume use subId/causeId = 0
        private const val RESUME_MODE = 1 // resume mode = 1
        private const val TIMEZONE_SUB_ID = 1 // set-time subId = 1 for the timezone/DST update path
        private const val TIMEZONE_AID_MODE = 0 // set-time aidMode = 0
        private const val NEEDLE_CHECK_TIMEOUT_MS = 90_000L // physical cannula insertion may take a while
        private const val SAFETY_PROGRESS_HEADROOM_SEC = 30 // Progress timeout = firstFrame.durationSeconds + 30
    }

    private val _safetyProgress = MutableSharedFlow<SafetyProgress>(extraBufferCapacity = 16)
    val safetyProgress: SharedFlow<SafetyProgress> = _safetyProgress.asSharedFlow()

    fun execute(command: CustomCommand): PumpEnactResult? = when (command) {
        is CmdSafetyCheck            -> runSafetyCheck()
        is CmdNeedleCheck            -> runNeedleCheck()
        is CmdSetBasal               -> runSetBasal()
        is CmdAdditionalPriming      -> runAdditionalPriming()
        is CmdDiscard                -> runDiscard()
        is CmdPumpStop               -> runPumpStop(command.durationMin)
        is CmdPumpResume             -> runPumpResume()
        is CmdTimeZoneUpdate         -> runSingleWrite("timeZoneUpdate") {
            SetTimeCommand(subId = TIMEZONE_SUB_ID, volume = command.insulinAmount, aidMode = TIMEZONE_AID_MODE, dateTime = DateTime.now())
        }

        is CmdUpdateMaxBolus         -> runUpdateMaxBolus(command.maxBolusDose)
        is CmdUpdateLowInsulinNotice -> runUpdateLowInsulinNotice(command.hours)
        is CmdUpdateExpiredThreshold -> runUpdateExpiredThreshold(command.hours)
        is CmdUpdateBuzzer           -> runUpdateBuzzer(command.on)
        is CmdAlarmClear             -> runAlarmClear(command)
        is CmdAlarmClearPatchDiscard -> runAlarmClearPatchDiscard(command)
        else                         -> null
    }

    /**
     * Initial basal program (**delivery-critical**): build the 3-program plan with the use case's exact
     * mapping, send all three [app.aaps.pump.carelevo.ble.commands.BasalProgramCommand]s over ONE session
     * (via [CarelevoBleSession.runBasalProgram]), and only on all-success reuse the use case's `mode=1` +
     * infusion-info persist. Activation-only op.
     */
    private fun runSetBasal(): PumpEnactResult {
        val result = pumpEnactResultProvider.get()
        val profile = carelevoPatch.profile.value?.getOrNull()
            ?: return result.success(false).enacted(false).comment("profile not set")
        val address = carelevoPatch.getPatchInfoAddress()
            ?: return result.success(false).enacted(false).comment("no patch address")
        return try {
            val plan = setBasalUseCase.buildBasalProgramPlan(profile)
            val programmed = runBlocking {
                bleSession.runBasalProgram(address, plan.programs)
            }
            val persisted = if (programmed) setBasalUseCase.persistBasalProgram(plan.segments) else false
            aapsLogger.info(LTag.PUMPCOMM, "newBle.setBasal programmed=$programmed persisted=$persisted")
            val ok = programmed && persisted
            result.success(ok).enacted(ok)
        } catch (e: Exception) {
            aapsLogger.error(LTag.PUMPCOMM, "newBle.setBasal FAILED", e)
            result.success(false).enacted(false).comment(e.message ?: "error")
        }
    }

    /**
     * Pump stop (suspend delivery): write [PumpStopCommand] on the session; on success
     * (`resultCode == 0`) persist the suspended state (`pumpStopUseCase.persistStopped`).
     */
    private fun runPumpStop(durationMin: Int): PumpEnactResult {
        val result = pumpEnactResultProvider.get()
        val address = carelevoPatch.getPatchInfoAddress()
            ?: return result.success(false).enacted(false).comment("no patch address")
        return try {
            val response = runBlocking {
                bleSession.runSingle(address, PumpStopCommand(durationMinutes = durationMin, subId = STOP_RESUME_SUB_ID))
            }
            if (response.resultCode != RESULT_SUCCESS) {
                aapsLogger.error(LTag.PUMPCOMM, "newBle.pumpStop rejected result=${response.resultCode}")
                return result.success(false).enacted(false).comment("stop result ${response.resultCode}")
            }
            val persisted = pumpStopUseCase.persistStopped(durationMin)
            aapsLogger.info(LTag.PUMPCOMM, "newBle.pumpStop OK durationMin=$durationMin persisted=$persisted")
            result.success(persisted).enacted(persisted)
        } catch (e: Exception) {
            aapsLogger.error(LTag.PUMPCOMM, "newBle.pumpStop FAILED", e)
            result.success(false).enacted(false).comment(e.message ?: "error")
        }
    }

    private fun runPumpResume(): PumpEnactResult {
        val result = pumpEnactResultProvider.get()
        val address = carelevoPatch.getPatchInfoAddress()
            ?: return result.success(false).enacted(false).comment("no patch address")
        return try {
            val response = runBlocking {
                bleSession.runSingle(address, PumpResumeCommand(mode = RESUME_MODE, subId = STOP_RESUME_SUB_ID))
            }
            if (response.resultCode != RESULT_SUCCESS) { // 0 = BY_REQ
                aapsLogger.error(LTag.PUMPCOMM, "newBle.pumpResume rejected result=${response.resultCode}")
                return result.success(false).enacted(false).comment("resume result ${response.resultCode}")
            }
            val persisted = pumpResumeUseCase.persistResumed()
            aapsLogger.info(LTag.PUMPCOMM, "newBle.pumpResume OK persisted=$persisted")
            result.success(persisted).enacted(persisted)
        } catch (e: Exception) {
            aapsLogger.error(LTag.PUMPCOMM, "newBle.pumpResume FAILED", e)
            result.success(false).enacted(false).comment(e.message ?: "error")
        }
    }

    /**
     * Buzzer setting via [BuzzModeCommand] (0x18 → 0x78). Pure write, no persistence (the preference is
     * the source of truth).
     */
    private fun runUpdateBuzzer(on: Boolean): PumpEnactResult {
        val result = pumpEnactResultProvider.get()
        val address = carelevoPatch.getPatchInfoAddress()
            ?: return result.success(false).enacted(false).comment("no patch address")
        return try {
            val response = runBlocking {
                bleSession.runSingle(address, BuzzModeCommand(use = on))
            }
            val success = response.resultCode == RESULT_SUCCESS
            aapsLogger.info(LTag.PUMPCOMM, "newBle.buzzer OK on=$on result=${response.resultCode}")
            result.success(success).enacted(success)
        } catch (e: Exception) {
            aapsLogger.error(LTag.PUMPCOMM, "newBle.buzzer FAILED", e)
            result.success(false).enacted(false).comment(e.message ?: "error")
        }
    }

    /**
     * Max-bolus setting. Never push a threshold mid-bolus (persist locally + defer via
     * `needMaxBolusDoseSyncPatch`); otherwise write [InfusionThresholdCommand]
     * (max-volume), then persist with `synced` = the patch confirmed. On any failure the value is still
     * persisted with the sync flag set so the deferred-sync re-pushes on reconnect.
     */
    private fun runUpdateMaxBolus(value: Double): PumpEnactResult {
        val result = pumpEnactResultProvider.get()
        if (updateMaxBolusDoseUseCase.isBolusRunning()) {
            val persisted = updateMaxBolusDoseUseCase.persistMaxBolusDose(value, synced = false)
            aapsLogger.info(LTag.PUMPCOMM, "newBle.maxBolus bolus-running → deferred persisted=$persisted")
            return result.success(persisted).enacted(persisted)
        }
        val address = carelevoPatch.getPatchInfoAddress()
            ?: return result.success(false).enacted(false).comment("no patch address")
        return try {
            val response = runBlocking {
                bleSession.runSingle(address, InfusionThresholdCommand(isMaxVolume = true, value = value))
            }
            val pushed = response.resultCode == RESULT_SUCCESS
            val persisted = updateMaxBolusDoseUseCase.persistMaxBolusDose(value, synced = pushed)
            aapsLogger.info(LTag.PUMPCOMM, "newBle.maxBolus OK value=$value result=${response.resultCode} persisted=$persisted")
            val success = pushed && persisted
            result.success(success).enacted(success)
        } catch (e: Exception) {
            aapsLogger.error(LTag.PUMPCOMM, "newBle.maxBolus FAILED", e)
            updateMaxBolusDoseUseCase.persistMaxBolusDose(value, synced = false) // keep desired value + defer re-sync
            result.success(false).enacted(false).comment(e.message ?: "error")
        }
    }

    /**
     * Low-insulin-notice setting. The 0x75 response fabricates a result of 0, so arrival = success.
     * Persists via the use case with `synced` = arrived; on failure the value is
     * persisted deferred for the next reconnect.
     */
    private fun runUpdateLowInsulinNotice(hours: Int): PumpEnactResult {
        val result = pumpEnactResultProvider.get()
        val address = carelevoPatch.getPatchInfoAddress()
            ?: return result.success(false).enacted(false).comment("no patch address")
        return try {
            val response = runBlocking {
                bleSession.runSingle(address, NoticeThresholdCommand(thresholdType = NoticeThresholdCommand.TYPE_LOW_INSULIN, value = hours))
            }
            val pushed = response.resultCode == RESULT_SUCCESS
            val persisted = updateLowInsulinNoticeAmountUseCase.persistLowInsulinNoticeAmount(hours, synced = pushed)
            aapsLogger.info(LTag.PUMPCOMM, "newBle.lowInsulinNotice OK hours=$hours result=${response.resultCode} persisted=$persisted")
            val success = pushed && persisted
            result.success(success).enacted(success)
        } catch (e: Exception) {
            aapsLogger.error(LTag.PUMPCOMM, "newBle.lowInsulinNotice FAILED", e)
            updateLowInsulinNoticeAmountUseCase.persistLowInsulinNoticeAmount(hours, synced = false)
            result.success(false).enacted(false).comment(e.message ?: "error")
        }
    }

    /**
     * Patch-expiry-reminder setting. No userSettingInfo persist (the preference is the source of truth),
     * so this is a pure BLE write like the buzzer; arrival of the 0x75 frame (fabricated result 0) = success.
     */
    private fun runUpdateExpiredThreshold(hours: Int): PumpEnactResult {
        val result = pumpEnactResultProvider.get()
        val address = carelevoPatch.getPatchInfoAddress()
            ?: return result.success(false).enacted(false).comment("no patch address")
        return try {
            val response = runBlocking {
                bleSession.runSingle(address, NoticeThresholdCommand(thresholdType = NoticeThresholdCommand.TYPE_EXPIRY, value = hours))
            }
            val success = response.resultCode == RESULT_SUCCESS
            aapsLogger.info(LTag.PUMPCOMM, "newBle.expiryThreshold OK hours=$hours result=${response.resultCode}")
            result.success(success).enacted(success)
        } catch (e: Exception) {
            aapsLogger.error(LTag.PUMPCOMM, "newBle.expiryThreshold FAILED", e)
            result.success(false).enacted(false).comment(e.message ?: "error")
        }
    }

    /**
     * Shared path for a pure single-response WRITE with no persistence (buzzer-style ops:
     * additional-priming, timezone, …): run [command] on the session, success = `resultCode == 0`.
     * Runs on the queue worker (blocked inside the command).
     */
    private fun runSingleWrite(label: String, command: () -> BleCommand<SimpleResultResponse>): PumpEnactResult {
        val result = pumpEnactResultProvider.get()
        val address = carelevoPatch.getPatchInfoAddress()
            ?: return result.success(false).enacted(false).comment("no patch address")
        return try {
            val response = runBlocking {
                bleSession.runSingle(address, command())
            }
            val success = response.resultCode == RESULT_SUCCESS
            aapsLogger.info(LTag.PUMPCOMM, "ble.$label OK result=${response.resultCode}")
            result.success(success).enacted(success)
        } catch (e: Exception) {
            aapsLogger.error(LTag.PUMPCOMM, "ble.$label FAILED", e)
            result.success(false).enacted(false).comment(e.message ?: "error")
        }
    }

    private fun runAdditionalPriming(): PumpEnactResult {
        val result = pumpEnactResultProvider.get()
        val address = carelevoPatch.getPatchInfoAddress()
            ?: return result.success(false).enacted(false).comment("no patch address")
        return try {
            val response = runBlocking {
                bleSession.runAdditionalPriming(address)
            }
            val success = response.resultCode == RESULT_SUCCESS
            aapsLogger.info(LTag.PUMPCOMM, "ble.additionalPriming OK result=${response.resultCode}")
            result.success(success).enacted(success)
        } catch (e: Exception) {
            aapsLogger.error(LTag.PUMPCOMM, "ble.additionalPriming FAILED", e)
            result.success(false).enacted(false).comment(e.message ?: "error")
        }
    }

    /**
     * BLE discard (tell the patch to stop delivering) + teardown. Routed through the queue so the
     * teardown runs HERE on the queue worker thread (no reconnect race, ~300ms BLE teardown off the Main
     * thread). The STOP write alone ([PatchDiscardCommand] 0x36) decides success; a teardown failure must
     * not flip an already-stopped patch to failure. The DB-only force-discard stays in the ViewModels as
     * the fallback for when the patch cannot be reached at all.
     */
    private fun runDiscard(): PumpEnactResult {
        val result = pumpEnactResultProvider.get()
        val address = carelevoPatch.getPatchInfoAddress()
            ?: return result.success(false).enacted(false).comment("no patch address")
        val stopped = try {
            val response = runBlocking {
                bleSession.runSingle(address, PatchDiscardCommand())
            }
            response.resultCode == RESULT_SUCCESS
        } catch (e: Exception) {
            aapsLogger.error(LTag.PUMPCOMM, "newBle.discard stop exception", e)
            false
        }
        if (stopped) carelevoPatch.discardTeardown()
        aapsLogger.info(LTag.PUMPCOMM, "newBle.discard stopped=$stopped")
        return result.success(stopped).enacted(stopped)
    }

    /**
     * Cannula-insertion (needle) check. Long timeout — the pump reports 0x79 only after the physical
     * insertion. `resultCode == 0` = SUCCESS; the `checkNeedle`/failure-count persist is reused from the
     * use case. Activation-only op.
     */
    private fun runNeedleCheck(): PumpEnactResult {
        val result = pumpEnactResultProvider.get()
        val address = carelevoPatch.getPatchInfoAddress()
            ?: return result.success(false).enacted(false).comment("no patch address")
        return try {
            val response = runBlocking {
                bleSession.runSingle(address, NeedleStatusCommand(), timeoutMs = NEEDLE_CHECK_TIMEOUT_MS)
            }
            val inserted = response.resultCode == RESULT_SUCCESS
            val persisted = needleCheckUseCase.persistNeedleResult(inserted)
            aapsLogger.info(LTag.PUMPCOMM, "newBle.needleCheck inserted=$inserted result=${response.resultCode} persisted=$persisted")
            val ok = inserted && persisted
            result.success(ok).enacted(ok)
        } catch (e: Exception) {
            aapsLogger.error(LTag.PUMPCOMM, "newBle.needleCheck FAILED", e)
            result.success(false).enacted(false).comment(e.message ?: "error")
        }
    }

    /**
     * Safety Check over `requestStream`. Streams each 0x72 frame; progress frames
     * (REP_REQUEST/REP_REQUEST1) drive the wizard countdown via [_safetyProgress] (one Progress emit),
     * the terminal SUCCESS frame persists `checkSafety` + emits Success, any other
     * terminal is an Error. Activation-only op.
     */
    private fun runSafetyCheck(): PumpEnactResult {
        val result = pumpEnactResultProvider.get()
        val address = carelevoPatch.getPatchInfoAddress()
            ?: return result.success(false).enacted(false).comment("no patch address")
        var success = false
        var progressEmitted = false
        return try {
            runBlocking {
                bleSession.runSafetyCheck(address) { frame ->
                    when (frame.resultCode) {
                        SafetyCheckCommand.REP_REQUEST, SafetyCheckCommand.REP_REQUEST1 -> {
                            if (!progressEmitted) {
                                progressEmitted = true
                                _safetyProgress.tryEmit(SafetyProgress.Progress((frame.durationSeconds + SAFETY_PROGRESS_HEADROOM_SEC).toLong()))
                            }
                        }

                        SafetyCheckCommand.RESULT_SUCCESS                              -> {
                            if (safetyCheckUseCase.persistSafetyChecked()) {
                                success = true
                                _safetyProgress.tryEmit(SafetyProgress.Success(SafetyCheckResultModel(SafetyCheckResult.SUCCESS, frame.insulinVolume, frame.durationSeconds)))
                            } else {
                                _safetyProgress.tryEmit(SafetyProgress.Error(IllegalStateException("update patch info is failed")))
                            }
                        }

                        else                                                          ->
                            _safetyProgress.tryEmit(SafetyProgress.Error(IllegalStateException("safety check failed: result ${frame.resultCode}")))
                    }
                }
            }
            result.success(success).enacted(success)
        } catch (e: Exception) {
            aapsLogger.error(LTag.PUMPCOMM, "newBle.safetyCheck FAILED", e)
            _safetyProgress.tryEmit(SafetyProgress.Error(e))
            result.success(false).enacted(false).comment(e.message ?: "error")
        }
    }

    /**
     * Alarm clear: map the cause to the wire alarm-type byte, send [AlarmClearCommand] (0x47 → 0xA7),
     * and on `resultCode == 0` reuse the use case's remove-alarm persist.
     */
    private fun runAlarmClear(command: CmdAlarmClear): PumpEnactResult {
        val result = pumpEnactResultProvider.get()
        val address = carelevoPatch.getPatchInfoAddress()
            ?: return result.success(false).enacted(false).comment("no patch address")
        return try {
            val alarmTypeByte = alarmClearRequestUseCase.commandAlarmType(command.alarmCause)
            val cause = command.alarmCause.code ?: 0
            val response = runBlocking {
                bleSession.runSingle(address, AlarmClearCommand(alarmTypeByte, cause))
            }
            val cleared = response.resultCode == RESULT_SUCCESS
            val persisted = if (cleared) alarmClearRequestUseCase.persistAlarmCleared(command.alarmId) else false
            aapsLogger.info(LTag.PUMPCOMM, "newBle.alarmClear cleared=$cleared result=${response.resultCode} persisted=$persisted")
            val ok = cleared && persisted
            result.success(ok).enacted(ok)
        } catch (e: Exception) {
            aapsLogger.error(LTag.PUMPCOMM, "newBle.alarmClear FAILED", e)
            result.success(false).enacted(false).comment(e.message ?: "error")
        }
    }

    /**
     * Alarm-triggered patch discard: send [PatchDiscardCommand] (0x36) then reuse the use case's DB
     * cleanup (ack + reset sync flags + delete infusion/patch). Unlike the plain [runDiscard], this does
     * NOT unbond (no `discardTeardown`).
     */
    private fun runAlarmClearPatchDiscard(command: CmdAlarmClearPatchDiscard): PumpEnactResult {
        val result = pumpEnactResultProvider.get()
        val address = carelevoPatch.getPatchInfoAddress()
            ?: return result.success(false).enacted(false).comment("no patch address")
        return try {
            val response = runBlocking {
                bleSession.runSingle(address, PatchDiscardCommand())
            }
            val discarded = response.resultCode == RESULT_SUCCESS
            val persisted = if (discarded) alarmClearPatchDiscardUseCase.persistAlarmDiscarded(command.alarmId) else false
            aapsLogger.info(LTag.PUMPCOMM, "newBle.alarmClearPatchDiscard discarded=$discarded result=${response.resultCode} persisted=$persisted")
            val ok = discarded && persisted
            result.success(ok).enacted(ok)
        } catch (e: Exception) {
            aapsLogger.error(LTag.PUMPCOMM, "newBle.alarmClearPatchDiscard FAILED", e)
            result.success(false).enacted(false).comment(e.message ?: "error")
        }
    }
}

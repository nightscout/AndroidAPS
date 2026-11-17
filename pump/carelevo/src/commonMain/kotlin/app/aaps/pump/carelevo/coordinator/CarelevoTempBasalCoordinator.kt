package app.aaps.pump.carelevo.coordinator

import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.pump.PumpRate
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.pump.carelevo.ble.CarelevoBleSession
import app.aaps.pump.carelevo.ble.commands.InfusionInfoResponse
import app.aaps.pump.carelevo.ble.commands.TempBasalCancelCommand
import app.aaps.pump.carelevo.ble.commands.TempBasalCommand
import app.aaps.pump.carelevo.common.CarelevoPatch
import app.aaps.pump.carelevo.domain.model.bt.InfusionModeResult
import app.aaps.pump.carelevo.domain.model.bt.InfusionModeResult.Companion.codeToInfusionModeCommand
import app.aaps.pump.carelevo.domain.usecase.basal.CarelevoCancelTempBasalInfusionUseCase
import app.aaps.pump.carelevo.domain.usecase.basal.CarelevoStartTempBasalInfusionUseCase
import app.aaps.pump.carelevo.domain.usecase.basal.model.StartTempBasalInfusionRequestModel
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Provider
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.runBlocking

@SingleIn(AppScope::class)
class CarelevoTempBasalCoordinator @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val dateUtil: DateUtil,
    private val pumpSync: PumpSync,
    private val pumpEnactResultProvider: Provider<PumpEnactResult>,
    private val carelevoPatch: CarelevoPatch,
    private val bleSession: CarelevoBleSession,
    private val startTempBasalInfusionUseCase: CarelevoStartTempBasalInfusionUseCase,
    private val cancelTempBasalInfusionUseCase: CarelevoCancelTempBasalInfusionUseCase
) {

    private companion object {

        private const val RESULT_SUCCESS = 0
    }

    /**
     * Sends a temp basal command and tells whether it took effect. A patch that answers "no" did nothing. Without an
     * answer the patch may still have done it, so then what it runs now decides. Found by the pump scenario tests: the
     * patch ran a temp basal that AAPS did not know of, or AAPS kept one the patch had ended.
     * @param doneWhenTempBasalRuns a start is done when a temp basal runs, a cancel when none runs
     * @throws Exception the one of the command, when the patch does not show that it was done
     */
    private fun sentOrAsked(address: String, what: String, doneWhenTempBasalRuns: Boolean, send: suspend () -> Boolean): Boolean =
        try {
            runBlocking { send() }
        } catch (e: Exception) {
            val runs = tempBasalRunsOnPatch(address)
            aapsLogger.warn(LTag.PUMPCOMM, "newBle.$what got no answer ($e). The patch runs a temp basal: $runs")
            if (runs == doneWhenTempBasalRuns) true else throw e
        }

    /** Whether the patch runs a temp basal now, or null when it does not answer either. */
    private fun tempBasalRunsOnPatch(address: String): Boolean? =
        try {
            runBlocking { bleSession.readInfusionInfo(address) }.modeRaw.codeToInfusionModeCommand() == InfusionModeResult.TEMP_BASAL
        } catch (e: Exception) {
            aapsLogger.error(LTag.PUMPCOMM, "newBle.readInfusionInfo after a temp basal failed error=$e")
            null
        }

    /**
     * Ends the temp basal that AAPS keeps when the patch runs none. A cancel whose answer is lost, and then a lost
     * read of what the patch runs, leave AAPS with a temp basal the patch has ended, and nothing else corrects it.
     * Only plain basal mode surely means no temp basal: during a bolus the mode shows the bolus. The pump state does not
     * help, a real patch reports 0 (READY) also while it gives basal. A suspend is a PUMP_SUSPEND record that the resume
     * ends, so it is left alone.
     * The patch gives no end time, so the temp basal ends at this read.
     * @param info a status read of the patch
     */
    suspend fun endTempBasalThePatchDoesNotRun(info: InfusionInfoResponse, serialNumber: String) {
        if (info.modeRaw.codeToInfusionModeCommand() != InfusionModeResult.BASAL) return
        val tempBasal = pumpSync.expectedPumpState().temporaryBasal ?: return
        if (tempBasal.type == PumpSync.TemporaryBasalType.PUMP_SUSPEND) return
        val now = dateUtil.now()
        val ended = pumpSync.syncStopTemporaryBasalWithPumpId(
            timestamp = now,
            endPumpId = now,
            pumpType = PumpType.CAREMEDI_CARELEVO,
            pumpSerial = serialNumber
        )
        if (!ended) return
        val persisted = cancelTempBasalInfusionUseCase.persistTempBasalCancelled()
        aapsLogger.warn(
            LTag.PUMPCOMM,
            "newBle.status: the patch runs no temp basal, but AAPS had one since ${dateUtil.dateAndTimeString(tempBasal.timestamp)}. " +
                "Ended it now. persisted=$persisted"
        )
    }

    /**
     * Set an absolute temp basal (**delivery-critical**). Discrete `TempBasalCommand.byUnit` (0x23→0x83)
     * on the session → on `resultCode==0` reuse the use case's `mode=2` persist → then
     * `pumpSync.syncTemporaryBasalWithPumpId`.
     */
    fun setTempBasalAbsolute(
        absoluteRate: Double,
        durationInMinutes: Int,
        tbrType: PumpSync.TemporaryBasalType,
        serialNumber: String,
        onLastDataUpdated: () -> Unit
    ): PumpEnactResult {
        aapsLogger.info(
            LTag.PUMPCOMM,
            "setTempBasalAbsolute.start absoluteRate=${absoluteRate.toFloat()} durationInMinutes=${durationInMinutes.toLong()}"
        )
        // Fail-fast guards (same style as deliverTreatment): a zero/negative duration or rate would
        // otherwise be silently mangled into wire bytes.
        require(absoluteRate >= 0.0) { "TBR absolute rate must be >= 0, got $absoluteRate" }
        require(durationInMinutes > 0) { "TBR duration must be > 0 min, got $durationInMinutes" }
        val result = pumpEnactResultProvider()
        if (!carelevoPatch.isBluetoothEnabled()) {
            aapsLogger.info(LTag.PUMPCOMM, "setTempBasalAbsolute.skip reason=bluetoothDisabled")
            return result
        }
        val address = carelevoPatch.getPatchInfoAddress()
            ?: return result.success(false).enacted(false).comment("no patch address")
        val hour = durationInMinutes / 60
        val min = durationInMinutes % 60
        return try {
            val success = sentOrAsked(address, "setTempBasalAbsolute", doneWhenTempBasalRuns = true) {
                bleSession.runSingle(address, TempBasalCommand.byUnit(absoluteRate, hour, min)).resultCode == RESULT_SUCCESS
            }
            // Pump-is-authoritative: once the patch ACKs, the TBR IS running — it must reach pumpSync
            // even if the local persist fails, or basal IOB modeling silently diverges from reality
            // for the whole TBR duration.
            val persisted = success && startTempBasalInfusionUseCase.persistTempBasalStarted(
                StartTempBasalInfusionRequestModel(isUnit = true, speed = absoluteRate, minutes = durationInMinutes)
            )
            aapsLogger.info(LTag.PUMPCOMM, "newBle.setTempBasalAbsolute rate=$absoluteRate success=$success persisted=$persisted")
            if (success && !persisted) aapsLogger.error(LTag.PUMPCOMM, "newBle.setTempBasalAbsolute persist failed — pump enacted, recording in pumpSync anyway")
            if (success) {
                onLastDataUpdated()
                runBlocking {
                    pumpSync.syncTemporaryBasalWithPumpId(
                        timestamp = dateUtil.now(),
                        rate = PumpRate(absoluteRate),
                        duration = T.mins(durationInMinutes.toLong()).msecs(),
                        isAbsolute = true,
                        type = tbrType,
                        pumpId = dateUtil.now(),
                        pumpType = PumpType.CAREMEDI_CARELEVO,
                        pumpSerial = serialNumber
                    )
                }
                result.success(true).enacted(true)
                    .duration(durationInMinutes)
                    .absolute(absoluteRate)
                    .isPercent(false)
                    .isTempCancel(false)
            } else {
                result.success(false).enacted(false).comment("Internal error")
            }
        } catch (e: Exception) {
            aapsLogger.error(LTag.PUMPCOMM, "newBle.setTempBasalAbsolute FAILED", e)
            result.success(false).enacted(false).comment(e.message ?: "error")
        }
    }

    /**
     * Set a percent temp basal. Discrete `TempBasalCommand.byPercent` (5-byte, value=`percent/100`) on
     * the session → `mode=2` persist → `pumpSync`.
     */
    fun setTempBasalPercent(
        percent: Int,
        durationInMinutes: Int,
        tbrType: PumpSync.TemporaryBasalType,
        serialNumber: String,
        onLastDataUpdated: () -> Unit
    ): PumpEnactResult {
        require(percent >= 0) { "TBR percent must be >= 0, got $percent" }
        require(durationInMinutes > 0) { "TBR duration must be > 0 min, got $durationInMinutes" }
        val result = pumpEnactResultProvider()
        aapsLogger.debug(LTag.PUMPCOMM, "setTempBasalPercent.start percent=$percent durationInMinutes=$durationInMinutes")
        if (!carelevoPatch.isBluetoothEnabled()) {
            aapsLogger.debug(LTag.PUMPCOMM, "setTempBasalPercent.skip reason=bluetoothDisabled")
            return result
        }
        val address = carelevoPatch.getPatchInfoAddress()
            ?: return result.success(false).enacted(false).comment("no patch address")
        val hour = durationInMinutes / 60
        val min = durationInMinutes % 60
        return try {
            val success = sentOrAsked(address, "setTempBasalPercent", doneWhenTempBasalRuns = true) {
                bleSession.runSingle(address, TempBasalCommand.byPercent(percent, hour, min)).resultCode == RESULT_SUCCESS
            }
            // Pump-is-authoritative — see setTempBasalAbsolute.
            val persisted = success && startTempBasalInfusionUseCase.persistTempBasalStarted(
                StartTempBasalInfusionRequestModel(isUnit = false, percent = percent, minutes = durationInMinutes)
            )
            aapsLogger.info(LTag.PUMPCOMM, "newBle.setTempBasalPercent percent=$percent success=$success persisted=$persisted")
            if (success && !persisted) aapsLogger.error(LTag.PUMPCOMM, "newBle.setTempBasalPercent persist failed — pump enacted, recording in pumpSync anyway")
            if (success) {
                onLastDataUpdated()
                runBlocking {
                    pumpSync.syncTemporaryBasalWithPumpId(
                        timestamp = dateUtil.now(),
                        rate = PumpRate(percent.toDouble()),
                        duration = T.mins(durationInMinutes.toLong()).msecs(),
                        isAbsolute = false,
                        type = tbrType,
                        pumpId = dateUtil.now(),
                        pumpType = PumpType.CAREMEDI_CARELEVO,
                        pumpSerial = serialNumber
                    )
                }
                result.success = true
                result.enacted = true
                result.duration = durationInMinutes
                result.percent = percent
                result.isPercent = true
                result.isTempCancel = false
                result
            } else {
                result.success(false).enacted(false).comment("Internal error")
            }
        } catch (e: Exception) {
            aapsLogger.error(LTag.PUMPCOMM, "newBle.setTempBasalPercent FAILED", e)
            result.success(false).enacted(false).comment(e.message ?: "error")
        }
    }

    /**
     * Cancel a running temp basal. Discrete `TempBasalCancelCommand` (0x2D→0x8D) on the session →
     * delete + recompute-mode persist → `pumpSync.syncStop…`. The session's own settle provides the
     * inter-op spacing.
     */
    fun cancelTempBasal(
        serialNumber: String,
        onLastDataUpdated: () -> Unit
    ): PumpEnactResult {
        val result = pumpEnactResultProvider()
        aapsLogger.debug(LTag.PUMPCOMM, "cancelTempBasal.start")
        if (!carelevoPatch.isBluetoothEnabled()) {
            aapsLogger.debug(LTag.PUMPCOMM, "cancelTempBasal.skip reason=bluetoothDisabled")
            return result
        }
        val address = carelevoPatch.getPatchInfoAddress()
            ?: return result.success(false).enacted(false).comment("no patch address")
        return try {
            val success = sentOrAsked(address, "cancelTempBasal", doneWhenTempBasalRuns = false) {
                bleSession.runSingle(address, TempBasalCancelCommand()).resultCode == RESULT_SUCCESS
            }
            // Pump-is-authoritative — on ACK the TBR IS stopped on the patch; the stop must reach
            // pumpSync even if the local persist fails.
            val persisted = success && cancelTempBasalInfusionUseCase.persistTempBasalCancelled()
            aapsLogger.info(LTag.PUMPCOMM, "newBle.cancelTempBasal success=$success persisted=$persisted")
            if (success && !persisted) aapsLogger.error(LTag.PUMPCOMM, "newBle.cancelTempBasal persist failed — pump stopped, recording stop in pumpSync anyway")
            if (success) {
                onLastDataUpdated()
                runBlocking {
                    pumpSync.syncStopTemporaryBasalWithPumpId(
                        timestamp = dateUtil.now(),
                        endPumpId = dateUtil.now(),
                        pumpType = PumpType.CAREMEDI_CARELEVO,
                        pumpSerial = serialNumber
                    )
                }
                result.success = true
                result.enacted = true
                result.isTempCancel = true
                result
            } else {
                result.success = false
                result.enacted = false
                result
            }
        } catch (e: Exception) {
            aapsLogger.error(LTag.PUMPCOMM, "newBle.cancelTempBasal FAILED", e)
            result.success = false
            result.enacted = false
            result
        }
    }
}

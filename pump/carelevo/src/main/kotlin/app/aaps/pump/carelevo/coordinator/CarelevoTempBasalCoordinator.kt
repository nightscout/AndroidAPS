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
import app.aaps.pump.carelevo.ble.commands.TempBasalCancelCommand
import app.aaps.pump.carelevo.ble.commands.TempBasalCommand
import app.aaps.pump.carelevo.common.CarelevoPatch
import app.aaps.pump.carelevo.domain.usecase.basal.CarelevoCancelTempBasalInfusionUseCase
import app.aaps.pump.carelevo.domain.usecase.basal.CarelevoStartTempBasalInfusionUseCase
import app.aaps.pump.carelevo.domain.usecase.basal.model.StartTempBasalInfusionRequestModel
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
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
        val result = pumpEnactResultProvider.get()
        if (!carelevoPatch.isBluetoothEnabled()) {
            aapsLogger.info(LTag.PUMPCOMM, "setTempBasalAbsolute.skip reason=bluetoothDisabled")
            return result
        }
        val address = carelevoPatch.getPatchInfoAddress()
            ?: return result.success(false).enacted(false).comment("no patch address")
        val hour = durationInMinutes / 60
        val min = durationInMinutes % 60
        return try {
            val response = runBlocking { bleSession.runSingle(address, TempBasalCommand.byUnit(absoluteRate, hour, min)) }
            val success = response.resultCode == RESULT_SUCCESS
            // Pump-is-authoritative: once the patch ACKs, the TBR IS running — it must reach pumpSync
            // even if the local persist fails, or basal IOB modeling silently diverges from reality
            // for the whole TBR duration.
            val persisted = success && startTempBasalInfusionUseCase.persistTempBasalStarted(
                StartTempBasalInfusionRequestModel(isUnit = true, speed = absoluteRate, minutes = durationInMinutes)
            )
            aapsLogger.info(LTag.PUMPCOMM, "newBle.setTempBasalAbsolute rate=$absoluteRate result=${response.resultCode} persisted=$persisted")
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
        val result = pumpEnactResultProvider.get()
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
            val response = runBlocking { bleSession.runSingle(address, TempBasalCommand.byPercent(percent, hour, min)) }
            val success = response.resultCode == RESULT_SUCCESS
            // Pump-is-authoritative — see setTempBasalAbsolute.
            val persisted = success && startTempBasalInfusionUseCase.persistTempBasalStarted(
                StartTempBasalInfusionRequestModel(isUnit = false, percent = percent, minutes = durationInMinutes)
            )
            aapsLogger.info(LTag.PUMPCOMM, "newBle.setTempBasalPercent percent=$percent result=${response.resultCode} persisted=$persisted")
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
        val result = pumpEnactResultProvider.get()
        aapsLogger.debug(LTag.PUMPCOMM, "cancelTempBasal.start")
        if (!carelevoPatch.isBluetoothEnabled()) {
            aapsLogger.debug(LTag.PUMPCOMM, "cancelTempBasal.skip reason=bluetoothDisabled")
            return result
        }
        val address = carelevoPatch.getPatchInfoAddress()
            ?: return result.success(false).enacted(false).comment("no patch address")
        return try {
            val response = runBlocking { bleSession.runSingle(address, TempBasalCancelCommand()) }
            val success = response.resultCode == RESULT_SUCCESS
            // Pump-is-authoritative — on ACK the TBR IS stopped on the patch; the stop must reach
            // pumpSync even if the local persist fails.
            val persisted = success && cancelTempBasalInfusionUseCase.persistTempBasalCancelled()
            aapsLogger.info(LTag.PUMPCOMM, "newBle.cancelTempBasal result=${response.resultCode} persisted=$persisted")
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

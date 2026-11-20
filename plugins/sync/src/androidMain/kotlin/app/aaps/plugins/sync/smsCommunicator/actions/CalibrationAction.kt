package app.aaps.plugins.sync.smsCommunicator.actions

import app.aaps.core.interfaces.bolus.BatchAction
import app.aaps.core.interfaces.bolus.MasterActionExecutor
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.smsCommunicator.Sms
import app.aaps.core.interfaces.smsCommunicator.SmsCommunicator
import app.aaps.plugins.sync.SyncStrings
import app.aaps.plugins.sync.smsCommunicator.SmsAction
import app.aaps.plugins.sync.smsCommunicator.SmsBatchResult
import app.aaps.plugins.sync.smsCommunicator.runSmsBatch

/**
 * A calibration through [MasterActionExecutor]: CAL <value>. The same path as the calibration dialog and a client, so
 * the BG source decides where it goes - to xDrip+ or the Eversense transmitter, to the calibration plugin, or refused
 * with the reason when the sensor is calibrated in its own app or on the pump. The executor logs it.
 */
class CalibrationAction(
    private val bgMgdl: Double,
    private val receivedSms: Sms,
    private val masterActionExecutor: MasterActionExecutor,
    private val rh: TextResolver,
    private val smsCommunicator: SmsCommunicator,
    private val sendSMSToAllNumbers: (Sms) -> Unit
) : SmsAction(pumpCommand = false) {

    override suspend fun run() {
        when (val result = masterActionExecutor.runSmsBatch(listOf(BatchAction.Calibration(bgMgdl)))) {
            is SmsBatchResult.Done    ->
                sendSMSToAllNumbers(Sms(receivedSms.phoneNumber, rh.gs(SyncStrings.smscommunicator_calibration_accepted)))

            is SmsBatchResult.NotDone ->
                smsCommunicator.sendSMS(Sms(receivedSms.phoneNumber, result.reply(rh.gs(SyncStrings.smscommunicator_calibration_not_accepted))))
        }
    }
}

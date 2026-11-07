package app.aaps.plugins.sync.smsCommunicator.actions

import app.aaps.core.data.model.TT
import app.aaps.core.interfaces.bolus.BatchAction
import app.aaps.core.interfaces.bolus.WizardBolusExecutor
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.smsCommunicator.Sms
import app.aaps.core.interfaces.smsCommunicator.SmsCommunicator
import app.aaps.plugins.sync.SyncStrings
import app.aaps.plugins.sync.smsCommunicator.SmsAction
import app.aaps.plugins.sync.smsCommunicator.SmsBatchResult
import app.aaps.plugins.sync.smsCommunicator.runSmsBatch

/**
 * Cancels an active temp target through [WizardBolusExecutor]: TARGET STOP/CANCEL. A temp target with duration 0
 * is how a batch says "cancel", the same as the phone's temp target screen.
 */
class TempTargetCancelAction(
    private val receivedSms: Sms,
    private val wizardBolusExecutor: WizardBolusExecutor,
    private val rh: TextResolver,
    private val smsCommunicator: SmsCommunicator,
    private val sendSMSToAllNumbers: (Sms) -> Unit
) : SmsAction(pumpCommand = false) {

    override suspend fun run() {
        val cancel = BatchAction.TempTarget(reason = TT.Reason.CUSTOM.text, lowMgdl = 0.0, highMgdl = 0.0, durationMinutes = 0, startOffsetMinutes = 0)
        when (val result = wizardBolusExecutor.runSmsBatch(listOf(cancel))) {
            is SmsBatchResult.Done    -> sendSMSToAllNumbers(Sms(receivedSms.phoneNumber, rh.gs(SyncStrings.smscommunicator_tt_canceled)))
            is SmsBatchResult.NotDone -> smsCommunicator.sendSMS(Sms(receivedSms.phoneNumber, result.reply(rh.gs(SyncStrings.smscommunicator_remote_command_not_possible))))
        }
    }
}

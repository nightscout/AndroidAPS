package app.aaps.plugins.sync.smsCommunicator.actions

import app.aaps.core.data.model.RM
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
 * Changes the running mode: LOOP DISABLE/CLOSED/LGS/RESUME/SUSPEND and PUMP CONNECT/DISCONNECT.
 *
 * Applied through [WizardBolusExecutor], which checks again that [mode] is allowed now and picks the audit action
 * (for RESUME: a reconnect when the pump was disconnected). The pump side follows from the new mode, the same as when
 * the mode is changed on the phone: the loop sets the zero temp basal on DISCONNECT, and its running mode reconciler
 * cancels the temp basal on SUSPEND and DISABLE. So this action sends no pump command itself, and the reply does not
 * claim a temp basal result it did not see.
 *
 * [replyToAll] sends the success reply to every allowed number, not only to the sender.
 */
class RunningModeAction(
    private val mode: RM.Mode,
    private val durationMinutes: Int,
    private val successText: String,
    private val replyToAll: Boolean,
    pumpCommand: Boolean,
    private val receivedSms: Sms,
    private val wizardBolusExecutor: WizardBolusExecutor,
    private val rh: TextResolver,
    private val smsCommunicator: SmsCommunicator,
    private val sendSMSToAllNumbers: (Sms) -> Unit
) : SmsAction(pumpCommand) {

    override suspend fun run() {
        when (val result = wizardBolusExecutor.runSmsBatch(listOf(BatchAction.RunningMode(mode = mode, durationMinutes = durationMinutes)))) {
            is SmsBatchResult.Done    ->
                if (replyToAll) sendSMSToAllNumbers(Sms(receivedSms.phoneNumber, successText))
                else smsCommunicator.sendSMS(Sms(receivedSms.phoneNumber, successText))

            is SmsBatchResult.NotDone ->
                smsCommunicator.sendSMS(Sms(receivedSms.phoneNumber, result.reply(rh.gs(SyncStrings.smscommunicator_remote_command_not_possible))))
        }
    }
}

package app.aaps.plugins.sync.smsCommunicator.actions

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
 * Sets a temp basal: BASAL <U/h> [<minutes>] or BASAL <pct>% [<minutes>].
 *
 * Set through [WizardBolusExecutor], which caps it and gates it on the running mode like a temp basal from the phone.
 * [rate] is in the pump's own style: the plugin refuses the other style before it asks for a pass code.
 */
class TempBasalAction(
    private val rate: Double,
    private val isPercent: Boolean,
    private val durationMinutes: Int,
    private val receivedSms: Sms,
    private val wizardBolusExecutor: WizardBolusExecutor,
    private val rh: TextResolver,
    private val smsCommunicator: SmsCommunicator,
    private val sendSMSToAllNumbers: (Sms) -> Unit,
    private val shortStatusBlocking: () -> String
) : SmsAction(pumpCommand = true) {

    override suspend fun run() {
        val tempBasal = BatchAction.TempBasal(rate = rate, isPercent = isPercent, durationMinutes = durationMinutes)
        when (val result = wizardBolusExecutor.runSmsBatch(listOf(tempBasal))) {
            is SmsBatchResult.Done    -> {
                val replyText =
                    if (isPercent) rh.gs(SyncStrings.smscommunicator_tempbasal_set_percent, rate.toInt(), durationMinutes)
                    else rh.gs(SyncStrings.smscommunicator_tempbasal_set, rate, durationMinutes)
                sendSMSToAllNumbers(Sms(receivedSms.phoneNumber, replyText + "\n" + shortStatusBlocking()))
            }

            is SmsBatchResult.NotDone ->
                smsCommunicator.sendSMS(Sms(receivedSms.phoneNumber, result.reply(rh.gs(SyncStrings.smscommunicator_tempbasal_failed), shortStatusBlocking)))
        }
    }
}

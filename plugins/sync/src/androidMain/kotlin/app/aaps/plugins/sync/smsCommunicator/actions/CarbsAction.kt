package app.aaps.plugins.sync.smsCommunicator.actions

import app.aaps.core.interfaces.bolus.BatchAction
import app.aaps.core.interfaces.bolus.WizardBolusExecutor
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.smsCommunicator.Sms
import app.aaps.core.interfaces.smsCommunicator.SmsCommunicator
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.plugins.sync.SyncStrings
import app.aaps.plugins.sync.smsCommunicator.SmsAction
import app.aaps.plugins.sync.smsCommunicator.SmsBatchResult
import app.aaps.plugins.sync.smsCommunicator.runSmsBatch

/**
 * Records carbs at a given time: CARBS <grams> [<time>].
 *
 * Entered through [WizardBolusExecutor], the same carbs path the phone's carbs dialog uses. The time travels as an
 * offset from now, worked out when the command runs.
 */
class CarbsAction(
    val grams: Int,
    val timestamp: Long,
    private val receivedSms: Sms,
    private val wizardBolusExecutor: WizardBolusExecutor,
    private val dateUtil: DateUtil,
    private val rh: TextResolver,
    private val smsCommunicator: SmsCommunicator,
    private val sendSMSToAllNumbers: (Sms) -> Unit,
    private val shortStatusBlocking: () -> String
) : SmsAction(pumpCommand = true) {

    override suspend fun run() {
        val offsetMinutes = ((timestamp - dateUtil.now()) / 60_000L).toInt()
        val carbs = BatchAction.Bolus(
            insulin = 0.0, carbs = grams, carbsTimeOffsetMinutes = offsetMinutes, carbsDurationHours = 0,
            recordOnly = false, notes = "", timestamp = 0L, iCfg = null
        )
        when (val result = wizardBolusExecutor.runSmsBatch(listOf(carbs), waitForDose = true)) {
            is SmsBatchResult.Done    -> {
                val replyText = rh.gs(SyncStrings.smscommunicator_carbs_set, result.preview.carbs) + "\n" + shortStatusBlocking()
                sendSMSToAllNumbers(Sms(receivedSms.phoneNumber, replyText))
            }

            is SmsBatchResult.NotDone ->
                smsCommunicator.sendSMS(Sms(receivedSms.phoneNumber, result.reply(rh.gs(SyncStrings.smscommunicator_carbs_failed, grams), shortStatusBlocking)))
        }
    }
}

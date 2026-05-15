package app.aaps.plugins.sync.smsCommunicator.actions

import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.smsCommunicator.Sms
import app.aaps.core.interfaces.smsCommunicator.SmsCommunicator
import app.aaps.plugins.sync.R
import app.aaps.plugins.sync.smsCommunicator.SmsAction

/** Records carbs at a given timestamp: CARBS <grams> [<time>]. */
class CarbsAction(
    val grams: Int,
    val timestamp: Long,
    private val receivedSms: Sms,
    private val commandQueue: CommandQueue,
    private val rh: ResourceHelper,
    private val uel: UserEntryLogger,
    private val smsCommunicator: SmsCommunicator,
    private val sendSMSToAllNumbers: (Sms) -> Unit,
    private val shortStatusBlocking: () -> String
) : SmsAction(pumpCommand = true) {

    override suspend fun run() {
        val detailedBolusInfo = DetailedBolusInfo()
        detailedBolusInfo.carbs = grams.toDouble()
        detailedBolusInfo.timestamp = timestamp
        commandQueue.bolus(detailedBolusInfo, object : Callback() {
            override fun run() {
                if (result.success) {
                    var replyText = rh.gs(R.string.smscommunicator_carbs_set, grams)
                    replyText += "\n" + shortStatusBlocking()
                    sendSMSToAllNumbers(Sms(receivedSms.phoneNumber, replyText))
                    uel.log(
                        Action.CARBS, Sources.SMS, shortStatusBlocking() + ": " + rh.gs(R.string.smscommunicator_carbs_set, grams),
                        ValueWithUnit.Gram(grams)
                    )
                } else {
                    var replyText = rh.gs(R.string.smscommunicator_carbs_failed, grams)
                    replyText += "\n" + shortStatusBlocking()
                    smsCommunicator.sendSMS(Sms(receivedSms.phoneNumber, replyText))
                    uel.log(
                        Action.CARBS, Sources.SMS, shortStatusBlocking() + ": " + rh.gs(R.string.smscommunicator_carbs_failed, grams),
                        ValueWithUnit.Gram(grams)
                    )
                }
            }
        })
    }
}

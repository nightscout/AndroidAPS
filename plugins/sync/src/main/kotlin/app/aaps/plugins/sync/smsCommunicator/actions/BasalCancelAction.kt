package app.aaps.plugins.sync.smsCommunicator.actions

import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.smsCommunicator.Sms
import app.aaps.core.interfaces.smsCommunicator.SmsCommunicator
import app.aaps.plugins.sync.R
import app.aaps.plugins.sync.smsCommunicator.SmsAction

/** Cancels the current temp basal: BASAL CANCEL/STOP. */
class BasalCancelAction(
    private val receivedSms: Sms,
    private val commandQueue: CommandQueue,
    private val rh: ResourceHelper,
    private val uel: UserEntryLogger,
    private val smsCommunicator: SmsCommunicator,
    private val sendSMSToAllNumbers: (Sms) -> Unit,
    private val shortStatusBlocking: () -> String
) : SmsAction(pumpCommand = true) {

    override suspend fun run() {
        commandQueue.cancelTempBasal(enforceNew = true, callback = object : Callback() {
            override fun run() {
                if (result.success) {
                    var replyText = rh.gs(R.string.smscommunicator_tempbasal_canceled)
                    replyText += "\n" + shortStatusBlocking()
                    sendSMSToAllNumbers(Sms(receivedSms.phoneNumber, replyText))
                    uel.log(
                        Action.TEMP_BASAL, Sources.SMS, shortStatusBlocking() + "\n" + rh.gs(R.string.smscommunicator_tempbasal_canceled),
                        ValueWithUnit.SimpleString(rh.gsNotLocalised(R.string.smscommunicator_tempbasal_canceled))
                    )
                } else {
                    var replyText = rh.gs(R.string.smscommunicator_tempbasal_cancel_failed)
                    replyText += "\n" + shortStatusBlocking()
                    smsCommunicator.sendSMS(Sms(receivedSms.phoneNumber, replyText))
                    uel.log(
                        Action.TEMP_BASAL, Sources.SMS, shortStatusBlocking() + "\n" + rh.gs(R.string.smscommunicator_tempbasal_cancel_failed),
                        ValueWithUnit.SimpleString(rh.gsNotLocalised(R.string.smscommunicator_tempbasal_cancel_failed))
                    )
                }
            }
        })
    }
}

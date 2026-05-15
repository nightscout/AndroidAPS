package app.aaps.plugins.sync.smsCommunicator.actions

import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.smsCommunicator.Sms
import app.aaps.core.interfaces.smsCommunicator.SmsCommunicator
import app.aaps.plugins.sync.R
import app.aaps.plugins.sync.smsCommunicator.SmsAction

/** Delivers an extended bolus: EXTENDED <U> <minutes>. */
class ExtendedSetAction(
    val insulin: Double,
    val durationMinutes: Int,
    private val receivedSms: Sms,
    private val commandQueue: CommandQueue,
    private val config: Config,
    private val rh: ResourceHelper,
    private val uel: UserEntryLogger,
    private val smsCommunicator: SmsCommunicator,
    private val sendSMSToAllNumbers: (Sms) -> Unit,
    private val shortStatusBlocking: () -> String
) : SmsAction(pumpCommand = true) {

    override suspend fun run() {
        commandQueue.extendedBolus(insulin, durationMinutes, object : Callback() {
            override fun run() {
                if (result.success) {
                    var replyText = rh.gs(R.string.smscommunicator_extended_set, insulin, durationMinutes)
                    if (config.APS) replyText += "\n" + rh.gs(app.aaps.core.ui.R.string.loopsuspended)
                    replyText += "\n" + shortStatusBlocking()
                    sendSMSToAllNumbers(Sms(receivedSms.phoneNumber, replyText))
                    if (config.APS)
                        uel.log(
                            action = Action.EXTENDED_BOLUS,
                            source = Sources.SMS,
                            note = shortStatusBlocking() + "\n" + rh.gs(
                                R.string.smscommunicator_extended_set,
                                insulin,
                                durationMinutes
                            ) + " / " + rh.gs(app.aaps.core.ui.R.string.loopsuspended),
                            listValues = listOf(
                                ValueWithUnit.Insulin(insulin),
                                ValueWithUnit.Minute(durationMinutes),
                                ValueWithUnit.SimpleString(rh.gsNotLocalised(app.aaps.core.ui.R.string.loopsuspended))
                            )
                        )
                    else
                        uel.log(
                            action = Action.EXTENDED_BOLUS,
                            source = Sources.SMS,
                            note = shortStatusBlocking() + "\n" + rh.gs(R.string.smscommunicator_extended_set, insulin, durationMinutes),
                            listValues = listOf(
                                ValueWithUnit.Insulin(insulin),
                                ValueWithUnit.Minute(durationMinutes)
                            )
                        )
                } else {
                    var replyText = rh.gs(R.string.smscommunicator_extended_failed)
                    replyText += "\n" + shortStatusBlocking()
                    smsCommunicator.sendSMS(Sms(receivedSms.phoneNumber, replyText))
                    uel.log(
                        Action.EXTENDED_BOLUS, Sources.SMS, shortStatusBlocking() + "\n" + rh.gs(R.string.smscommunicator_extended_failed),
                        ValueWithUnit.SimpleString(rh.gsNotLocalised(R.string.smscommunicator_extended_failed))
                    )
                }
            }
        })
    }
}

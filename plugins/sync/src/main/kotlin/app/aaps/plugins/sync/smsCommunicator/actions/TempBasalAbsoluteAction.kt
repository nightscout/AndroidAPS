package app.aaps.plugins.sync.smsCommunicator.actions

import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.smsCommunicator.Sms
import app.aaps.core.interfaces.smsCommunicator.SmsCommunicator
import app.aaps.plugins.sync.R
import app.aaps.plugins.sync.smsCommunicator.SmsAction

/** Sets an absolute temp basal: BASAL <U/h> [<minutes>]. */
class TempBasalAbsoluteAction(
    val rateUnitsPerHour: Double,
    val durationMinutes: Int,
    private val receivedSms: Sms,
    private val profile: Profile,
    private val commandQueue: CommandQueue,
    private val rh: ResourceHelper,
    private val uel: UserEntryLogger,
    private val smsCommunicator: SmsCommunicator,
    private val sendSMSToAllNumbers: (Sms) -> Unit,
    private val shortStatusBlocking: () -> String
) : SmsAction(pumpCommand = true) {

    override suspend fun run() {
        commandQueue.tempBasalAbsolute(rateUnitsPerHour, durationMinutes, true, profile, PumpSync.TemporaryBasalType.NORMAL, object : Callback() {
            override fun run() {
                if (result.success) {
                    var replyText = if (result.isPercent) rh.gs(R.string.smscommunicator_tempbasal_set_percent, result.percent, result.duration)
                    else rh.gs(R.string.smscommunicator_tempbasal_set, result.absolute, result.duration)
                    replyText += "\n" + shortStatusBlocking()
                    sendSMSToAllNumbers(Sms(receivedSms.phoneNumber, replyText))
                    if (result.isPercent)
                        uel.log(
                            action = Action.TEMP_BASAL,
                            source = Sources.SMS,
                            note = shortStatusBlocking() + "\n" + rh.gs(R.string.smscommunicator_tempbasal_set_percent, result.percent, result.duration),
                            listValues = listOf(
                                ValueWithUnit.Percent(result.percent),
                                ValueWithUnit.Minute(result.duration)
                            )
                        )
                    else
                        uel.log(
                            action = Action.TEMP_BASAL,
                            source = Sources.SMS,
                            note = shortStatusBlocking() + "\n" + rh.gs(R.string.smscommunicator_tempbasal_set, result.absolute, result.duration),
                            listValues = listOf(
                                ValueWithUnit.UnitPerHour(result.absolute),
                                ValueWithUnit.Minute(result.duration)
                            )
                        )
                } else {
                    var replyText = rh.gs(R.string.smscommunicator_tempbasal_failed)
                    replyText += "\n" + shortStatusBlocking()
                    smsCommunicator.sendSMS(Sms(receivedSms.phoneNumber, replyText))
                    uel.log(
                        Action.TEMP_BASAL, Sources.SMS, shortStatusBlocking() + "\n" + rh.gs(R.string.smscommunicator_tempbasal_failed),
                        ValueWithUnit.SimpleString(rh.gsNotLocalised(R.string.smscommunicator_tempbasal_failed))
                    )
                }
            }
        })
    }
}

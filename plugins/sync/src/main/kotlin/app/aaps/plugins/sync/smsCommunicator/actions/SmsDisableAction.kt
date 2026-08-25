package app.aaps.plugins.sync.smsCommunicator.actions

import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.smsCommunicator.Sms
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.plugins.sync.R
import app.aaps.plugins.sync.smsCommunicator.SmsAction

/** Disables remote SMS commands: SMS STOP/DISABLE. */
class SmsDisableAction(
    private val receivedSms: Sms,
    private val preferences: Preferences,
    private val rh: ResourceHelper,
    private val uel: UserEntryLogger,
    private val sendSMSToAllNumbers: (Sms) -> Unit
) : SmsAction(pumpCommand = false) {

    override suspend fun run() {
        preferences.put(BooleanKey.SmsAllowRemoteCommands, false)
        val replyText = rh.gs(R.string.smscommunicator_stopped_sms)
        sendSMSToAllNumbers(Sms(receivedSms.phoneNumber, replyText))
        uel.log(
            Action.STOP_SMS, Sources.SMS, rh.gs(R.string.smscommunicator_stopped_sms),
            ValueWithUnit.SimpleString(rh.gsNotLocalised(R.string.smscommunicator_stopped_sms))
        )
    }
}

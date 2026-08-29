package app.aaps.plugins.sync.smsCommunicator.actions

import app.aaps.core.data.model.RM
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.smsCommunicator.Sms
import app.aaps.core.interfaces.smsCommunicator.SmsCommunicator
import app.aaps.plugins.sync.R
import app.aaps.plugins.sync.smsCommunicator.SmsAction

/** Reconnects the pump: PUMP CONNECT. */
class PumpConnectAction(
    private val receivedSms: Sms,
    private val profileFunction: ProfileFunction,
    private val loop: Loop,
    private val rh: ResourceHelper,
    private val smsCommunicator: SmsCommunicator
) : SmsAction(pumpCommand = true) {

    override suspend fun run() {
        val profile = profileFunction.getProfile() ?: return
        loop.handleRunningModeChange(newRM = RM.Mode.RESUME, action = Action.RECONNECT, source = Sources.SMS, profile = profile)
        smsCommunicator.sendSMS(Sms(receivedSms.phoneNumber, rh.gs(R.string.smscommunicator_reconnect)))
    }
}

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

/** Disconnects the pump for the given duration: PUMP DISCONNECT <minutes>. */
class PumpDisconnectAction(
    val durationMinutes: Int,
    private val receivedSms: Sms,
    private val profileFunction: ProfileFunction,
    private val loop: Loop,
    private val rh: ResourceHelper,
    private val smsCommunicator: SmsCommunicator
) : SmsAction(pumpCommand = true) {

    override suspend fun run() {
        val profile = profileFunction.getProfile() ?: return
        loop.handleRunningModeChange(
            durationInMinutes = durationMinutes,
            profile = profile,
            newRM = RM.Mode.DISCONNECTED_PUMP,
            action = Action.DISCONNECT,
            source = Sources.SMS
        )
        smsCommunicator.sendSMS(Sms(receivedSms.phoneNumber, rh.gs(R.string.smscommunicator_pump_disconnected)))
    }
}

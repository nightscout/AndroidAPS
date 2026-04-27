package app.aaps.plugins.sync.smsCommunicator.actions

import app.aaps.core.data.model.RM
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.smsCommunicator.Sms
import app.aaps.plugins.sync.R
import app.aaps.plugins.sync.smsCommunicator.SmsAction

/** Switches loop to Closed Loop mode: LOOP CLOSED. */
class LoopClosedAction(
    private val receivedSms: Sms,
    private val profile: Profile,
    private val loop: Loop,
    private val rh: ResourceHelper,
    private val sendSMSToAllNumbers: (Sms) -> Unit
) : SmsAction(pumpCommand = false) {

    override suspend fun run() {
        loop.handleRunningModeChange(
            newRM = RM.Mode.CLOSED_LOOP,
            action = Action.CLOSED_LOOP_MODE,
            source = Sources.SMS,
            profile = profile
        )
        val replyText = rh.gs(R.string.smscommunicator_current_loop_mode, rh.gs(app.aaps.core.ui.R.string.closedloop))
        sendSMSToAllNumbers(Sms(receivedSms.phoneNumber, replyText))
    }
}

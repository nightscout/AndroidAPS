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

/** Resumes a suspended loop: LOOP RESUME. */
class LoopResumeAction(
    private val receivedSms: Sms,
    private val profile: Profile,
    private val loop: Loop,
    private val rh: ResourceHelper,
    private val sendSMSToAllNumbers: (Sms) -> Unit
) : SmsAction(pumpCommand = true) {

    override suspend fun run() {
        loop.handleRunningModeChange(
            newRM = RM.Mode.RESUME,
            action = Action.RESUME,
            source = Sources.SMS,
            profile = profile
        )
        sendSMSToAllNumbers(Sms(receivedSms.phoneNumber, rh.gs(R.string.smscommunicator_loop_resumed)))
    }
}

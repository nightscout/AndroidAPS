package app.aaps.plugins.sync.smsCommunicator.actions

import app.aaps.core.data.model.RM
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.smsCommunicator.Sms
import app.aaps.core.interfaces.smsCommunicator.SmsCommunicator
import app.aaps.plugins.sync.R
import app.aaps.plugins.sync.smsCommunicator.SmsAction

/** Executes a remote loop disable: LOOP DISABLE/STOP. */
class LoopDisableAction(
    private val receivedSms: Sms,
    private val profile: Profile,
    private val loop: Loop,
    private val rh: ResourceHelper,
    private val smsCommunicator: SmsCommunicator
) : SmsAction(pumpCommand = false) {

    override suspend fun run() {
        val result = loop.handleRunningModeChange(
            newRM = RM.Mode.DISABLED_LOOP,
            action = Action.LOOP_DISABLED,
            source = Sources.SMS,
            profile = profile
        )
        val replyText = rh.gs(R.string.smscommunicator_loop_has_been_disabled) + " " +
            rh.gs(if (result) R.string.smscommunicator_tempbasal_canceled else R.string.smscommunicator_tempbasal_cancel_failed)
        smsCommunicator.sendSMS(Sms(receivedSms.phoneNumber, replyText))
    }
}

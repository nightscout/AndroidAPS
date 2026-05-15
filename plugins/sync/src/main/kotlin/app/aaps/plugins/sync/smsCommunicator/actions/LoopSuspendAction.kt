package app.aaps.plugins.sync.smsCommunicator.actions

import app.aaps.core.data.model.RM
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.smsCommunicator.Sms
import app.aaps.core.interfaces.smsCommunicator.SmsCommunicator
import app.aaps.plugins.sync.R
import app.aaps.plugins.sync.smsCommunicator.SmsAction
import kotlinx.coroutines.runBlocking

/** Suspends the loop for a given duration: LOOP SUSPEND <minutes>. */
class LoopSuspendAction(
    val durationMinutes: Int,
    private val receivedSms: Sms,
    private val profile: Profile,
    private val loop: Loop,
    private val commandQueue: CommandQueue,
    private val rh: ResourceHelper,
    private val smsCommunicator: SmsCommunicator,
    private val sendSMSToAllNumbers: (Sms) -> Unit,
    private val shortStatusBlocking: () -> String
) : SmsAction(pumpCommand = true) {

    override suspend fun run() {
        commandQueue.cancelTempBasal(enforceNew = true, callback = object : Callback() {
            override fun run() {
                if (result.success) {
                    // TODO: Callback.run() is sync; runBlocking matches existing pattern in this file.
                    runBlocking {
                        loop.handleRunningModeChange(
                            newRM = RM.Mode.SUSPENDED_BY_USER,
                            durationInMinutes = durationMinutes,
                            action = Action.SUSPEND,
                            source = Sources.SMS,
                            profile = profile
                        )
                    }
                    val replyText = rh.gs(R.string.smscommunicator_loop_suspended) + " " +
                        rh.gs(if (result.success) R.string.smscommunicator_tempbasal_canceled else R.string.smscommunicator_tempbasal_cancel_failed)
                    sendSMSToAllNumbers(Sms(receivedSms.phoneNumber, replyText))
                } else {
                    var replyText = rh.gs(R.string.smscommunicator_tempbasal_cancel_failed)
                    replyText += "\n" + shortStatusBlocking()
                    smsCommunicator.sendSMS(Sms(receivedSms.phoneNumber, replyText))
                }
            }
        })
    }
}

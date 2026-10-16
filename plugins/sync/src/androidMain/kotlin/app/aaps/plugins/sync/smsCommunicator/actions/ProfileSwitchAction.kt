package app.aaps.plugins.sync.smsCommunicator.actions

import app.aaps.core.interfaces.bolus.BatchAction
import app.aaps.core.interfaces.bolus.WizardBolusExecutor
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.smsCommunicator.Sms
import app.aaps.core.interfaces.smsCommunicator.SmsCommunicator
import app.aaps.core.ui.CoreUiStrings
import app.aaps.plugins.sync.SyncStrings
import app.aaps.plugins.sync.smsCommunicator.SmsAction
import app.aaps.plugins.sync.smsCommunicator.SmsBatchResult
import app.aaps.plugins.sync.smsCommunicator.runSmsBatch

/**
 * Switches to the named profile at a given percentage: PROFILE <index> [<percentage>].
 *
 * The switch is applied through [WizardBolusExecutor], the same path the phone, the watch and a paired client
 * already use, so the master validates a switch once and in one place. This used to call
 * [app.aaps.core.interfaces.profile.ProfileFunction.createProfileSwitch] directly, which reproduced the
 * executor's insulin check by hand and left out its range checks.
 */
class ProfileSwitchAction(
    private val profileName: String,
    private val percentage: Int,
    private val receivedSms: Sms,
    private val wizardBolusExecutor: WizardBolusExecutor,
    private val rh: TextResolver,
    private val smsCommunicator: SmsCommunicator
) : SmsAction(pumpCommand = true) {

    override suspend fun run() {
        val switch = BatchAction.ProfileSwitch(percentage = percentage, timeShiftHours = 0, durationMinutes = 0, profileName = profileName)
        // The executor refuses a switch it cannot apply - no insulin in force, a name the master's store does not
        // hold, a value out of range. It already says why, in one short line, so that is passed on.
        val replyText = when (val result = wizardBolusExecutor.runSmsBatch(listOf(switch))) {
            is SmsBatchResult.Done    -> rh.gs(SyncStrings.sms_profile_switch_created)
            is SmsBatchResult.NotDone -> result.reply(rh.gs(CoreUiStrings.invalid_profile))
        }
        smsCommunicator.sendSMS(Sms(receivedSms.phoneNumber, replyText))
    }
}

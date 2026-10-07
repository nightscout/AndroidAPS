package app.aaps.plugins.sync.smsCommunicator.actions

import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.bolus.BatchAction
import app.aaps.core.interfaces.bolus.WizardBolusExecutor
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.smsCommunicator.Sms
import app.aaps.core.interfaces.smsCommunicator.SmsCommunicator
import app.aaps.core.ui.CoreUiStrings
import app.aaps.plugins.sync.SyncStrings
import app.aaps.plugins.sync.smsCommunicator.SmsAction

/**
 * Switches to the named profile at a given percentage: PROFILE <index> [<percentage>].
 *
 * The switch is applied through [WizardBolusExecutor], the same path the phone, the watch and a paired client
 * already use, so the master validates a switch once and in one place. This used to call
 * [app.aaps.core.interfaces.profile.ProfileFunction.createProfileSwitch] directly, which reproduced the
 * executor's insulin check by hand and left out its range checks.
 *
 * Prepare and confirm run back to back here, rather than parking at parse time and confirming when the pass
 * code arrives. The executor holds one batch in a single slot, and an SMS confirmation is valid for
 * `Constants.SMS_CONFIRM_TIMEOUT` (5 minutes) — long enough that a bolus from the phone or the watch would
 * clear the parked switch and leave the user with a "nothing pending" reply.
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
        val prepared = wizardBolusExecutor.prepareBatch(
            listOf(
                BatchAction.ProfileSwitch(
                    percentage = percentage,
                    timeShiftHours = 0,
                    durationMinutes = 0,
                    profileName = profileName
                )
            )
        )
        if (prepared !is WizardBolusExecutor.PrepareResult.Preview) {
            // The executor refuses a switch it cannot apply - no insulin in force, a name the master's store
            // does not hold, a value out of range. It already says why, in one short line, so pass that on.
            val reason = (prepared as? WizardBolusExecutor.PrepareResult.Error)?.message ?: rh.gs(CoreUiStrings.invalid_profile)
            smsCommunicator.sendSMS(Sms(receivedSms.phoneNumber, reason))
            return
        }
        var failure: String? = null
        val result = wizardBolusExecutor.confirm(prepared.bolusId, Sources.SMS, { failure = it.comment })
        val replyText =
            if (result == WizardBolusExecutor.ConfirmResult.Delivered) rh.gs(SyncStrings.sms_profile_switch_created)
            else failure ?: rh.gs(CoreUiStrings.invalid_profile)
        smsCommunicator.sendSMS(Sms(receivedSms.phoneNumber, replyText))
    }
}

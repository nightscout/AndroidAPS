package app.aaps.plugins.sync.smsCommunicator.actions

import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.insulin.Insulin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileStore
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.smsCommunicator.Sms
import app.aaps.core.interfaces.smsCommunicator.SmsCommunicator
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.plugins.sync.R
import app.aaps.plugins.sync.smsCommunicator.SmsAction

/** Switches to the named profile at a given percentage: PROFILE <index> [<percentage>]. */
class ProfileSwitchAction(
    private val profileName: String,
    private val percentage: Int,
    private val receivedSms: Sms,
    private val store: ProfileStore,
    private val insulin: Insulin,
    private val profileFunction: ProfileFunction,
    private val dateUtil: DateUtil,
    private val rh: ResourceHelper,
    private val smsCommunicator: SmsCommunicator
) : SmsAction(pumpCommand = true) {

    override suspend fun run() {
        val iCfg = insulin.iCfg          // use Current running iCfg, changing iCfg with Automation not allowed
        if (profileFunction.createProfileSwitch(
                profileStore = store,
                profileName = profileName,
                durationInMinutes = 0,
                percentage = percentage,
                timeShiftInHours = 0,
                timestamp = dateUtil.now(),
                action = Action.PROFILE_SWITCH,
                source = Sources.SMS,
                note = rh.gs(R.string.sms_profile_switch_created),
                listValues = listOf(ValueWithUnit.SimpleString(rh.gsNotLocalised(R.string.sms_profile_switch_created))),
                iCfg = iCfg
            ) != null
        ) {
            val replyText = rh.gs(R.string.sms_profile_switch_created)
            smsCommunicator.sendSMS(Sms(receivedSms.phoneNumber, replyText))
        } else {
            smsCommunicator.sendSMS(Sms(receivedSms.phoneNumber, rh.gs(app.aaps.core.ui.R.string.invalid_profile)))
        }
    }
}

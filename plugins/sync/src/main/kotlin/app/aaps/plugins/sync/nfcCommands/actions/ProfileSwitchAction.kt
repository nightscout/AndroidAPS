package app.aaps.plugins.sync.nfcCommands.actions

import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.plugins.sync.R
import app.aaps.plugins.sync.nfcCommands.NfcCommandsPlugin
import app.aaps.plugins.sync.nfcCommands.NfcExecutionResult
import org.json.JSONObject
import app.aaps.core.ui.R as CoreUiR

class ProfileSwitchAction(plugin: NfcCommandsPlugin) : NfcAction(plugin) {
    override suspend fun execute(params: JSONObject, tagName: String?): NfcExecutionResult {
        val profileName = params.optString("profileName")
        if (profileName.isNullOrBlank()) return invalidFormat()
        val percentage = params.optInt("percentage", 100).coerceIn(10, 500)
        
        val profileStore = plugin.profileRepository.profile.value ?: return NfcExecutionResult(false, plugin.rh.gs(CoreUiR.string.notconfigured))
        
        val created = plugin.profileFunction.createProfileSwitch(
            profileStore = profileStore,
            profileName = profileName,
            durationInMinutes = 0,
            percentage = percentage,
            timeShiftInHours = 0,
            timestamp = plugin.dateUtil.now(),
            action = Action.PROFILE_SWITCH,
            source = source,
            note = plugin.rh.gs(R.string.nfccommands_profile_switch_created),
            listValues = listOf(ValueWithUnit.SimpleString(plugin.rh.gsNotLocalised(R.string.nfccommands_profile_switch_created))),
            iCfg = plugin.insulin.iCfg,
        )
        return if (created != null) {
            uel.log(
                action = Action.PROFILE_SWITCH,
                source = source,
                note = tagName,
                listValues = listOf(
                    ValueWithUnit.SimpleString(profileName),
                    ValueWithUnit.Percent(percentage)
                )
            )
            NfcExecutionResult(true, plugin.rh.gs(R.string.nfccommands_profile_switch_created))
        } else {
            NfcExecutionResult(false, plugin.rh.gs(CoreUiR.string.invalid_profile))
        }
    }
}

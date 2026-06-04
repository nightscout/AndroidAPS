package app.aaps.plugins.main.general.nfcCommands.actions

import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.plugins.main.general.nfcCommands.NfcCommandsPlugin
import app.aaps.plugins.main.general.nfcCommands.NfcExecutionResult
import app.aaps.plugins.main.R
import org.json.JSONObject

class ProfileSwitchAction(plugin: NfcCommandsPlugin) : NfcAction(plugin) {
    override suspend fun execute(params: JSONObject): NfcExecutionResult {
        val profileName = params.optString("profileName")
        if (profileName.isNullOrBlank()) return invalidFormat()
        val percentage = params.optInt("percentage", 100).coerceIn(10, 500)
        
        val profileStore = plugin.profileRepository.profile.value ?: return NfcExecutionResult(false, plugin.rh.gs(app.aaps.core.ui.R.string.notconfigured))
        
        val created = plugin.profileFunction.createProfileSwitch(
            profileStore = profileStore,
            profileName = profileName,
            durationInMinutes = 0,
            percentage = percentage,
            timeShiftInHours = 0,
            timestamp = plugin.dateUtil.now(),
            action = Action.PROFILE_SWITCH,
            source = Sources.NfcCommands,
            note = plugin.rh.gs(R.string.nfccommands_profile_switch_created),
            listValues = listOf(ValueWithUnit.SimpleString(plugin.rh.gsNotLocalised(R.string.nfccommands_profile_switch_created))),
            iCfg = plugin.insulin.iCfg,
        )
        return if (created != null) {
            NfcExecutionResult(true, plugin.rh.gs(R.string.nfccommands_profile_switch_created))
        } else {
            NfcExecutionResult(false, plugin.rh.gs(app.aaps.core.ui.R.string.invalid_profile))
        }
    }
}

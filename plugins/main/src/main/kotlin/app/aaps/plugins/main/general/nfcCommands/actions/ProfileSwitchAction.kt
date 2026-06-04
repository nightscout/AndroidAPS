package app.aaps.plugins.main.general.nfcCommands.actions

import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.utils.SafeParse
import app.aaps.plugins.main.general.nfcCommands.NfcCommandsPlugin
import app.aaps.plugins.main.general.nfcCommands.NfcExecutionResult
import app.aaps.plugins.main.R

class ProfileSwitchAction(plugin: NfcCommandsPlugin) : NfcAction(plugin) {
    override suspend fun execute(divided: List<String>): NfcExecutionResult {
        if (divided.size !in 2..3) return invalidFormat()
        val indexToken = divided[1]
        if (indexToken.any { !it.isDigit() }) return invalidFormat()
        val index = SafeParse.stringToInt(indexToken)
        val percentage = divided.getOrNull(2)?.let { SafeParse.stringToInt(it) } ?: 100
        val profileStore = plugin.profileRepository.profile.value ?: return NfcExecutionResult(false, plugin.rh.gs(app.aaps.core.ui.R.string.notconfigured))
        val list = profileStore.getProfileList()
        if (index <= 0 || percentage !in 10..500 || index > list.size) return invalidFormat()
        val name = list[index - 1] as String
        val created = plugin.profileFunction.createProfileSwitch(
            profileStore = profileStore,
            profileName = name,
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

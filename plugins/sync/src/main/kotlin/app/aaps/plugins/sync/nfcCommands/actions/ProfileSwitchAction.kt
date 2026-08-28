package app.aaps.plugins.sync.nfcCommands.actions

import androidx.annotation.StringRes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.ui.compose.navigation.icon
import app.aaps.plugins.sync.R
import app.aaps.plugins.sync.nfcCommands.ArgType
import app.aaps.plugins.sync.nfcCommands.NfcCommandsPlugin
import app.aaps.plugins.sync.nfcCommands.NfcExecutionResult
import app.aaps.plugins.sync.nfcCommands.NfcJsonKeys
import org.json.JSONObject
import app.aaps.core.ui.R as CoreUiR

class ProfileSwitchAction(plugin: NfcCommandsPlugin) : NfcAction(plugin) {
    @StringRes override val labelResId = CoreUiR.string.careportal_profileswitch
    override val elementType = ElementType.PROFILE_MANAGEMENT
    override val argType = listOf(ArgType.PROFILE_NAME, ArgType.PERCENT)
    override val icon
        get() = elementType.icon()
    override val customIconColor: @Composable () -> Color = {
        if (MaterialTheme.colorScheme.surface.luminance() > 0.5f) Color.Black else Color.White
    }

    override suspend fun getDefaultParams(): JSONObject {
        val profileName = plugin.profileFunction.getOriginalProfileName()
        return JSONObject().put(NfcJsonKeys.PROFILE_NAME, profileName).put(NfcJsonKeys.PERCENT, 100)
    }

    override suspend fun formatParams(): String? {
        val profileName = params.optString(NfcJsonKeys.PROFILE_NAME)
        val percentage = params.optInt(NfcJsonKeys.PERCENT, 100)
        return if (percentage == 100) profileName else "$profileName $percentage%"
    }

    override suspend fun execute(): NfcExecutionResult {
        val profileName = params.optString(NfcJsonKeys.PROFILE_NAME)
        if (profileName.isNullOrBlank()) return invalidFormat()
        val percentage = params.optInt(NfcJsonKeys.PERCENT, 100).coerceIn(10, 500)
        
        val profileStore = plugin.profileRepository.profile.value ?: return NfcExecutionResult(false, plugin.rh.gs(CoreUiR.string.notconfigured))
        
        val iCfg = plugin.profileFunction.getRunningOrRequestedICfg()
            ?: return NfcExecutionResult(false, plugin.rh.gs(CoreUiR.string.profile_switch_no_insulin))

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
            iCfg = iCfg,
        )
        return if (created != null) {
            val resultMessage = if (percentage == 100) profileName else "$profileName $percentage%"
            uel.log(
                action = Action.PROFILE_SWITCH,
                source = source,
                note = params.optString(NfcJsonKeys.TAG_NAME, ""),
                listValues = listOf(
                    ValueWithUnit.SimpleString(profileName),
                    ValueWithUnit.Percent(percentage)
                )
            )
            NfcExecutionResult(true, resultMessage)
        } else {
            NfcExecutionResult(false, plugin.rh.gs(CoreUiR.string.invalid_profile))
        }
    }
}

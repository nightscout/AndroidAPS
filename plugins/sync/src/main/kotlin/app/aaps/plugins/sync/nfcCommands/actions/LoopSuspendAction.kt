package app.aaps.plugins.sync.nfcCommands.actions

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import app.aaps.core.data.model.RM
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.icons.IcLoopPaused
import app.aaps.core.ui.compose.navigation.ElementType
import app.aaps.plugins.sync.nfcCommands.ArgType
import app.aaps.plugins.sync.nfcCommands.NfcCommandsPlugin
import app.aaps.plugins.sync.nfcCommands.NfcExecutionResult
import app.aaps.plugins.sync.nfcCommands.NfcJsonKeys
import app.aaps.plugins.sync.R
import org.json.JSONObject
import app.aaps.core.ui.R as CoreUiR

class LoopSuspendAction(plugin: NfcCommandsPlugin) : NfcAction(plugin) {
    @StringRes override val labelResId = CoreUiR.string.suspendloop
    override val elementType = ElementType.LOOP
    override val argType = listOf(ArgType.DURATION)
    override val icon = IcLoopPaused
    override val customIconColor: @Composable () -> Color = { AapsTheme.elementColors.loopSuspended }

    override suspend fun getDefaultParams(): JSONObject = 
        JSONObject().put(NfcJsonKeys.DURATION, 60)

    override suspend fun formatParams(): String {
        val duration = params.optInt(NfcJsonKeys.DURATION, 60)
        return plugin.rh.gs(CoreUiR.string.format_mins, duration)
    }

    override suspend fun execute(): NfcExecutionResult {
        val profile = plugin.profileFunction.getProfile() ?: return NfcExecutionResult(false, plugin.rh.gs(CoreUiR.string.noprofile))
        val duration = params.optInt(NfcJsonKeys.DURATION, 60)
        val normalizedDuration = duration.coerceIn(1, 180)
        
        if (!plugin.loop.allowedNextModes().contains(RM.Mode.SUSPENDED_BY_USER)) {
            return commandNotPossible()
        }
        val result = plugin.loop.handleRunningModeChange(
            newRM = RM.Mode.SUSPENDED_BY_USER,
            durationInMinutes = normalizedDuration,
            action = Action.SUSPEND,
            source = source,
            profile = profile,
        )
        if (result) {
            uel.log(
                action = Action.SUSPEND,
                source = source,
                note = params.optString(NfcJsonKeys.TAG_NAME, ""),
                listValues = listOf(
                    ValueWithUnit.RMMode(RM.Mode.SUSPENDED_BY_USER),
                    ValueWithUnit.Minute(normalizedDuration)
                )
            )
        }
        val message = if (result) {
            plugin.rh.gs(CoreUiR.string.loopsuspended) + " (" + plugin.rh.gs(CoreUiR.string.format_mins, normalizedDuration) + ")"
        } else {
            plugin.rh.gs(R.string.nfccommands_remote_command_not_possible)
        }
        return NfcExecutionResult(result, message)
    }
}

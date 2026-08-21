package app.aaps.plugins.sync.nfcCommands.actions

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import app.aaps.core.data.model.RM
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.icons.IcLoopDisconnected
import app.aaps.plugins.sync.nfcCommands.ArgType
import app.aaps.plugins.sync.nfcCommands.NfcCommandsPlugin
import app.aaps.plugins.sync.nfcCommands.NfcExecutionResult
import app.aaps.plugins.sync.nfcCommands.NfcJsonKeys
import app.aaps.plugins.sync.R
import org.json.JSONObject
import app.aaps.core.ui.R as CoreUiR

class PumpDisconnectAction(plugin: NfcCommandsPlugin) : NfcAction(plugin) {
    @StringRes override val labelResId = R.string.nfccommands_cmd_pump_disconnect
    override val elementType = ElementType.PUMP
    override val argType = listOf(ArgType.DURATION)
    override val icon = IcLoopDisconnected
    override val customIconColor: @Composable () -> Color = { AapsTheme.elementColors.loopDisconnected }

    override suspend fun getDefaultParams(): JSONObject = 
        JSONObject().put(NfcJsonKeys.DURATION, 30)

    override suspend fun formatParams(): String {
        val duration = params.optInt(NfcJsonKeys.DURATION, 30).coerceIn(1, 180)
        return plugin.rh.gs(CoreUiR.string.format_mins, duration)
    }

    override suspend fun execute(): NfcExecutionResult {
        val duration = params.optInt(NfcJsonKeys.DURATION, 30).coerceIn(1, 180)
        val profile = plugin.profileFunction.getProfile() ?: return NfcExecutionResult(false, plugin.rh.gs(CoreUiR.string.noprofile))

        val result = plugin.loop.handleRunningModeChange(
            durationInMinutes = duration,
            profile = profile,
            newRM = RM.Mode.DISCONNECTED_PUMP,
            action = Action.DISCONNECT,
            source = source,
            listValues = listOf(
                ValueWithUnit.RMMode(RM.Mode.DISCONNECTED_PUMP),
                ValueWithUnit.Minute(duration),
                ValueWithUnit.SimpleString(params.optString(NfcJsonKeys.TAG_NAME, ""))
            )
        )
        val message = if (result) {
            plugin.rh.gs(
                CoreUiR.string.text_with_detail,
                plugin.rh.gs(CoreUiR.string.pump_disconnected),
                plugin.rh.gs(CoreUiR.string.format_mins, duration)
            )
        } else {
            plugin.rh.gs(R.string.nfccommands_remote_command_not_possible)
        }
        return NfcExecutionResult(result, message)
    }
}

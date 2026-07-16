package app.aaps.plugins.sync.nfcCommands.actions

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import app.aaps.core.data.model.RM
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.icons.IcLoopClosed
import app.aaps.plugins.sync.R
import app.aaps.plugins.sync.nfcCommands.ArgType
import app.aaps.plugins.sync.nfcCommands.NfcCommandsPlugin
import app.aaps.plugins.sync.nfcCommands.NfcExecutionResult
import app.aaps.plugins.sync.nfcCommands.NfcJsonKeys
import app.aaps.core.ui.R as CoreUiR

class LoopClosedAction(plugin: NfcCommandsPlugin) : NfcAction(plugin) {
    @StringRes override val labelResId = R.string.nfccommands_cmd_loop_close
    override val elementType = ElementType.LOOP
    override val argType = listOf<ArgType>()
    override val icon = IcLoopClosed
    override val customIconColor: @Composable () -> Color = { AapsTheme.elementColors.loopClosed }

    override suspend fun execute(): NfcExecutionResult {
        val profile = plugin.profileFunction.getProfile() ?: return NfcExecutionResult(false, plugin.rh.gs(CoreUiR.string.noprofile))
        if (!plugin.loop.allowedNextModes().contains(RM.Mode.CLOSED_LOOP)) {
            return commandNotPossible()
        }
        val result = plugin.loop.handleRunningModeChange(
            newRM = RM.Mode.CLOSED_LOOP,
            action = Action.CLOSED_LOOP_MODE,
            source = source,
            profile = profile,
        )
        if (result) {
            uel.log(
                action = Action.CLOSED_LOOP_MODE,
                source = source,
                note = params.optString(NfcJsonKeys.TAG_NAME, ""),
                listValues = listOf(
                    ValueWithUnit.RMMode(RM.Mode.CLOSED_LOOP)
                )
            )
        }
        val message = if (result) {
            plugin.rh.gs(R.string.nfccommands_current_loop_mode, plugin.rh.gs(CoreUiR.string.closedloop))
        } else {
            plugin.rh.gs(R.string.nfccommands_remote_command_not_possible)
        }
        return NfcExecutionResult(result, message)
    }
}

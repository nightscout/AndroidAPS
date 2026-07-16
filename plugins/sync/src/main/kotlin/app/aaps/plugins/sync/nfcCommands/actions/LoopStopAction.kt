package app.aaps.plugins.sync.nfcCommands.actions

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import app.aaps.core.data.model.RM
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.icons.IcLoopDisabled
import app.aaps.plugins.sync.R
import app.aaps.plugins.sync.nfcCommands.ArgType
import app.aaps.plugins.sync.nfcCommands.NfcCommandsPlugin
import app.aaps.plugins.sync.nfcCommands.NfcExecutionResult
import app.aaps.plugins.sync.nfcCommands.NfcJsonKeys
import app.aaps.core.ui.R as CoreUiR

class LoopStopAction(plugin: NfcCommandsPlugin) : NfcAction(plugin) {
    @StringRes override val labelResId = R.string.nfccommands_cmd_loop_stop
    override val elementType = ElementType.LOOP
    override val argType = listOf<ArgType>()
    override val icon = IcLoopDisabled
    override val customIconColor: @Composable () -> Color = { AapsTheme.elementColors.loopDisabled }

    override suspend fun execute(): NfcExecutionResult {
        val profile = plugin.profileFunction.getProfile() ?: return NfcExecutionResult(false, plugin.rh.gs(CoreUiR.string.noprofile))
        if (!plugin.loop.allowedNextModes().contains(RM.Mode.DISABLED_LOOP)) {
            return NfcExecutionResult(false, plugin.rh.gs(CoreUiR.string.loopisdisabled))
        }
        val result = plugin.loop.handleRunningModeChange(
            newRM = RM.Mode.DISABLED_LOOP,
            durationInMinutes = Int.MAX_VALUE,
            action = Action.LOOP_DISABLED,
            source = source,
            profile = profile,
        )
        if (result) {
            uel.log(
                action = Action.LOOP_DISABLED,
                source = source,
                note = params.optString(NfcJsonKeys.TAG_NAME, ""),
                listValues = listOf(
                    ValueWithUnit.RMMode(RM.Mode.DISABLED_LOOP)
                )
            )
        }
        val messageId = if (result) R.string.nfccommands_loop_has_been_disabled else R.string.nfccommands_remote_command_not_possible
        return NfcExecutionResult(result, plugin.rh.gs(messageId))
    }
}

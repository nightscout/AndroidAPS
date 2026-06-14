package app.aaps.plugins.sync.nfcCommands.actions

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import app.aaps.core.data.model.RM
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.icons.IcLoopClosed
import app.aaps.core.ui.compose.navigation.ElementType
import app.aaps.plugins.sync.R
import app.aaps.plugins.sync.nfcCommands.ArgType
import app.aaps.plugins.sync.nfcCommands.NfcCommandsPlugin
import app.aaps.plugins.sync.nfcCommands.NfcExecutionResult
import app.aaps.plugins.sync.nfcCommands.NfcJsonKeys
import app.aaps.core.ui.R as CoreUiR

class LoopResumeAction(plugin: NfcCommandsPlugin) : NfcAction(plugin) {
    @StringRes override val labelResId = CoreUiR.string.resumeloop
    override val elementType = ElementType.LOOP
    override val argType = listOf<ArgType>()
    override val icon = IcLoopClosed
    override val customIconColor: @Composable () -> Color = { AapsTheme.elementColors.loopClosed }

    override suspend fun execute(): NfcExecutionResult {
        val profile = plugin.profileFunction.getProfile() ?: return NfcExecutionResult(false, plugin.rh.gs(CoreUiR.string.noprofile))
        if (!plugin.loop.allowedNextModes().contains(RM.Mode.RESUME) && plugin.loop.runningMode() != RM.Mode.DISABLED_LOOP) {
            return commandNotPossible()
        }
        val result = plugin.loop.handleRunningModeChange(
            newRM = RM.Mode.RESUME,
            action = Action.RESUME,
            source = source,
            profile = profile,
        )
        if (result) {
            uel.log(
                action = Action.RESUME,
                source = source,
                note = params.optString(NfcJsonKeys.TAG_NAME, ""),
                listValues = listOf(
                    ValueWithUnit.RMMode(RM.Mode.RESUME)
                )
            )
        }
        val messageId = if (result) R.string.nfccommands_loop_resumed else R.string.nfccommands_remote_command_not_possible
        return NfcExecutionResult(result, plugin.rh.gs(messageId))
    }
}

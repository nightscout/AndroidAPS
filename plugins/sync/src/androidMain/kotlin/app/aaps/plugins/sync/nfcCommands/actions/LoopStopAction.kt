package app.aaps.plugins.sync.nfcCommands.actions

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import app.aaps.core.data.model.RM
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.icons.IcLoopDisabled
import app.aaps.plugins.sync.SyncStrings
import app.aaps.plugins.sync.nfcCommands.ArgType
import app.aaps.plugins.sync.nfcCommands.NfcExecutionResult

class LoopStopAction(
    aapsLogger: AAPSLogger,
    rh: TextResolver,
    uel: UserEntryLogger,
    private val loop: Loop,
    private val profileFunction: ProfileFunction
) : NfcAction(aapsLogger, rh, uel) {
    override val label: TextRef = SyncStrings.nfccommands_cmd_loop_stop
    override val elementType = ElementType.LOOP
    override val argType = listOf<ArgType>()
    override val icon = IcLoopDisabled
    override val customIconColor: @Composable () -> Color = { AapsTheme.elementColors.loopDisabled }

    override suspend fun execute(tagName: String): NfcExecutionResult {
        val profile = profileFunction.getProfile() ?: return NfcExecutionResult(false, rh.gs(CoreUiStrings.noprofile))
        if (!loop.allowedNextModes().contains(RM.Mode.DISABLED_LOOP)) {
            return NfcExecutionResult(false, rh.gs(CoreUiStrings.loopisdisabled))
        }
        val result = loop.handleRunningModeChange(
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
                note = tagName,
                listValues = listOf(
                    ValueWithUnit.RMMode(RM.Mode.DISABLED_LOOP)
                )
            )
        }
        val message = if (result) SyncStrings.nfccommands_loop_has_been_disabled else SyncStrings.nfccommands_remote_command_not_possible
        return NfcExecutionResult(result, rh.gs(message))
    }
}

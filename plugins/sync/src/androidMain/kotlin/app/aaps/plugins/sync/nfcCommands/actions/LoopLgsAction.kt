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
import app.aaps.core.ui.compose.icons.IcLoopLgs
import app.aaps.plugins.sync.SyncStrings
import app.aaps.plugins.sync.nfcCommands.ArgType
import app.aaps.plugins.sync.nfcCommands.NfcExecutionResult

class LoopLgsAction(
    aapsLogger: AAPSLogger,
    rh: TextResolver,
    uel: UserEntryLogger,
    private val loop: Loop,
    private val profileFunction: ProfileFunction
) : NfcAction(aapsLogger, rh, uel) {
    override val label: TextRef = CoreUiStrings.lowglucosesuspend
    override val elementType = ElementType.LOOP
    override val argType = listOf<ArgType>()
    override val icon = IcLoopLgs
    override val customIconColor: @Composable () -> Color = { AapsTheme.elementColors.loopLgs }

    override suspend fun execute(tagName: String): NfcExecutionResult {
        val profile = profileFunction.getProfile() ?: return NfcExecutionResult(false, rh.gs(CoreUiStrings.noprofile))
        if (!loop.allowedNextModes().contains(RM.Mode.CLOSED_LOOP_LGS)) {
            return commandNotPossible()
        }
        val result = loop.handleRunningModeChange(
            newRM = RM.Mode.CLOSED_LOOP_LGS,
            action = Action.LGS_LOOP_MODE,
            source = source,
            profile = profile,
        )
        if (result) {
            uel.log(
                action = Action.LGS_LOOP_MODE,
                source = source,
                note = tagName,
                listValues = listOf(
                    ValueWithUnit.RMMode(RM.Mode.CLOSED_LOOP_LGS)
                )
            )
        }
        val message = if (result) {
            rh.gs(SyncStrings.nfccommands_current_loop_mode, rh.gs(CoreUiStrings.lowglucosesuspend))
        } else {
            rh.gs(SyncStrings.nfccommands_remote_command_not_possible)
        }
        return NfcExecutionResult(result, message)
    }
}

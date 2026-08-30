package app.aaps.plugins.sync.nfcCommands.actions

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import app.aaps.core.data.model.RM
import app.aaps.core.data.ue.Action
import app.aaps.core.interfaces.InterfacesStrings
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.icons.IcLoopReconnect
import app.aaps.plugins.sync.SyncStrings
import app.aaps.plugins.sync.nfcCommands.ArgType
import app.aaps.plugins.sync.nfcCommands.NfcExecutionResult

class PumpConnectAction(
    aapsLogger: AAPSLogger,
    rh: TextResolver,
    uel: UserEntryLogger,
    private val loop: Loop,
    private val profileFunction: ProfileFunction
) : NfcAction(aapsLogger, rh, uel) {
    override val label: TextRef = SyncStrings.nfccommands_cmd_pump_connect
    override val elementType = ElementType.PUMP
    override val argType = listOf<ArgType>()
    override val icon = IcLoopReconnect
    override val customIconColor: @Composable () -> Color = { AapsTheme.elementColors.loopClosed }

    override suspend fun execute(tagName: String): NfcExecutionResult {
        val profile = profileFunction.getProfile() ?: return NfcExecutionResult(false, rh.gs(CoreUiStrings.noprofile))
        if (!loop.allowedNextModes().contains(RM.Mode.RESUME)) {
            return NfcExecutionResult(true, rh.gs(InterfacesStrings.connected))
        }
        val result = loop.handleRunningModeChange(
            newRM = RM.Mode.RESUME,
            action = Action.RECONNECT,
            source = source,
            profile = profile,
        )
        if (result) {
            uel.log(
                action = Action.RECONNECT,
                source = source,
                note = tagName
            )
        }
        val message = if (result) SyncStrings.nfccommands_reconnect else SyncStrings.nfccommands_remote_command_not_possible
        return NfcExecutionResult(result, rh.gs(message))
    }
}

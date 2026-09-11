package app.aaps.plugins.sync.nfcCommands.actions

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import app.aaps.core.data.model.RM
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.ValueWithUnit
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
import app.aaps.core.ui.compose.icons.IcLoopDisconnected
import app.aaps.plugins.sync.SyncStrings
import app.aaps.plugins.sync.nfcCommands.ArgType
import app.aaps.plugins.sync.nfcCommands.NfcDefaults
import app.aaps.plugins.sync.nfcCommands.NfcExecutionResult
import app.aaps.plugins.sync.nfcCommands.NfcParams

class PumpDisconnectAction(
    aapsLogger: AAPSLogger,
    rh: TextResolver,
    uel: UserEntryLogger,
    private val loop: Loop,
    private val profileFunction: ProfileFunction
) : NfcAction(aapsLogger, rh, uel) {
    override val label: TextRef = SyncStrings.nfccommands_cmd_pump_disconnect
    override val elementType = ElementType.PUMP
    override val argType = listOf(ArgType.DURATION)
    override val icon = IcLoopDisconnected
    override val customIconColor: @Composable () -> Color = { AapsTheme.elementColors.loopDisconnected }

    override suspend fun getDefaultParams() = NfcParams(duration = NfcDefaults.PUMP_DISCONNECT_DURATION_MINUTES)

    override suspend fun formatParams(tagName: String): String {
        val duration = (params.duration ?: NfcDefaults.PUMP_DISCONNECT_DURATION_MINUTES)
            .coerceIn(NfcDefaults.PUMP_DISCONNECT_DURATION_RANGE)
        return rh.gs(CoreUiStrings.format_mins, duration)
    }

    override suspend fun execute(tagName: String): NfcExecutionResult {
        val duration = (params.duration ?: return invalidFormat())
            .coerceIn(NfcDefaults.PUMP_DISCONNECT_DURATION_RANGE)
        val profile = profileFunction.getProfile() ?: return NfcExecutionResult(false, rh.gs(CoreUiStrings.noprofile))

        val result = loop.handleRunningModeChange(
            durationInMinutes = duration,
            profile = profile,
            newRM = RM.Mode.DISCONNECTED_PUMP,
            action = Action.DISCONNECT,
            source = source,
            listValues = listOf(
                ValueWithUnit.RMMode(RM.Mode.DISCONNECTED_PUMP),
                ValueWithUnit.Minute(duration),
                ValueWithUnit.SimpleString(tagName)
            )
        )
        val message = if (result) {
            rh.gs(
                CoreUiStrings.text_with_detail,
                rh.gs(InterfacesStrings.pump_disconnected),
                rh.gs(CoreUiStrings.format_mins, duration)
            )
        } else {
            rh.gs(SyncStrings.nfccommands_remote_command_not_possible)
        }
        return NfcExecutionResult(result, message)
    }
}

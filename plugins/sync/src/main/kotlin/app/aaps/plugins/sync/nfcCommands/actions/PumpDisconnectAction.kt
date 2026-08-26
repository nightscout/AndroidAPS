package app.aaps.plugins.sync.nfcCommands.actions

import androidx.annotation.StringRes
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
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.icons.IcLoopDisconnected
import app.aaps.plugins.sync.nfcCommands.ArgType
import app.aaps.plugins.sync.nfcCommands.NfcExecutionResult
import app.aaps.plugins.sync.R
import app.aaps.core.interfaces.R as InterfacesR
import app.aaps.core.ui.R as CoreUiR
import app.aaps.plugins.sync.nfcCommands.NfcParams

class PumpDisconnectAction(
    aapsLogger: AAPSLogger,
    rh: ResourceHelper,
    uel: UserEntryLogger,
    private val loop: Loop,
    private val profileFunction: ProfileFunction
) : NfcAction(aapsLogger, rh, uel) {
    @StringRes override val labelResId = R.string.nfccommands_cmd_pump_disconnect
    override val elementType = ElementType.PUMP
    override val argType = listOf(ArgType.DURATION)
    override val icon = IcLoopDisconnected
    override val customIconColor: @Composable () -> Color = { AapsTheme.elementColors.loopDisconnected }

    override suspend fun getDefaultParams() = NfcParams(duration = 30)

    override suspend fun formatParams(tagName: String): String {
        val duration = (params.duration ?: 30).coerceIn(1, 180)
        return rh.gs(CoreUiR.string.format_mins, duration)
    }

    override suspend fun execute(tagName: String): NfcExecutionResult {
        val duration = (params.duration ?: 30).coerceIn(1, 180)
        val profile = profileFunction.getProfile() ?: return NfcExecutionResult(false, rh.gs(CoreUiR.string.noprofile))

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
                CoreUiR.string.text_with_detail,
                rh.gs(InterfacesR.string.pump_disconnected),
                rh.gs(CoreUiR.string.format_mins, duration)
            )
        } else {
            rh.gs(R.string.nfccommands_remote_command_not_possible)
        }
        return NfcExecutionResult(result, message)
    }
}

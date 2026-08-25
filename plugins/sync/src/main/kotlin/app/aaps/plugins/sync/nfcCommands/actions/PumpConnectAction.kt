package app.aaps.plugins.sync.nfcCommands.actions

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import app.aaps.core.data.model.RM
import app.aaps.core.data.ue.Action
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.icons.IcLoopReconnect
import app.aaps.plugins.sync.R
import app.aaps.plugins.sync.nfcCommands.ArgType
import app.aaps.plugins.sync.nfcCommands.NfcExecutionResult
import app.aaps.plugins.sync.nfcCommands.NfcJsonKeys
import app.aaps.core.ui.R as CoreUiR

class PumpConnectAction(
    aapsLogger: AAPSLogger,
    rh: ResourceHelper,
    uel: UserEntryLogger,
    private val loop: Loop,
    private val profileFunction: ProfileFunction
) : NfcAction(aapsLogger, rh, uel) {
    @StringRes override val labelResId = R.string.nfccommands_cmd_pump_connect
    override val elementType = ElementType.PUMP
    override val argType = listOf<ArgType>()
    override val icon = IcLoopReconnect
    override val customIconColor: @Composable () -> Color = { AapsTheme.elementColors.loopClosed }

    override suspend fun execute(): NfcExecutionResult {
        val profile = profileFunction.getProfile() ?: return NfcExecutionResult(false, rh.gs(CoreUiR.string.noprofile))
        if (!loop.allowedNextModes().contains(RM.Mode.RESUME)) {
            return NfcExecutionResult(true, rh.gs(app.aaps.core.interfaces.R.string.connected))
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
                note = params.optString(NfcJsonKeys.TAG_NAME, "")
            )
        }
        val messageId = if (result) R.string.nfccommands_reconnect else R.string.nfccommands_remote_command_not_possible
        return NfcExecutionResult(result, rh.gs(messageId))
    }
}

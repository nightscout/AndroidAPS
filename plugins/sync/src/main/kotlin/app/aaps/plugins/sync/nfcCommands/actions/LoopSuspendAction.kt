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
import app.aaps.core.ui.compose.icons.IcLoopPaused
import app.aaps.plugins.sync.nfcCommands.ArgType
import app.aaps.plugins.sync.nfcCommands.NfcExecutionResult
import app.aaps.plugins.sync.R
import app.aaps.core.interfaces.R as InterfacesR
import app.aaps.core.ui.R as CoreUiR
import app.aaps.plugins.sync.nfcCommands.NfcParams

class LoopSuspendAction(
    aapsLogger: AAPSLogger,
    rh: ResourceHelper,
    uel: UserEntryLogger,
    private val loop: Loop,
    private val profileFunction: ProfileFunction
) : NfcAction(aapsLogger, rh, uel) {
    @StringRes override val labelResId = CoreUiR.string.suspendloop
    override val elementType = ElementType.LOOP
    override val argType = listOf(ArgType.DURATION)
    override val icon = IcLoopPaused
    override val customIconColor: @Composable () -> Color = { AapsTheme.elementColors.loopSuspended }

    override suspend fun getDefaultParams() = NfcParams(duration = 60)

    override suspend fun formatParams(tagName: String): String {
        val duration = (params.duration ?: 60)
        return rh.gs(CoreUiR.string.format_mins, duration)
    }

    override suspend fun execute(tagName: String): NfcExecutionResult {
        val profile = profileFunction.getProfile() ?: return NfcExecutionResult(false, rh.gs(CoreUiR.string.noprofile))
        val duration = (params.duration ?: 60)
        val normalizedDuration = duration.coerceIn(1, 180)
        
        if (!loop.allowedNextModes().contains(RM.Mode.SUSPENDED_BY_USER)) {
            return commandNotPossible()
        }
        val result = loop.handleRunningModeChange(
            newRM = RM.Mode.SUSPENDED_BY_USER,
            durationInMinutes = normalizedDuration,
            action = Action.SUSPEND,
            source = source,
            profile = profile,
        )
        if (result) {
            uel.log(
                action = Action.SUSPEND,
                source = source,
                note = tagName,
                listValues = listOf(
                    ValueWithUnit.RMMode(RM.Mode.SUSPENDED_BY_USER),
                    ValueWithUnit.Minute(normalizedDuration)
                )
            )
        }
        val message = if (result) {
            rh.gs(
                CoreUiR.string.text_with_detail,
                rh.gs(InterfacesR.string.loopsuspended),
                rh.gs(CoreUiR.string.format_mins, normalizedDuration)
            )
        } else {
            rh.gs(R.string.nfccommands_remote_command_not_possible)
        }
        return NfcExecutionResult(result, message)
    }
}

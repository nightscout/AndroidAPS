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
import app.aaps.core.ui.compose.icons.IcLoopPaused
import app.aaps.plugins.sync.SyncStrings
import app.aaps.plugins.sync.nfcCommands.ArgType
import app.aaps.plugins.sync.nfcCommands.NfcDefaults
import app.aaps.plugins.sync.nfcCommands.NfcExecutionResult
import app.aaps.plugins.sync.nfcCommands.NfcParams

class LoopSuspendAction(
    aapsLogger: AAPSLogger,
    rh: TextResolver,
    uel: UserEntryLogger,
    private val loop: Loop,
    private val profileFunction: ProfileFunction
) : NfcAction(aapsLogger, rh, uel) {
    override val label: TextRef = CoreUiStrings.suspendloop
    override val elementType = ElementType.LOOP
    override val argType = listOf(ArgType.DURATION)
    override val icon = IcLoopPaused
    override val customIconColor: @Composable () -> Color = { AapsTheme.elementColors.loopSuspended }

    override suspend fun getDefaultParams() = NfcParams(duration = NfcDefaults.LOOP_SUSPEND_DURATION_MINUTES)

    override suspend fun formatParams(tagName: String): String {
        val duration = (params.duration ?: NfcDefaults.LOOP_SUSPEND_DURATION_MINUTES)
        return rh.gs(CoreUiStrings.format_mins, duration)
    }

    override suspend fun execute(tagName: String): NfcExecutionResult {
        val profile = profileFunction.getProfile() ?: return NfcExecutionResult(false, rh.gs(CoreUiStrings.noprofile))
        val duration = params.duration ?: return invalidFormat()
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
                CoreUiStrings.text_with_detail,
                rh.gs(InterfacesStrings.loopsuspended),
                rh.gs(CoreUiStrings.format_mins, normalizedDuration)
            )
        } else {
            rh.gs(SyncStrings.nfccommands_remote_command_not_possible)
        }
        return NfcExecutionResult(result, message)
    }
}

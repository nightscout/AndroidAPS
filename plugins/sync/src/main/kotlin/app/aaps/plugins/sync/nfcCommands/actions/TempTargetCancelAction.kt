package app.aaps.plugins.sync.nfcCommands.actions

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.icons.IcTtCancel
import app.aaps.plugins.sync.nfcCommands.ArgType
import app.aaps.plugins.sync.nfcCommands.NfcCommandsPlugin
import app.aaps.plugins.sync.nfcCommands.NfcExecutionResult
import app.aaps.plugins.sync.nfcCommands.NfcJsonKeys
import app.aaps.plugins.sync.R
import app.aaps.core.ui.R as CoreUiR

class TempTargetCancelAction(plugin: NfcCommandsPlugin) : NfcAction(plugin) {
    @StringRes override val labelResId = CoreUiR.string.stoptemptarget
    override val elementType = ElementType.TEMP_TARGET_MANAGEMENT
    override val argType = listOf<ArgType>()
    override val icon = IcTtCancel
    override val customIconColor: @Composable () -> Color = { AapsTheme.elementColors.loopDisabled }

    override suspend fun execute(): NfcExecutionResult {
        plugin.persistenceLayer.cancelCurrentTemporaryTargetIfAny(
            timestamp = plugin.dateUtil.now(),
            action = Action.CANCEL_TT,
            source = source,
            note = plugin.rh.gs(R.string.nfccommands_tt_canceled),
            listValues = listOf(ValueWithUnit.SimpleString(plugin.rh.gsNotLocalised(R.string.nfccommands_tt_canceled))),
        )
        uel.log(
            action = Action.CANCEL_TT,
            source = source,
            note = params.optString(NfcJsonKeys.TAG_NAME, "")
        )
        return NfcExecutionResult(true, plugin.rh.gs(R.string.nfccommands_tt_canceled))
    }
}

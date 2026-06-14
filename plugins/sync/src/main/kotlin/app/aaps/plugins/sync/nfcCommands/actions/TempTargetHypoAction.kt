package app.aaps.plugins.sync.nfcCommands.actions

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.TT
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.tempTargets.ttDurationMinutes
import app.aaps.core.interfaces.tempTargets.ttTargetMgdl
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.icons.IcTtHypo
import app.aaps.core.ui.compose.navigation.ElementType
import app.aaps.plugins.sync.nfcCommands.ArgType
import app.aaps.plugins.sync.nfcCommands.NfcCommandsPlugin
import app.aaps.plugins.sync.nfcCommands.NfcExecutionResult
import app.aaps.plugins.sync.nfcCommands.NfcJsonKeys
import app.aaps.plugins.sync.R
import java.util.concurrent.TimeUnit
import app.aaps.core.ui.R as CoreUiR

class TempTargetHypoAction(plugin: NfcCommandsPlugin) : NfcAction(plugin) {
    @StringRes override val labelResId = CoreUiR.string.hypo
    override val elementType = ElementType.TEMP_TARGET_MANAGEMENT
    override val argType = listOf<ArgType>()
    override val icon = IcTtHypo
    override val customIconColor: @Composable () -> Color = { AapsTheme.elementColors.loopDisabled }

    override suspend fun formatParams(): String {
        val units = plugin.profileUtil.units
        val ttDuration = plugin.preferences.ttDurationMinutes(TT.Reason.HYPOGLYCEMIA)
        val tt = plugin.profileUtil.fromMgdlToUnits(plugin.preferences.ttTargetMgdl(TT.Reason.HYPOGLYCEMIA), plugin.profileUtil.units)
        val ttString = if (units == GlucoseUnit.MMOL) plugin.decimalFormatter.to1Decimal(tt) else plugin.decimalFormatter.to0Decimal(tt)
        val unitLabel = if (units == GlucoseUnit.MMOL) "mmol/l" else "mg/dl"
        return "$ttString $unitLabel, ${ttDuration}min"
    }

    override suspend fun execute(): NfcExecutionResult {
        val units = plugin.profileUtil.units
        val ttDuration = plugin.preferences.ttDurationMinutes(TT.Reason.HYPOGLYCEMIA)
        val tt = plugin.profileUtil.fromMgdlToUnits(plugin.preferences.ttTargetMgdl(TT.Reason.HYPOGLYCEMIA), plugin.profileUtil.units)
        val reason = TT.Reason.HYPOGLYCEMIA

        plugin.persistenceLayer.insertAndCancelCurrentTemporaryTarget(
            temporaryTarget = TT(
                timestamp = plugin.dateUtil.now(),
                duration = TimeUnit.MINUTES.toMillis(ttDuration.toLong()),
                reason = reason,
                lowTarget = plugin.profileUtil.convertToMgdl(tt, plugin.profileUtil.units),
                highTarget = plugin.profileUtil.convertToMgdl(tt, plugin.profileUtil.units),
            ),
            action = Action.TT,
            source = source,
            note = params.optString(NfcJsonKeys.TAG_NAME, ""),
            listValues = listOf(
                ValueWithUnit.TETTReason(reason),
                ValueWithUnit.fromGlucoseUnit(tt, units),
                ValueWithUnit.Minute(ttDuration),
            ),
        )
        val ttString = if (units == GlucoseUnit.MMOL) plugin.decimalFormatter.to1Decimal(tt) else plugin.decimalFormatter.to0Decimal(tt)
        return NfcExecutionResult(true, plugin.rh.gs(R.string.nfccommands_tt_set, ttString, ttDuration))
    }
}

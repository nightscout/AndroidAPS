package app.aaps.plugins.sync.nfcCommands.actions

import androidx.annotation.StringRes
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.TT
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.ui.compose.icons.IcTtManual
import app.aaps.plugins.sync.nfcCommands.NfcCommandsPlugin
import app.aaps.plugins.sync.nfcCommands.NfcExecutionResult
import app.aaps.plugins.sync.R
import app.aaps.plugins.sync.nfcCommands.ArgType
import app.aaps.plugins.sync.nfcCommands.NfcJsonKeys
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import app.aaps.core.ui.R as CoreUiR

class TempTargetManualAction(plugin: NfcCommandsPlugin) : NfcAction(plugin) {
    @StringRes override val labelResId = CoreUiR.string.custom
    override val elementType = ElementType.TEMP_TARGET_MANAGEMENT
    override val argType = listOf(ArgType.GLUCOSE_TARGET, ArgType.DURATION)
    override val icon = IcTtManual
    
    override suspend fun getDefaultParams(): JSONObject = JSONObject().apply {
        val units = plugin.profileUtil.units
        val profile = plugin.profileFunction.getProfile()
        val defaultTargetMgdl = profile?.getTargetLowMgdl() ?: 100.0
        val defaultTarget = plugin.profileUtil.fromMgdlToUnits(defaultTargetMgdl, units)
        
        put(NfcJsonKeys.GLUCOSE, defaultTarget)
        put(NfcJsonKeys.DURATION, 60)
    }

    override suspend fun formatParams(): String {
        val units = plugin.profileUtil.units
        val glucose = params.optDouble(NfcJsonKeys.GLUCOSE, 0.0)
        val duration = params.optInt(NfcJsonKeys.DURATION, 0)
        val unitLabel = if (units == GlucoseUnit.MMOL) "mmol/l" else "mg/dl"
        val glucoseString = if (units == GlucoseUnit.MMOL) plugin.decimalFormatter.to1Decimal(glucose) else plugin.decimalFormatter.to0Decimal(glucose)
        return "$glucoseString $unitLabel, ${duration}min"
    }

    override suspend fun execute(): NfcExecutionResult {
        val units = plugin.profileUtil.units
        val glucose = params.optDouble(NfcJsonKeys.GLUCOSE, 0.0)
        val durationMinutes = params.optInt(NfcJsonKeys.DURATION, 60)

        if (glucose <= 0.0 || durationMinutes <= 0) return invalidFormat()

        val targetMgdl = plugin.profileUtil.convertToMgdl(glucose, units)
        val reason = TT.Reason.CUSTOM

        plugin.persistenceLayer.insertAndCancelCurrentTemporaryTarget(
            temporaryTarget = TT(
                timestamp = plugin.dateUtil.now(),
                duration = TimeUnit.MINUTES.toMillis(durationMinutes.toLong()),
                reason = reason,
                lowTarget = targetMgdl,
                highTarget = targetMgdl,
            ),
            action = Action.TT,
            source = source,
            note = params.optString(NfcJsonKeys.TAG_NAME, ""),
            listValues = listOf(
                ValueWithUnit.TETTReason(reason),
                ValueWithUnit.fromGlucoseUnit(glucose, units),
                ValueWithUnit.Minute(durationMinutes),
            ),
        )
        
        val ttString = if (units == GlucoseUnit.MMOL) plugin.decimalFormatter.to1Decimal(glucose) else plugin.decimalFormatter.to0Decimal(glucose)
        return NfcExecutionResult(true, plugin.rh.gs(R.string.nfccommands_tt_set, ttString, durationMinutes))
    }
}

package app.aaps.plugins.main.general.nfcCommands.actions

import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.TT
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.tempTargets.ttDurationMinutes
import app.aaps.core.interfaces.tempTargets.ttTargetMgdl
import app.aaps.plugins.main.general.nfcCommands.NfcCommandsPlugin
import app.aaps.plugins.main.general.nfcCommands.NfcExecutionResult
import app.aaps.plugins.main.R
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class TempTargetSetAction(plugin: NfcCommandsPlugin) : NfcAction(plugin) {
    override suspend fun execute(params: JSONObject, tagName: String?): NfcExecutionResult {
        val type = params.optString("type")
        val isMeal = type.equals("MEAL", ignoreCase = true)
        val isActivity = type.equals("ACTIVITY", ignoreCase = true)
        val isHypo = type.equals("HYPO", ignoreCase = true)
        
        if (!isMeal && !isActivity && !isHypo) return invalidFormat()
        
        val units = plugin.profileUtil.units
        var reason = TT.Reason.EATING_SOON
        var ttDuration = 0
        var tt = 0.0
        when {
            isMeal -> {
                ttDuration = plugin.preferences.ttDurationMinutes(TT.Reason.EATING_SOON)
                tt = plugin.profileUtil.fromMgdlToUnits(plugin.preferences.ttTargetMgdl(TT.Reason.EATING_SOON), plugin.profileUtil.units)
                reason = TT.Reason.EATING_SOON
            }
            isActivity -> {
                ttDuration = plugin.preferences.ttDurationMinutes(TT.Reason.ACTIVITY)
                tt = plugin.profileUtil.fromMgdlToUnits(plugin.preferences.ttTargetMgdl(TT.Reason.ACTIVITY), plugin.profileUtil.units)
                reason = TT.Reason.ACTIVITY
            }
            isHypo -> {
                ttDuration = plugin.preferences.ttDurationMinutes(TT.Reason.HYPOGLYCEMIA)
                tt = plugin.profileUtil.fromMgdlToUnits(plugin.preferences.ttTargetMgdl(TT.Reason.HYPOGLYCEMIA), plugin.profileUtil.units)
                reason = TT.Reason.HYPOGLYCEMIA
            }
        }
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
            note = tagName,
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

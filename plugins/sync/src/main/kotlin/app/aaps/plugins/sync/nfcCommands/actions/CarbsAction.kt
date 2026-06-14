package app.aaps.plugins.sync.nfcCommands.actions

import androidx.annotation.StringRes
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.objects.constraints.ConstraintObject
import app.aaps.core.ui.compose.icons.IcCarbs
import app.aaps.core.ui.compose.navigation.ElementType
import app.aaps.core.ui.compose.navigation.icon
import app.aaps.plugins.sync.nfcCommands.ArgType
import app.aaps.plugins.sync.nfcCommands.NfcCommandsPlugin
import app.aaps.plugins.sync.nfcCommands.NfcExecutionResult
import app.aaps.plugins.sync.nfcCommands.NfcJsonKeys
import org.json.JSONObject
import app.aaps.core.ui.R as CoreUiR

class CarbsAction(plugin: NfcCommandsPlugin) : NfcAction(plugin) {
    @StringRes override val labelResId = CoreUiR.string.carbs
    override val elementType = ElementType.CARBS
    override val argType = listOf(ArgType.AMOUNT_GRAMS)
    override val icon
        get() = elementType.icon()

    override suspend fun getDefaultParams(): JSONObject = 
        JSONObject().put(NfcJsonKeys.AMOUNT, 0)

    override suspend fun formatParams(): String {
        val grams = params.optInt(NfcJsonKeys.AMOUNT, 0)
        return plugin.rh.gs(CoreUiR.string.format_carbs, grams)
    }

    override suspend fun execute(): NfcExecutionResult {
        var grams = params.optInt(NfcJsonKeys.AMOUNT, 0)
        
        if (grams == 0) return invalidFormat()
        
        grams = plugin.constraintChecker.applyCarbsConstraints(ConstraintObject(grams, plugin.aapsLogger)).value()

        val detailedBolusInfo = DetailedBolusInfo().apply {
            carbs = grams.toDouble()
            timestamp = plugin.dateUtil.now()
        }
        val result = plugin.commandQueue.bolus(detailedBolusInfo)
        if (result.success) {
            uel.log(
                action = Action.CARBS,
                source = source,
                note = params.optString(NfcJsonKeys.TAG_NAME, ""),
                listValues = listOf(
                    ValueWithUnit.Gram(grams)
                )
            )
            return NfcExecutionResult(true, plugin.rh.gs(CoreUiR.string.format_carbs, grams))
        } else {
            plugin.aapsLogger.error(LTag.NFC, "carbs bolus failed: ${result.comment}")
            return commandNotPossible()
        }
    }
}

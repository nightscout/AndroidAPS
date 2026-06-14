package app.aaps.plugins.sync.nfcCommands.actions

import androidx.annotation.StringRes
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.objects.constraints.ConstraintObject
import app.aaps.core.ui.compose.icons.IcExtendedBolus
import app.aaps.core.ui.compose.navigation.ElementType
import app.aaps.core.ui.compose.navigation.icon
import app.aaps.plugins.sync.nfcCommands.ArgType
import app.aaps.plugins.sync.nfcCommands.NfcCommandsPlugin
import app.aaps.plugins.sync.nfcCommands.NfcExecutionResult
import app.aaps.plugins.sync.nfcCommands.NfcJsonKeys
import app.aaps.plugins.sync.R
import org.json.JSONObject
import app.aaps.core.ui.R as CoreUiR

class ExtendedSetAction(plugin: NfcCommandsPlugin) : NfcAction(plugin) {
    @StringRes override val labelResId = CoreUiR.string.extended_bolus
    override val elementType = ElementType.EXTENDED_BOLUS
    override val argType = listOf(ArgType.INSULIN, ArgType.DURATION)
    override val icon
        get() = elementType.icon()

    override suspend fun getDefaultParams(): JSONObject = 
        JSONObject().put(NfcJsonKeys.AMOUNT, 0.0).put(NfcJsonKeys.DURATION, 30)

    override suspend fun execute(): NfcExecutionResult {
        var amount = params.optDouble(NfcJsonKeys.AMOUNT, 0.0)
        val duration = params.optInt(NfcJsonKeys.DURATION, 0)
        
        if (amount <= 0.0 || duration <= 0) return invalidFormat()
        
        amount = plugin.constraintChecker.applyExtendedBolusConstraints(ConstraintObject(amount, plugin.aapsLogger)).value()
        
        val result = plugin.commandQueue.extendedBolus(amount, duration)
        if (result.success) {
            uel.log(
                action = Action.EXTENDED_BOLUS,
                source = source,
                note = params.optString(NfcJsonKeys.TAG_NAME, ""),
                listValues = listOf(
                    ValueWithUnit.Insulin(amount),
                    ValueWithUnit.Minute(duration)
                )
            )
            val amountString = plugin.decimalFormatter.to2Decimal(amount)
            return NfcExecutionResult(true, plugin.rh.gs(R.string.nfccommands_extended_set, amountString, duration))
        } else {
            plugin.aapsLogger.error(LTag.NFC, "extendedBolus failed: ${result.comment}")
            return commandNotPossible()
        }
    }
}

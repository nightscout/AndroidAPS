package app.aaps.plugins.sync.nfcCommands.actions

import androidx.annotation.StringRes
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.objects.constraints.ConstraintObject
import app.aaps.core.ui.compose.navigation.icon
import app.aaps.plugins.sync.nfcCommands.ArgType
import app.aaps.plugins.sync.nfcCommands.NfcExecutionResult
import app.aaps.plugins.sync.nfcCommands.NfcJsonKeys
import app.aaps.plugins.sync.R
import org.json.JSONObject
import app.aaps.core.ui.R as CoreUiR

class ExtendedSetAction(
    aapsLogger: AAPSLogger,
    rh: ResourceHelper,
    uel: UserEntryLogger,
    private val commandQueue: CommandQueue,
    private val constraintChecker: ConstraintsChecker,
    private val decimalFormatter: DecimalFormatter
) : NfcAction(aapsLogger, rh, uel) {
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
        
        amount = constraintChecker.applyExtendedBolusConstraints(ConstraintObject(amount, aapsLogger)).value()
        
        val result = commandQueue.extendedBolus(amount, duration)
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
            val amountString = decimalFormatter.to2Decimal(amount)
            return NfcExecutionResult(true, rh.gs(R.string.nfccommands_extended_set, amountString, duration))
        } else {
            aapsLogger.error(LTag.NFC, "extendedBolus failed: ${result.comment}")
            return commandNotPossible()
        }
    }
}

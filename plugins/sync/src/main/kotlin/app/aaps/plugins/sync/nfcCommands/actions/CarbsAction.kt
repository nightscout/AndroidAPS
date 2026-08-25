package app.aaps.plugins.sync.nfcCommands.actions

import androidx.annotation.StringRes
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.objects.constraints.ConstraintObject
import app.aaps.core.ui.compose.navigation.icon
import app.aaps.plugins.sync.nfcCommands.ArgType
import app.aaps.plugins.sync.nfcCommands.NfcExecutionResult
import app.aaps.plugins.sync.nfcCommands.NfcJsonKeys
import org.json.JSONObject
import app.aaps.core.interfaces.R as InterfacesR
import app.aaps.core.ui.R as CoreUiR

class CarbsAction(
    aapsLogger: AAPSLogger,
    rh: ResourceHelper,
    uel: UserEntryLogger,
    private val commandQueue: CommandQueue,
    private val constraintChecker: ConstraintsChecker,
    private val dateUtil: DateUtil
) : NfcAction(aapsLogger, rh, uel) {
    @StringRes override val labelResId = InterfacesR.string.carbs
    override val elementType = ElementType.CARBS
    override val argType = listOf(ArgType.AMOUNT_GRAMS)
    override val icon
        get() = elementType.icon()

    override suspend fun getDefaultParams(): JSONObject = 
        JSONObject().put(NfcJsonKeys.AMOUNT, 0)

    override suspend fun formatParams(): String {
        val grams = params.optInt(NfcJsonKeys.AMOUNT, 0)
        return rh.gs(InterfacesR.string.format_carbs, grams)
    }

    override suspend fun execute(): NfcExecutionResult {
        var grams = params.optInt(NfcJsonKeys.AMOUNT, 0)
        
        if (grams == 0) return invalidFormat()
        
        grams = constraintChecker.applyCarbsConstraints(ConstraintObject(grams, aapsLogger)).value()

        val detailedBolusInfo = DetailedBolusInfo().apply {
            carbs = grams.toDouble()
            timestamp = dateUtil.now()
        }
        val result = commandQueue.bolus(detailedBolusInfo)
        if (result.success) {
            uel.log(
                action = Action.CARBS,
                source = source,
                note = params.optString(NfcJsonKeys.TAG_NAME, ""),
                listValues = listOf(
                    ValueWithUnit.Gram(grams)
                )
            )
            return NfcExecutionResult(true, rh.gs(InterfacesR.string.format_carbs, grams))
        } else {
            aapsLogger.error(LTag.NFC, "carbs bolus failed: ${result.comment}")
            return commandNotPossible()
        }
    }
}

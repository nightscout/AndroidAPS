package app.aaps.plugins.sync.nfcCommands.actions

import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.InterfacesStrings
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.objects.constraints.ConstraintObject
import app.aaps.core.ui.compose.navigation.icon
import app.aaps.plugins.sync.nfcCommands.ArgType
import app.aaps.plugins.sync.nfcCommands.NfcDefaults
import app.aaps.plugins.sync.nfcCommands.NfcExecutionResult
import app.aaps.plugins.sync.nfcCommands.NfcParams

class CarbsAction(
    aapsLogger: AAPSLogger,
    rh: TextResolver,
    uel: UserEntryLogger,
    private val commandQueue: CommandQueue,
    private val constraintChecker: ConstraintsChecker,
    private val dateUtil: DateUtil
) : NfcAction(aapsLogger, rh, uel) {
    override val label: TextRef = InterfacesStrings.carbs
    override val elementType = ElementType.CARBS
    override val argType = listOf(ArgType.AMOUNT_GRAMS)
    override val icon
        get() = elementType.icon()

    override suspend fun getDefaultParams() = NfcParams(carbs = NfcDefaults.CARBS_GRAMS)

    override suspend fun formatParams(tagName: String): String {
        val grams = (params.carbs ?: NfcDefaults.CARBS_GRAMS)
        return rh.gs(InterfacesStrings.format_carbs, grams)
    }

    override suspend fun execute(tagName: String): NfcExecutionResult {
        var grams = params.carbs ?: return invalidFormat()
        
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
                note = tagName,
                listValues = listOf(
                    ValueWithUnit.Gram(grams)
                )
            )
            return NfcExecutionResult(true, rh.gs(InterfacesStrings.format_carbs, grams))
        } else {
            aapsLogger.error(LTag.NFC, "carbs bolus failed: ${result.comment}")
            return commandNotPossible()
        }
    }
}

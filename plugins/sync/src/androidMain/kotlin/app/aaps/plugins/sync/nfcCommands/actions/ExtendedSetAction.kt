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
import app.aaps.plugins.sync.nfcCommands.NfcDefaults
import app.aaps.plugins.sync.nfcCommands.NfcExecutionResult
import app.aaps.plugins.sync.R
import app.aaps.core.ui.R as CoreUiR
import app.aaps.plugins.sync.nfcCommands.NfcParams

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

    override suspend fun getDefaultParams() = NfcParams(
        insulin = NfcDefaults.EXTENDED_BOLUS_INSULIN,
        duration = NfcDefaults.EXTENDED_BOLUS_DURATION_MINUTES
    )

    override suspend fun execute(tagName: String): NfcExecutionResult {
        var amount = params.insulin ?: return invalidFormat()
        val duration = params.duration ?: return invalidFormat()
        
        if (amount <= 0.0 || duration <= 0) return invalidFormat()
        
        amount = constraintChecker.applyExtendedBolusConstraints(ConstraintObject(amount, aapsLogger)).value()
        
        val result = commandQueue.extendedBolus(amount, duration)
        if (result.success) {
            uel.log(
                action = Action.EXTENDED_BOLUS,
                source = source,
                note = tagName,
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

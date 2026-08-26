package app.aaps.plugins.sync.nfcCommands.actions

import androidx.annotation.StringRes
import app.aaps.core.data.pump.defs.PumpDescription
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.objects.constraints.ConstraintObject
import app.aaps.core.ui.compose.navigation.icon
import app.aaps.plugins.sync.nfcCommands.ArgType
import app.aaps.plugins.sync.nfcCommands.NfcExecutionResult
import app.aaps.plugins.sync.R
import app.aaps.core.ui.R as CoreUiR
import app.aaps.plugins.sync.nfcCommands.NfcParams

class TempBasalAbsoluteAction(
    aapsLogger: AAPSLogger,
    rh: ResourceHelper,
    uel: UserEntryLogger,
    private val activePlugin: ActivePlugin,
    private val commandQueue: CommandQueue,
    private val constraintChecker: ConstraintsChecker,
    private val profileFunction: ProfileFunction
) : NfcAction(aapsLogger, rh, uel) {
    @StringRes override val labelResId = R.string.nfccommands_cmd_basal_absolute
    override val elementType = ElementType.TEMP_BASAL
    override val argType = listOf(ArgType.RATE, ArgType.DURATION)
    override val icon
        get() = elementType.icon()

    override suspend fun getDefaultParams() =
        NfcParams(rate = 0.0, duration = pumpBasalDurationStep(activePlugin))

    override fun isSupported(): Boolean = 
        activePlugin.activePump.pumpDescription.tempBasalStyle == PumpDescription.ABSOLUTE

    override suspend fun formatParams(tagName: String): String {
        val tempBasal = (params.rate ?: 0.0)
        val durationStep = pumpBasalDurationStep(activePlugin)
        val rawDuration = (params.duration ?: durationStep)
        val duration = roundUpToStep(rawDuration, durationStep)

        val rate = rh.gs(CoreUiR.string.pump_base_basal_rate, tempBasal)
        val mins = rh.gs(CoreUiR.string.format_mins, duration)
        return "$rate $mins"
    }

    override suspend fun execute(tagName: String): NfcExecutionResult {
        val profile = profileFunction.getProfile() ?: return NfcExecutionResult(false, rh.gs(CoreUiR.string.noprofile))
        var tempBasal = (params.rate ?: 0.0)
        val durationStep = pumpBasalDurationStep(activePlugin)
        val rawDuration = (params.duration ?: durationStep)
        
        if (rawDuration <= 0) return invalidFormat()
        
        val duration = roundUpToStep(rawDuration, durationStep)
        tempBasal = constraintChecker.applyBasalConstraints(ConstraintObject(tempBasal, aapsLogger), profile).value()
        val result = commandQueue.tempBasalAbsolute(
            tempBasal,
            duration,
            true,
            profile,
            PumpSync.TemporaryBasalType.NORMAL,
        )
        if (result.success) {
            uel.log(
                action = Action.TEMP_BASAL,
                source = source,
                note = tagName,
                listValues = listOf(
                    ValueWithUnit.UnitPerHour(tempBasal),
                    ValueWithUnit.Minute(duration)
                )
            )
            return NfcExecutionResult(true, formatParams(tagName))
        } else {
            aapsLogger.error(LTag.NFC, "tempBasalAbsolute failed: ${result.comment}")
            return commandNotPossible()
        }
    }
}

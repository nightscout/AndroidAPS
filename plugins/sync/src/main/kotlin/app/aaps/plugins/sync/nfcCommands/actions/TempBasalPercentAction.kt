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
import app.aaps.core.ui.compose.icons.IcTbrLow
import app.aaps.plugins.sync.nfcCommands.ArgType
import app.aaps.plugins.sync.nfcCommands.NfcExecutionResult
import app.aaps.plugins.sync.nfcCommands.NfcJsonKeys
import app.aaps.plugins.sync.R
import org.json.JSONObject
import app.aaps.core.ui.R as CoreUiR

class TempBasalPercentAction(
    aapsLogger: AAPSLogger,
    rh: ResourceHelper,
    uel: UserEntryLogger,
    private val activePlugin: ActivePlugin,
    private val commandQueue: CommandQueue,
    private val constraintChecker: ConstraintsChecker,
    private val profileFunction: ProfileFunction
) : NfcAction(aapsLogger, rh, uel) {
    @StringRes override val labelResId = R.string.nfccommands_cmd_basal_percent
    override val elementType = ElementType.TEMP_BASAL
    override val argType = listOf(ArgType.PERCENT, ArgType.DURATION)
    override val icon = IcTbrLow

    override suspend fun getDefaultParams(): JSONObject = 
        JSONObject().put(NfcJsonKeys.PERCENT, 100).put(NfcJsonKeys.DURATION, pumpBasalDurationStep(activePlugin))

    override fun isSupported(): Boolean = 
        activePlugin.activePump.pumpDescription.tempBasalStyle == PumpDescription.PERCENT

    override suspend fun formatParams(): String {
        val tempBasalPct = params.optInt(NfcJsonKeys.PERCENT, 100)
        val durationStep = pumpBasalDurationStep(activePlugin)
        val rawDuration = params.optInt(NfcJsonKeys.DURATION, durationStep)
        val duration = roundUpToStep(rawDuration, durationStep)

        val pct = rh.gs(CoreUiR.string.format_percent, tempBasalPct)
        val mins = rh.gs(CoreUiR.string.format_mins, duration)
        return "$pct $mins"
    }

    override suspend fun execute(): NfcExecutionResult {
        val profile = profileFunction.getProfile() ?: return NfcExecutionResult(false, rh.gs(CoreUiR.string.noprofile))
        var tempBasalPct = params.optInt(NfcJsonKeys.PERCENT, 100)
        val durationStep = pumpBasalDurationStep(activePlugin)
        val rawDuration = params.optInt(NfcJsonKeys.DURATION, durationStep)
        
        if (rawDuration <= 0) return invalidFormat()
        
        val duration = roundUpToStep(rawDuration, durationStep)
        tempBasalPct = constraintChecker.applyBasalPercentConstraints(ConstraintObject(tempBasalPct, aapsLogger), profile).value()
        val result = commandQueue.tempBasalPercent(
            tempBasalPct,
            duration,
            true,
            profile,
            PumpSync.TemporaryBasalType.NORMAL,
        )
        if (result.success) {
            uel.log(
                action = Action.TEMP_BASAL,
                source = source,
                note = params.optString(NfcJsonKeys.TAG_NAME, ""),
                listValues = listOf(
                    ValueWithUnit.Percent(tempBasalPct),
                    ValueWithUnit.Minute(duration)
                )
            )
            return NfcExecutionResult(true, formatParams())
        } else {
            aapsLogger.error(LTag.NFC, "tempBasalPercent failed: ${result.comment}")
            return commandNotPossible()
        }
    }
}

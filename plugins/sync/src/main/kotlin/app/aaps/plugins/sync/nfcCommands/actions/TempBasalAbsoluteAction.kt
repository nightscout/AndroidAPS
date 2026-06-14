package app.aaps.plugins.sync.nfcCommands.actions

import androidx.annotation.StringRes
import app.aaps.core.data.pump.defs.PumpDescription
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.objects.constraints.ConstraintObject
import app.aaps.core.ui.compose.icons.IcTbrHigh
import app.aaps.core.ui.compose.navigation.ElementType
import app.aaps.core.ui.compose.navigation.icon
import app.aaps.plugins.sync.nfcCommands.ArgType
import app.aaps.plugins.sync.nfcCommands.NfcCommandsPlugin
import app.aaps.plugins.sync.nfcCommands.NfcExecutionResult
import app.aaps.plugins.sync.nfcCommands.NfcJsonKeys
import app.aaps.plugins.sync.R
import org.json.JSONObject
import app.aaps.core.ui.R as CoreUiR

class TempBasalAbsoluteAction(plugin: NfcCommandsPlugin) : NfcAction(plugin) {
    @StringRes override val labelResId = R.string.nfccommands_cmd_basal_absolute
    override val elementType = ElementType.TEMP_BASAL
    override val argType = listOf(ArgType.RATE, ArgType.DURATION)
    override val icon
        get() = elementType.icon()

    override suspend fun getDefaultParams(): JSONObject = 
        JSONObject().put(NfcJsonKeys.RATE, 0.0).put(NfcJsonKeys.DURATION, plugin.pumpBasalDurationStep())

    override fun isSupported(): Boolean = 
        plugin.activePlugin.activePump.pumpDescription.tempBasalStyle == PumpDescription.ABSOLUTE

    override suspend fun formatParams(): String {
        val tempBasal = params.optDouble(NfcJsonKeys.RATE, 0.0)
        val durationStep = plugin.pumpBasalDurationStep()
        val rawDuration = params.optInt(NfcJsonKeys.DURATION, durationStep)
        val duration = plugin.roundUpToStep(rawDuration, durationStep)

        val rate = plugin.rh.gs(CoreUiR.string.pump_base_basal_rate, tempBasal)
        val mins = plugin.rh.gs(CoreUiR.string.format_mins, duration)
        return "$rate $mins"
    }

    override suspend fun execute(): NfcExecutionResult {
        val profile = plugin.profileFunction.getProfile() ?: return NfcExecutionResult(false, plugin.rh.gs(CoreUiR.string.noprofile))
        var tempBasal = params.optDouble(NfcJsonKeys.RATE, 0.0)
        val durationStep = plugin.pumpBasalDurationStep()
        val rawDuration = params.optInt(NfcJsonKeys.DURATION, durationStep)
        
        if (rawDuration <= 0) return invalidFormat()
        
        val duration = plugin.roundUpToStep(rawDuration, durationStep)
        tempBasal = plugin.constraintChecker.applyBasalConstraints(ConstraintObject(tempBasal, plugin.aapsLogger), profile).value()
        val result = plugin.commandQueue.tempBasalAbsolute(
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
                note = params.optString(NfcJsonKeys.TAG_NAME, ""),
                listValues = listOf(
                    ValueWithUnit.UnitPerHour(tempBasal),
                    ValueWithUnit.Minute(duration)
                )
            )
            return NfcExecutionResult(true, formatParams())
        } else {
            plugin.aapsLogger.error(LTag.NFC, "tempBasalAbsolute failed: ${result.comment}")
            return commandNotPossible()
        }
    }
}

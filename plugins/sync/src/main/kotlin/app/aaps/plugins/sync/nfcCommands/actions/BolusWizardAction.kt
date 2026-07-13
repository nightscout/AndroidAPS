package app.aaps.plugins.sync.nfcCommands.actions

import androidx.annotation.StringRes
import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.time.T
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.BooleanNonKey
import app.aaps.core.keys.IntKey
import app.aaps.core.objects.wizard.BolusWizard
import app.aaps.core.ui.compose.icons.IcCalculator
import app.aaps.core.ui.compose.navigation.ElementType
import app.aaps.core.ui.compose.navigation.icon
import app.aaps.plugins.sync.nfcCommands.ArgType
import app.aaps.plugins.sync.nfcCommands.NfcCommandsPlugin
import app.aaps.plugins.sync.nfcCommands.NfcExecutionResult
import app.aaps.plugins.sync.nfcCommands.NfcJsonKeys
import app.aaps.plugins.sync.R
import org.json.JSONObject
import app.aaps.core.ui.R as CoreUiR

class BolusWizardAction(plugin: NfcCommandsPlugin) : NfcAction(plugin) {
    @StringRes override val labelResId = CoreUiR.string.boluswizard
    override val elementType = ElementType.BOLUS_WIZARD
    override val argType = listOf(ArgType.BOLUS_WIZARD_OPTIONS, ArgType.AMOUNT_GRAMS, ArgType.PERCENT)
    override val icon
        get() = elementType.icon()

    override suspend fun getDefaultParams(): JSONObject {
        // Load saved preferences
        val useTrend = plugin.preferences.get(BooleanNonKey.WizardIncludeTrend)
        val useCOB = plugin.preferences.get(BooleanNonKey.WizardIncludeCob)
        val showNotes = plugin.preferences.get(BooleanKey.OverviewShowNotesInDialogs)
        val useBolusAdvisor = plugin.preferences.get(BooleanKey.OverviewUseBolusAdvisor)

        // Percentage: reset to 100% if last BG is too old
        var percentage = plugin.preferences.get(IntKey.OverviewBolusPercentage)
        val time = plugin.preferences.get(IntKey.OverviewResetBolusPercentageTime).toLong()
        plugin.persistenceLayer.getLastGlucoseValue().let {
            if (it != null) {
                if (it.timestamp < plugin.dateUtil.now() - T.mins(time).msecs())
                    percentage = 100
            } else percentage = 100
        }
        return JSONObject().apply {
            put(NfcJsonKeys.AMOUNT, 0)
            put(NfcJsonKeys.PERCENT, percentage)
            put(NfcJsonKeys.USE_BG, true)
            put(NfcJsonKeys.USE_TT, true)
            put(NfcJsonKeys.USE_TREND, useTrend)
            put(NfcJsonKeys.USE_IOB, true)
            put(NfcJsonKeys.USE_COB, useCOB)
        }
    }

    override suspend fun formatParams(): String? {
        val amount = params.optInt(NfcJsonKeys.AMOUNT, 0)
        val wizard = performCalculation() ?: return null
        plugin.setActionState(params.toString(), wizard)

        val base = plugin.rh.gs(CoreUiR.string.goingtodeliver, wizard.insulinAfterConstraints)
        val carbs = plugin.rh.gs(CoreUiR.string.format_carbs, amount)
        return "$base ($carbs)"
    }

    override suspend fun execute(): NfcExecutionResult {
        if (plugin.commandQueue.bolusInQueue()) {
            return NfcExecutionResult(false, plugin.rh.gs(R.string.nfccommands_another_bolus_in_queue))
        }
        if (plugin.dateUtil.now() - plugin.lastRemoteBolusTime < Constants.remoteBolusMinDistance) {
            return NfcExecutionResult(false, plugin.rh.gs(R.string.nfccommands_remote_bolus_not_allowed))
        }
        if (plugin.loop.runningMode().pausesLoopExecution()) {
            return NfcExecutionResult(false, plugin.rh.gs(CoreUiR.string.pumpsuspended))
        }

        val wizard = (plugin.getActionState(params.toString()) as? BolusWizard)
        if (wizard == null) {
            plugin.aapsLogger.debug(LTag.NFC, "BolusWizard state not found. Key: ${params}")
            return commandNotPossible()
        }

        // Execute the bolus
        wizard.executeNormal(
            onError = { plugin.aapsLogger.error(LTag.NFC, "Calculator bolus failed: $it") }
        )

        plugin.setLastRemoteBolusTime(plugin.dateUtil.now())

        return NfcExecutionResult(true, plugin.rh.gs(R.string.smscommunicator_bolus_delivered, wizard.insulinAfterConstraints))
    }

    private suspend fun performCalculation(): BolusWizard? {
        val carbs = params.optInt(NfcJsonKeys.AMOUNT, 0)
        val percentage = params.optInt(NfcJsonKeys.PERCENT, 100)
        val useBg = params.optBoolean(NfcJsonKeys.USE_BG, true)
        val useTT = params.optBoolean(NfcJsonKeys.USE_TT, true)
        val useTrend = params.optBoolean(NfcJsonKeys.USE_TREND, true)
        val useIOB = params.optBoolean(NfcJsonKeys.USE_IOB, true)
        val useCOB = params.optBoolean(NfcJsonKeys.USE_COB, true)

        val profile = plugin.profileFunction.getProfile() ?: return null
        val profileName = plugin.profileFunction.getOriginalProfileName()
        val tempTarget = plugin.persistenceLayer.getTemporaryTargetActiveAt(plugin.dateUtil.now())
        val bg = plugin.glucoseStatusProvider.glucoseStatusData?.glucose ?: 0.0
        val cob = plugin.iobCobCalculator.getCobInfo("NFC").displayCob ?: 0.0

        val wizard = plugin.bolusWizard
        wizard.doCalc(
            profile = profile,
            profileName = profileName,
            tempTarget = tempTarget,
            carbs = carbs,
            cob = cob,
            bg = bg,
            correction = 0.0,
            useBg = useBg,
            useCob = useCOB,
            includeBolusIOB = useIOB,
            includeBasalIOB = useIOB,
            useSuperBolus = false,
            useTT = useTT,
            useTrend = useTrend,
            useAlarm = false,
            totalPercentage = percentage.toDouble(),
            usePercentage = true,
            notes = params.optString(NfcJsonKeys.TAG_NAME, ""),
            source = Sources.NfcCommands
        )
        return wizard
    }
}

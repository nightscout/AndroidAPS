package app.aaps.plugins.sync.nfcCommands.actions

import androidx.annotation.StringRes
import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.bolus.WizardBolusExecutor
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.BooleanNonKey
import app.aaps.core.keys.IntKey
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
        val useTrend = plugin.preferences.get(BooleanNonKey.WizardIncludeTrend)
        val useCOB = plugin.preferences.get(BooleanNonKey.WizardIncludeCob)
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
        return when (val prepared = prepareWizard()) {
            is WizardBolusExecutor.PrepareResult.Preview -> {
                // Park the SAME preview (bolusId + computed insulin) the confirm dialog just displayed —
                // execute() commits it by id through the shared WizardBolusExecutor (identical to wear /
                // client-control), instead of re-driving a shared/leftover BolusWizard instance.
                plugin.setActionState(params.toString(), prepared)
                val base = plugin.rh.gs(CoreUiR.string.goingtodeliver, prepared.insulin)
                val carbs = plugin.rh.gs(CoreUiR.string.format_carbs, amount)
                "$base ($carbs)"
            }
            is WizardBolusExecutor.PrepareResult.Error   -> prepared.message
            WizardBolusExecutor.PrepareResult.NoAction   -> null
        }
    }

    override suspend fun execute(): NfcExecutionResult {
        if (plugin.commandQueue.bolusInQueue()) {
            return NfcExecutionResult(false, plugin.rh.gs(R.string.nfccommands_another_bolus_in_queue))
        }
        if (plugin.dateUtil.now() - plugin.lastRemoteBolusTime < Constants.REMOTE_BOLUS_MIN_DISTANCE) {
            return NfcExecutionResult(false, plugin.rh.gs(R.string.nfccommands_remote_bolus_not_allowed))
        }
        if (plugin.loop.runningMode().pausesLoopExecution()) {
            return NfcExecutionResult(false, plugin.rh.gs(CoreUiR.string.pumpsuspended))
        }

        val prepared = plugin.getActionState(params.toString()) as? WizardBolusExecutor.PrepareResult.Preview
        if (prepared == null) {
            plugin.aapsLogger.debug(LTag.NFC, "BolusWizard state not found. Key: ${params}")
            return commandNotPossible()
        }

        // Commit the parked dose by id — the SAME consume-once relay wear/client-control use
        // (WizardBolusExecutor.confirm). A stale/already-consumed id returns NoPending instead of
        // silently re-delivering or no-op'ing without telling the caller.
        val result = plugin.wizardBolusExecutor.confirm(
            bolusId = prepared.bolusId,
            source = source,
            onError = { plugin.aapsLogger.error(LTag.NFC, "Calculator bolus failed: $it") }
        )
        if (result is WizardBolusExecutor.ConfirmResult.NoPending) {
            plugin.aapsLogger.debug(LTag.NFC, "BolusWizard confirm: no pending dose for id ${prepared.bolusId}")
            return commandNotPossible()
        }

        plugin.setLastRemoteBolusTime(plugin.dateUtil.now())
        return NfcExecutionResult(true, plugin.rh.gs(R.string.smscommunicator_bolus_delivered, prepared.insulin))
    }

    /**
     * Recompute + cap + park the dose through the shared [WizardBolusExecutor] — the SAME "prepare" entry
     * point wear uses (DataHandlerMobile.handleWizardPreCheck) and client-control. This always builds a
     * FRESH BolusWizard internally (via Provider), so the wizard's one-shot `accepted` delivery guard can
     * never leak across NFC scans, unlike the previous design which reused one shared instance forever.
     */
    private suspend fun prepareWizard(): WizardBolusExecutor.PrepareResult {
        val carbs = params.optInt(NfcJsonKeys.AMOUNT, 0)
        val percentage = params.optInt(NfcJsonKeys.PERCENT, 100)
        val useBg = params.optBoolean(NfcJsonKeys.USE_BG, true)
        val useTT = params.optBoolean(NfcJsonKeys.USE_TT, true)
        val useTrend = params.optBoolean(NfcJsonKeys.USE_TREND, true)
        val useIOB = params.optBoolean(NfcJsonKeys.USE_IOB, true)
        val useCOB = params.optBoolean(NfcJsonKeys.USE_COB, true)
        val bgMgdl = plugin.glucoseStatusProvider.glucoseStatusData?.glucose ?: 0.0

        return plugin.wizardBolusExecutor.prepareWizard(
            WizardBolusExecutor.WizardInputs(
                bg = plugin.profileUtil.fromMgdlToUnits(bgMgdl),
                carbs = carbs,
                percentage = percentage,
                directCorrection = 0.0,
                carbTime = 0,
                useBg = useBg,
                useCob = useCOB,
                useIob = useIOB,
                useTt = useTT,
                useTrend = useTrend,
                alarm = false,
                notes = params.optString(NfcJsonKeys.TAG_NAME, ""),
                source = source
            )
        )
    }
}
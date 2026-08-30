package app.aaps.plugins.sync.nfcCommands.actions

import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.InterfacesStrings
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.bolus.WizardBolusExecutor
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.iob.GlucoseStatusProvider
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.BooleanNonKey
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.navigation.icon
import app.aaps.plugins.sync.SyncStrings
import app.aaps.plugins.sync.nfcCommands.ArgType
import app.aaps.plugins.sync.nfcCommands.NfcDefaults
import app.aaps.plugins.sync.nfcCommands.NfcExecutionResult
import app.aaps.plugins.sync.nfcCommands.NfcParams
import app.aaps.plugins.sync.nfcCommands.NfcRuntimeState

class BolusWizardAction(
    aapsLogger: AAPSLogger,
    rh: TextResolver,
    uel: UserEntryLogger,
    private val commandQueue: CommandQueue,
    private val dateUtil: DateUtil,
    private val glucoseStatusProvider: GlucoseStatusProvider,
    private val loop: Loop,
    private val persistenceLayer: PersistenceLayer,
    private val preferences: Preferences,
    private val profileUtil: ProfileUtil,
    private val runtimeState: NfcRuntimeState,
    private val wizardBolusExecutor: WizardBolusExecutor
) : NfcAction(aapsLogger, rh, uel) {
    override val label: TextRef = CoreUiStrings.boluswizard
    override val elementType = ElementType.BOLUS_WIZARD
    override val argType = listOf(ArgType.BOLUS_WIZARD_OPTIONS, ArgType.AMOUNT_GRAMS, ArgType.PERCENT)
    override val icon
        get() = elementType.icon()

    override suspend fun getDefaultParams(): NfcParams {
        val useTrend = preferences.get(BooleanNonKey.WizardIncludeTrend)
        val useCOB = preferences.get(BooleanNonKey.WizardIncludeCob)
        var percentage = preferences.get(IntKey.OverviewBolusPercentage)
        val time = preferences.get(IntKey.OverviewResetBolusPercentageTime).toLong()
        persistenceLayer.getLastGlucoseValue().let {
            if (it != null) {
                if (it.timestamp < dateUtil.now() - T.mins(time).msecs())
                    percentage = NfcDefaults.WIZARD_PERCENTAGE
            } else percentage = NfcDefaults.WIZARD_PERCENTAGE
        }
        // useBg, useTt and useIob are not repeated here: NfcParams already declares them true, and
        // stating the same default in two places is how the others stopped matching.
        return NfcParams(
            carbs = NfcDefaults.CARBS_GRAMS,
            percent = percentage,
            useTrend = useTrend,
            useCob = useCOB
        )
    }

    override suspend fun formatParams(tagName: String): String? {
        val amount = (params.carbs ?: NfcDefaults.CARBS_GRAMS)
        return when (val prepared = prepareWizard(tagName)) {
            is WizardBolusExecutor.PrepareResult.Preview -> {
                // Park the SAME preview (bolusId + computed insulin) the confirm dialog just displayed —
                // execute() commits it by id through the shared WizardBolusExecutor (identical to wear /
                // client-control), instead of re-driving a shared/leftover BolusWizard instance.
                runtimeState.setWizardPreview(params.toString(), prepared)
                val base = rh.gs(CoreUiStrings.goingtodeliver, prepared.insulin)
                val carbs = rh.gs(InterfacesStrings.format_carbs, amount)
                "$base ($carbs)"
            }
            is WizardBolusExecutor.PrepareResult.Error   -> prepared.message
            WizardBolusExecutor.PrepareResult.NoAction   -> null
        }
    }

    override suspend fun execute(tagName: String): NfcExecutionResult {
        if (commandQueue.bolusInQueue()) {
            return NfcExecutionResult(false, rh.gs(SyncStrings.nfccommands_another_bolus_in_queue))
        }
        if (dateUtil.now() - runtimeState.lastRemoteBolusTime < Constants.REMOTE_BOLUS_MIN_DISTANCE) {
            return NfcExecutionResult(false, rh.gs(SyncStrings.nfccommands_remote_bolus_not_allowed))
        }
        if (loop.runningMode().pausesLoopExecution()) {
            return NfcExecutionResult(false, rh.gs(InterfacesStrings.pumpsuspended))
        }

        val prepared = runtimeState.getWizardPreview(params.toString())
        if (prepared == null) {
            aapsLogger.debug(LTag.NFC, "BolusWizard state not found. Key: ${params}")
            return commandNotPossible()
        }

        // Commit the parked dose by id — the SAME consume-once relay wear/client-control use
        // (WizardBolusExecutor.confirm). A stale/already-consumed id returns NoPending instead of
        // silently re-delivering or no-op'ing without telling the caller.
        val result = wizardBolusExecutor.confirm(
            bolusId = prepared.bolusId,
            source = source,
            onError = { aapsLogger.error(LTag.NFC, "Calculator bolus failed: $it") }
        )
        if (result is WizardBolusExecutor.ConfirmResult.NoPending) {
            aapsLogger.debug(LTag.NFC, "BolusWizard confirm: no pending dose for id ${prepared.bolusId}")
            return commandNotPossible()
        }

        runtimeState.lastRemoteBolusTime = dateUtil.now()
        return NfcExecutionResult(true, rh.gs(SyncStrings.smscommunicator_bolus_delivered, prepared.insulin))
    }

    /**
     * Recompute + cap + park the dose through the shared [WizardBolusExecutor] — the SAME "prepare" entry
     * point wear uses (DataHandlerMobile.handleWizardPreCheck) and client-control. This always builds a
     * FRESH BolusWizard internally (via Provider), so the wizard's one-shot `accepted` delivery guard can
     * never leak across NFC scans, unlike the previous design which reused one shared instance forever.
     */
    private suspend fun prepareWizard(tagName: String): WizardBolusExecutor.PrepareResult {
        val carbs = (params.carbs ?: NfcDefaults.CARBS_GRAMS)
        val percentage = (params.percent ?: NfcDefaults.WIZARD_PERCENTAGE)
        val useBg = params.useBg
        val useTT = params.useTt
        val useTrend = params.useTrend
        val useIOB = params.useIob
        val useCOB = params.useCob
        val bgMgdl = glucoseStatusProvider.glucoseStatusData?.glucose ?: 0.0

        return wizardBolusExecutor.prepareWizard(
            WizardBolusExecutor.WizardInputs(
                bg = profileUtil.fromMgdlToUnits(bgMgdl),
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
                notes = tagName,
                source = source
            )
        )
    }
}
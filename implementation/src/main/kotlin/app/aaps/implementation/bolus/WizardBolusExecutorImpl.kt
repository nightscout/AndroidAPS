package app.aaps.implementation.bolus

import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.model.BCR
import app.aaps.core.data.model.BS
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.ICfg
import app.aaps.core.data.model.RM
import app.aaps.core.data.model.TE
import app.aaps.core.data.model.TT
import app.aaps.core.data.pump.defs.PumpDescription
import app.aaps.core.data.time.T
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.data.ui.ConfirmationLine
import app.aaps.core.data.ui.ConfirmationRole
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.automation.Automation
import app.aaps.core.interfaces.bolus.BatchAction
import app.aaps.core.interfaces.bolus.WizardBolusExecutor
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.di.ApplicationScope
import app.aaps.core.interfaces.insulin.Insulin
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileRepository
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.pump.defs.determineCorrectBolusStepSize
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.objects.constraints.ConstraintObject
import app.aaps.core.objects.profile.ProfileSealed
import app.aaps.core.objects.runningMode.PumpCommandGate
import app.aaps.core.objects.runningMode.RunningModeGuard
import app.aaps.core.objects.wizard.BolusWizard
import app.aaps.core.objects.wizard.QuickWizard
import app.aaps.core.objects.wizard.QuickWizardEntry
import app.aaps.core.ui.R
import app.aaps.core.ui.compose.formatMinutesAsDuration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.ceil

/**
 * [WizardBolusExecutor] implementation. The `BolusWizard`/`QuickWizardEntry` compute objects stay
 * **internal** here (`:implementation` sees `core:objects`); the interface exposes only the primitive
 * result, so it can live in `core:interfaces` with no `core:objects` dependency.
 */
@Singleton
class WizardBolusExecutorImpl @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val rh: ResourceHelper,
    private val config: Config,
    private val quickWizard: QuickWizard,
    private val bolusWizardProvider: Provider<BolusWizard>,
    private val profileFunction: ProfileFunction,
    private val profileRepository: ProfileRepository,
    private val insulin: Insulin,
    private val iobCobCalculator: IobCobCalculator,
    private val constraintChecker: ConstraintsChecker,
    private val activePlugin: ActivePlugin,
    private val runningModeGuard: RunningModeGuard,
    private val commandQueue: CommandQueue,
    private val persistenceLayer: PersistenceLayer,
    private val uel: UserEntryLogger,
    private val loop: Loop,
    private val dateUtil: DateUtil,
    private val decimalFormatter: DecimalFormatter,
    private val profileUtil: ProfileUtil,
    private val automation: Automation,
    private val notificationManager: NotificationManager,
    private val bolusProgressData: BolusProgressData,
    @ApplicationScope private val appScope: CoroutineScope
) : WizardBolusExecutor {

    /**
     * The consumed-once slot. [entry] is non-null only for a quick-wizard prepare (carb timing / super-bolus /
     * eCarbs). [carbTimeMinutes]/[notes] are carried so a manual-wizard prepare (no entry) delivers identically.
     */
    private enum class BolusMode {

        WIZARD, FIXED
    }

    private data class PendingBolus(
        val insulin: Double,
        val carbs: Int,
        val bcr: BCR?,
        val bolusId: Long,
        val entry: QuickWizardEntry?,
        val carbTimeMinutes: Int = 0,
        // Manual-wizard "time to eat" reminder flag. A QuickWizard ignores this and reads its own
        // entry.useAlarm() at confirm() instead; parked here so a MANUAL wizard (entry == null) can
        // still schedule the reminder on the master — without it the manual-wizard alarm was dropped.
        val useAlarm: Boolean = false,
        val notes: String? = null,
        val mode: BolusMode = BolusMode.WIZARD,
        val eventType: TE.Type? = null,
        val carbsDurationHours: Int = 0,
        val eCarbsGrams: Int = 0,
        val eCarbsDelayMinutes: Int = 0,
        val eCarbsDurationHours: Int = 0,
        // Batch extension: a FIXED prepare may carry a temp target + a record-only flag/insulin/back-date,
        // delivered together in [confirm] (decision-B order). Null/false for a plain fixed or wizard prepare.
        val tempTarget: BatchAction.TempTarget? = null,
        val profileSwitch: BatchAction.ProfileSwitch? = null,
        val runningMode: BatchAction.RunningMode? = null,
        val tempBasal: BatchAction.TempBasal? = null,
        val extendedBolus: BatchAction.ExtendedBolus? = null,
        val cancelTempBasal: Boolean = false,
        val cancelExtendedBolus: Boolean = false,
        val insulinActivate: BatchAction.InsulinActivate? = null,
        // Careportal events — a LIST (defensive; clients currently send one per batch), unlike the ≤1 single-slot types.
        val therapyEvents: List<BatchAction.TherapyEvent> = emptyList(),
        // Edits of existing therapy events (location/arrow/note) — likewise list-handled; the master updates in place.
        val therapyEventEdits: List<BatchAction.TherapyEventEdit> = emptyList(),
        val recordOnly: Boolean = false,
        val iCfg: ICfg? = null,
        val bolusTimestamp: Long? = null
    )

    // Consume-once slots keyed by bolusId — a map, NOT a single var, so prepares from different actors (the
    // master's own dialogs, the watch, and EVERY paired client) don't clobber each other's parked dose, and a
    // commit only ever consumes the dose with the MATCHING id. remove(bolusId) is atomic, so two concurrent
    // commits of the same id can't both deliver — the loser gets null → NoPending (no double bolus).
    private val pending = ConcurrentHashMap<Long, PendingBolus>()
    private val pendingTtlMs = 10 * 60_000L // a parked dose unconfirmed this long is abandoned; trimmed on the next prepare

    // bolusId is a timestamp, so a key older than the TTL is an abandoned prepare — drop it so the map can't grow.
    private fun evictStalePending() {
        val cutoff = dateUtil.now() - pendingTtlMs
        pending.keys.removeIf { it < cutoff }
    }

    override fun setPending(insulin: Double, carbs: Int, bolusCalculatorResult: BCR?, bolusId: Long) {
        evictStalePending()
        pending[bolusId] = PendingBolus(insulin, carbs, bolusCalculatorResult, bolusId, entry = null)
    }

    override fun clearPending() {
        // Per-id keying already prevents a stale prepare from leaking into an unrelated commit; just trim stale ids.
        evictStalePending()
    }

    override suspend fun prepareQuickWizard(guid: String): WizardBolusExecutor.PrepareResult {
        // Reject before plugins are wired up: a remote prepare (client-control) can land while the master is still
        // initializing — before ConfigBuilder.initialize() ran verifySelectionInCategories(). At that point activeAPS
        // is still null (unlike activePump, which has an init-time fallback), so getProfile() builds a profile whose
        // BolusWizard.doCalc would hit ProfileSealed's "APS not defined" guard. Mirrors the appInitialized gate every
        // other external trigger (wear, automation, widgets) already has.
        if (!config.appInitialized) return WizardBolusExecutor.PrepareResult.Error(rh.gs(R.string.initializing))
        runningModeGuard.rejectionMessage(PumpCommandGate.CommandKind.BOLUS)?.let { return WizardBolusExecutor.PrepareResult.Error(it) }
        val actualBg = iobCobCalculator.ads.actualBg()
        val profile = profileFunction.getProfile()
        val profileName = profileFunction.getProfileName()
        val entry = quickWizard.get(guid) ?: return WizardBolusExecutor.PrepareResult.Error(rh.gs(R.string.quick_wizard_not_available))
        if (actualBg == null) return WizardBolusExecutor.PrepareResult.Error(rh.gs(R.string.wizard_no_actual_bg))
        if (profile == null) return WizardBolusExecutor.PrepareResult.Error(rh.gs(R.string.wizard_no_active_profile))
        // No COB gate: QuickWizardEntry.doCalc treats a null COB as 0 (only ever LOWERS the dose = safe),
        // so requiring COB here was stricter than the local QuickWizard and the real "missing data" mismatch.
        val pump = activePlugin.activePump
        if (!pump.isInitialized()) return WizardBolusExecutor.PrepareResult.Error(rh.gs(R.string.wizard_pump_not_available))

        val wizard = entry.doCalc(profile, profileName, actualBg)

        val carbsAfterConstraints = constraintChecker.applyCarbsConstraints(ConstraintObject(entry.carbs(), aapsLogger)).value()
        if (carbsAfterConstraints != entry.carbs()) return WizardBolusExecutor.PrepareResult.Error(rh.gs(R.string.wizard_carbs_constraint))
        val insulinAfterConstraints = wizard.insulinAfterConstraints
        val minStep = pump.pumpDescription.pumpType.determineCorrectBolusStepSize(insulinAfterConstraints)
        if (abs(insulinAfterConstraints - wizard.calculatedTotalInsulin) >= minStep)
            return WizardBolusExecutor.PrepareResult.Error(rh.gs(R.string.wizard_constraint_bolus_size, wizard.calculatedTotalInsulin))
        // A correction-only QuickWizard (0 carbs) that nets to nothing → reject rather than show an empty confirm.
        if (wizard.calculatedTotalInsulin <= 0.0 && wizard.carbs <= 0)
            return WizardBolusExecutor.PrepareResult.Error(rh.gs(R.string.wizard_no_insulin_required))

        evictStalePending()
        pending[wizard.timeStamp] = PendingBolus(wizard.unclampedCalculatedInsulin, wizard.carbs, wizard.createBolusCalculatorResult(), wizard.timeStamp, entry, carbTimeMinutes = entry.carbTime(), notes = entry.buttonText())
        // Build the master's color-coded confirmation lines here so the client renders the master's EXACT
        // wizard confirmation (shared builder). advisorApplies offers the high-BG "correct now, eat later" fork.
        val advisorApplies = wizard.needsBolusAdvisor()
        val eCarbsGrams = if (entry.useEcarbs() == QuickWizardEntry.YES) entry.carbs2() else 0
        return WizardBolusExecutor.PrepareResult.Preview(
            insulin = wizard.calculatedTotalInsulin,
            carbs = wizard.carbs,
            bolusId = wizard.timeStamp,
            lines = wizard.buildConfirmationLines(advisor = false, quickWizardEntry = entry),
            advisorApplies = advisorApplies,
            advisorLines = if (advisorApplies) wizard.buildConfirmationLines(advisor = true, quickWizardEntry = entry) else emptyList(),
            wizardDetail = wizard.buildWizardDetail().copy(
                eCarbsGrams = eCarbsGrams,
                eCarbsDelayMinutes = if (eCarbsGrams > 0) entry.time() else 0,
                eCarbsDurationHours = if (eCarbsGrams > 0) entry.duration() else 0,
                carbTimeMinutes = entry.carbTime(),
                alarm = entry.useAlarm() == QuickWizardEntry.YES && entry.carbTime() > 0,
                maxBolus = constraintChecker.getMaxBolusAllowed().value(),
                bolusStep = pump.pumpDescription.pumpType.determineCorrectBolusStepSize(wizard.insulinAfterConstraints),
            ),
        )
    }

    override suspend fun prepareWizard(inputs: WizardBolusExecutor.WizardInputs): WizardBolusExecutor.PrepareResult {
        // Same pre-init guard as prepareQuickWizard: doCalc would otherwise hit ProfileSealed's "APS not defined" guard
        // when a remote prepare arrives before verifySelectionInCategories() has populated activeAPS.
        if (!config.appInitialized) return WizardBolusExecutor.PrepareResult.Error(rh.gs(R.string.initializing))
        runningModeGuard.rejectionMessage(PumpCommandGate.CommandKind.BOLUS)?.let { return WizardBolusExecutor.PrepareResult.Error(it) }
        // Resolve the dialog's profile selection: null → the master's active profile (kept dynamic — the master is
        // authoritative); a name → that stored profile (a client/watch relays a name the master owns), wrapped so
        // doCalc sees a full Profile. An unknown name fails the prepare (the picker offered a profile the master lacks).
        // Captured to a local val first: a cross-module public property can't be smart-cast to non-null.
        val selectedProfile = inputs.profileName
        val profile: Profile
        val profileName: String
        if (selectedProfile == null) {
            profile = profileFunction.getProfile() ?: return WizardBolusExecutor.PrepareResult.Error(rh.gs(R.string.wizard_no_active_profile))
            profileName = profileFunction.getProfileName()
        } else {
            val pure = profileRepository.profile.value?.getSpecificProfile(selectedProfile)
                ?: return WizardBolusExecutor.PrepareResult.Error(rh.gs(R.string.scene_profile_not_found, selectedProfile))
            profile = ProfileSealed.Pure(pure, activePlugin)
            profileName = selectedProfile
        }
        val pump = activePlugin.activePump
        if (!pump.isInitialized()) return WizardBolusExecutor.PrepareResult.Error(rh.gs(R.string.wizard_pump_not_available))
        // Recompute on the MASTER's live state (temp target + COB) using the client's inputs + the resolved profile.
        val tempTarget = persistenceLayer.getTemporaryTargetActiveAt(dateUtil.now())
        val cob = if (inputs.useCob) iobCobCalculator.getCobInfo("WizardPrepare").displayCob ?: 0.0 else 0.0
        val carbsAfterConstraints = constraintChecker.applyCarbsConstraints(ConstraintObject(inputs.carbs, aapsLogger)).value()
        if (carbsAfterConstraints != inputs.carbs) return WizardBolusExecutor.PrepareResult.Error(rh.gs(R.string.wizard_carbs_constraint))
        val wizard = bolusWizardProvider.get().doCalc(
            profile, profileName, tempTarget, carbsAfterConstraints, cob, inputs.bg, inputs.directCorrection, inputs.percentage,
            inputs.useBg, inputs.useCob, inputs.useIob, inputs.useIob, false, inputs.useTt, inputs.useTrend, inputs.alarm, inputs.notes, inputs.carbTime
        )
        val insulinAfterConstraints = wizard.insulinAfterConstraints
        val minStep = pump.pumpDescription.pumpType.determineCorrectBolusStepSize(insulinAfterConstraints)
        if (abs(insulinAfterConstraints - wizard.calculatedTotalInsulin) >= minStep)
            return WizardBolusExecutor.PrepareResult.Error(rh.gs(R.string.wizard_constraint_bolus_size, wizard.calculatedTotalInsulin))
        // Nothing to deliver (e.g. BG below target + high IOB, no carbs): reject so the caller shows the standard
        // "no insulin required" instead of an empty confirmation. The guard lives here so every surface that recomputes
        // through this path — phone wizard dialog, client relay, watch — gets it (it was previously only in the wear handler).
        if (wizard.calculatedTotalInsulin <= 0.0 && wizard.carbs <= 0)
            return WizardBolusExecutor.PrepareResult.Error(rh.gs(R.string.wizard_no_insulin_required))
        evictStalePending()
        pending[wizard.timeStamp] =
            PendingBolus(
                wizard.unclampedCalculatedInsulin,
                wizard.carbs,
                wizard.createBolusCalculatorResult(),
                wizard.timeStamp,
                entry = null,
                carbTimeMinutes = inputs.carbTime,
                useAlarm = inputs.alarm,
                notes = inputs.notes,
                eCarbsGrams = inputs.eCarbsGrams,
                eCarbsDelayMinutes = inputs.eCarbsDelayMinutes,
                eCarbsDurationHours = inputs.eCarbsDurationHours
            )
        val advisorApplies = wizard.needsBolusAdvisor()
        return WizardBolusExecutor.PrepareResult.Preview(
            insulin = wizard.calculatedTotalInsulin,
            carbs = wizard.carbs,
            bolusId = wizard.timeStamp,
            // Pass the eCarbs split (food type → extended carbs) so the delivery confirmation shows the eCarbs line too —
            // the record-only path already does this via getConfirmationSummary(). Advisor = correction-only ("eat later"),
            // so eCarbs don't apply there.
            lines = wizard.buildConfirmationLines(
                advisor = false,
                eCarbsGrams = inputs.eCarbsGrams,
                eCarbsDelayMinutes = inputs.eCarbsDelayMinutes,
                eCarbsDurationHours = inputs.eCarbsDurationHours
            ),
            advisorApplies = advisorApplies,
            advisorLines = if (advisorApplies) wizard.buildConfirmationLines(advisor = true) else emptyList(),
            wizardDetail = wizard.buildWizardDetail().copy(
                eCarbsGrams = inputs.eCarbsGrams,
                eCarbsDelayMinutes = inputs.eCarbsDelayMinutes,
                eCarbsDurationHours = inputs.eCarbsDurationHours,
                carbTimeMinutes = inputs.carbTime,
                alarm = inputs.alarm && inputs.carbTime > 0,
                maxBolus = constraintChecker.getMaxBolusAllowed().value(),
                bolusStep = pump.pumpDescription.pumpType.determineCorrectBolusStepSize(wizard.insulinAfterConstraints),
            ),
        )
    }

    override suspend fun prepareBatch(actions: List<BatchAction>): WizardBolusExecutor.PrepareResult {
        val bolus = actions.filterIsInstance<BatchAction.Bolus>().firstOrNull()
        val tt = actions.filterIsInstance<BatchAction.TempTarget>().firstOrNull() // ≤1 by construction
        val ps = actions.filterIsInstance<BatchAction.ProfileSwitch>().firstOrNull() // ≤1 by construction
        val rm = actions.filterIsInstance<BatchAction.RunningMode>().firstOrNull() // ≤1 by construction
        val tb = actions.filterIsInstance<BatchAction.TempBasal>().firstOrNull() // ≤1 by construction
        val eb = actions.filterIsInstance<BatchAction.ExtendedBolus>().firstOrNull() // ≤1 by construction
        val ctb = actions.filterIsInstance<BatchAction.CancelTempBasal>().firstOrNull() // ≤1 by construction
        val ceb = actions.filterIsInstance<BatchAction.CancelExtendedBolus>().firstOrNull() // ≤1 by construction
        val ia = actions.filterIsInstance<BatchAction.InsulinActivate>().firstOrNull() // ≤1 by construction
        val tes = actions.filterIsInstance<BatchAction.TherapyEvent>() // ≥0; handled as a list (defensive — clients currently send one per batch)
        val teEdits = actions.filterIsInstance<BatchAction.TherapyEventEdit>() // ≥0; existing-event metadata edits (location/arrow/note)
        val recordOnly = bolus?.recordOnly == true
        // Originating QuickWizard (INSULIN/CARBS mode), resolved on the MASTER's own store so confirm() can mark it used
        // (lastUsed cooldown) here — the master is SOT and republishes the pref; the client never writes it. Null for a
        // dialog/wear batch, or a guid the master hasn't synced yet (graceful → no mark).
        val entry = bolus?.quickWizardGuid?.takeIf { it.isNotEmpty() }?.let { quickWizard.get(it) }
        // Gate + pump-init only for an actual INSULIN delivery — carbs-only, a record-only log, and a TT-only batch
        // are always allowed (mirrors executeBolus, which gates only when insulin > 0).
        if (bolus != null && !recordOnly && bolus.insulin > 0.0) {
            runningModeGuard.rejectionMessage(PumpCommandGate.CommandKind.BOLUS)?.let { return WizardBolusExecutor.PrepareResult.Error(it) }
            if (!activePlugin.activePump.isInitialized())
                return WizardBolusExecutor.PrepareResult.Error(rh.gs(R.string.wizard_pump_not_available))
        }
        // Cap a delivery (caps only reduce = safe); a record-only is persisted as given (a record must not be altered).
        // SECURITY NOTE: record-only is DELIBERATELY uncapped (a legitimate pen bolus may exceed the soft max). A
        // recorded bolus still counts toward IOB/TDD, so a relayed value from a compromised paired client could skew
        // IOB. No absolute sanity ceiling is applied here yet — a bound needs a chosen value (review finding, pending).
        val (insulin, carbs, eventType) = when {
            bolus == null -> Triple(0.0, 0, TE.Type.CORRECTION_BOLUS)
            recordOnly    -> Triple(bolus.insulin, bolus.carbs, TE.Type.CORRECTION_BOLUS)
            else          -> capFixed(bolus.insulin, bolus.carbs).let { (i, c, t) -> Triple(i, clampNegativeCarbs(c, bolus.carbsTimeOffsetMinutes), t) }
        }
        // Validate a profile switch up-front (the master is the authority): no active profile, or a value out of the
        // CPP hard limits, fails the whole batch before parking — mirrors the old wear-local checks.
        if (ps != null) {
            val psName = ps.profileName
            if (psName != null) {
                // Named switch: the target must exist in the MASTER's store (a client may relay a name the master resolves).
                if (profileRepository.profile.value?.getSpecificProfile(psName) == null)
                    return WizardBolusExecutor.PrepareResult.Error(rh.gs(R.string.scene_profile_not_found, psName))
            } else if (profileFunction.getProfile() == null) {
                return WizardBolusExecutor.PrepareResult.Error(rh.gs(R.string.no_profile_set))
            }
            if (ps.percentage < Constants.CPP_MIN_PERCENTAGE || ps.percentage > Constants.CPP_MAX_PERCENTAGE)
                return WizardBolusExecutor.PrepareResult.Error(rh.gs(R.string.valueoutofrange, "Profile-Percentage"))
            if (ps.timeShiftHours < Constants.CPP_MIN_TIMESHIFT || ps.timeShiftHours > Constants.CPP_MAX_TIMESHIFT)
                return WizardBolusExecutor.PrepareResult.Error(rh.gs(R.string.valueoutofrange, "Profile-Timeshift"))
            if (ps.durationMinutes < 0 || ps.durationMinutes > Constants.MAX_PROFILE_SWITCH_DURATION)
                return WizardBolusExecutor.PrepareResult.Error(rh.gs(R.string.valueoutofrange, "Profile-Duration"))
        }
        // Validate a running-mode change up-front: the master re-checks the mode is still a legal transition
        // (a client/watch list may be stale — TOCTOU), and the temporary modes require a positive duration.
        if (rm != null) {
            if (rm.mode !in loop.allowedNextModes())
                return WizardBolusExecutor.PrepareResult.Error(rh.gs(R.string.running_mode_change_not_allowed))
            if ((rm.mode == RM.Mode.SUSPENDED_BY_USER || rm.mode == RM.Mode.DISCONNECTED_PUMP) && rm.durationMinutes <= 0)
                return WizardBolusExecutor.PrepareResult.Error(rh.gs(R.string.valueoutofrange, "Duration"))
        }
        // Temp basal (manual): validate against the MASTER's pump, then cap. A client mirrors the master's pump via
        // RunningConfiguration, so a capability/style mismatch means the client's config is briefly out of sync → reject.
        var cappedTb: BatchAction.TempBasal? = null
        if (tb != null) {
            val pump = activePlugin.activePump
            if (!pump.isInitialized()) return WizardBolusExecutor.PrepareResult.Error(rh.gs(R.string.wizard_pump_not_available))
            val masterIsPercent = pump.pumpDescription.tempBasalStyle and PumpDescription.PERCENT == PumpDescription.PERCENT
            if (!pump.pumpDescription.isTempBasalCapable || tb.isPercent != masterIsPercent)
                return WizardBolusExecutor.PrepareResult.Error(rh.gs(R.string.clientcontrol_pump_out_of_sync))
            runningModeGuard.rejectionMessage(if (tb.rate == 0.0) PumpCommandGate.CommandKind.TEMP_BASAL_ZERO else PumpCommandGate.CommandKind.TEMP_BASAL_NONZERO)
                ?.let { return WizardBolusExecutor.PrepareResult.Error(it) }
            val profile = profileFunction.getProfile() ?: return WizardBolusExecutor.PrepareResult.Error(rh.gs(R.string.no_profile_set))
            val cappedRate = if (tb.isPercent)
                constraintChecker.applyBasalPercentConstraints(ConstraintObject(tb.rate.toInt(), aapsLogger), profile).value().toDouble()
            else
                constraintChecker.applyBasalConstraints(ConstraintObject(tb.rate, aapsLogger), profile).value()
            cappedTb = tb.copy(rate = cappedRate)
        }
        // Extended bolus (manual): validate + cap like a bolus; a net-zero after caps (e.g. closed loop) is rejected.
        var cappedEb: BatchAction.ExtendedBolus? = null
        if (eb != null) {
            val pump = activePlugin.activePump
            if (!pump.isInitialized()) return WizardBolusExecutor.PrepareResult.Error(rh.gs(R.string.wizard_pump_not_available))
            if (!pump.pumpDescription.isExtendedBolusCapable) return WizardBolusExecutor.PrepareResult.Error(rh.gs(R.string.clientcontrol_pump_out_of_sync))
            runningModeGuard.rejectionMessage(PumpCommandGate.CommandKind.EXTENDED_BOLUS)?.let { return WizardBolusExecutor.PrepareResult.Error(it) }
            val cappedInsulin = constraintChecker.applyExtendedBolusConstraints(ConstraintObject(eb.insulin, aapsLogger)).value()
            if (cappedInsulin <= 0.0) return WizardBolusExecutor.PrepareResult.Error(rh.gs(R.string.wizard_no_insulin_required))
            cappedEb = eb.copy(insulin = cappedInsulin)
        }
        // Cancel a running TBR / extended bolus on the MASTER's pump — no cap, no running-mode gate (a stop is always
        // allowed). Validate only that the master's pump is up + capable (a stale client config → reject).
        if (ctb != null) {
            val pump = activePlugin.activePump
            if (!pump.isInitialized()) return WizardBolusExecutor.PrepareResult.Error(rh.gs(R.string.wizard_pump_not_available))
            if (!pump.pumpDescription.isTempBasalCapable) return WizardBolusExecutor.PrepareResult.Error(rh.gs(R.string.clientcontrol_pump_out_of_sync))
        }
        if (ceb != null) {
            val pump = activePlugin.activePump
            if (!pump.isInitialized()) return WizardBolusExecutor.PrepareResult.Error(rh.gs(R.string.wizard_pump_not_available))
            if (!pump.pumpDescription.isExtendedBolusCapable) return WizardBolusExecutor.PrepareResult.Error(rh.gs(R.string.clientcontrol_pump_out_of_sync))
        }
        // Insulin activation: the master re-applies its CURRENT active profile with this insulin
        // (createProfileSwitchWithNewInsulin), which needs an active EPS profile — validate up-front so a no-profile
        // client gets an error instead of a silent no-op at confirm (the batch confirm path has no per-action failure channel).
        if (ia != null && profileFunction.getProfile() !is ProfileSealed.EPS)
            return WizardBolusExecutor.PrepareResult.Error(rh.gs(R.string.no_profile_set))
        if (insulin <= 0.0 && carbs == 0 && tt == null && ps == null && rm == null && tb == null && eb == null && ctb == null && ceb == null && ia == null && tes.isEmpty() && teEdits.isEmpty())
        // Nothing to do after caps/clamps (e.g. negative carbs with no COB to remove): a no-op, NOT a delivery
        // error — the caller renders the neutral "no action selected" message, never the bolus-error title.
            return WizardBolusExecutor.PrepareResult.NoAction
        val bolusId = dateUtil.now()
        evictStalePending()
        pending[bolusId] = PendingBolus(
            insulin = insulin, carbs = carbs, bcr = null, bolusId = bolusId, entry = entry,
            carbTimeMinutes = bolus?.carbsTimeOffsetMinutes ?: 0, notes = bolus?.notes, mode = BolusMode.FIXED,
            eventType = eventType, carbsDurationHours = bolus?.carbsDurationHours ?: 0,
            eCarbsGrams = bolus?.eCarbsGrams ?: 0, eCarbsDelayMinutes = bolus?.eCarbsDelayMinutes ?: 0, eCarbsDurationHours = bolus?.eCarbsDurationHours ?: 0,
            tempTarget = tt, profileSwitch = ps, runningMode = rm, recordOnly = recordOnly, iCfg = bolus?.iCfg, bolusTimestamp = bolus?.timestamp?.takeIf { it > 0L },
            tempBasal = cappedTb, extendedBolus = cappedEb, cancelTempBasal = ctb != null, cancelExtendedBolus = ceb != null,
            insulinActivate = ia, therapyEvents = tes, therapyEventEdits = teEdits
        )
        // The master is the SOLE author of the confirmation: build the MERGED lines for the whole batch here, so the
        // client renders the master's exact string and a master-local dialog renders the identical one (decision 1).
        val isTtOnly = bolus == null && ps == null && rm == null && cappedTb == null && cappedEb == null && ctb == null && ceb == null && ia == null && tes.isEmpty() && teEdits.isEmpty()
        val lines = buildFixedLines(bolus, insulin, carbs, recordOnly) +
            (tt?.let { buildTtLine(it, isTtOnly) } ?: emptyList()) +
            (ps?.let { buildPsLine(it) } ?: emptyList()) +
            (rm?.let { buildRmLine(it) } ?: emptyList()) +
            (cappedTb?.let { buildTempBasalLine(it, tb) } ?: emptyList()) +
            (cappedEb?.let { buildExtendedBolusLine(it, eb) } ?: emptyList()) +
            (ctb?.let { buildCancelLine(R.string.tempbasal_label) } ?: emptyList()) +
            (ceb?.let { buildCancelLine(R.string.extended_bolus) } ?: emptyList()) +
            (ia?.let { buildInsulinActivateLine(it) } ?: emptyList()) +
            tes.flatMap { buildTherapyEventLine(it) } +
            teEdits.flatMap { buildTherapyEventEditLine(it) }
        return WizardBolusExecutor.PrepareResult.Preview(insulin, carbs, bolusId, lines = lines, advisorApplies = false, advisorLines = emptyList())
    }

    override suspend fun confirm(bolusId: Long, source: Sources, onError: (String) -> Unit, asAdvisor: Boolean, correctionU: Double): WizardBolusExecutor.ConfirmResult {
        // Atomic consume-once: remove(bolusId) returns the parked dose and removes it in one step, so two
        // concurrent commits of the same id can't both deliver (the loser gets null → NoPending). A non-matching
        // id removes nothing, leaving other actors' parked doses intact.
        val p = pending.remove(bolusId) ?: return WizardBolusExecutor.ConfirmResult.NoPending

        val notes = p.notes

        // Fixed-amount bolus/carbs (Insulin / Treatment / Carbs dialogs): deliver the parked amounts as-is through
        // the shared core — no wizard recompute, no super-bolus/eCarbs/advisor (all wizard-only).
        if (p.mode == BolusMode.FIXED) {
            // A FIXED entry may carry a batch TempTarget. Decision-B order: a target-RAISING TT (hypo/activity) is
            // applied FIRST + unconditional; a target-LOWERING (eating-soon) TT applies only if the bolus passed the
            // SYNCHRONOUS gate (see `accepted` below).
            val tt = p.tempTarget
            val raising = tt != null && (tt.reason == TT.Reason.HYPOGLYCEMIA.text || tt.reason == TT.Reason.ACTIVITY.text)
            if (tt != null && raising) applyTempTarget(tt, source)
            // CAUTION: `accepted` flips false ONLY on a SYNCHRONOUS rejection (the running-mode gate, or a record-only
            // persist error). The actual pump delivery is asynchronous (`commandQueue.bolus` on appScope in executeBolus),
            // so a real PUMP failure fires AFTER confirm() has already returned — it is surfaced by the executor's URGENT
            // BOLUS_DELIVERY_FAILED alarm, NOT by reverting the steps below. Consequence: a target-LOWERING TT / PS / RM
            // can still apply when the bolus was queued but later failed on the pump (the alarm is the mitigation).
            var accepted = true
            val wrapped: (String) -> Unit = { accepted = false; onError(it) }
            when {
                p.recordOnly                     -> {
                    // Record-only (a pen bolus, or a master that can't deliver): persist insulin AND carbs as given,
                    // NOT capped, optionally back-dated. deliver() (not deliverInsulin) so a Treatment record keeps its carbs.
                    val carbsTime = if (p.carbs > 0) dateUtil.now() + T.mins(p.carbTimeMinutes.toLong()).msecs() else null
                    deliver(
                        p.insulin,
                        p.carbs,
                        carbsTime = carbsTime,
                        carbsDuration = p.carbsDurationHours,
                        bolusCalculatorResult = null,
                        notes = notes,
                        source = source,
                        onError = wrapped,
                        eventType = p.eventType,
                        recordOnly = true,
                        iCfg = p.iCfg,
                        timestamp = p.bolusTimestamp
                    )
                }

                p.insulin == 0.0 && p.carbs != 0 ->
                    // Carbs-only (positive add OR negative COB removal): funnel through the SAME eCarbs path the local
                    // Carbs dialog uses, so a client carb entry and a master carb entry are identical (TE.Type, duration,
                    // delay) — not the generic deliver() which would tag it CARBS_CORRECTION instead of deliverECarbs's
                    // CORRECTION_BOLUS convention.
                    deliverECarbs(p.carbs, dateUtil.now() + T.mins(p.carbTimeMinutes.toLong()).msecs(), p.carbsDurationHours, p.carbTimeMinutes, notes, source, wrapped)

                else                             -> {
                    // Insulin (± carbs): deliver the parked amounts as-is. carbsTime = now + offset; carbsDuration in hours.
                    val carbsTime = if (p.carbs > 0) dateUtil.now() + T.mins(p.carbTimeMinutes.toLong()).msecs() else null
                    deliver(p.insulin, p.carbs, carbsTime = carbsTime, carbsDuration = p.carbsDurationHours, bolusCalculatorResult = p.bcr, notes = notes, source = source, onError = wrapped, eventType = p.eventType)
                }
            }
            // eCarbs split (e.g. a CARBS-mode QuickWizard entry with eCarbs configured): the immediate carbs are
            // delivered above; the extended portion is scheduled here, mirroring the wizard path's carbs2 delivery.
            if (p.eCarbsGrams > 0) {
                val eCarbsTime = dateUtil.now() + T.mins(p.eCarbsDelayMinutes.toLong()).msecs()
                deliverECarbs(p.eCarbsGrams, eCarbsTime, p.eCarbsDurationHours, p.eCarbsDelayMinutes, notes, source, wrapped)
            }
            if (tt != null && !raising && accepted) applyTempTarget(tt, source)
            // Insulin activation re-applies the active profile with the new insulin — run BEFORE any explicit PS
            // (insulin set first), independent of any dose (an InsulinActivate-only batch no-ops the deliver(0,0)).
            p.insulinActivate?.let { applyInsulinActivate(it, source) }
            // Careportal therapy events (≥0) — dose-independent metadata; the master persists them (sole writer).
            p.therapyEvents.forEach { applyTherapyEvent(it, source) }
            // Edits of existing therapy events (≥0) — the master updates its own copy in place (sole writer); a
            // not-found target surfaces via onError → the commit reports Rejected/Failed (not a silent success).
            p.therapyEventEdits.forEach { applyTherapyEventEdit(it, source, onError) }
            // A profile switch isn't gated on a dose (a PS-only batch funnels through here with a no-op deliver(0,0)).
            p.profileSwitch?.let { applyProfileSwitch(it, source) }
            // A running-mode change is likewise independent of any dose (an RM-only batch no-ops the deliver(0,0)).
            p.runningMode?.let { applyRunningMode(it, source) }
            // Pump-direct manual actions (relayed from a client, or a master-local dialog) — independent of any dose.
            p.tempBasal?.let { applyTempBasal(it, source, onError) }
            p.extendedBolus?.let { applyExtendedBolus(it, source, onError) }
            if (p.cancelTempBasal) applyCancelTempBasal(source, onError)
            if (p.cancelExtendedBolus) applyCancelExtendedBolus(source, onError)
            // QuickWizard INSULIN/CARBS: mark the originating entry used HERE on the master (SOT) — the lastUsed write
            // republishes to clients via the cold-doc sync. The client must NOT do this itself (it would push the synced
            // QuickWizard pref back over the round-trip and collide with this commit → "Update settings … Busy").
            p.entry?.markAsUsed()
            return WizardBolusExecutor.ConfirmResult.Delivered
        }

        // High-BG advisor branch (user chose "correct now, eat later"): a correction-only CORRECTION_BOLUS —
        // no carbs, no eCarbs, no super-bolus, no eat reminder. mg/dL BG comes from the BCR's glucoseValue.
        if (asAdvisor) {
            deliverBolusAdvisor(p.insulin, p.bcr?.glucoseValue, p.bcr, notes, source, onError)
            p.entry?.markAsUsed()
            return WizardBolusExecutor.ConfirmResult.Delivered
        }

        val carbTimeOffset = p.carbTimeMinutes.toLong()
        // Manual wizard: honor the parked alarm flag. A QuickWizard overrides this from its own
        // entry.useAlarm() in the `qwe != null` block below.
        var useAlarm = p.useAlarm
        val currentTime = dateUtil.now()
        var eventTime = currentTime
        var carbs2 = 0
        var duration = 0
        var eCarbsDelay = 0
        val qwe = p.entry
        if (qwe != null) {
            useAlarm = qwe.useAlarm() == QuickWizardEntry.YES
            if (qwe.useEcarbs() == QuickWizardEntry.YES) {
                eCarbsDelay = qwe.time()
                eventTime += (eCarbsDelay * 60000)
                carbs2 = qwe.carbs2()
                duration = qwe.duration()
            }
        } else if (p.eCarbsGrams > 0) {
            // Manual wizard from a client (no QuickWizardEntry): the eCarbs split travels in PendingBolus so the
            // extended-carbs portion is still scheduled on the master — matching the phone's executeNormal.
            eCarbsDelay = p.eCarbsDelayMinutes
            eventTime += (eCarbsDelay * 60000)
            carbs2 = p.eCarbsGrams
            duration = p.eCarbsDurationHours
        }
        // Super-bolus (a quick-wizard with useSuperBolus): write the SUPER_BOLUS mode change before the
        // bolus, driven off the CONSUMED entry — never the shared `pending` slot, which a stale unconfirmed
        // wear prepare could leak into an unrelated bolus. Mirrors the phone's executeNormal.
        if (p.entry?.useSuperBolus() == QuickWizardEntry.YES) {
            profileFunction.getProfile()?.let { profile ->
                if (loop.allowedNextModes().contains(RM.Mode.SUPER_BOLUS))
                    loop.handleRunningModeChange(
                        durationInMinutes = 2 * 60,
                        profile = profile,
                        newRM = RM.Mode.SUPER_BOLUS,
                        action = Action.SUPERBOLUS_TBR,
                        source = source
                    )
            }
        }
        // Canonical: a wear wizard bolus now records identically to the phone (BOLUS_WIZARD + mgdlGlucose).
        // The mg/dL BG comes from the BCR's glucoseValue (== profileUtil.convertToMgdl(bg, units)).
        // correctionU: watch-side ± adjustment added by the user on the result page; adjust BCR so the wizard log
        // records the actual delivered amount (otherCorrection mirrors the phone wizard's direct-correction field).
        val correctedInsulin = constraintChecker.applyBolusConstraints(
            ConstraintObject((p.insulin + correctionU).coerceAtLeast(0.0), aapsLogger)
        ).value()
        val correctedBcr = if (correctionU != 0.0)
            p.bcr?.copy(
                otherCorrection = p.bcr.otherCorrection + correctionU,
                totalInsulin = correctedInsulin  // actual delivered amount (already coerced ≥ 0)
            )
        else p.bcr
        deliverWizardBolus(correctedInsulin, p.carbs, carbTimeOffset.toInt(), p.bcr?.glucoseValue, correctedBcr, notes, source, onError)
        if (carbs2 > 0) deliverECarbs(carbs2, eventTime, duration, eCarbsDelay, notes, source, onError)
        if (useAlarm && p.carbs > 0 && carbTimeOffset > 0)
            automation.scheduleTimeToEatReminder(T.mins(carbTimeOffset).secs().toInt())
        p.entry?.markAsUsed()
        return WizardBolusExecutor.ConfirmResult.Delivered
    }

    /** Cap + classify a FIXED bolus/carbs (delivery only — a record-only entry must NOT be capped). */
    private fun capFixed(insulin: Double, carbs: Int): Triple<Double, Int, TE.Type> {
        val cappedInsulin = constraintChecker.applyBolusConstraints(ConstraintObject(insulin, aapsLogger)).value()
        val cappedCarbs = constraintChecker.applyCarbsConstraints(ConstraintObject(carbs, aapsLogger)).value()
        val eventType = when {
            cappedInsulin > 0.0 && cappedCarbs > 0 -> TE.Type.MEAL_BOLUS
            cappedInsulin > 0.0                    -> TE.Type.CORRECTION_BOLUS
            else                                   -> TE.Type.CARBS_CORRECTION
        }
        return Triple(cappedInsulin, cappedCarbs, eventType)
    }

    /**
     * Negative carbs = COB removal. Two invariants (mirrored by the Carbs dialog UI, re-enforced here as the master is
     * the authority + TOCTOU/relayed-client safety net): never remove more than the CURRENT COB (so COB can't be driven
     * to a misleading 0-by-overshoot), and never remove in the FUTURE (you can't pre-remove carbs not yet on board, and
     * `getCobInfo.futureCarbs` isn't floored — a future negative would render negative future COB). A back-dated removal
     * stays allowed: the COB integration re-floors the past correctly.
     */
    private suspend fun clampNegativeCarbs(carbs: Int, carbsTimeOffsetMinutes: Int): Int {
        if (carbs >= 0) return carbs
        if (carbsTimeOffsetMinutes > 0) return 0 // future removal is not allowed → no-op
        val cob = iobCobCalculator.getCobInfo("prepareBatch").displayCob ?: 0.0
        return if (carbs < -cob) ceil(-cob).toInt() else carbs // cap magnitude to current COB
    }

    /** Apply a batch TempTarget via the dialog-free domain path (mirrors onVerifiedTempTargetSet). */
    private suspend fun applyTempTarget(tt: BatchAction.TempTarget, source: Sources) {
        val reason = TT.Reason.fromString(tt.reason)
        // duration 0 = cancel the running TT (a CANCEL_TT entry), not a 0-minute target record.
        if (tt.durationMinutes == 0) {
            persistenceLayer.cancelCurrentTemporaryTargetIfAny(
                timestamp = dateUtil.now(),
                action = Action.CANCEL_TT,
                source = source,
                note = null,
                listValues = listOf(ValueWithUnit.TETTReason(reason))
            )
            return
        }
        persistenceLayer.insertAndCancelCurrentTemporaryTarget(
            temporaryTarget = TT(
                timestamp = dateUtil.now() + T.mins(tt.startOffsetMinutes.toLong()).msecs(),
                duration = T.mins(tt.durationMinutes.toLong()).msecs(),
                reason = reason,
                lowTarget = tt.lowMgdl,
                highTarget = tt.highMgdl
            ),
            action = Action.TT,
            source = source,
            note = tt.notes,
            listValues = listOf(ValueWithUnit.Mgdl(tt.lowMgdl), ValueWithUnit.Minute(tt.durationMinutes))
        )
    }

    /** The MERGED confirmation lines for a FIXED batch bolus/carbs — the master is the sole author (client renders these verbatim). */
    private fun buildFixedLines(bolus: BatchAction.Bolus?, insulin: Double, carbs: Int, recordOnly: Boolean): List<ConfirmationLine> {
        bolus ?: return emptyList()
        val pumpDescription = activePlugin.activePump.pumpDescription
        val out = mutableListOf<ConfirmationLine>()
        if (insulin > 0.0) {
            out += ConfirmationLine(ConfirmationRole.BOLUS, rh.gs(R.string.confirmation_line, rh.gs(R.string.bolus), decimalFormatter.toPumpSupportedBolusWithUnits(insulin, pumpDescription.bolusStep)))
            if (recordOnly) {
                out += ConfirmationLine(ConfirmationRole.WARNING, rh.gs(R.string.bolus_recorded_only))
                bolus.iCfg?.let { out += ConfirmationLine(ConfirmationRole.NORMAL, rh.gs(R.string.selected_insulin, it.insulinLabel)) }
            } else if (abs(insulin - bolus.insulin) > pumpDescription.pumpType.determineCorrectBolusStepSize(insulin)) {
                out += ConfirmationLine(ConfirmationRole.WARNING, rh.gs(R.string.bolus_constraint_applied_warn, bolus.insulin, insulin))
            }
        }
        if (carbs != 0) {
            out += ConfirmationLine(ConfirmationRole.CARBS, rh.gs(R.string.confirmation_line, rh.gs(R.string.carbs), rh.gs(R.string.format_carbs, carbs)))
            if (!recordOnly && carbs != bolus.carbs)
                out += ConfirmationLine(ConfirmationRole.WARNING, rh.gs(R.string.constraint_applied))
            // Delayed/extended carbs (e.g. wear eCarbs): show the scheduled start time on the general line so every
            // surface (phone, client, watch) renders it identically — the one piece of info added to the shared path.
            if (bolus.carbsTimeOffsetMinutes != 0)
                out += ConfirmationLine(ConfirmationRole.NORMAL, rh.gs(R.string.confirmation_line, rh.gs(R.string.time), dateUtil.timeString(dateUtil.now() + T.mins(bolus.carbsTimeOffsetMinutes.toLong()).msecs())))
            if (bolus.carbsDurationHours > 0)
                out += ConfirmationLine(ConfirmationRole.NORMAL, rh.gs(R.string.confirmation_line, rh.gs(R.string.duration), rh.gs(R.string.value_with_unit, bolus.carbsDurationHours.toString(), rh.gs(app.aaps.core.interfaces.R.string.shorthour))))
        }
        if (bolus.eCarbsGrams > 0)
            out += ConfirmationLine(ConfirmationRole.CARBS, rh.gs(R.string.wizard_ecarbs, bolus.eCarbsGrams, bolus.eCarbsDurationHours, bolus.eCarbsDelayMinutes))
        if (bolus.notes.isNotEmpty())
            out += ConfirmationLine(ConfirmationRole.NORMAL, rh.gs(R.string.confirmation_line, rh.gs(R.string.notes_label), bolus.notes))
        return out
    }

    /**
     * Shared TT confirmation line(s) — the single generator behind the batch dialog, every client, and the wear
     * TT confirm. Renders the target (a `low – high` range when they differ, else a single value) + an optional
     * reason line, or a "cancel" line when [durationMinutes] is 0. [lowMgdl]/[highMgdl] in mg/dL; [reasonDisplay]
     * already localized (or "").
     */
    private fun buildTempTargetLines(reasonDisplay: String, lowMgdl: Double, highMgdl: Double, durationMinutes: Int, standalone: Boolean = false): List<ConfirmationLine> {
        val out = mutableListOf<ConfirmationLine>()
        if (durationMinutes == 0) {
            out += ConfirmationLine(ConfirmationRole.NORMAL, rh.gs(R.string.confirmation_line, rh.gs(R.string.temporary_target), rh.gs(R.string.cancel)))
        } else {
            val units = profileFunction.getUnits()
            val unitLabel = if (units == GlucoseUnit.MMOL) rh.gs(R.string.mmol) else rh.gs(R.string.mgdl)
            val low = decimalFormatter.to1Decimal(profileUtil.fromMgdlToUnits(lowMgdl, units))
            val target = if (lowMgdl == highMgdl) low else low + " – " + decimalFormatter.to1Decimal(profileUtil.fromMgdlToUnits(highMgdl, units))
            val durationText = formatMinutesAsDuration(durationMinutes, rh)
            val targetLabel = if (standalone) rh.gs(R.string.target_label) else rh.gs(R.string.temporary_target)
            out += ConfirmationLine(
                ConfirmationRole.TEMP_TARGET,
                rh.gs(R.string.confirmation_line, targetLabel, rh.gs(R.string.value_with_unit, target, unitLabel))
            )
            out += ConfirmationLine(
                ConfirmationRole.NORMAL,
                rh.gs(R.string.confirmation_line, rh.gs(R.string.duration), durationText)
            )
            if (reasonDisplay.isNotEmpty())
                out += ConfirmationLine(ConfirmationRole.NORMAL, rh.gs(R.string.confirmation_line, rh.gs(R.string.reason), reasonDisplay))
        }
        return out
    }

    /** The TT line(s) for any batch (wear / client / phone) — range + duration + a localized reason, or a cancel line. */
    private fun buildTtLine(tt: BatchAction.TempTarget, standalone: Boolean = false): List<ConfirmationLine> =
        buildTempTargetLines(localizeTtReason(tt.reason), tt.lowMgdl, tt.highMgdl, tt.durationMinutes, standalone)

    /**
     * Apply a batch ProfileSwitch via the dialog-free domain path. [profileName] non-null → switch to that named
     * profile from the master's store (a relayed/dialog switch); null → modify the currently active profile (wear / CPP).
     */
    private suspend fun applyProfileSwitch(ps: BatchAction.ProfileSwitch, source: Sources) {
        val psName = ps.profileName
        if (psName != null) {
            val store = profileRepository.profile.value ?: return
            profileFunction.createProfileSwitch(
                profileStore = store,
                profileName = psName,
                durationInMinutes = ps.durationMinutes,
                percentage = ps.percentage,
                timeShiftInHours = ps.timeShiftHours,
                timestamp = dateUtil.now(),
                action = Action.PROFILE_SWITCH,
                source = source,
                note = ps.notes,
                listValues = listOfNotNull(
                    ValueWithUnit.SimpleString(psName),
                    ValueWithUnit.Percent(ps.percentage),
                    ValueWithUnit.Hour(ps.timeShiftHours).takeIf { ps.timeShiftHours != 0 },
                    ValueWithUnit.Minute(ps.durationMinutes).takeIf { ps.durationMinutes != 0 }
                ),
                iCfg = profileFunction.getProfile()?.iCfg ?: insulin.iCfg
            )
        } else {
            profileFunction.createProfileSwitch(
                durationInMinutes = ps.durationMinutes,
                percentage = ps.percentage,
                timeShiftInHours = ps.timeShiftHours,
                action = Action.PROFILE_SWITCH,
                source = source,
                note = null,
                listValues = listOfNotNull(
                    ValueWithUnit.Percent(ps.percentage),
                    ValueWithUnit.Hour(ps.timeShiftHours).takeIf { ps.timeShiftHours != 0 },
                    ValueWithUnit.Minute(ps.durationMinutes)
                )
            )
        }
    }

    /** The PS line(s) for any batch (wear / client / phone) — target profile name + percentage + optional time-shift + duration. */
    private suspend fun buildPsLine(ps: BatchAction.ProfileSwitch): List<ConfirmationLine> {
        val out = mutableListOf<ConfirmationLine>()
        out += ConfirmationLine(ConfirmationRole.PRIMARY, rh.gs(R.string.confirmation_line, rh.gs(R.string.profile), ps.profileName ?: profileFunction.getOriginalProfileName()))
        out += ConfirmationLine(ConfirmationRole.NORMAL, rh.gs(R.string.confirmation_line, rh.gs(R.string.percentage_label), rh.gs(R.string.format_percent, ps.percentage)))
        if (ps.timeShiftHours != 0)
            out += ConfirmationLine(ConfirmationRole.NORMAL, rh.gs(R.string.confirmation_line, rh.gs(R.string.timeshift_label), rh.gs(R.string.value_with_unit, ps.timeShiftHours.toString(), rh.gs(app.aaps.core.interfaces.R.string.shorthour))))
        if (ps.durationMinutes > 0)
            out += ConfirmationLine(ConfirmationRole.NORMAL, rh.gs(R.string.confirmation_line, rh.gs(R.string.duration), formatMinutesAsDuration(ps.durationMinutes, rh)))
        return out
    }

    /** Localized reason for a preset TT (Activity / Hypo / Eating-soon); "" for manual/wear/custom (no reason line). */
    private fun localizeTtReason(reasonText: String): String = when (TT.Reason.fromString(reasonText)) {
        TT.Reason.HYPOGLYCEMIA -> rh.gs(R.string.hypo)
        TT.Reason.ACTIVITY     -> rh.gs(R.string.activity)
        TT.Reason.EATING_SOON  -> rh.gs(R.string.eatingsoon)
        else                   -> ""
    }

    /**
     * Apply a batch RunningMode change via the dialog-free domain path — the single execution point for every
     * source (phone screen, client round-trip, wear). The [Action] is resolved from the [mode] (+ current loop
     * state for RESUME: a reconnect vs a resume) so the UEL records the right entry, mirroring the screen/wear.
     */
    private suspend fun applyRunningMode(rm: BatchAction.RunningMode, source: Sources) {
        val profile = profileFunction.getProfile() ?: return
        when (rm.mode) {
            RM.Mode.CLOSED_LOOP                                                      -> loop.handleRunningModeChange(newRM = RM.Mode.CLOSED_LOOP, action = Action.CLOSED_LOOP_MODE, source = source, profile = profile)
            RM.Mode.CLOSED_LOOP_LGS                                                  -> loop.handleRunningModeChange(newRM = RM.Mode.CLOSED_LOOP_LGS, action = Action.LGS_LOOP_MODE, source = source, profile = profile)
            RM.Mode.OPEN_LOOP                                                        -> loop.handleRunningModeChange(newRM = RM.Mode.OPEN_LOOP, action = Action.OPEN_LOOP_MODE, source = source, profile = profile)
            RM.Mode.DISABLED_LOOP                                                    -> loop.handleRunningModeChange(newRM = RM.Mode.DISABLED_LOOP, action = Action.LOOP_DISABLED, source = source, profile = profile)

            RM.Mode.RESUME                                                           -> {
                // Resume-from-suspend vs reconnect-the-pump share the RESUME mode; the UEL action differs.
                val action = if (loop.runningMode() == RM.Mode.DISCONNECTED_PUMP) Action.RECONNECT else Action.RESUME
                loop.handleRunningModeChange(newRM = RM.Mode.RESUME, action = action, source = source, profile = profile)
            }

            RM.Mode.SUSPENDED_BY_USER                                                -> loop.handleRunningModeChange(
                newRM = RM.Mode.SUSPENDED_BY_USER,
                durationInMinutes = rm.durationMinutes,
                action = Action.SUSPEND,
                source = source,
                profile = profile
            )

            RM.Mode.DISCONNECTED_PUMP                                                -> loop.handleRunningModeChange(
                newRM = RM.Mode.DISCONNECTED_PUMP, durationInMinutes = rm.durationMinutes, action = Action.DISCONNECT, source = source, profile = profile,
                listValues = listOf(if (rm.durationMinutes >= 60) ValueWithUnit.Hour(rm.durationMinutes / 60) else ValueWithUnit.Minute(rm.durationMinutes))
            )

            // Non-user-selectable modes (super-bolus / pump-suspend / DST) never arrive here — prepareBatch gates on allowedNextModes.
            RM.Mode.SUPER_BOLUS, RM.Mode.SUSPENDED_BY_PUMP, RM.Mode.SUSPENDED_BY_DST -> Unit
        }
    }

    /** The RM line(s) for any batch (wear / client / phone) — the target mode title + an optional duration. */
    private suspend fun buildRmLine(rm: BatchAction.RunningMode): List<ConfirmationLine> {
        val out = mutableListOf<ConfirmationLine>()
        out += ConfirmationLine(rmModeRole(rm.mode), rmModeTitle(rm.mode))
        if (rm.durationMinutes > 0)
            out += ConfirmationLine(ConfirmationRole.NORMAL, rh.gs(R.string.confirmation_line, rh.gs(R.string.duration), formatMinutesAsDuration(rm.durationMinutes, rh)))
        return out
    }

    private fun rmModeRole(mode: RM.Mode): ConfirmationRole = when (mode) {
        RM.Mode.CLOSED_LOOP       -> ConfirmationRole.LOOP_CLOSED
        RM.Mode.CLOSED_LOOP_LGS   -> ConfirmationRole.LOOP_LGS
        RM.Mode.OPEN_LOOP         -> ConfirmationRole.LOOP_OPEN
        RM.Mode.DISABLED_LOOP     -> ConfirmationRole.LOOP_DISABLED
        RM.Mode.SUSPENDED_BY_USER -> ConfirmationRole.LOOP_SUSPENDED
        RM.Mode.DISCONNECTED_PUMP -> ConfirmationRole.LOOP_DISCONNECTED
        RM.Mode.RESUME            -> ConfirmationRole.LOOP_CLOSED
        else                      -> ConfirmationRole.PRIMARY
    }

    /** Localized title for a target running mode (RESUME resolves to reconnect vs resume by the current loop state). */
    private suspend fun rmModeTitle(mode: RM.Mode): String = when (mode) {
        RM.Mode.CLOSED_LOOP                                                      -> rh.gs(R.string.closedloop)
        RM.Mode.CLOSED_LOOP_LGS                                                  -> rh.gs(R.string.lowglucosesuspend)
        RM.Mode.OPEN_LOOP                                                        -> rh.gs(R.string.openloop)
        RM.Mode.DISABLED_LOOP                                                    -> rh.gs(R.string.disableloop)
        RM.Mode.SUSPENDED_BY_USER                                                -> rh.gs(R.string.suspendloop)
        RM.Mode.DISCONNECTED_PUMP                                                -> rh.gs(R.string.pump_disconnected)
        RM.Mode.RESUME                                                           -> if (loop.runningMode() == RM.Mode.DISCONNECTED_PUMP) rh.gs(R.string.pump_reconnect) else rh.gs(R.string.resumeloop)
        RM.Mode.SUPER_BOLUS, RM.Mode.SUSPENDED_BY_PUMP, RM.Mode.SUSPENDED_BY_DST -> rh.gs(R.string.running_mode)
    }

    /** Apply a batch temp basal on the master's pump (already capped + style-validated in prepareBatch). */
    private suspend fun applyTempBasal(tb: BatchAction.TempBasal, source: Sources, onError: (String) -> Unit) {
        val profile = profileFunction.getProfile() ?: return
        val result = if (tb.isPercent) {
            uel.log(action = Action.TEMP_BASAL, source = source, listValues = listOf(ValueWithUnit.Percent(tb.rate.toInt()), ValueWithUnit.Minute(tb.durationMinutes)))
            commandQueue.tempBasalPercent(tb.rate.toInt(), tb.durationMinutes, true, profile, PumpSync.TemporaryBasalType.NORMAL)
        } else {
            uel.log(action = Action.TEMP_BASAL, source = source, listValues = listOf(ValueWithUnit.Insulin(tb.rate), ValueWithUnit.Minute(tb.durationMinutes)))
            commandQueue.tempBasalAbsolute(tb.rate, tb.durationMinutes, true, profile, PumpSync.TemporaryBasalType.NORMAL)
        }
        if (!result.success) onError(result.comment)
    }

    /** The TBR line(s) — rate (percent or absolute) + duration, with a cap warning when reduced. */
    private fun buildTempBasalLine(capped: BatchAction.TempBasal, original: BatchAction.TempBasal?): List<ConfirmationLine> {
        val out = mutableListOf<ConfirmationLine>()
        val rateStr = if (capped.isPercent) rh.gs(R.string.format_percent, capped.rate.toInt()) else rh.gs(R.string.pump_base_basal_rate, capped.rate)
        out += ConfirmationLine(ConfirmationRole.PRIMARY, rh.gs(R.string.confirmation_line, rh.gs(R.string.tempbasal_label), rateStr))
        out += ConfirmationLine(ConfirmationRole.NORMAL, rh.gs(R.string.confirmation_line, rh.gs(R.string.duration), rh.gs(R.string.format_mins, capped.durationMinutes)))
        if (original != null && capped.rate != original.rate) out += ConfirmationLine(ConfirmationRole.WARNING, rh.gs(R.string.constraint_applied))
        return out
    }

    /** Apply a batch extended bolus on the master's pump (already capped in prepareBatch). */
    private suspend fun applyExtendedBolus(eb: BatchAction.ExtendedBolus, source: Sources, onError: (String) -> Unit) {
        uel.log(action = Action.EXTENDED_BOLUS, source = source, listValues = listOf(ValueWithUnit.Insulin(eb.insulin), ValueWithUnit.Minute(eb.durationMinutes)))
        val result = commandQueue.extendedBolus(eb.insulin, eb.durationMinutes)
        if (!result.success) onError(result.comment)
    }

    /** The extended-bolus line(s) — insulin + duration, with a cap warning when reduced. */
    private fun buildExtendedBolusLine(capped: BatchAction.ExtendedBolus, original: BatchAction.ExtendedBolus?): List<ConfirmationLine> {
        val out = mutableListOf<ConfirmationLine>()
        out += ConfirmationLine(ConfirmationRole.BOLUS, rh.gs(R.string.format_insulin_units, capped.insulin))
        out += ConfirmationLine(ConfirmationRole.NORMAL, rh.gs(R.string.confirmation_line, rh.gs(R.string.duration), rh.gs(R.string.format_mins, capped.durationMinutes)))
        if (original != null && abs(capped.insulin - original.insulin) > 0.01) out += ConfirmationLine(ConfirmationRole.WARNING, rh.gs(R.string.constraint_applied))
        return out
    }

    /** Cancel a running temp basal on the master's pump. */
    private suspend fun applyCancelTempBasal(source: Sources, onError: (String) -> Unit) {
        uel.log(action = Action.CANCEL_TEMP_BASAL, source = source)
        val result = commandQueue.cancelTempBasal(enforceNew = true)
        if (!result.success) onError(result.comment)
    }

    /** Cancel a running extended bolus on the master's pump. */
    private suspend fun applyCancelExtendedBolus(source: Sources, onError: (String) -> Unit) {
        uel.log(action = Action.CANCEL_EXTENDED_BOLUS, source = source)
        val result = commandQueue.cancelExtended()
        if (!result.success) onError(result.comment)
    }

    /** The single cancel line — "Cancel: Temp basal" / "Cancel: Extended bolus" ([labelRes] = the cancelled action). */
    private fun buildCancelLine(labelRes: Int): List<ConfirmationLine> =
        listOf(ConfirmationLine(ConfirmationRole.PRIMARY, rh.gs(R.string.confirmation_line, rh.gs(R.string.cancel), rh.gs(labelRes))))

    /** The insulin-activate line — "Activate insulin: <label>" (PRIMARY: changing insulin materially affects IOB). */
    private fun buildInsulinActivateLine(ia: BatchAction.InsulinActivate): List<ConfirmationLine> =
        listOf(ConfirmationLine(ConfirmationRole.PRIMARY, rh.gs(R.string.confirmation_line, rh.gs(R.string.activate_insulin), ia.iCfg.insulinLabel)))

    /** Apply an insulin activation: re-apply the master's CURRENT active profile with this insulin (active-EPS precondition checked at prepare). */
    private suspend fun applyInsulinActivate(ia: BatchAction.InsulinActivate, source: Sources) {
        profileFunction.createProfileSwitchWithNewInsulin(ia.iCfg, source)
    }

    /** The careportal-event confirmation line (rarely surfaced — careportal auto-commits without showing the batch preview). */
    private fun buildTherapyEventLine(@Suppress("UNUSED_PARAMETER") te: BatchAction.TherapyEvent): List<ConfirmationLine> =
        listOf(ConfirmationLine(ConfirmationRole.NORMAL, rh.gs(R.string.careportal)))

    /**
     * Persist a careportal therapy event — the SOLE authoritative write (it then syncs to clients via NS). Glucose
     * is sanity-CLAMPED to a plausible window (an untrusted paired client's value otherwise feeds the UI/NS unchecked);
     * the window covers all real readings, so a legitimate value is never altered. The user entry is logged with the
     * executor-provided [source] (a relayed client event is logged as Sources.NSClient — the wire `te.source` is not read here).
     */
    private suspend fun applyTherapyEvent(te: BatchAction.TherapyEvent, source: Sources) {
        val event = TE(
            timestamp = te.timestamp,
            type = te.teType,
            glucoseUnit = GlucoseUnit.MGDL,
            location = te.location,
            arrow = te.arrow
        ).apply {
            te.glucoseMgdl?.let { glucose = it.coerceIn(10.0, 1000.0) } // sanity-clamp to a plausible mg/dL window (untrusted client value); covers all real readings
            glucoseType = te.glucoseType
            if (te.durationMinutes > 0) duration = T.mins(te.durationMinutes.toLong()).msecs()
            te.note?.let { note = it }
            enteredBy = "AAPS"
        }
        val values = buildList {
            add(ValueWithUnit.Timestamp(te.timestamp))
            add(ValueWithUnit.TEType(event.type))
            event.glucose?.let { g -> add(ValueWithUnit.fromGlucoseUnit(g, GlucoseUnit.MGDL)); te.glucoseType?.let { add(ValueWithUnit.TEMeterType(it)) } }
            if (te.durationMinutes > 0) add(ValueWithUnit.Minute(te.durationMinutes))
            te.location?.let { add(ValueWithUnit.TELocation(it)) }
            te.arrow?.let { add(ValueWithUnit.TEArrow(it)) }
        }
        // UEL action category mirrors the originating dialogs: fill site/cartridge keep their specific category
        // (CANNULA_CHANGE / INSULIN_CHANGE only ever originate from the fill dialog), all else is generic careportal.
        val uelAction = when (te.teType) {
            TE.Type.CANNULA_CHANGE -> Action.SITE_CHANGE
            TE.Type.INSULIN_CHANGE -> Action.RESERVOIR_CHANGE
            else                   -> Action.CAREPORTAL
        }
        persistenceLayer.insertPumpTherapyEventIfNewByTimestamp(therapyEvent = event, action = uelAction, source = source, note = te.note, listValues = values)
    }

    /** The therapy-event-edit confirmation line (rarely surfaced — the management screen shows its own local diff). */
    private fun buildTherapyEventEditLine(@Suppress("UNUSED_PARAMETER") te: BatchAction.TherapyEventEdit): List<ConfirmationLine> =
        listOf(ConfirmationLine(ConfirmationRole.NORMAL, rh.gs(R.string.site_rotation)))

    /**
     * Apply an EDIT to an existing therapy event (location/arrow/note): the master locates ITS OWN copy by
     * timestamp+type (the cross-device treatment identity) and updates it IN PLACE — keeping the `ids` bundle so the
     * NS sync re-uploads it as a PUT to the SAME record (a reset would POST a duplicate). A missing target is NOT
     * silently dropped: it [onError]s so the round-trip reports Rejected/Failed to the user (a stale client edit of a
     * since-deleted event must surface, not vanish). [note] is applied verbatim (incl. null) so clearing a note works.
     * A relayed edit is logged with the executor-provided [source].
     */
    private suspend fun applyTherapyEventEdit(te: BatchAction.TherapyEventEdit, source: Sources, onError: (String) -> Unit) {
        val existing = persistenceLayer.getTherapyEventDataFromToTime(te.timestamp, te.timestamp).firstOrNull { it.type == te.teType }
        if (existing == null) {
            aapsLogger.warn(LTag.DATABASE, "TherapyEvent edit target not found at ${te.timestamp} ${te.teType}")
            onError(rh.gs(R.string.clientcontrol_fail_site_entry_not_found))
            return
        }
        val updated = existing.copy(location = te.location, arrow = te.arrow, note = te.note) // keep ids → NS PUT-updates the same record (no duplicate)
        uel.log(
            action = if (te.teType == TE.Type.CANNULA_CHANGE) Action.SITE_LOCATION else Action.SENSOR_LOCATION,
            source = source,
            note = te.note,
            listValues = listOf(ValueWithUnit.Timestamp(te.timestamp), ValueWithUnit.TELocation(te.location ?: TE.Location.NONE), ValueWithUnit.TEArrow(te.arrow ?: TE.Arrow.NONE))
        )
        persistenceLayer.insertOrUpdateTherapyEvent(therapyEvent = updated)
    }

    override suspend fun deliverWizardBolus(
        insulin: Double,
        carbs: Int,
        carbTimeMinutes: Int,
        mgdlGlucose: Double?,
        bolusCalculatorResult: BCR?,
        notes: String?,
        source: Sources,
        onError: (String) -> Unit
    ) {
        // Type-specific entry point: build the canonical BOLUS_WIZARD end state from the wizard inputs,
        // then funnel into the shared core. Phone (WizardDialog) and watch (QuickWizard) differ only in
        // [source] — the recorded treatment is identical.
        val detailedBolusInfo = DetailedBolusInfo().apply {
            eventType = TE.Type.BOLUS_WIZARD
            this.insulin = insulin
            this.carbs = carbs.toDouble()
            bolusType = BS.Type.NORMAL
            mgdlGlucose?.let { this.mgdlGlucose = it; glucoseType = TE.MeterType.MANUAL }
            carbsTimestamp = dateUtil.now() + T.mins(carbTimeMinutes.toLong()).msecs()
            this.notes = notes
            bolusCalculatorResult?.let { this.bolusCalculatorResult = it }
        }
        if (insulin > 0 || carbs != 0) {
            val action = when {
                insulin == 0.0 -> Action.CARBS
                carbs == 0     -> Action.BOLUS
                else           -> Action.TREATMENT
            }
            val uelValues = listOfNotNull(
                ValueWithUnit.TEType(TE.Type.BOLUS_WIZARD),
                ValueWithUnit.Insulin(insulin).takeIf { insulin != 0.0 },
                ValueWithUnit.Gram(carbs).takeIf { carbs != 0 },
                ValueWithUnit.Minute(carbTimeMinutes).takeIf { carbTimeMinutes != 0 }
            )
            executeBolus(detailedBolusInfo, action, uelValues, notes, bolusCalculatorResult, source, onError)
        }
    }

    override suspend fun deliverBolusAdvisor(
        insulin: Double,
        mgdlGlucose: Double?,
        bolusCalculatorResult: BCR?,
        notes: String?,
        source: Sources,
        onError: (String) -> Unit
    ) {
        // Correction-only advisor bolus (BG high, carbs imminent): canonical CORRECTION_BOLUS end state,
        // and the eat reminder is scheduled on delivery success. The BCR rides the DBI but is not persisted
        // to the BCR table (matching both legacy advisor paths).
        val detailedBolusInfo = DetailedBolusInfo().apply {
            eventType = TE.Type.CORRECTION_BOLUS
            this.insulin = insulin
            carbs = 0.0
            mgdlGlucose?.let { this.mgdlGlucose = it; glucoseType = TE.MeterType.MANUAL }
            bolusCalculatorResult?.let { this.bolusCalculatorResult = it }
            this.notes = notes
        }
        if (insulin > 0) {
            val uelValues = listOf(
                ValueWithUnit.TEType(TE.Type.CORRECTION_BOLUS),
                ValueWithUnit.Insulin(insulin)
            )
            executeBolus(
                detailedBolusInfo, Action.BOLUS_ADVISOR, uelValues, notes,
                bolusCalculatorResult = null,
                source, onError,
                onSuccess = { automation.scheduleAutomationEventEatReminder() }
            )
        }
    }

    override suspend fun deliverInsulin(
        insulin: Double,
        note: String?,
        source: Sources,
        onError: (String) -> Unit,
        timestamp: Long?,
        treatmentNote: String?,
        recordOnly: Boolean,
        iCfg: ICfg?,
        onSuccess: () -> Unit
    ) {
        // Fixed-amount correction insulin: CORRECTION_BOLUS + BOLUS user entry. [note] is logged on the user
        // entry; [treatmentNote] (when given) is stored on the bolus treatment; [timestamp] back/forward-dates
        // it. [recordOnly] persists directly (needs [iCfg]) instead of delivering.
        val detailedBolusInfo = DetailedBolusInfo().apply {
            eventType = TE.Type.CORRECTION_BOLUS
            this.insulin = insulin
            this.notes = treatmentNote
            timestamp?.let { this.timestamp = it }
        }
        if (insulin > 0) {
            executeBolus(
                detailedBolusInfo,
                Action.BOLUS,
                listOf(ValueWithUnit.Insulin(insulin)),
                note = note,
                bolusCalculatorResult = null,
                source = source,
                onError = onError,
                onSuccess = onSuccess,
                recordOnly = recordOnly,
                iCfg = iCfg
            )
        }
    }

    override suspend fun deliverCarbs(carbs: Int, note: String?, source: Sources, onError: (String) -> Unit, onSuccess: () -> Unit) {
        // Instant carbs at now (zero insulin): CARBS_CORRECTION + CARBS user entry; note on the entry only.
        val detailedBolusInfo = DetailedBolusInfo().apply {
            eventType = TE.Type.CARBS_CORRECTION
            this.carbs = carbs.toDouble()
            carbsTimestamp = dateUtil.now()
        }
        if (carbs != 0) {
            executeBolus(
                detailedBolusInfo,
                Action.CARBS,
                listOf(ValueWithUnit.Gram(carbs)),
                note = note,
                bolusCalculatorResult = null,
                source = source,
                onError = onError,
                onSuccess = onSuccess
            )
        }
    }

    override suspend fun deliver(
        amount: Double,
        carbs: Int,
        carbsTime: Long?,
        carbsDuration: Int,
        bolusCalculatorResult: BCR?,
        notes: String?,
        source: Sources,
        onError: (String) -> Unit,
        eventType: TE.Type?,
        recordOnly: Boolean,
        iCfg: ICfg?,
        timestamp: Long?,
        onSuccess: () -> Unit
    ) {
        val detailedBolusInfo = DetailedBolusInfo().apply {
            eventType?.let { this.eventType = it }
            insulin = amount
            this.carbs = carbs.toDouble()
            bolusType = BS.Type.NORMAL
            carbsTimestamp = carbsTime
            this.carbsDuration = T.hours(carbsDuration.toLong()).msecs()
            this.notes = notes
            timestamp?.let { this.timestamp = it }
        }
        if (amount > 0 || carbs != 0) {
            val action = when {
                amount == 0.0     -> Action.CARBS
                carbs == 0        -> Action.BOLUS
                carbsDuration > 0 -> Action.EXTENDED_CARBS
                else              -> Action.TREATMENT
            }
            val uelValues = listOfNotNull(
                ValueWithUnit.Insulin(amount).takeIf { amount != 0.0 },
                ValueWithUnit.Gram(carbs).takeIf { carbs != 0 },
                ValueWithUnit.Hour(carbsDuration).takeIf { carbsDuration != 0 }
            )
            executeBolus(detailedBolusInfo, action, uelValues, note = null, bolusCalculatorResult, source, onError, onSuccess, recordOnly, iCfg)
        }
    }

    /**
     * Shared core every bolus entry point funnels into: gate → log → queue → persist BCR → super-bolus.
     * The caller has already built the canonical [detailedBolusInfo] and its [uelValues]; this is the one
     * audited path to the pump.
     */
    private suspend fun executeBolus(
        detailedBolusInfo: DetailedBolusInfo,
        action: Action,
        uelValues: List<ValueWithUnit>,
        note: String?,
        bolusCalculatorResult: BCR?,
        source: Sources,
        onError: (String) -> Unit,
        onSuccess: () -> Unit = {},
        recordOnly: Boolean = false,
        iCfg: ICfg? = null
    ) {
        if (recordOnly) {
            // No pump command (AAPSCLIENT / uninitialized pump / loop forbids a bolus): persist the
            // treatment(s) directly. The persistence layer emits the user entry, so there's no uel.log
            // and no running-mode gate here. The user's own notes ride [detailedBolusInfo.notes] onto the
            // BS/CA; the user entry gets the uniform "record" marker (no fragile "Record: notes" concat).
            val recordNote = rh.gs(R.string.record)
            appScope.launch {
                if (detailedBolusInfo.insulin > 0) {
                    val cfg = iCfg ?: profileFunction.getProfile()?.iCfg
                    if (cfg != null)
                        persistenceLayer.insertOrUpdateBolus(detailedBolusInfo.createBolus(cfg), action = action, source = source, note = recordNote)
                    else
                        aapsLogger.error(LTag.UI, "record-only bolus skipped: no insulin config available")
                }
                if (detailedBolusInfo.carbs != 0.0)
                    persistenceLayer.insertOrUpdateCarbs(detailedBolusInfo.createCarbs(), action = action, source = source, note = recordNote)
                onSuccess()
            }
            bolusCalculatorResult?.let { persistenceLayer.insertOrUpdateBolusCalculatorResult(it) }
            return
        }
        if (detailedBolusInfo.insulin > 0) {
            runningModeGuard.rejectionMessage(PumpCommandGate.CommandKind.BOLUS)?.let {
                onError(it)
                return
            }
        }
        uel.log(action = action, source = source, note = note, listValues = uelValues)
        appScope.launch {
            val result = commandQueue.bolus(detailedBolusInfo)
            if (!result.success && !bolusProgressData.isStopPressed) {
                val errorText = rh.gs(R.string.treatmentdeliveryerror) + "\n" + result.comment
                // Async delivery failure: the entry dialog is long gone, so surface it as an URGENT notification —
                // the single, reliable master-side alarm for EVERY bolus path, regardless of which UI started it.
                // SMB stays silent (the loop self-corrects next cycle). onError still fires so the initiating
                // transport relays too (the watch's sendError today; a client's late ack in phase 1b).
                if (detailedBolusInfo.bolusType != BS.Type.SMB)
                    notificationManager.post(NotificationId.BOLUS_DELIVERY_FAILED, errorText, validMinutes = 0, soundRes = R.raw.boluserror)
                onError(errorText)
            } else
                onSuccess()
        }
        bolusCalculatorResult?.let { persistenceLayer.insertOrUpdateBolusCalculatorResult(it) }
    }

    override suspend fun deliverFillBolus(amount: Double, notes: String?, source: Sources, onError: (String) -> Unit, onSuccess: () -> Unit) {
        // Prime/fill now rides the shared core (one audited path). PRIMING type keeps it out of IOB/TDD.
        val detailedBolusInfo = DetailedBolusInfo().apply {
            insulin = amount
            bolusType = BS.Type.PRIMING
            this.notes = notes
        }
        if (amount > 0) {
            executeBolus(
                detailedBolusInfo,
                Action.PRIME_BOLUS,
                listOf(ValueWithUnit.Insulin(amount)),
                note = notes,
                bolusCalculatorResult = null,
                source = source,
                onError = onError,
                onSuccess = onSuccess
            )
        }
    }

    override suspend fun deliverECarbs(carbs: Int, carbsTime: Long, duration: Int, delayMinutes: Int, notes: String?, source: Sources, onError: (String) -> Unit, onSuccess: () -> Unit) {
        // Funnel straight through the shared core (not via deliver) so the UEL is logged exactly once with
        // the eCarbs Timestamp; routing via deliver would re-log [Gram, Hour] as a second, duplicate row.
        val detailedBolusInfo = DetailedBolusInfo().apply {
            eventType = TE.Type.CORRECTION_BOLUS
            this.carbs = carbs.toDouble()
            carbsTimestamp = carbsTime
            carbsDuration = T.hours(duration.toLong()).msecs()
            this.notes = notes
        }
        if (carbs != 0) {
            val action = if (duration == 0) Action.CARBS else Action.EXTENDED_CARBS
            val uelValues = listOfNotNull(
                ValueWithUnit.Timestamp(carbsTime),
                ValueWithUnit.Gram(carbs),
                ValueWithUnit.Minute(delayMinutes).takeIf { delayMinutes != 0 },
                ValueWithUnit.Hour(duration).takeIf { duration != 0 }
            )
            executeBolus(detailedBolusInfo, action, uelValues, note = notes, bolusCalculatorResult = null, source, onError, onSuccess)
        }
    }
}

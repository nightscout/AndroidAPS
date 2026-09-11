package app.aaps.core.interfaces.bolus

import app.aaps.core.data.model.BCR
import app.aaps.core.data.model.ICfg
import app.aaps.core.data.model.TE
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ui.ConfirmationLine
import app.aaps.core.interfaces.rx.weardata.EventData

/**
 * Transport-neutral spine for wizard / quick-wizard bolus **prepare → confirm → deliver**. Owns the
 * single consume-once "pending bolus" slot shared by the full-wizard and quick-wizard prechecks, computes
 * the dose on the master's own current state, and delivers via the command queue (which re-applies bolus
 * constraints — the safety cap holds regardless of caller).
 *
 * One audited path a bolus reaches the pump from any trigger (wear today, client-control + the local UI
 * next): callers adapt only the transport — render the [PrepareResult.Preview], surface errors via the
 * [onError] sink, and tag the user entry with a [Sources].
 *
 * Declared here (interfaces only — no `core:objects` dependency, so it exposes primitives, not the
 * `BolusWizard` compute object); impl lives in `:implementation`. Idempotency: [confirm] drains the slot
 * and requires the bolusId to match, so a re-sent confirm finds it empty → never a double-bolus.
 */
interface WizardBolusExecutor {

    /**
     * Park a pending bolus directly. **Test-only seeding seam** for the consume-once [confirm] contract — there is
     * no production caller: every surface now parks via [prepareWizard] / [prepareQuickWizard] / [prepareBatch].
     */
    fun setPending(insulin: Double, carbs: Int, bolusCalculatorResult: BCR?, bolusId: Long)

    /** Drop any pending bolus (a non-wizard precheck supersedes it). */
    fun clearPending()

    /**
     * Quick-wizard prepare: gate → compute on current state → constraint-cap → park in the slot.
     * Returns the computed [PrepareResult.Preview] (or an [PrepareResult.Error]); the caller renders it
     * and, on the user's OK, calls [confirm] with the preview's `bolusId`.
     */
    suspend fun prepareQuickWizard(guid: String): PrepareResult

    /**
     * Manual-wizard prepare: recompute the dose on the master's own profile / temp target / COB / IOB from the
     * client's raw wizard [inputs], constraint-cap, and park. Same shape + [confirm] as [prepareQuickWizard],
     * but driven by inputs rather than a synced QuickWizard entry — so the master is authoritative for a
     * client's manual wizard bolus too.
     */
    suspend fun prepareWizard(inputs: WizardInputs): PrepareResult

    /**
     * Multi-action prepare (the unified Insulin / Carbs / Treatment path): cap a FIXED bolus/carbs (or keep a
     * record-only as given, uncapped), build the MERGED confirmation lines for the WHOLE batch (bolus + carbs + TT)
     * — the master is the sole author the client renders — park the bundle, and return the [PrepareResult.Preview].
     * [confirm] then delivers + applies the TT in decision-B order (raising TT first, bolus, lowering TT only if
     * accepted). At most one [BatchAction.Bolus] and one [BatchAction.TempTarget].
     */
    suspend fun prepareBatch(actions: List<BatchAction>): PrepareResult

    /**
     * Drain the slot, verify [bolusId] matches the parked bolus, and deliver. Idempotent (consume-once): a second
     * confirm finds the slot empty → [ConfirmResult.NoPending], never a second bolus. For a batch the steps run in
     * Decision-B order: target-RAISING TT (unconditional) → bolus/carbs → target-LOWERING TT (only if the bolus passed
     * the synchronous gate) → ProfileSwitch / RunningMode / InsulinActivate. [onError] reports only SYNCHRONOUS failures
     * (the running-mode gate or a record-only persist error); an async pump-delivery failure is surfaced by the
     * executor's URGENT alarm, NOT via [onError]. [asAdvisor] delivers the correction-only advisor bolus (high-BG "eat
     * later" branch) instead of the carb wizard bolus.
     */
    suspend fun confirm(bolusId: Long, source: Sources, onError: (String) -> Unit, asAdvisor: Boolean = false, correctionU: Double = 0.0): ConfirmResult

    /**
     * Canonical wizard / quick-wizard bolus — a type-specific entry point taking exactly the wizard
     * inputs. The executor preprocesses them into the `BOLUS_WIZARD` end state and funnels into the shared
     * delivery core, so a wizard bolus is recorded identically from phone or watch — only [source] differs.
     */
    suspend fun deliverWizardBolus(
        insulin: Double,
        carbs: Int,
        carbTimeMinutes: Int,
        mgdlGlucose: Double?,
        bolusCalculatorResult: BCR?,
        notes: String?,
        source: Sources,
        onError: (String) -> Unit
    )

    /**
     * Correction-only advisor bolus (high BG with carbs imminent) — the canonical `CORRECTION_BOLUS` end
     * state with a `BOLUS_ADVISOR` user entry; the eat reminder is scheduled on delivery success. Shared by
     * the wizard dialog and the quick-wizard advisor branches so the advisor records identically too.
     */
    suspend fun deliverBolusAdvisor(
        insulin: Double,
        mgdlGlucose: Double?,
        bolusCalculatorResult: BCR?,
        notes: String?,
        source: Sources,
        onError: (String) -> Unit
    )

    /**
     * Plain correction insulin bolus (e.g. a fixed-amount QuickWizard INSULIN button) — `CORRECTION_BOLUS`
     * with a `BOLUS` user entry. [note] is logged on the user entry; [treatmentNote] (when given) is also
     * stored on the bolus treatment; [timestamp] back/forward-dates it; [onSuccess] runs after a successful
     * submit. When [recordOnly] is true the bolus is persisted directly (no pump command, needs [iCfg]).
     */
    suspend fun deliverInsulin(
        insulin: Double,
        note: String?,
        source: Sources,
        onError: (String) -> Unit,
        timestamp: Long? = null,
        treatmentNote: String? = null,
        recordOnly: Boolean = false,
        iCfg: ICfg? = null,
        onSuccess: () -> Unit = {}
    )

    /**
     * Plain instant carbs at "now" (zero insulin, no duration) — `CARBS_CORRECTION` end state with an
     * `Action.CARBS` user entry; [note] is logged on the user entry only (not stored on the treatment).
     * For QuickWizard CARBS buttons and remote carb posts. [carbs] may be negative (COB correction).
     * The caller is responsible for any carbs constraint cap. [onSuccess] runs after a successful queue submit.
     */
    suspend fun deliverCarbs(carbs: Int, note: String?, source: Sources, onError: (String) -> Unit, onSuccess: () -> Unit = {})

    /**
     * Build + queue a generic bolus/carbs treatment. [onError] receives the mode-rejection and the async
     * failure; [onSuccess] runs after a successful submit. [eventType] is set on the treatment when given
     * (preserves the dialog's MEAL_BOLUS / CORRECTION_BOLUS / CARBS_CORRECTION classification). When
     * [recordOnly] is true the treatment is persisted directly (no pump command, no mode gate) — the
     * insulin record needs [iCfg]; the user-entry note falls back to the localized "record" marker.
     */
    suspend fun deliver(
        amount: Double,
        carbs: Int,
        carbsTime: Long?,
        carbsDuration: Int,
        bolusCalculatorResult: BCR?,
        notes: String?,
        source: Sources,
        onError: (String) -> Unit,
        eventType: TE.Type? = null,
        recordOnly: Boolean = false,
        iCfg: ICfg? = null,
        timestamp: Long? = null,
        onSuccess: () -> Unit = {}
    )

    /**
     * Prime/fill bolus (no carbs), `PRIMING` type so it's excluded from IOB/TDD. [notes] is set on the
     * treatment and logged on the user entry; [onSuccess] runs after a successful queue submit (e.g. the
     * fill dialog's chained profile switch on insulin change).
     */
    suspend fun deliverFillBolus(amount: Double, notes: String?, source: Sources, onError: (String) -> Unit, onSuccess: () -> Unit = {})

    /**
     * Extended/delayed carbs (zero insulin) — recorded once with the eCarbs timestamp. [delayMinutes] is the
     * delay from now used in the user entry (0 = no `Minute` value); [onSuccess] runs after a successful queue
     * submit. The single audited eCarbs path for the wizard, quick-wizard and wear.
     */
    suspend fun deliverECarbs(carbs: Int, carbsTime: Long, duration: Int, delayMinutes: Int, notes: String?, source: Sources, onError: (String) -> Unit, onSuccess: () -> Unit = {})

    sealed interface PrepareResult {

        /**
         * Computed, constraint-capped, parked. [bolusId] is the confirm id; [insulin]/[carbs] are for the master's
         * own log; [lines] are the master-built color-coded confirmation rows every surface (phone, client, watch)
         * renders verbatim; [advisorApplies] (with [advisorLines]) offers the high-BG "correct now, eat later" choice.
         */
        data class Preview(
            val insulin: Double,
            val carbs: Int,
            val bolusId: Long,
            val lines: List<ConfirmationLine> = emptyList(),
            val advisorApplies: Boolean = false,
            val advisorLines: List<ConfirmationLine> = emptyList(),
            val wizardDetail: EventData.WizardDetail? = null,
        ) : PrepareResult

        data class Error(val message: String) : PrepareResult

        /**
         * The prepare resolved to a no-op: nothing to do after caps/clamps (e.g. negative carbs entered with no COB
         * to remove, a back-dated COB removal, or an empty batch). This is NOT a delivery error — the caller should
         * surface it as the neutral "no action selected" message, never the bolus-error alarm/title.
         */
        data object NoAction : PrepareResult
    }

    sealed interface ConfirmResult {

        /** Bolus started (async). */
        data object Delivered : ConfirmResult

        /** Slot empty or id mismatch — nothing delivered (idempotent retry / stale confirm). */
        data object NoPending : ConfirmResult
    }

    /**
     * The manual Bolus Wizard's user inputs, sent client→master for [prepareWizard]. Only these user-entered
     * values travel; the master fills in its own live temp target / COB / IOB. [bg] is the value in the client's
     * BG field (user-overridable); the rest mirror the wizard dialog's toggles + amounts. [profileName] is the
     * dialog's profile selection: null → recompute on the master's CURRENT active profile (kept dynamic — the
     * master is authoritative); a name → that stored profile from the master's profile store (the master resolves
     * it, so a client/watch can relay a profile the master owns).
     */
    data class WizardInputs(
        val bg: Double,
        val carbs: Int,
        val percentage: Int,
        val directCorrection: Double,
        val carbTime: Int,
        val useBg: Boolean,
        val useCob: Boolean,
        val useIob: Boolean,
        val useTt: Boolean,
        val useTrend: Boolean,
        val alarm: Boolean,
        val notes: String,
        val eCarbsGrams: Int = 0,
        val eCarbsDelayMinutes: Int = 0,
        val eCarbsDurationHours: Int = 0,
        val profileName: String? = null,
        val source: Sources = Sources.WizardDialog
    )
}

package app.aaps.implementation.bolus

import app.aaps.core.data.model.BCR
import app.aaps.core.data.model.BS
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.ICfg
import app.aaps.core.data.model.RM
import app.aaps.core.data.model.TE
import app.aaps.core.data.model.TT
import app.aaps.core.data.iob.CobInfo
import app.aaps.core.data.pump.defs.PumpDescription
import app.aaps.core.data.time.T
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.aps.AutosensDataStore
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.automation.Automation
import app.aaps.core.interfaces.bolus.BatchAction
import app.aaps.core.interfaces.bolus.WizardBolusExecutor
import app.aaps.core.interfaces.constraints.Constraint
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.notifications.NotificationAction
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.notifications.NotificationLevel
import app.aaps.core.interfaces.profile.EffectiveProfile
import app.aaps.core.interfaces.profile.ProfileStore
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.pump.PumpWithConcentration
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.objects.profile.ProfileSealed
import app.aaps.core.objects.runningMode.RunningModeGuard
import app.aaps.core.objects.wizard.BolusWizard
import app.aaps.core.objects.wizard.QuickWizard
import app.aaps.core.objects.wizard.QuickWizardEntry
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argThat
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import javax.inject.Provider

/**
 * Focused tests for the canonical [WizardBolusExecutorImpl.deliverWizardBolus] entry point — the shared
 * end state that the phone wizard dialog and the watch quick-wizard both funnel into. Asserts the
 * recorded [DetailedBolusInfo] fields, the user-entry log values, and that the BCR is persisted exactly
 * once. `appScope` is `Unconfined` so the bolus coroutine runs synchronously within the call.
 */
class WizardBolusExecutorImplTest : TestBaseWithProfile() {

    @Mock lateinit var quickWizard: QuickWizard
    @Mock lateinit var bolusWizardProvider: Provider<BolusWizard>
    @Mock lateinit var runningModeGuard: RunningModeGuard
    @Mock lateinit var commandQueue: CommandQueue
    @Mock lateinit var persistenceLayer: PersistenceLayer
    @Mock lateinit var uel: UserEntryLogger
    @Mock lateinit var loop: Loop
    @Mock lateinit var automation: Automation
    @Mock lateinit var bolusProgressData: BolusProgressData

    private fun create() = WizardBolusExecutorImpl(
        aapsLogger, rh, config, quickWizard, bolusWizardProvider, profileFunction, profileRepository, insulin, iobCobCalculator, constraintsChecker, activePlugin,
        runningModeGuard, commandQueue, persistenceLayer, uel, loop, dateUtil, decimalFormatter, profileUtil, automation, notificationManager, bolusProgressData,
        CoroutineScope(Dispatchers.Unconfined)
    )

    // The base mocks ConstraintsChecker without a default answer; the FIXED/batch cap path needs a passthrough.
    private fun stubPassthroughConstraints() {
        whenever(constraintsChecker.applyBolusConstraints(any())).thenAnswer { it.getArgument<Constraint<Double>>(0) }
        whenever(constraintsChecker.applyCarbsConstraints(any())).thenAnswer { it.getArgument<Constraint<Int>>(0) }
        // The merged-confirmation line builders (prepareBatch) call rh.gs(...) + getUnits() heavily; the @Mock rh /
        // profileFunction return null by default → NPE in ConfirmationLine / ProfileUtil. Stub them to non-null.
        whenever(rh.gs(any<Int>())).thenReturn("s")
        whenever(rh.gs(any<Int>(), anyOrNull())).thenReturn("s")
        whenever(rh.gs(any<Int>(), anyOrNull(), anyOrNull())).thenReturn("s")
        whenever(profileFunction.getUnits()).thenReturn(GlucoseUnit.MGDL)
    }

    @Test
    fun deliverWizardBolus_recordsCanonicalBolusWizardAndPersistsBcrOnce() = runTest {
        whenever(runningModeGuard.rejectionMessage(any())).thenReturn(null)
        whenever(commandQueue.bolus(anyOrNull())).thenReturn(pumpEnactResultProvider.get().success(true))
        val bcr = mock<BCR>()
        val executor = create()

        val before = dateUtil.now()
        executor.deliverWizardBolus(
            insulin = 2.0,
            carbs = 30,
            carbTimeMinutes = 20,
            mgdlGlucose = 120.0,
            bolusCalculatorResult = bcr,
            notes = "lunch",
            source = Sources.WizardDialog,
            onError = { }
        )
        val after = dateUtil.now()

        val dbiCaptor = argumentCaptor<DetailedBolusInfo>()
        verify(commandQueue).bolus(dbiCaptor.capture())
        val dbi = dbiCaptor.firstValue
        assertThat(dbi.eventType).isEqualTo(TE.Type.BOLUS_WIZARD)
        assertThat(dbi.insulin).isEqualTo(2.0)
        assertThat(dbi.carbs).isEqualTo(30.0)
        assertThat(dbi.bolusType).isEqualTo(BS.Type.NORMAL)
        assertThat(dbi.mgdlGlucose).isEqualTo(120.0)
        assertThat(dbi.glucoseType).isEqualTo(TE.MeterType.MANUAL)
        assertThat(dbi.notes).isEqualTo("lunch")
        assertThat(dbi.bolusCalculatorResult).isEqualTo(bcr)
        val delay = T.mins(20).msecs()
        assertThat(dbi.carbsTimestamp).isAtLeast(before + delay)
        assertThat(dbi.carbsTimestamp).isAtMost(after + delay)

        val uelValues = argumentCaptor<List<ValueWithUnit>>()
        verify(uel).log(eq(Action.TREATMENT), eq(Sources.WizardDialog), eq("lunch"), uelValues.capture())
        assertThat(uelValues.firstValue).containsExactly(
            ValueWithUnit.TEType(TE.Type.BOLUS_WIZARD),
            ValueWithUnit.Insulin(2.0),
            ValueWithUnit.Gram(30),
            ValueWithUnit.Minute(20)
        ).inOrder()

        verify(persistenceLayer).insertOrUpdateBolusCalculatorResult(bcr)
    }

    @Test
    fun deliverWizardBolus_bolusOnly_omitsGlucoseAndDoesNotPersistNullBcr() = runTest {
        whenever(runningModeGuard.rejectionMessage(any())).thenReturn(null)
        whenever(commandQueue.bolus(anyOrNull())).thenReturn(pumpEnactResultProvider.get().success(true))
        val executor = create()

        executor.deliverWizardBolus(
            insulin = 1.0,
            carbs = 0,
            carbTimeMinutes = 0,
            mgdlGlucose = null,
            bolusCalculatorResult = null,
            notes = null,
            source = Sources.QuickWizard,
            onError = { }
        )

        val dbiCaptor = argumentCaptor<DetailedBolusInfo>()
        verify(commandQueue).bolus(dbiCaptor.capture())
        val dbi = dbiCaptor.firstValue
        assertThat(dbi.eventType).isEqualTo(TE.Type.BOLUS_WIZARD)
        assertThat(dbi.insulin).isEqualTo(1.0)
        assertThat(dbi.carbs).isEqualTo(0.0)
        // mgdlGlucose null → glucoseType is left untouched (not forced to MANUAL)
        assertThat(dbi.mgdlGlucose).isNull()
        assertThat(dbi.glucoseType).isNull()

        val uelValues = argumentCaptor<List<ValueWithUnit>>()
        verify(uel).log(eq(Action.BOLUS), eq(Sources.QuickWizard), anyOrNull(), uelValues.capture())
        assertThat(uelValues.firstValue).containsExactly(
            ValueWithUnit.TEType(TE.Type.BOLUS_WIZARD),
            ValueWithUnit.Insulin(1.0)
        ).inOrder()

        verify(persistenceLayer, never()).insertOrUpdateBolusCalculatorResult(any())
    }

    @Test
    fun deliverBolusAdvisor_recordsCorrectionBolus_schedulesEatReminderOnSuccess() = runTest {
        whenever(runningModeGuard.rejectionMessage(any())).thenReturn(null)
        whenever(commandQueue.bolus(anyOrNull())).thenReturn(pumpEnactResultProvider.get().success(true))
        val bcr = mock<BCR>()
        val executor = create()
        var errored = false

        executor.deliverBolusAdvisor(
            insulin = 1.5,
            mgdlGlucose = 200.0,
            bolusCalculatorResult = bcr,
            notes = "correction",
            source = Sources.WizardDialog,
            onError = { errored = true }
        )

        val dbiCaptor = argumentCaptor<DetailedBolusInfo>()
        verify(commandQueue).bolus(dbiCaptor.capture())
        val dbi = dbiCaptor.firstValue
        assertThat(dbi.eventType).isEqualTo(TE.Type.CORRECTION_BOLUS)
        assertThat(dbi.insulin).isEqualTo(1.5)
        assertThat(dbi.carbs).isEqualTo(0.0)
        assertThat(dbi.mgdlGlucose).isEqualTo(200.0)
        assertThat(dbi.glucoseType).isEqualTo(TE.MeterType.MANUAL)
        assertThat(dbi.bolusCalculatorResult).isEqualTo(bcr)

        val uelValues = argumentCaptor<List<ValueWithUnit>>()
        verify(uel).log(eq(Action.BOLUS_ADVISOR), eq(Sources.WizardDialog), eq("correction"), uelValues.capture())
        assertThat(uelValues.firstValue).containsExactly(
            ValueWithUnit.TEType(TE.Type.CORRECTION_BOLUS),
            ValueWithUnit.Insulin(1.5)
        ).inOrder()

        // BCR rides the DBI but is NOT persisted to the BCR table (matches the legacy advisor paths)
        verify(persistenceLayer, never()).insertOrUpdateBolusCalculatorResult(any())
        // Eat reminder fires on delivery success
        verify(automation).scheduleAutomationEventEatReminder()
        assertThat(errored).isFalse()
    }

    // ---- confirm(): the consume-once delivery the remote-bolus commit (and local QuickWizard) relies on ----

    @Test
    fun confirm_drainsSlotAndDeliversOnce_secondConfirmIsNoPending() = runTest {
        whenever(runningModeGuard.rejectionMessage(any())).thenReturn(null)
        whenever(commandQueue.bolus(anyOrNull())).thenReturn(pumpEnactResultProvider.get().success(true))
        val executor = create()
        // confirm() now constraint-caps the (insulin + correctionU) dose, so the passthrough must be stubbed
        // even on the setPending path (which bypasses prepare()'s own constraint stubbing).
        whenever(constraintsChecker.applyBolusConstraints(any())).thenAnswer { it.getArgument<Constraint<Double>>(0) }
        executor.setPending(insulin = 2.0, carbs = 0, bolusCalculatorResult = null, bolusId = 999L)

        val first = executor.confirm(999L, Sources.NSClient, { })
        val second = executor.confirm(999L, Sources.NSClient, { })

        assertThat(first).isEqualTo(WizardBolusExecutor.ConfirmResult.Delivered)
        assertThat(second).isEqualTo(WizardBolusExecutor.ConfirmResult.NoPending)
        // The consume-once slot guarantees the pump fires EXACTLY once — no double-dose on a re-sent commit.
        verify(commandQueue, times(1)).bolus(anyOrNull())
    }

    @Test
    fun confirm_wrongBolusId_isNoPending_andDeliversNothing() = runTest {
        val executor = create()
        executor.setPending(insulin = 2.0, carbs = 0, bolusCalculatorResult = null, bolusId = 999L)

        val result = executor.confirm(123L, Sources.NSClient, { })

        assertThat(result).isEqualTo(WizardBolusExecutor.ConfirmResult.NoPending)
        verify(commandQueue, never()).bolus(anyOrNull())
    }

    @Test
    fun confirm_parkedDosesAreIsolatedByBolusId_aSecondPrepareDoesNotClobberTheFirst() = runTest {
        whenever(runningModeGuard.rejectionMessage(any())).thenReturn(null)
        whenever(commandQueue.bolus(anyOrNull())).thenReturn(pumpEnactResultProvider.get().success(true))
        val executor = create()
        // confirm() now constraint-caps the (insulin + correctionU) dose, so the passthrough must be stubbed
        // even on the setPending path (which bypasses prepare()'s own constraint stubbing).
        whenever(constraintsChecker.applyBolusConstraints(any())).thenAnswer { it.getArgument<Constraint<Double>>(0) }
        // bolusId is a timestamp; use realistic recent ids so evictStalePending's TTL window keeps them parked.
        executor.setPending(insulin = 1.0, carbs = 0, bolusCalculatorResult = null, bolusId = now)
        // A second actor's prepare (different bolusId) must NOT clobber the first — per-id slots, not one shared var.
        executor.setPending(insulin = 2.0, carbs = 0, bolusCalculatorResult = null, bolusId = now - 1000)

        // The first parked dose still delivers (not overwritten), and a re-sent commit of it finds the slot drained.
        assertThat(executor.confirm(now, Sources.NSClient, { })).isEqualTo(WizardBolusExecutor.ConfirmResult.Delivered)
        assertThat(executor.confirm(now, Sources.NSClient, { })).isEqualTo(WizardBolusExecutor.ConfirmResult.NoPending)
        // The second parked dose is intact and delivers on its own commit.
        assertThat(executor.confirm(now - 1000, Sources.NSClient, { })).isEqualTo(WizardBolusExecutor.ConfirmResult.Delivered)
        verify(commandQueue, times(2)).bolus(anyOrNull())
    }

    // ---- prepareBatch() → confirm(): the two-step multi-action transaction (decision-B ordering) ----

    @Test
    fun prepareBatch_raisingTtFirstThenBolus_bothAppliedOnConfirm() = runTest {
        whenever(runningModeGuard.rejectionMessage(any())).thenReturn(null)
        whenever(commandQueue.bolus(anyOrNull())).thenReturn(pumpEnactResultProvider.get().success(true))
        stubPassthroughConstraints()
        val executor = create()
        val actions = listOf(
            BatchAction.TempTarget(reason = TT.Reason.HYPOGLYCEMIA.text, lowMgdl = 120.0, highMgdl = 120.0, durationMinutes = 60, startOffsetMinutes = 0),
            BatchAction.Bolus(insulin = 1.0, carbs = 0, carbsTimeOffsetMinutes = 0, carbsDurationHours = 0, recordOnly = false, notes = "", timestamp = 0L, iCfg = null)
        )

        val prepared = executor.prepareBatch(actions) as WizardBolusExecutor.PrepareResult.Preview
        val result = executor.confirm(prepared.bolusId, Sources.NSClient, { })

        assertThat(result).isEqualTo(WizardBolusExecutor.ConfirmResult.Delivered)
        verify(persistenceLayer).insertAndCancelCurrentTemporaryTarget(any(), any(), any(), anyOrNull(), any()) // target-raising TT applied unconditionally
        verify(commandQueue).bolus(anyOrNull()) // bolus delivered
    }

    @Test
    fun prepareBatch_quickWizardGuid_marksOriginatingEntryUsedOnConfirmNotPrepare() = runTest {
        // The master (SOT) marks the QuickWizard used on a successful commit — the client never writes the synced
        // QuickWizard pref itself (which previously raced the commit → "Update settings … Another action in progress").
        whenever(runningModeGuard.rejectionMessage(any())).thenReturn(null)
        whenever(commandQueue.bolus(anyOrNull())).thenReturn(pumpEnactResultProvider.get().success(true))
        stubPassthroughConstraints()
        val entry = mock<QuickWizardEntry>()
        whenever(quickWizard.get("qw-1")).thenReturn(entry)
        val executor = create()
        val actions = listOf(
            BatchAction.Bolus(insulin = 0.5, carbs = 0, carbsTimeOffsetMinutes = 0, carbsDurationHours = 0, recordOnly = false, notes = "Pre-bolus", timestamp = 0L, iCfg = null, quickWizardGuid = "qw-1")
        )

        val prepared = executor.prepareBatch(actions) as WizardBolusExecutor.PrepareResult.Preview
        verify(entry, never()).markAsUsed() // NOT marked at prepare time (consume-once: only a real delivery counts)

        val result = executor.confirm(prepared.bolusId, Sources.QuickWizard, { })

        assertThat(result).isEqualTo(WizardBolusExecutor.ConfirmResult.Delivered)
        verify(entry).markAsUsed() // marked HERE on the master after the bolus is delivered
    }

    @Test
    fun prepareBatch_noQuickWizardGuid_marksNothing() = runTest {
        // A dialog / wear batch (no guid) must not resolve or mark any entry — guards against a false-positive mark.
        whenever(runningModeGuard.rejectionMessage(any())).thenReturn(null)
        whenever(commandQueue.bolus(anyOrNull())).thenReturn(pumpEnactResultProvider.get().success(true))
        stubPassthroughConstraints()
        val executor = create()
        val actions = listOf(
            BatchAction.Bolus(insulin = 0.5, carbs = 0, carbsTimeOffsetMinutes = 0, carbsDurationHours = 0, recordOnly = false, notes = "", timestamp = 0L, iCfg = null)
        )

        val prepared = executor.prepareBatch(actions) as WizardBolusExecutor.PrepareResult.Preview
        executor.confirm(prepared.bolusId, Sources.NSClient, { })

        verify(quickWizard, never()).get(any<String>())
    }

    @Test
    fun prepareBatch_eatingSoonTtSkipped_whenBolusRejectedAtCommit() = runTest {
        whenever(runningModeGuard.rejectionMessage(any())).thenReturn(null) // prepare passes the gate
        stubPassthroughConstraints()
        val executor = create()
        val actions = listOf(
            BatchAction.TempTarget(reason = TT.Reason.EATING_SOON.text, lowMgdl = 90.0, highMgdl = 90.0, durationMinutes = 30, startOffsetMinutes = 0),
            BatchAction.Bolus(insulin = 2.0, carbs = 0, carbsTimeOffsetMinutes = 0, carbsDurationHours = 0, recordOnly = false, notes = "", timestamp = 0L, iCfg = null)
        )
        val prepared = executor.prepareBatch(actions) as WizardBolusExecutor.PrepareResult.Preview

        // The loop suspends between prepare and commit → the bolus is mode-rejected at delivery.
        whenever(runningModeGuard.rejectionMessage(any())).thenReturn("loop suspended")
        var err: String? = null
        executor.confirm(prepared.bolusId, Sources.NSClient, { err = it })

        assertThat(err).isNotNull()
        // Decision B: a target-LOWERING eating-soon TT is NOT applied when the bolus fails (else the loop chases the low).
        verify(persistenceLayer, never()).insertAndCancelCurrentTemporaryTarget(any(), any(), any(), anyOrNull(), any())
        verify(commandQueue, never()).bolus(anyOrNull())
    }

    @Test
    fun prepareBatch_profileSwitch_parksAndConfirmAppliesViaCreateProfileSwitch() = runTest {
        stubPassthroughConstraints()
        whenever(profileFunction.getProfile()).thenReturn(mock<EffectiveProfile>())
        whenever(profileFunction.getOriginalProfileName()).thenReturn("Test")
        val executor = create()

        val prepared = executor.prepareBatch(listOf(BatchAction.ProfileSwitch(percentage = 120, timeShiftHours = 2, durationMinutes = 60))) as WizardBolusExecutor.PrepareResult.Preview
        val result = executor.confirm(prepared.bolusId, Sources.NSClient, { })

        assertThat(result).isEqualTo(WizardBolusExecutor.ConfirmResult.Delivered)
        // PS-only batch: no pump bolus, no TT — only the dialog-free profile switch applied with the parked values.
        verify(commandQueue, never()).bolus(anyOrNull())
        verify(profileFunction).createProfileSwitch(eq(60), eq(120), eq(2), eq(Action.PROFILE_SWITCH), eq(Sources.NSClient), anyOrNull(), any())
    }

    @Test
    fun prepareBatch_profileSwitch_outOfRange_returnsErrorAndAppliesNothing() = runTest {
        stubPassthroughConstraints()
        whenever(profileFunction.getProfile()).thenReturn(mock<EffectiveProfile>())
        val executor = create()

        val result = executor.prepareBatch(listOf(BatchAction.ProfileSwitch(percentage = 9999, timeShiftHours = 0, durationMinutes = 30)))

        assertThat(result).isInstanceOf(WizardBolusExecutor.PrepareResult.Error::class.java)
        verify(profileFunction, never()).createProfileSwitch(any(), any(), any(), any(), any(), anyOrNull(), any())
    }

    // ---- prepareBatch(): negative carbs = COB removal (issue: was mislabeled "Bolus reported an error") ----

    @Test
    fun prepareBatch_negativeCarbsWithinCob_atNow_parksTheRemoval() = runTest {
        stubPassthroughConstraints()
        whenever(iobCobCalculator.getCobInfo(any())).thenReturn(CobInfo(0L, displayCob = 30.0, futureCarbs = 0.0))
        val executor = create()

        val prepared = executor.prepareBatch(
            listOf(BatchAction.Bolus(insulin = 0.0, carbs = -10, carbsTimeOffsetMinutes = 0, carbsDurationHours = 0, recordOnly = false, notes = "", timestamp = 0L, iCfg = null))
        )

        // A removal within COB is a real action — NOT a no-op — and is parked verbatim.
        assertThat(prepared).isInstanceOf(WizardBolusExecutor.PrepareResult.Preview::class.java)
        assertThat((prepared as WizardBolusExecutor.PrepareResult.Preview).carbs).isEqualTo(-10)
    }

    @Test
    fun prepareBatch_negativeCarbsExceedingCob_clampsMagnitudeToCob() = runTest {
        stubPassthroughConstraints()
        whenever(iobCobCalculator.getCobInfo(any())).thenReturn(CobInfo(0L, displayCob = 5.0, futureCarbs = 0.0))
        val executor = create()

        val prepared = executor.prepareBatch(
            listOf(BatchAction.Bolus(insulin = 0.0, carbs = -10, carbsTimeOffsetMinutes = 0, carbsDurationHours = 0, recordOnly = false, notes = "", timestamp = 0L, iCfg = null))
        )

        // Can't remove more than the 5 g on board → clamped to -5, still a real action.
        assertThat((prepared as WizardBolusExecutor.PrepareResult.Preview).carbs).isEqualTo(-5)
    }

    @Test
    fun prepareBatch_negativeCarbsWithNoCob_isNoAction() = runTest {
        stubPassthroughConstraints()
        whenever(iobCobCalculator.getCobInfo(any())).thenReturn(CobInfo(0L, displayCob = 0.0, futureCarbs = 0.0))
        val executor = create()

        val prepared = executor.prepareBatch(
            listOf(BatchAction.Bolus(insulin = 0.0, carbs = -10, carbsTimeOffsetMinutes = 0, carbsDurationHours = 0, recordOnly = false, notes = "", timestamp = 0L, iCfg = null))
        )

        // Nothing on board to remove → a genuine no-op, surfaced as NoAction (neutral), never the bolus-error path.
        assertThat(prepared).isEqualTo(WizardBolusExecutor.PrepareResult.NoAction)
    }

    @Test
    fun prepareBatch_negativeCarbsInFuture_isNoAction() = runTest {
        stubPassthroughConstraints()
        whenever(iobCobCalculator.getCobInfo(any())).thenReturn(CobInfo(0L, displayCob = 30.0, futureCarbs = 0.0))
        val executor = create()

        val prepared = executor.prepareBatch(
            listOf(BatchAction.Bolus(insulin = 0.0, carbs = -10, carbsTimeOffsetMinutes = 60, carbsDurationHours = 0, recordOnly = false, notes = "", timestamp = 0L, iCfg = null))
        )

        // A future-dated removal is dropped (you can't pre-remove carbs not yet on board) → NoAction.
        assertThat(prepared).isEqualTo(WizardBolusExecutor.PrepareResult.NoAction)
    }

    @Test
    fun prepareBatch_insulinActivate_parksAndConfirmAppliesViaCreateProfileSwitchWithNewInsulin() = runTest {
        stubPassthroughConstraints()
        // An active EPS profile is required to re-apply the new insulin onto.
        whenever(profileFunction.getProfile()).thenReturn(mock<ProfileSealed.EPS>())
        whenever(profileFunction.createProfileSwitchWithNewInsulin(any(), any())).thenReturn(true)
        val executor = create()
        val iCfg = ICfg(insulinLabel = "Rapid", insulinEndTime = 360, insulinPeakTime = 75, concentration = 1.0)

        val prepared = executor.prepareBatch(listOf(BatchAction.InsulinActivate(iCfg))) as WizardBolusExecutor.PrepareResult.Preview
        val result = executor.confirm(prepared.bolusId, Sources.NSClient, { })

        assertThat(result).isEqualTo(WizardBolusExecutor.ConfirmResult.Delivered)
        // InsulinActivate-only batch: no pump bolus — only the master re-applies its active profile with the new insulin.
        verify(commandQueue, never()).bolus(anyOrNull())
        verify(profileFunction).createProfileSwitchWithNewInsulin(argThat { insulinLabel == "Rapid" }, eq(Sources.NSClient))
    }

    @Test
    fun prepareBatch_insulinActivate_noActiveEpsProfile_returnsErrorAndAppliesNothing() = runTest {
        stubPassthroughConstraints()
        whenever(profileFunction.getProfile()).thenReturn(null) // no active EPS profile to re-apply onto → reject at prepare
        val executor = create()
        val iCfg = ICfg(insulinLabel = "Rapid", insulinEndTime = 360, insulinPeakTime = 75, concentration = 1.0)

        val result = executor.prepareBatch(listOf(BatchAction.InsulinActivate(iCfg)))

        assertThat(result).isInstanceOf(WizardBolusExecutor.PrepareResult.Error::class.java)
        verify(profileFunction, never()).createProfileSwitchWithNewInsulin(any(), any())
    }

    @Test
    fun prepareBatch_namedProfileSwitch_appliesViaNamedCreateProfileSwitchFromMasterStore() = runTest {
        stubPassthroughConstraints()
        val store = mock<ProfileStore>()
        whenever(store.getSpecificProfile("Lunch")).thenReturn(mock())
        whenever(profileRepository.profile).thenReturn(MutableStateFlow(store))
        whenever(insulin.iCfg).thenReturn(mock())
        val executor = create()

        val prepared = executor.prepareBatch(listOf(BatchAction.ProfileSwitch(110, 0, 60, profileName = "Lunch"))) as WizardBolusExecutor.PrepareResult.Preview
        val result = executor.confirm(prepared.bolusId, Sources.NSClient, { })

        assertThat(result).isEqualTo(WizardBolusExecutor.ConfirmResult.Delivered)
        // The named overload resolves the target from the MASTER's store (a client may relay a name the master owns).
        verify(profileFunction).createProfileSwitch(eq(store), eq("Lunch"), eq(60), eq(110), eq(0), any(), eq(Action.PROFILE_SWITCH), eq(Sources.NSClient), anyOrNull(), any(), any())
    }

    @Test
    fun prepareBatch_namedProfileSwitch_notInMasterStore_returnsError() = runTest {
        stubPassthroughConstraints()
        val store = mock<ProfileStore>()
        whenever(store.getSpecificProfile(any())).thenReturn(null)
        whenever(profileRepository.profile).thenReturn(MutableStateFlow(store))
        val executor = create()

        val result = executor.prepareBatch(listOf(BatchAction.ProfileSwitch(100, 0, 0, profileName = "Ghost")))

        assertThat(result).isInstanceOf(WizardBolusExecutor.PrepareResult.Error::class.java)
    }

    // ---- prepareBatch() → confirm(): running-mode changes (the shared path for the phone screen, a client, and wear) ----

    @Test
    fun prepareBatch_runningMode_parksAndConfirmAppliesViaHandleRunningModeChange() = runTest {
        stubPassthroughConstraints()
        whenever(loop.allowedNextModes()).thenReturn(listOf(RM.Mode.CLOSED_LOOP))
        whenever(profileFunction.getProfile()).thenReturn(mock<EffectiveProfile>())
        val executor = create()

        val prepared = executor.prepareBatch(listOf(BatchAction.RunningMode(RM.Mode.CLOSED_LOOP))) as WizardBolusExecutor.PrepareResult.Preview
        val result = executor.confirm(prepared.bolusId, Sources.NSClient, { })

        assertThat(result).isEqualTo(WizardBolusExecutor.ConfirmResult.Delivered)
        // RM-only batch: no pump bolus, no TT — only the dialog-free running-mode change with the mode-derived action.
        verify(commandQueue, never()).bolus(anyOrNull())
        verify(loop).handleRunningModeChange(eq(RM.Mode.CLOSED_LOOP), eq(Action.CLOSED_LOOP_MODE), eq(Sources.NSClient), any(), eq(0), any())
    }

    @Test
    fun prepareBatch_runningMode_illegalTransition_returnsErrorAndAppliesNothing() = runTest {
        stubPassthroughConstraints()
        whenever(loop.allowedNextModes()).thenReturn(emptyList()) // OPEN_LOOP not currently allowed
        val executor = create()

        val result = executor.prepareBatch(listOf(BatchAction.RunningMode(RM.Mode.OPEN_LOOP)))

        assertThat(result).isInstanceOf(WizardBolusExecutor.PrepareResult.Error::class.java)
        verify(loop, never()).handleRunningModeChange(any(), any(), any(), any(), any(), any())
    }

    @Test
    fun prepareBatch_runningMode_temporaryModeWithoutDuration_returnsError() = runTest {
        stubPassthroughConstraints()
        whenever(loop.allowedNextModes()).thenReturn(listOf(RM.Mode.SUSPENDED_BY_USER))
        val executor = create()

        val result = executor.prepareBatch(listOf(BatchAction.RunningMode(RM.Mode.SUSPENDED_BY_USER, durationMinutes = 0)))

        assertThat(result).isInstanceOf(WizardBolusExecutor.PrepareResult.Error::class.java)
        verify(loop, never()).handleRunningModeChange(any(), any(), any(), any(), any(), any())
    }

    @Test
    fun prepareBatch_runningMode_disconnectWithDuration_appliesWithDuration() = runTest {
        stubPassthroughConstraints()
        whenever(loop.allowedNextModes()).thenReturn(listOf(RM.Mode.DISCONNECTED_PUMP))
        whenever(profileFunction.getProfile()).thenReturn(mock<EffectiveProfile>())
        val executor = create()

        val prepared = executor.prepareBatch(listOf(BatchAction.RunningMode(RM.Mode.DISCONNECTED_PUMP, durationMinutes = 60))) as WizardBolusExecutor.PrepareResult.Preview
        val result = executor.confirm(prepared.bolusId, Sources.NSClient, { })

        assertThat(result).isEqualTo(WizardBolusExecutor.ConfirmResult.Delivered)
        verify(loop).handleRunningModeChange(eq(RM.Mode.DISCONNECTED_PUMP), eq(Action.DISCONNECT), eq(Sources.NSClient), any(), eq(60), any())
    }

    @Test
    fun prepareBatch_runningMode_resumeFromDisconnect_usesReconnectAction() = runTest {
        stubPassthroughConstraints()
        whenever(loop.allowedNextModes()).thenReturn(listOf(RM.Mode.RESUME))
        whenever(loop.runningMode()).thenReturn(RM.Mode.DISCONNECTED_PUMP) // currently disconnected → RESUME is a pump reconnect
        whenever(profileFunction.getProfile()).thenReturn(mock<EffectiveProfile>())
        val executor = create()

        val prepared = executor.prepareBatch(listOf(BatchAction.RunningMode(RM.Mode.RESUME))) as WizardBolusExecutor.PrepareResult.Preview
        val result = executor.confirm(prepared.bolusId, Sources.NSClient, { })

        assertThat(result).isEqualTo(WizardBolusExecutor.ConfirmResult.Delivered)
        // RESUME resolves to RECONNECT (not RESUME) because the current mode is DISCONNECTED_PUMP — the UEL action differs.
        verify(loop).handleRunningModeChange(eq(RM.Mode.RESUME), eq(Action.RECONNECT), eq(Sources.NSClient), any(), eq(0), any())
    }

    // ---- prepareBatch() → confirm(): manual temp basal / extended bolus (relayed from a client, or master-local) ----

    @Test
    fun prepareBatch_tempBasalPercent_parksAndConfirmAppliesViaCommandQueue() = runTest {
        stubPassthroughConstraints()
        whenever(runningModeGuard.rejectionMessage(any())).thenReturn(null)
        whenever(profileFunction.getProfile()).thenReturn(mock<EffectiveProfile>())
        whenever(constraintsChecker.applyBasalPercentConstraints(any(), any())).thenAnswer { it.getArgument<Constraint<Int>>(0) }
        val pump = mock<PumpWithConcentration>()
        whenever(activePlugin.activePump).thenReturn(pump)
        whenever(pump.isInitialized()).thenReturn(true)
        whenever(pump.pumpDescription).thenReturn(PumpDescription().also { it.isTempBasalCapable = true; it.tempBasalStyle = PumpDescription.PERCENT })
        whenever(commandQueue.tempBasalPercent(any(), any(), any(), any(), any())).thenReturn(pumpEnactResultProvider.get().success(true))
        val executor = create()

        val prepared = executor.prepareBatch(listOf(BatchAction.TempBasal(rate = 150.0, isPercent = true, durationMinutes = 30))) as WizardBolusExecutor.PrepareResult.Preview
        val result = executor.confirm(prepared.bolusId, Sources.NSClient, { })

        assertThat(result).isEqualTo(WizardBolusExecutor.ConfirmResult.Delivered)
        verify(commandQueue, never()).bolus(anyOrNull())
        verify(commandQueue).tempBasalPercent(eq(150), eq(30), eq(true), any(), any())
    }

    @Test
    fun prepareBatch_tempBasal_styleMismatch_returnsOutOfSyncAndAppliesNothing() = runTest {
        stubPassthroughConstraints()
        whenever(runningModeGuard.rejectionMessage(any())).thenReturn(null)
        val pump = mock<PumpWithConcentration>()
        whenever(activePlugin.activePump).thenReturn(pump)
        whenever(pump.isInitialized()).thenReturn(true)
        // The master's pump is ABSOLUTE-only, but the client relayed a PERCENT action → out of sync → reject, apply nothing.
        whenever(pump.pumpDescription).thenReturn(PumpDescription().also { it.isTempBasalCapable = true; it.tempBasalStyle = PumpDescription.ABSOLUTE })
        val executor = create()

        val result = executor.prepareBatch(listOf(BatchAction.TempBasal(rate = 150.0, isPercent = true, durationMinutes = 30)))

        assertThat(result).isInstanceOf(WizardBolusExecutor.PrepareResult.Error::class.java)
        verify(commandQueue, never()).tempBasalPercent(any(), any(), any(), any(), any())
        verify(commandQueue, never()).tempBasalAbsolute(any(), any(), any(), any(), any())
    }

    @Test
    fun prepareBatch_extendedBolus_parksAndConfirmDeliversViaCommandQueue() = runTest {
        stubPassthroughConstraints()
        whenever(runningModeGuard.rejectionMessage(any())).thenReturn(null)
        whenever(constraintsChecker.applyExtendedBolusConstraints(any())).thenAnswer { it.getArgument<Constraint<Double>>(0) }
        val pump = mock<PumpWithConcentration>()
        whenever(activePlugin.activePump).thenReturn(pump)
        whenever(pump.isInitialized()).thenReturn(true)
        whenever(pump.pumpDescription).thenReturn(PumpDescription().also { it.isExtendedBolusCapable = true })
        whenever(commandQueue.extendedBolus(any(), any())).thenReturn(pumpEnactResultProvider.get().success(true))
        val executor = create()

        val prepared = executor.prepareBatch(listOf(BatchAction.ExtendedBolus(insulin = 1.5, durationMinutes = 120))) as WizardBolusExecutor.PrepareResult.Preview
        val result = executor.confirm(prepared.bolusId, Sources.NSClient, { })

        assertThat(result).isEqualTo(WizardBolusExecutor.ConfirmResult.Delivered)
        verify(commandQueue, never()).bolus(anyOrNull())
        verify(commandQueue).extendedBolus(eq(1.5), eq(120))
    }

    @Test
    fun prepareBatch_tempBasalAbsolute_appliesViaCommandQueueAbsolute() = runTest {
        stubPassthroughConstraints()
        whenever(runningModeGuard.rejectionMessage(any())).thenReturn(null)
        whenever(profileFunction.getProfile()).thenReturn(mock<EffectiveProfile>())
        whenever(constraintsChecker.applyBasalConstraints(any(), any())).thenAnswer { it.getArgument<Constraint<Double>>(0) }
        val pump = mock<PumpWithConcentration>()
        whenever(activePlugin.activePump).thenReturn(pump)
        whenever(pump.isInitialized()).thenReturn(true)
        whenever(pump.pumpDescription).thenReturn(PumpDescription().also { it.isTempBasalCapable = true; it.tempBasalStyle = PumpDescription.ABSOLUTE })
        whenever(commandQueue.tempBasalAbsolute(any(), any(), any(), any(), any())).thenReturn(pumpEnactResultProvider.get().success(true))
        val executor = create()

        val prepared = executor.prepareBatch(listOf(BatchAction.TempBasal(rate = 1.25, isPercent = false, durationMinutes = 45))) as WizardBolusExecutor.PrepareResult.Preview
        val result = executor.confirm(prepared.bolusId, Sources.NSClient, { })

        assertThat(result).isEqualTo(WizardBolusExecutor.ConfirmResult.Delivered)
        verify(commandQueue).tempBasalAbsolute(eq(1.25), eq(45), eq(true), any(), any())
        verify(commandQueue, never()).tempBasalPercent(any(), any(), any(), any(), any())
    }

    @Test
    fun prepareBatch_tempBasalZeroPercent_isAllowedNotRejected() = runTest {
        // A 0% temp basal is a valid "suspend basal" command — it must NOT be rejected like a 0 U extended bolus.
        stubPassthroughConstraints()
        whenever(runningModeGuard.rejectionMessage(any())).thenReturn(null)
        whenever(profileFunction.getProfile()).thenReturn(mock<EffectiveProfile>())
        whenever(constraintsChecker.applyBasalPercentConstraints(any(), any())).thenAnswer { it.getArgument<Constraint<Int>>(0) }
        val pump = mock<PumpWithConcentration>()
        whenever(activePlugin.activePump).thenReturn(pump)
        whenever(pump.isInitialized()).thenReturn(true)
        whenever(pump.pumpDescription).thenReturn(PumpDescription().also { it.isTempBasalCapable = true; it.tempBasalStyle = PumpDescription.PERCENT })
        whenever(commandQueue.tempBasalPercent(any(), any(), any(), any(), any())).thenReturn(pumpEnactResultProvider.get().success(true))
        val executor = create()

        val prepared = executor.prepareBatch(listOf(BatchAction.TempBasal(rate = 0.0, isPercent = true, durationMinutes = 30))) as WizardBolusExecutor.PrepareResult.Preview
        executor.confirm(prepared.bolusId, Sources.NSClient, { })

        verify(commandQueue).tempBasalPercent(eq(0), eq(30), eq(true), any(), any())
    }

    @Test
    fun prepareBatch_extendedBolus_cappedToZero_returnsErrorAndDeliversNothing() = runTest {
        // Closed loop / safety can cap the EB to 0 → reject (no empty confirmation, no phantom delivery).
        stubPassthroughConstraints()
        whenever(runningModeGuard.rejectionMessage(any())).thenReturn(null)
        val zero = mock<Constraint<Double>>()
        whenever(zero.value()).thenReturn(0.0)
        whenever(constraintsChecker.applyExtendedBolusConstraints(any())).thenReturn(zero)
        val pump = mock<PumpWithConcentration>()
        whenever(activePlugin.activePump).thenReturn(pump)
        whenever(pump.isInitialized()).thenReturn(true)
        whenever(pump.pumpDescription).thenReturn(PumpDescription().also { it.isExtendedBolusCapable = true })
        val executor = create()

        val result = executor.prepareBatch(listOf(BatchAction.ExtendedBolus(insulin = 1.0, durationMinutes = 30)))

        assertThat(result).isInstanceOf(WizardBolusExecutor.PrepareResult.Error::class.java)
        verify(commandQueue, never()).extendedBolus(any(), any())
    }

    @Test
    fun prepareBatch_cancelTempBasal_parksAndConfirmCancelsViaCommandQueue() = runTest {
        stubPassthroughConstraints()
        val pump = mock<PumpWithConcentration>()
        whenever(activePlugin.activePump).thenReturn(pump)
        whenever(pump.isInitialized()).thenReturn(true)
        whenever(pump.pumpDescription).thenReturn(PumpDescription().also { it.isTempBasalCapable = true })
        whenever(commandQueue.cancelTempBasal(any(), any())).thenReturn(pumpEnactResultProvider.get().success(true))
        val executor = create()

        val prepared = executor.prepareBatch(listOf(BatchAction.CancelTempBasal)) as WizardBolusExecutor.PrepareResult.Preview
        val result = executor.confirm(prepared.bolusId, Sources.NSClient, { })

        assertThat(result).isEqualTo(WizardBolusExecutor.ConfirmResult.Delivered)
        verify(commandQueue, never()).bolus(anyOrNull())
        verify(commandQueue).cancelTempBasal(eq(true), any())
    }

    @Test
    fun prepareBatch_cancelExtendedBolus_parksAndConfirmCancelsViaCommandQueue() = runTest {
        stubPassthroughConstraints()
        val pump = mock<PumpWithConcentration>()
        whenever(activePlugin.activePump).thenReturn(pump)
        whenever(pump.isInitialized()).thenReturn(true)
        whenever(pump.pumpDescription).thenReturn(PumpDescription().also { it.isExtendedBolusCapable = true })
        whenever(commandQueue.cancelExtended()).thenReturn(pumpEnactResultProvider.get().success(true))
        val executor = create()

        val prepared = executor.prepareBatch(listOf(BatchAction.CancelExtendedBolus)) as WizardBolusExecutor.PrepareResult.Preview
        val result = executor.confirm(prepared.bolusId, Sources.NSClient, { })

        assertThat(result).isEqualTo(WizardBolusExecutor.ConfirmResult.Delivered)
        verify(commandQueue, never()).bolus(anyOrNull())
        verify(commandQueue).cancelExtended()
    }

    // ---- recompute paths reject a net-zero dose instead of parking an empty confirmation ----

    @Test
    fun prepareQuickWizard_netZeroInsulinAndZeroCarbs_returnsNoInsulinRequiredAndParksNothing() = runTest {
        // A correction-only QuickWizard (0 carbs) that nets to <= 0 insulin must error, not park an empty confirm.
        whenever(config.appInitialized).thenReturn(true)
        whenever(runningModeGuard.rejectionMessage(any())).thenReturn(null)
        val ads = mock<AutosensDataStore>()
        whenever(iobCobCalculator.ads).thenReturn(ads)
        whenever(ads.actualBg()).thenReturn(mock())
        whenever(profileFunction.getProfile()).thenReturn(mock<EffectiveProfile>())
        whenever(profileFunction.getProfileName()).thenReturn("Test")
        val pump = mock<PumpWithConcentration>()
        whenever(activePlugin.activePump).thenReturn(pump)
        whenever(pump.isInitialized()).thenReturn(true)
        whenever(pump.pumpDescription).thenReturn(PumpDescription())
        whenever(constraintsChecker.applyCarbsConstraints(any())).thenAnswer { it.getArgument<Constraint<Int>>(0) }
        whenever(rh.gs(any<Int>())).thenReturn("No insulin required")
        val entry = mock<QuickWizardEntry>()
        whenever(quickWizard.get("g")).thenReturn(entry)
        whenever(entry.carbs()).thenReturn(0)
        val bw = mock<BolusWizard>()
        whenever(entry.doCalc(anyOrNull(), anyOrNull(), anyOrNull())).thenReturn(bw)
        whenever(bw.insulinAfterConstraints).thenReturn(0.0)
        whenever(bw.calculatedTotalInsulin).thenReturn(0.0)
        whenever(bw.carbs).thenReturn(0)
        val executor = create()

        val result = executor.prepareQuickWizard("g")

        // The guard is the SAME two lines in prepareWizard, so this covers the manual-wizard path too.
        assertThat(result).isInstanceOf(WizardBolusExecutor.PrepareResult.Error::class.java)
        assertThat((result as WizardBolusExecutor.PrepareResult.Error).message).isEqualTo("No insulin required")
        // Nothing parked → a confirm of the wizard timestamp finds no pending dose (no phantom delivery).
        verify(commandQueue, never()).bolus(anyOrNull())
    }

    @Test
    fun prepareQuickWizard_beforeAppInitialized_returnsErrorWithoutTouchingPlugins() = runTest {
        // Regression: a remote QuickWizard prepare landing during the master's startup window (before
        // verifySelectionInCategories() populated activeAPS) must be rejected here — NOT crash later in
        // BolusWizard.doCalc with "APS not defined". The guard bails before any plugin / profile access.
        whenever(config.appInitialized).thenReturn(false)
        whenever(rh.gs(any<Int>())).thenReturn("Initializing…")
        val executor = create()

        val result = executor.prepareQuickWizard("g")

        assertThat(result).isInstanceOf(WizardBolusExecutor.PrepareResult.Error::class.java)
        assertThat((result as WizardBolusExecutor.PrepareResult.Error).message).isEqualTo("Initializing…")
        verify(runningModeGuard, never()).rejectionMessage(any())
        verify(quickWizard, never()).get(any<String>())
    }

    @Test
    fun confirm_asAdvisor_deliversCorrectionAdvisorNotCarbWizardBolus() = runTest {
        whenever(runningModeGuard.rejectionMessage(any())).thenReturn(null)
        whenever(commandQueue.bolus(anyOrNull())).thenReturn(pumpEnactResultProvider.get().success(true))
        val executor = create()
        // Parked WITH carbs, but the user took the high-BG advisor branch → correction-only delivery.
        executor.setPending(insulin = 1.5, carbs = 40, bolusCalculatorResult = null, bolusId = 999L)

        val result = executor.confirm(999L, Sources.NSClient, { }, asAdvisor = true)

        assertThat(result).isEqualTo(WizardBolusExecutor.ConfirmResult.Delivered)
        val dbiCaptor = argumentCaptor<DetailedBolusInfo>()
        verify(commandQueue).bolus(dbiCaptor.capture())
        val dbi = dbiCaptor.firstValue
        assertThat(dbi.eventType).isEqualTo(TE.Type.CORRECTION_BOLUS)
        assertThat(dbi.insulin).isEqualTo(1.5)
        assertThat(dbi.carbs).isEqualTo(0.0) // advisor = correction only; the parked carbs are NOT delivered
    }

    @Test
    fun deliverBolusAdvisor_onFailure_routesToErrorAndSkipsEatReminder() = runTest {
        whenever(runningModeGuard.rejectionMessage(any())).thenReturn(null)
        whenever(commandQueue.bolus(anyOrNull())).thenReturn(pumpEnactResultProvider.get().success(false))
        val executor = create()
        var errorMsg: String? = null

        executor.deliverBolusAdvisor(
            insulin = 1.5,
            mgdlGlucose = 200.0,
            bolusCalculatorResult = null,
            notes = null,
            source = Sources.WizardDialog,
            onError = { errorMsg = it }
        )

        assertThat(errorMsg).isNotNull()
        verify(automation, never()).scheduleAutomationEventEatReminder()
    }

    @Test
    fun bolus_onCommandFailure_postsUrgentBolusFailedAlarm() = runTest {
        whenever(runningModeGuard.rejectionMessage(any())).thenReturn(null)
        whenever(commandQueue.bolus(anyOrNull())).thenReturn(pumpEnactResultProvider.get().success(false))
        val executor = create()

        executor.deliverWizardBolus(
            insulin = 1.0, carbs = 0, carbTimeMinutes = 0, mgdlGlucose = null,
            bolusCalculatorResult = null, notes = null, source = Sources.QuickWizard, onError = { }
        )

        // The async delivery failure raises the single URGENT alarm from the executor (not the now-gone dialog).
        verify(notificationManager).post(
            eq(NotificationId.BOLUS_DELIVERY_FAILED), any<String>(), any<NotificationLevel>(), any<Int>(),
            anyOrNull<Int>(), any<List<NotificationAction>>(), anyOrNull<() -> Boolean>()
        )
    }

    @Test
    fun bolus_onCommandFailure_whenStopPressed_doesNotPostAlarm() = runTest {
        whenever(runningModeGuard.rejectionMessage(any())).thenReturn(null)
        whenever(commandQueue.bolus(anyOrNull())).thenReturn(pumpEnactResultProvider.get().success(false))
        whenever(bolusProgressData.isStopPressed).thenReturn(true)
        val executor = create()

        executor.deliverWizardBolus(
            insulin = 1.0, carbs = 0, carbTimeMinutes = 0, mgdlGlucose = null,
            bolusCalculatorResult = null, notes = null, source = Sources.QuickWizard, onError = { }
        )

        // A user-initiated cancel is not a failure: no URGENT alarm even though the command result is unsuccessful.
        verify(notificationManager, never()).post(
            eq(NotificationId.BOLUS_DELIVERY_FAILED), any<String>(), any<NotificationLevel>(), any<Int>(),
            anyOrNull<Int>(), any<List<NotificationAction>>(), anyOrNull<() -> Boolean>()
        )
    }

    @Test
    fun deliverInsulin_recordsCorrectionBolusWithNoteOnUserEntry() = runTest {
        whenever(runningModeGuard.rejectionMessage(any())).thenReturn(null)
        whenever(commandQueue.bolus(anyOrNull())).thenReturn(pumpEnactResultProvider.get().success(true))
        val executor = create()

        executor.deliverInsulin(
            insulin = 1.0,
            note = "Lunch",
            source = Sources.QuickWizard,
            onError = { }
        )

        val dbiCaptor = argumentCaptor<DetailedBolusInfo>()
        verify(commandQueue).bolus(dbiCaptor.capture())
        val dbi = dbiCaptor.firstValue
        assertThat(dbi.eventType).isEqualTo(TE.Type.CORRECTION_BOLUS)
        assertThat(dbi.insulin).isEqualTo(1.0)
        assertThat(dbi.carbs).isEqualTo(0.0)
        // legacy path left the treatment notes unset; the note rides the user entry only
        assertThat(dbi.notes).isNull()

        val uelValues = argumentCaptor<List<ValueWithUnit>>()
        verify(uel).log(eq(Action.BOLUS), eq(Sources.QuickWizard), eq("Lunch"), uelValues.capture())
        assertThat(uelValues.firstValue).containsExactly(ValueWithUnit.Insulin(1.0)).inOrder()

        verify(persistenceLayer, never()).insertOrUpdateBolusCalculatorResult(any())
    }
}

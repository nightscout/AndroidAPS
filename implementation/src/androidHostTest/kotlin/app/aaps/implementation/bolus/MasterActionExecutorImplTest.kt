package app.aaps.implementation.bolus

import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.iob.CobInfo
import app.aaps.core.data.model.BCR
import app.aaps.core.data.model.BS
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.ICfg
import app.aaps.core.data.model.PS
import app.aaps.core.data.model.RM
import app.aaps.core.data.model.TE
import app.aaps.core.data.model.TT
import app.aaps.core.data.pump.defs.PumpDescription
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.data.time.T
import app.aaps.core.data.ui.ConfirmationLine
import app.aaps.core.data.ui.ConfirmationRole
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.alerts.EatReminder
import app.aaps.core.interfaces.aps.AutosensDataStore
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.automation.Automation
import app.aaps.core.interfaces.bolus.BatchAction
import app.aaps.core.interfaces.bolus.MasterActionExecutor
import app.aaps.core.interfaces.bolus.RemoteCaller
import app.aaps.core.interfaces.constraints.Constraint
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.notifications.AlarmSound
import app.aaps.core.interfaces.notifications.NotificationAction
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.notifications.NotificationLevel
import app.aaps.core.interfaces.profile.EffectiveProfile
import app.aaps.core.interfaces.profile.ProfileStore
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.pump.PumpWithConcentration
import app.aaps.core.interfaces.pump.defs.fillFor
import app.aaps.core.interfaces.queue.Command
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.queue.SmbPause
import app.aaps.core.interfaces.rx.weardata.EventData
import app.aaps.core.keys.interfaces.ClientRole
import app.aaps.core.objects.constraints.ConstraintObject
import app.aaps.core.objects.profile.ProfileSealed
import app.aaps.core.objects.runningMode.RunningModeGuard
import app.aaps.core.objects.wizard.BolusWizard
import app.aaps.core.objects.wizard.QuickWizard
import app.aaps.core.objects.wizard.QuickWizardEntry
import app.aaps.core.ui.CoreUiStrings
import app.aaps.implementation.queue.SmbPauseImpl
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.Mockito.mockingDetails
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argThat
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Focused tests for the canonical [MasterActionExecutorImpl.deliverWizardBolus] entry point — the shared
 * end state that the phone wizard dialog and the watch quick-wizard both funnel into. Asserts the
 * recorded [DetailedBolusInfo] fields, the user-entry log values, and that the BCR is persisted exactly
 * once. `appScope` is `Unconfined` so the bolus coroutine runs synchronously within the call.
 */
class MasterActionExecutorImplTest : TestBaseWithProfile() {

    @Mock lateinit var quickWizard: QuickWizard
    @Mock lateinit var bolusWizardProvider: () -> BolusWizard
    @Mock lateinit var runningModeGuard: RunningModeGuard
    @Mock lateinit var commandQueue: CommandQueue
    @Mock lateinit var persistenceLayer: PersistenceLayer
    @Mock lateinit var uel: UserEntryLogger
    @Mock lateinit var loop: Loop
    @Mock lateinit var automation: Automation
    @Mock lateinit var bolusProgressData: BolusProgressData
    @Mock lateinit var masterRecordWriter: MasterRecordWriter
    @Mock lateinit var eatReminder: EatReminder

    // Real: the tests below read whether a pause is on, and confirm() checks it.
    private val smbPause by lazy { SmbPauseImpl(aapsLogger, dateUtil) }

    private fun create() = MasterActionExecutorImpl(
        aapsLogger, baseText, config, quickWizard, bolusWizardProvider, profileFunction, profileRepository, iobCobCalculator, constraintsChecker, activePlugin,
        runningModeGuard, commandQueue, persistenceLayer, uel, loop, dateUtil, decimalFormatter, profileUtil, automation, notificationManager, bolusProgressData,
        CoroutineScope(Dispatchers.Unconfined), masterRecordWriter, smbPause, eatReminder
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
        whenever(commandQueue.bolus(anyOrNull())).thenReturn(pumpEnactResultProvider().success(true))
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
        whenever(commandQueue.bolus(anyOrNull())).thenReturn(pumpEnactResultProvider().success(true))
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
        whenever(commandQueue.bolus(anyOrNull())).thenReturn(pumpEnactResultProvider().success(true))
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
        whenever(commandQueue.bolus(anyOrNull())).thenReturn(pumpEnactResultProvider().success(true))
        val executor = create()
        // confirm() now constraint-caps the (insulin + correctionU) dose, so the passthrough must be stubbed
        // even on the setPending path (which bypasses prepare()'s own constraint stubbing).
        whenever(constraintsChecker.applyBolusConstraints(any())).thenAnswer { it.getArgument<Constraint<Double>>(0) }
        executor.setPending(insulin = 2.0, carbs = 0, bolusCalculatorResult = null, bolusId = 999L)

        val first = executor.confirm(999L, Sources.NSClient, { })
        val second = executor.confirm(999L, Sources.NSClient, { })

        assertThat(first).isEqualTo(MasterActionExecutor.ConfirmResult.Delivered)
        assertThat(second).isEqualTo(MasterActionExecutor.ConfirmResult.NoPending)
        // The consume-once slot guarantees the pump fires EXACTLY once — no double-dose on a re-sent commit.
        verify(commandQueue, times(1)).bolus(anyOrNull())
    }

    @Test
    fun confirm_wrongBolusId_isNoPending_andDeliversNothing() = runTest {
        val executor = create()
        executor.setPending(insulin = 2.0, carbs = 0, bolusCalculatorResult = null, bolusId = 999L)

        val result = executor.confirm(123L, Sources.NSClient, { })

        assertThat(result).isEqualTo(MasterActionExecutor.ConfirmResult.NoPending)
        verify(commandQueue, never()).bolus(anyOrNull())
    }

    @Test
    fun confirm_parkedDosesAreIsolatedByBolusId_aSecondPrepareDoesNotClobberTheFirst() = runTest {
        whenever(runningModeGuard.rejectionMessage(any())).thenReturn(null)
        whenever(commandQueue.bolus(anyOrNull())).thenReturn(pumpEnactResultProvider().success(true))
        val executor = create()
        // confirm() now constraint-caps the (insulin + correctionU) dose, so the passthrough must be stubbed
        // even on the setPending path (which bypasses prepare()'s own constraint stubbing).
        whenever(constraintsChecker.applyBolusConstraints(any())).thenAnswer { it.getArgument<Constraint<Double>>(0) }
        // bolusId is a timestamp; use realistic recent ids so evictStalePending's TTL window keeps them parked.
        executor.setPending(insulin = 1.0, carbs = 0, bolusCalculatorResult = null, bolusId = now)
        // A second actor's prepare (different bolusId) must NOT clobber the first — per-id slots, not one shared var.
        executor.setPending(insulin = 2.0, carbs = 0, bolusCalculatorResult = null, bolusId = now - 1000)

        // The first parked dose still delivers (not overwritten), and a re-sent commit of it finds the slot drained.
        assertThat(executor.confirm(now, Sources.NSClient, { })).isEqualTo(MasterActionExecutor.ConfirmResult.Delivered)
        assertThat(executor.confirm(now, Sources.NSClient, { })).isEqualTo(MasterActionExecutor.ConfirmResult.NoPending)
        // The second parked dose is intact and delivers on its own commit.
        assertThat(executor.confirm(now - 1000, Sources.NSClient, { })).isEqualTo(MasterActionExecutor.ConfirmResult.Delivered)
        verify(commandQueue, times(2)).bolus(anyOrNull())
    }

    // ---- confirm(): a parked action belongs to the one who prepared it, and needs its role at commit ----

    private val bolusAction = BatchAction.Bolus(insulin = 1.0, carbs = 0, carbsTimeOffsetMinutes = 0, carbsDurationHours = 0, recordOnly = false, notes = "", timestamp = 0L, iCfg = null)
    private val targetAction = BatchAction.TempTarget(reason = TT.Reason.ACTIVITY.text, lowMgdl = 140.0, highMgdl = 140.0, durationMinutes = 60, startOffsetMinutes = 0)

    @Test
    fun confirm_byAnotherClient_isNoPending_andKeepsTheParkForItsOwner() = runTest {
        whenever(runningModeGuard.rejectionMessage(any())).thenReturn(null)
        whenever(commandQueue.bolus(anyOrNull())).thenReturn(pumpEnactResultProvider().success(true))
        stubPassthroughConstraints()
        val executor = create()
        val prepared = executor.prepareBatch(listOf(bolusAction), RemoteCaller("a", ClientRole.Caregiver)) as MasterActionExecutor.PrepareResult.Preview

        // Client b knows the id (it is in the cold doc), but the dose is not its own.
        val stranger = executor.confirm(prepared.bolusId, Sources.NSClient, { }, caller = RemoteCaller("b", ClientRole.Admin))
        verify(commandQueue, never()).bolus(anyOrNull())
        // The owner's commit still finds it.
        val owner = executor.confirm(prepared.bolusId, Sources.NSClient, { }, caller = RemoteCaller("a", ClientRole.Caregiver))

        assertThat(stranger).isEqualTo(MasterActionExecutor.ConfirmResult.NoPending)
        assertThat(owner).isEqualTo(MasterActionExecutor.ConfirmResult.Delivered)
        verify(commandQueue, times(1)).bolus(anyOrNull())
    }

    @Test
    fun confirm_byAClient_ofAParkMadeOnTheMaster_isNoPending() = runTest {
        whenever(runningModeGuard.rejectionMessage(any())).thenReturn(null)
        whenever(commandQueue.bolus(anyOrNull())).thenReturn(pumpEnactResultProvider().success(true))
        stubPassthroughConstraints()
        val executor = create()
        val prepared = executor.prepareBatch(listOf(bolusAction)) as MasterActionExecutor.PrepareResult.Preview

        val remote = executor.confirm(prepared.bolusId, Sources.NSClient, { }, caller = RemoteCaller("a", ClientRole.Admin))
        val local = executor.confirm(prepared.bolusId, Sources.NSClient, { })

        assertThat(remote).isEqualTo(MasterActionExecutor.ConfirmResult.NoPending)
        assertThat(local).isEqualTo(MasterActionExecutor.ConfirmResult.Delivered)
    }

    @Test
    fun confirm_aBolusOnlyBatch_byAMealAssistant_isDelivered() = runTest {
        whenever(runningModeGuard.rejectionMessage(any())).thenReturn(null)
        whenever(commandQueue.bolus(anyOrNull())).thenReturn(pumpEnactResultProvider().success(true))
        stubPassthroughConstraints()
        val executor = create()
        val caller = RemoteCaller("a", ClientRole.MealAssistant)
        val prepared = executor.prepareBatch(listOf(bolusAction), caller) as MasterActionExecutor.PrepareResult.Preview

        assertThat(executor.confirm(prepared.bolusId, Sources.NSClient, { }, caller = caller)).isEqualTo(MasterActionExecutor.ConfirmResult.Delivered)
    }

    @Test
    fun confirm_afterTheRoleWasLowered_isNotAllowed_andAppliesNothing() = runTest {
        whenever(runningModeGuard.rejectionMessage(any())).thenReturn(null)
        whenever(commandQueue.bolus(anyOrNull())).thenReturn(pumpEnactResultProvider().success(true))
        stubPassthroughConstraints()
        val executor = create()
        // A temp target needs Caregiver. The master lowers the client to Meals between prepare and commit.
        val prepared = executor.prepareBatch(listOf(targetAction, bolusAction), RemoteCaller("a", ClientRole.Caregiver)) as MasterActionExecutor.PrepareResult.Preview

        val result = executor.confirm(prepared.bolusId, Sources.NSClient, { }, caller = RemoteCaller("a", ClientRole.MealAssistant))

        assertThat(result).isEqualTo(MasterActionExecutor.ConfirmResult.NotAllowed)
        verify(commandQueue, never()).bolus(anyOrNull())
        verify(persistenceLayer, never()).insertAndCancelCurrentTemporaryTarget(any(), any(), any(), anyOrNull(), any())
    }

    // ---- prepareBatch() → confirm(): the two-step multi-action transaction (decision-B ordering) ----

    @Test
    fun prepareBatch_raisingTtFirstThenBolus_bothAppliedOnConfirm() = runTest {
        whenever(runningModeGuard.rejectionMessage(any())).thenReturn(null)
        whenever(commandQueue.bolus(anyOrNull())).thenReturn(pumpEnactResultProvider().success(true))
        stubPassthroughConstraints()
        val executor = create()
        val actions = listOf(
            BatchAction.TempTarget(reason = TT.Reason.HYPOGLYCEMIA.text, lowMgdl = 120.0, highMgdl = 120.0, durationMinutes = 60, startOffsetMinutes = 0),
            BatchAction.Bolus(insulin = 1.0, carbs = 0, carbsTimeOffsetMinutes = 0, carbsDurationHours = 0, recordOnly = false, notes = "", timestamp = 0L, iCfg = null)
        )

        val prepared = executor.prepareBatch(actions) as MasterActionExecutor.PrepareResult.Preview
        val result = executor.confirm(prepared.bolusId, Sources.NSClient, { })

        assertThat(result).isEqualTo(MasterActionExecutor.ConfirmResult.Delivered)
        verify(persistenceLayer).insertAndCancelCurrentTemporaryTarget(any(), any(), any(), anyOrNull(), any()) // target-raising TT applied unconditionally
        verify(commandQueue).bolus(anyOrNull()) // bolus delivered
    }

    @Test
    fun prepareBatch_quickWizardGuid_marksOriginatingEntryUsedOnConfirmNotPrepare() = runTest {
        // The master (SOT) marks the QuickWizard used on a successful commit — the client never writes the synced
        // QuickWizard pref itself (which previously raced the commit → "Update settings … Another action in progress").
        whenever(runningModeGuard.rejectionMessage(any())).thenReturn(null)
        whenever(commandQueue.bolus(anyOrNull())).thenReturn(pumpEnactResultProvider().success(true))
        stubPassthroughConstraints()
        val entry = mock<QuickWizardEntry>()
        whenever(quickWizard.get("qw-1")).thenReturn(entry)
        val executor = create()
        val actions = listOf(
            BatchAction.Bolus(insulin = 0.5, carbs = 0, carbsTimeOffsetMinutes = 0, carbsDurationHours = 0, recordOnly = false, notes = "Pre-bolus", timestamp = 0L, iCfg = null, quickWizardGuid = "qw-1")
        )

        val prepared = executor.prepareBatch(actions) as MasterActionExecutor.PrepareResult.Preview
        verify(entry, never()).markAsUsed() // NOT marked at prepare time (consume-once: only a real delivery counts)

        val result = executor.confirm(prepared.bolusId, Sources.QuickWizard, { })

        assertThat(result).isEqualTo(MasterActionExecutor.ConfirmResult.Delivered)
        verify(entry).markAsUsed() // marked HERE on the master after the bolus is delivered
    }

    @Test
    fun prepareBatch_noQuickWizardGuid_marksNothing() = runTest {
        // A dialog / wear batch (no guid) must not resolve or mark any entry — guards against a false-positive mark.
        whenever(runningModeGuard.rejectionMessage(any())).thenReturn(null)
        whenever(commandQueue.bolus(anyOrNull())).thenReturn(pumpEnactResultProvider().success(true))
        stubPassthroughConstraints()
        val executor = create()
        val actions = listOf(
            BatchAction.Bolus(insulin = 0.5, carbs = 0, carbsTimeOffsetMinutes = 0, carbsDurationHours = 0, recordOnly = false, notes = "", timestamp = 0L, iCfg = null)
        )

        val prepared = executor.prepareBatch(actions) as MasterActionExecutor.PrepareResult.Preview
        executor.confirm(prepared.bolusId, Sources.NSClient, { })

        verify(quickWizard, never()).get(any<String>())
    }

    // ---- #946: the "constraint applied" warning compares with the step the pump reported ----

    /** A Dana RS set to a 0.1 U step: its pump type says 0.05 U, the description the driver fills says 0.1 U. */
    private suspend fun preparedBolusOnA01StepPump(requested: Double, delivered: Double): MasterActionExecutor.PrepareResult.Preview {
        whenever(runningModeGuard.rejectionMessage(any())).thenReturn(null)
        stubPassthroughConstraints()
        whenever(constraintsChecker.applyBolusConstraints(any())).thenReturn(ConstraintObject(delivered, aapsLogger))
        testPumpPlugin.pumpDescription = PumpDescription().fillFor(PumpType.DANA_RS).also { it.bolusStep = 0.1 }
        val bolus = BatchAction.Bolus(insulin = requested, carbs = 0, carbsTimeOffsetMinutes = 0, carbsDurationHours = 0, recordOnly = false, notes = "", timestamp = 0L, iCfg = null)
        return create().prepareBatch(listOf(bolus)) as MasterActionExecutor.PrepareResult.Preview
    }

    /** A QuickWizard that calculates [calculated] U, of which the constraints leave [afterConstraints] U. */
    private suspend fun preparedQuickWizardOnA01StepPump(calculated: Double, afterConstraints: Double): MasterActionExecutor.PrepareResult {
        whenever(config.appInitialized).thenReturn(true)
        whenever(runningModeGuard.rejectionMessage(any())).thenReturn(null)
        val ads = mock<AutosensDataStore>()
        whenever(iobCobCalculator.ads).thenReturn(ads)
        whenever(ads.actualBg()).thenReturn(mock())
        whenever(profileFunction.getProfile()).thenReturn(mock<EffectiveProfile>())
        whenever(profileFunction.getProfileName()).thenReturn("Test")
        whenever(constraintsChecker.applyCarbsConstraints(any())).thenAnswer { it.getArgument<Constraint<Int>>(0) }
        whenever(constraintsChecker.getMaxBolusAllowed()).thenReturn(ConstraintObject(10.0, aapsLogger))
        testPumpPlugin.pumpDescription = PumpDescription().fillFor(PumpType.DANA_RS).also { it.bolusStep = 0.1 }
        val entry = mock<QuickWizardEntry>()
        whenever(quickWizard.get("g")).thenReturn(entry)
        whenever(entry.carbs()).thenReturn(20)
        val bw = mock<BolusWizard>()
        whenever(entry.doCalc(anyOrNull(), anyOrNull(), anyOrNull())).thenReturn(bw)
        whenever(bw.calculatedTotalInsulin).thenReturn(calculated)
        whenever(bw.insulinAfterConstraints).thenReturn(afterConstraints)
        whenever(bw.carbs).thenReturn(20)
        whenever(bw.createBolusCalculatorResult()).thenReturn(mock<BCR>())
        whenever(bw.buildWizardDetail()).thenReturn(
            EventData.WizardDetail(
                totalInsulin = calculated, carbs = 20, insulinFromBG = 0.0, insulinFromTrend = 0.0, insulinFromCOB = 0.0, insulinFromCarbs = calculated,
                insulinFromBolusIOB = 0.0, insulinFromBasalIOB = 0.0, includeBolusIOB = true, includeBasalIOB = true, percentageCorrection = 100, cob = 0.0,
                tempTargetLabel = null, ic = 10.0, sens = 50.0
            )
        )
        return create().prepareQuickWizard("g")
    }

    @Test
    fun prepareQuickWizard_flooredWithinThePumpStep_isNotAConstraintError() = runTest {
        // 1.58 -> 1.5 is only the rounding of this pump. With the pump type step (0.05 U) it was refused.
        val result = preparedQuickWizardOnA01StepPump(calculated = 1.58, afterConstraints = 1.5)

        assertThat(result).isInstanceOf(MasterActionExecutor.PrepareResult.Preview::class.java)
        // The step sent on to the confirmation (client, watch) is the one the pump reported too.
        assertThat((result as MasterActionExecutor.PrepareResult.Preview).wizardDetail?.bolusStep).isEqualTo(0.1)
    }

    @Test
    fun prepareQuickWizard_cutByMoreThanThePumpStep_isAConstraintError() = runTest {
        val result = preparedQuickWizardOnA01StepPump(calculated = 1.65, afterConstraints = 1.5)

        assertThat(result).isInstanceOf(MasterActionExecutor.PrepareResult.Error::class.java)
    }

    @Test
    fun prepareBatch_bolusFlooredWithinThePumpStep_hasNoConstraintWarning() = runTest {
        // 1.58 -> 1.5 is only the rounding of this pump, not a constraint.
        val prepared = preparedBolusOnA01StepPump(requested = 1.58, delivered = 1.5)

        assertThat(prepared.lines.map { it.role }).doesNotContain(ConfirmationRole.WARNING)
    }

    @Test
    fun prepareBatch_bolusCutByMoreThanThePumpStep_hasTheConstraintWarning() = runTest {
        val prepared = preparedBolusOnA01StepPump(requested = 1.65, delivered = 1.5)

        assertThat(prepared.lines.map { it.role }).contains(ConfirmationRole.WARNING)
    }

    // A limit that cuts exactly one step is a limit, although 1.2 - 1.1 is 0.0999... as a Double.
    @Test
    fun prepareBatch_bolusCutByExactlyOneStep_hasTheConstraintWarning() = runTest {
        val prepared = preparedBolusOnA01StepPump(requested = 1.2, delivered = 1.1)

        assertThat(prepared.lines.map { it.role }).contains(ConfirmationRole.WARNING)
    }

    @Test
    fun prepareQuickWizard_cutByExactlyOneStep_isAConstraintError() = runTest {
        val result = preparedQuickWizardOnA01StepPump(calculated = 1.2, afterConstraints = 1.1)

        assertThat(result).isInstanceOf(MasterActionExecutor.PrepareResult.Error::class.java)
    }

    // ---- manual wizard above the max bolus: capped with a warning, not refused (as the phone wizard did in v3) ----

    /** A manual wizard that calculates [calculated] U, of which the constraints leave [afterConstraints] U. */
    private suspend fun preparedManualWizard(
        calculated: Double,
        afterConstraints: Double,
        carbs: Int = 20,
        executor: MasterActionExecutorImpl = create(),
        carbTime: Int = 0,
        alarm: Boolean = false
    ): MasterActionExecutor.PrepareResult {
        whenever(config.appInitialized).thenReturn(true)
        whenever(runningModeGuard.rejectionMessage(any())).thenReturn(null)
        whenever(profileFunction.getProfile()).thenReturn(mock<EffectiveProfile>())
        whenever(profileFunction.getProfileName()).thenReturn("Test")
        whenever(constraintsChecker.applyCarbsConstraints(any())).thenAnswer { it.getArgument<Constraint<Int>>(0) }
        whenever(constraintsChecker.getMaxBolusAllowed()).thenReturn(ConstraintObject(12.0, aapsLogger))
        testPumpPlugin.pumpDescription = PumpDescription().fillFor(PumpType.DANA_RS).also { it.bolusStep = 0.1 }
        val bw = mock<BolusWizard>()
        whenever(bolusWizardProvider.invoke()).thenReturn(bw)
        whenever(
            bw.doCalc(
                anyOrNull(), any(), anyOrNull(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), anyOrNull()
            )
        ).thenReturn(bw)
        whenever(bw.calculatedTotalInsulin).thenReturn(calculated)
        whenever(bw.unclampedCalculatedInsulin).thenReturn(calculated)
        whenever(bw.insulinAfterConstraints).thenReturn(afterConstraints)
        whenever(bw.carbs).thenReturn(carbs)
        // Read before the stubbing: dateUtil is a mock too, and a mock call inside thenReturn breaks the stubbing.
        val now = dateUtil.now()
        whenever(bw.timeStamp).thenReturn(now)
        whenever(bw.createBolusCalculatorResult()).thenReturn(mock<BCR>())
        whenever(bw.buildWizardDetail()).thenReturn(
            EventData.WizardDetail(
                totalInsulin = calculated, carbs = carbs, insulinFromBG = 0.0, insulinFromTrend = 0.0, insulinFromCOB = 0.0, insulinFromCarbs = calculated,
                insulinFromBolusIOB = 0.0, insulinFromBasalIOB = 0.0, includeBolusIOB = true, includeBasalIOB = true, percentageCorrection = 100, cob = 0.0,
                tempTargetLabel = null, ic = 10.0, sens = 50.0
            )
        )
        val inputs = MasterActionExecutor.WizardInputs(
            bg = 0.0, carbs = carbs, percentage = 100, directCorrection = 0.0, carbTime = carbTime, useBg = false, useCob = false, useIob = false,
            useTt = false, useTrend = false, alarm = alarm, notes = ""
        )
        return executor.prepareWizard(inputs)
    }

    // ---- the "time to eat" timer counts from the end of the bolus (#5327), as in 3.x ----

    @Test
    fun confirm_wizardWithAlarm_startsTheTimerOnlyAfterThePumpFinished() = runTest {
        whenever(constraintsChecker.applyBolusConstraints(any())).thenAnswer { it.getArgument<Constraint<Double>>(0) }
        var timerStartedBeforeTheBolusEnded = false
        whenever(commandQueue.bolus(anyOrNull())).thenAnswer {
            // The pump is still delivering here.
            timerStartedBeforeTheBolusEnded = mockingDetails(eatReminder).invocations.isNotEmpty()
            pumpEnactResultProvider().success(true)
        }
        val executor = create()
        val prepared = preparedManualWizard(calculated = 2.0, afterConstraints = 2.0, executor = executor, carbTime = 15, alarm = true) as MasterActionExecutor.PrepareResult.Preview

        executor.confirm(prepared.bolusId, Sources.WizardDialog, { })

        assertThat(timerStartedBeforeTheBolusEnded).isFalse()
        verify(eatReminder).start(T.mins(15).msecs())
    }

    @Test
    fun confirm_wizardWithAlarm_bolusFailsOnPump_noTimer() = runTest {
        whenever(constraintsChecker.applyBolusConstraints(any())).thenAnswer { it.getArgument<Constraint<Double>>(0) }
        whenever(commandQueue.bolus(anyOrNull())).thenReturn(pumpEnactResultProvider().success(false).comment("pump error"))
        val executor = create()
        val prepared = preparedManualWizard(calculated = 2.0, afterConstraints = 2.0, executor = executor, carbTime = 15, alarm = true) as MasterActionExecutor.PrepareResult.Preview

        executor.confirm(prepared.bolusId, Sources.WizardDialog, { })

        verify(eatReminder, never()).start(any())
    }

    @Test
    fun confirm_wizardWithoutAlarm_noTimer() = runTest {
        whenever(constraintsChecker.applyBolusConstraints(any())).thenAnswer { it.getArgument<Constraint<Double>>(0) }
        whenever(commandQueue.bolus(anyOrNull())).thenReturn(pumpEnactResultProvider().success(true))
        val executor = create()
        val prepared = preparedManualWizard(calculated = 2.0, afterConstraints = 2.0, executor = executor, carbTime = 15, alarm = false) as MasterActionExecutor.PrepareResult.Preview

        executor.confirm(prepared.bolusId, Sources.WizardDialog, { })

        verify(eatReminder, never()).start(any())
    }

    /** The carbs dialog's timer starts on the master too, also when a client entered the carbs. */
    @Test
    fun confirm_batchCarbsLaterWithAlarm_startsTheTimer() = runTest {
        whenever(commandQueue.bolus(anyOrNull())).thenReturn(pumpEnactResultProvider().success(true))
        stubPassthroughConstraints()
        val executor = create()
        val carbs = BatchAction.Bolus(
            insulin = 0.0, carbs = 20, carbsTimeOffsetMinutes = 30, carbsDurationHours = 0, recordOnly = false, notes = "", timestamp = 0L, iCfg = null, alarm = true
        )
        val prepared = executor.prepareBatch(listOf(carbs)) as MasterActionExecutor.PrepareResult.Preview

        executor.confirm(prepared.bolusId, Sources.CarbDialog, { })

        verify(eatReminder).start(T.mins(30).msecs())
    }

    @Test
    fun confirm_batchCarbsNowWithAlarm_noTimer() = runTest {
        whenever(commandQueue.bolus(anyOrNull())).thenReturn(pumpEnactResultProvider().success(true))
        stubPassthroughConstraints()
        val executor = create()
        val carbs = BatchAction.Bolus(
            insulin = 0.0, carbs = 20, carbsTimeOffsetMinutes = 0, carbsDurationHours = 0, recordOnly = false, notes = "", timestamp = 0L, iCfg = null, alarm = true
        )
        val prepared = executor.prepareBatch(listOf(carbs)) as MasterActionExecutor.PrepareResult.Preview

        executor.confirm(prepared.bolusId, Sources.CarbDialog, { })

        verify(eatReminder, never()).start(any())
    }

    @Test
    fun prepareWizard_aboveMaxBolus_isCappedNotRefused() = runTest {
        val result = preparedManualWizard(calculated = 13.9, afterConstraints = 12.0)

        assertThat(result).isInstanceOf(MasterActionExecutor.PrepareResult.Preview::class.java)
        // The watch adds its +/- steps to this base, so the total it shows is the capped dose that is given.
        assertThat((result as MasterActionExecutor.PrepareResult.Preview).wizardDetail?.unclampedInsulin).isEqualTo(12.0)
    }

    @Test
    fun prepareWizard_aboveMaxBolus_confirmGivesTheCappedDose() = runTest {
        whenever(commandQueue.bolus(anyOrNull())).thenReturn(pumpEnactResultProvider().success(true))
        whenever(constraintsChecker.applyBolusConstraints(any())).thenAnswer {
            ConstraintObject(it.getArgument<Constraint<Double>>(0).value().coerceAtMost(12.0), aapsLogger)
        }
        val executor = create()
        val prepared = preparedManualWizard(calculated = 13.9, afterConstraints = 12.0, executor = executor) as MasterActionExecutor.PrepareResult.Preview

        executor.confirm(prepared.bolusId, Sources.WizardDialog, { })

        val dbiCaptor = argumentCaptor<DetailedBolusInfo>()
        verify(commandQueue).bolus(dbiCaptor.capture())
        assertThat(dbiCaptor.firstValue.insulin).isEqualTo(12.0)
    }

    // The watch shows the base + its steps as the total. Below the cap, that total is exactly the dose given.
    @Test
    fun prepareWizard_aboveMaxBolus_watchStepDownGivesTheShownTotal() = runTest {
        whenever(commandQueue.bolus(anyOrNull())).thenReturn(pumpEnactResultProvider().success(true))
        whenever(constraintsChecker.applyBolusConstraints(any())).thenAnswer {
            ConstraintObject(it.getArgument<Constraint<Double>>(0).value().coerceAtMost(12.0), aapsLogger)
        }
        val executor = create()
        val prepared = preparedManualWizard(calculated = 13.9, afterConstraints = 12.0, executor = executor) as MasterActionExecutor.PrepareResult.Preview
        val shownOnWatch = prepared.wizardDetail!!.unclampedInsulin + -0.5

        executor.confirm(prepared.bolusId, Sources.Wear, { }, correctionU = -0.5)

        val dbiCaptor = argumentCaptor<DetailedBolusInfo>()
        verify(commandQueue).bolus(dbiCaptor.capture())
        assertThat(dbiCaptor.firstValue.insulin).isWithin(1e-9).of(shownOnWatch)
        assertThat(dbiCaptor.firstValue.insulin).isWithin(1e-9).of(11.5)
    }

    @Test
    fun prepareWizard_flooredWithinThePumpStep_keepsTheCalculatedBase() = runTest {
        val result = preparedManualWizard(calculated = 1.58, afterConstraints = 1.5)

        assertThat(result).isInstanceOf(MasterActionExecutor.PrepareResult.Preview::class.java)
        assertThat((result as MasterActionExecutor.PrepareResult.Preview).wizardDetail?.unclampedInsulin).isEqualTo(1.58)
    }

    @Test
    fun prepareWizard_limitLeavesNoInsulinAndNoCarbs_isRefused() = runTest {
        val result = preparedManualWizard(calculated = 2.0, afterConstraints = 0.0, carbs = 0)

        assertThat(result).isInstanceOf(MasterActionExecutor.PrepareResult.Error::class.java)
        // The constraint message, not "no insulin required": the calculation did ask for insulin.
        assertThat((result as MasterActionExecutor.PrepareResult.Error).message).contains("Cannot deliver 2.00")
    }

    // A record-only dose is kept as typed, so the confirmation shows all of it, not the 1.3 U of a 0.1 U pump.
    @Test
    fun prepareBatch_recordOnlyDose_isShownWithTwoDecimals() = runTest {
        stubPassthroughConstraints()
        testPumpPlugin.pumpDescription = PumpDescription().fillFor(PumpType.DANA_RS).also { it.bolusStep = 0.1 }
        val bolus = BatchAction.Bolus(
            insulin = 1.33, carbs = 0, carbsTimeOffsetMinutes = 0, carbsDurationHours = 0, recordOnly = true, notes = "", timestamp = 0L,
            iCfg = ICfg("Pen", 75, 6.0, 1.0)
        )

        val prepared = create().prepareBatch(listOf(bolus)) as MasterActionExecutor.PrepareResult.Preview

        assertThat(prepared.lines.first { it.role == ConfirmationRole.BOLUS }.text).containsMatch("1[.,]33 U")
    }

    // ---- confirm(onSuccess): the end of an asynchronous dose, for a caller that has to wait for it (SMS) ----

    @Test
    fun confirm_batchBolusDelivered_callsOnSuccessNotOnError() = runTest {
        whenever(runningModeGuard.rejectionMessage(any())).thenReturn(null)
        whenever(commandQueue.bolus(anyOrNull())).thenReturn(pumpEnactResultProvider().success(true))
        stubPassthroughConstraints()
        val executor = create()
        val bolus = BatchAction.Bolus(insulin = 1.0, carbs = 0, carbsTimeOffsetMinutes = 0, carbsDurationHours = 0, recordOnly = false, notes = "", timestamp = 0L, iCfg = null)
        val prepared = executor.prepareBatch(listOf(bolus)) as MasterActionExecutor.PrepareResult.Preview

        var successes = 0
        var error: MasterActionExecutor.Failure? = null
        executor.confirm(prepared.bolusId, Sources.SMS, { error = it }, onSuccess = { successes++ })

        assertThat(successes).isEqualTo(1)
        assertThat(error).isNull()
    }

    @Test
    fun confirm_batchBolusFailsOnPump_callsOnErrorNotOnSuccess() = runTest {
        whenever(runningModeGuard.rejectionMessage(any())).thenReturn(null)
        whenever(commandQueue.bolus(anyOrNull())).thenReturn(pumpEnactResultProvider().success(false).comment("pump error"))
        stubPassthroughConstraints()
        val executor = create()
        val bolus = BatchAction.Bolus(insulin = 1.0, carbs = 0, carbsTimeOffsetMinutes = 0, carbsDurationHours = 0, recordOnly = false, notes = "", timestamp = 0L, iCfg = null)
        val prepared = executor.prepareBatch(listOf(bolus)) as MasterActionExecutor.PrepareResult.Preview

        var successes = 0
        var error: MasterActionExecutor.Failure? = null
        val result = executor.confirm(prepared.bolusId, Sources.SMS, { error = it }, onSuccess = { successes++ })

        // confirm only started the bolus; the failure comes afterwards, through onError alone.
        assertThat(result).isEqualTo(MasterActionExecutor.ConfirmResult.Delivered)
        assertThat(error).isNotNull()
        assertThat(successes).isEqualTo(0)
    }

    @Test
    fun confirm_batchCarbsOnly_callsOnSuccess() = runTest {
        whenever(commandQueue.bolus(anyOrNull())).thenReturn(pumpEnactResultProvider().success(true))
        stubPassthroughConstraints()
        val executor = create()
        val carbs = BatchAction.Bolus(insulin = 0.0, carbs = 20, carbsTimeOffsetMinutes = 0, carbsDurationHours = 0, recordOnly = false, notes = "", timestamp = 0L, iCfg = null)
        val prepared = executor.prepareBatch(listOf(carbs)) as MasterActionExecutor.PrepareResult.Preview

        var successes = 0
        executor.confirm(prepared.bolusId, Sources.SMS, { }, onSuccess = { successes++ })

        assertThat(successes).isEqualTo(1)
    }

    @Test
    fun confirm_batchWithoutDose_neverCallsOnSuccess() = runTest {
        // Nothing to wait for: a temp target is awaited inside confirm, so the return value is the whole answer.
        stubPassthroughConstraints()
        val executor = create()
        val target = BatchAction.TempTarget(reason = TT.Reason.ACTIVITY.text, lowMgdl = 140.0, highMgdl = 140.0, durationMinutes = 60, startOffsetMinutes = 0)
        val prepared = executor.prepareBatch(listOf(target)) as MasterActionExecutor.PrepareResult.Preview

        var successes = 0
        val result = executor.confirm(prepared.bolusId, Sources.SMS, { }, onSuccess = { successes++ })

        assertThat(result).isEqualTo(MasterActionExecutor.ConfirmResult.Delivered)
        assertThat(successes).isEqualTo(0)
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
        val prepared = executor.prepareBatch(actions) as MasterActionExecutor.PrepareResult.Preview

        // The loop suspends between prepare and commit → the bolus is mode-rejected at delivery.
        whenever(runningModeGuard.rejectionMessage(any())).thenReturn("loop suspended")
        var err: String? = null
        executor.confirm(prepared.bolusId, Sources.NSClient, { err = it.comment })

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
        whenever(profileFunction.createProfileSwitch(any(), any(), any(), any(), any(), anyOrNull(), any(), any())).thenReturn(mock<PS>())
        val executor = create()

        val prepared = executor.prepareBatch(listOf(BatchAction.ProfileSwitch(percentage = 120, timeShiftHours = 2, durationMinutes = 60))) as MasterActionExecutor.PrepareResult.Preview
        val result = executor.confirm(prepared.bolusId, Sources.NSClient, { })

        assertThat(result).isEqualTo(MasterActionExecutor.ConfirmResult.Delivered)
        // PS-only batch: no pump bolus, no TT — only the dialog-free profile switch applied with the parked values.
        verify(commandQueue, never()).bolus(anyOrNull())
        // timestamp 0 = now: this batch carries no picked time, so the master stamps its own clock.
        verify(profileFunction).createProfileSwitch(eq(60), eq(120), eq(2), eq(Action.PROFILE_SWITCH), eq(Sources.NSClient), anyOrNull(), any(), eq(0L))
    }

    @Test
    fun prepareBatch_profileSwitch_outOfRange_returnsErrorAndAppliesNothing() = runTest {
        stubPassthroughConstraints()
        whenever(profileFunction.getProfile()).thenReturn(mock<EffectiveProfile>())
        val executor = create()

        val result = executor.prepareBatch(listOf(BatchAction.ProfileSwitch(percentage = 9999, timeShiftHours = 0, durationMinutes = 30)))

        assertThat(result).isInstanceOf(MasterActionExecutor.PrepareResult.Error::class.java)
        verify(profileFunction, never()).createProfileSwitch(any(), any(), any(), any(), any(), anyOrNull(), any(), any())
    }

    // ---- prepareBatch(): the time a temp target starts ----

    @Test
    fun prepareBatch_tempTarget_startOffsetTooFarInThePast_returnsErrorAndAppliesNothing() = runTest {
        stubPassthroughConstraints()
        val executor = create()

        // What the temp target screen produced while its activation time was stuck at the epoch: an offset of
        // about -29.9 million minutes. It used to be stored as a target in 1970 that never became active and
        // never reported anything, so the user's target silently did not happen.
        val result = executor.prepareBatch(listOf(BatchAction.TempTarget(TT.Reason.EATING_SOON.text, 90.0, 90.0, 45, -29_860_000)))

        assertThat(result).isInstanceOf(MasterActionExecutor.PrepareResult.Error::class.java)
        verify(persistenceLayer, never()).insertAndCancelCurrentTemporaryTarget(any(), any(), any(), anyOrNull(), any())
    }

    @Test
    fun prepareBatch_tempTarget_startOffsetTooFarInTheFuture_returnsErrorAndAppliesNothing() = runTest {
        stubPassthroughConstraints()
        val executor = create()

        val twoDaysMinutes = (T.days(2).msecs() / 60_000L).toInt()
        val result = executor.prepareBatch(listOf(BatchAction.TempTarget(TT.Reason.ACTIVITY.text, 90.0, 90.0, 45, twoDaysMinutes)))

        assertThat(result).isInstanceOf(MasterActionExecutor.PrepareResult.Error::class.java)
        verify(persistenceLayer, never()).insertAndCancelCurrentTemporaryTarget(any(), any(), any(), anyOrNull(), any())
    }

    @Test
    fun prepareBatch_tempTarget_movedOffNowGainsAStartTimeLine() = runTest {
        stubPassthroughConstraints()
        val executor = create()

        // A target the user moved 90 min back is legitimate, and the confirmation has to name the moment it
        // starts — the screen used to change the time with nothing on the dialog mentioning it. Asserted as
        // "one line more than the same target starting now", because the mocked rh.gs returns a fixed string,
        // so matching the formatted date would only test the mock. Offset 0 must stay clean: a Time line there
        // would be noise on every temp target.
        val movedBack = executor.prepareBatch(listOf(BatchAction.TempTarget(TT.Reason.ACTIVITY.text, 90.0, 90.0, 45, -90))) as MasterActionExecutor.PrepareResult.Preview
        val startingNow = executor.prepareBatch(listOf(BatchAction.TempTarget(TT.Reason.ACTIVITY.text, 90.0, 90.0, 45, 0))) as MasterActionExecutor.PrepareResult.Preview

        assertThat(movedBack.lines).hasSize(startingNow.lines.size + 1)
    }

    // ---- prepareBatch(): the time the user picked for a profile switch (issue #5239) ----

    @Test
    fun prepareBatch_profileSwitch_appliesThePickedTimeInsteadOfNow() = runTest {
        stubPassthroughConstraints()
        val store = mock<ProfileStore>()
        whenever(store.getSpecificProfile("Lunch")).thenReturn(mock())
        whenever(profileRepository.profile).thenReturn(MutableStateFlow(store))
        whenever(profileFunction.getRunningOrRequestedICfg()).thenReturn(someICfg)
        val picked = dateUtil.now() + T.hours(2).msecs()
        val executor = create()

        val prepared = executor.prepareBatch(listOf(BatchAction.ProfileSwitch(100, 0, 0, profileName = "Lunch", timestamp = picked))) as MasterActionExecutor.PrepareResult.Preview
        executor.confirm(prepared.bolusId, Sources.NSClient, { })

        // The switch is stored at the picked time, not at now — ProfileSwitchScheduler then arms its start.
        verify(profileFunction).createProfileSwitch(eq(store), eq("Lunch"), eq(0), eq(100), eq(0), eq(picked), eq(Action.PROFILE_SWITCH), eq(Sources.NSClient), anyOrNull(), any(), eq(someICfg))
    }

    @Test
    fun prepareBatch_profileSwitch_withoutAPickedTime_stampsNow() = runTest {
        stubPassthroughConstraints()
        val store = mock<ProfileStore>()
        whenever(store.getSpecificProfile("Lunch")).thenReturn(mock())
        whenever(profileRepository.profile).thenReturn(MutableStateFlow(store))
        whenever(profileFunction.getRunningOrRequestedICfg()).thenReturn(someICfg)
        val executor = create()

        val before = dateUtil.now()
        val prepared = executor.prepareBatch(listOf(BatchAction.ProfileSwitch(100, 0, 0, profileName = "Lunch"))) as MasterActionExecutor.PrepareResult.Preview
        executor.confirm(prepared.bolusId, Sources.NSClient, { })
        val after = dateUtil.now()

        val stamped = argumentCaptor<Long>()
        verify(profileFunction).createProfileSwitch(eq(store), eq("Lunch"), eq(0), eq(100), eq(0), stamped.capture(), eq(Action.PROFILE_SWITCH), eq(Sources.NSClient), anyOrNull(), any(), eq(someICfg))
        assertThat(stamped.firstValue).isAtLeast(before)
        assertThat(stamped.firstValue).isAtMost(after)
    }

    @Test
    fun prepareBatch_profileSwitch_timeTooFarInTheFuture_returnsErrorAndAppliesNothing() = runTest {
        stubPassthroughConstraints()
        val store = mock<ProfileStore>()
        whenever(store.getSpecificProfile("Lunch")).thenReturn(mock())
        whenever(profileRepository.profile).thenReturn(MutableStateFlow(store))
        whenever(profileFunction.getRunningOrRequestedICfg()).thenReturn(someICfg)
        val executor = create()

        // Two days ahead — outside Constants.PROFILE_SWITCH_TIME_OFFSET. A client clock must not place a
        // switch there, and it is refused rather than quietly moved to a time the user did not pick.
        val result = executor.prepareBatch(listOf(BatchAction.ProfileSwitch(100, 0, 0, profileName = "Lunch", timestamp = dateUtil.now() + T.days(2).msecs())))

        assertThat(result).isInstanceOf(MasterActionExecutor.PrepareResult.Error::class.java)
        verify(profileFunction, never()).createProfileSwitch(any(), any(), any(), any(), any(), any(), any(), any(), anyOrNull(), any(), any())
    }

    @Test
    fun prepareBatch_profileSwitch_timeTooFarInThePast_returnsErrorAndAppliesNothing() = runTest {
        stubPassthroughConstraints()
        val store = mock<ProfileStore>()
        whenever(store.getSpecificProfile("Lunch")).thenReturn(mock())
        whenever(profileRepository.profile).thenReturn(MutableStateFlow(store))
        whenever(profileFunction.getRunningOrRequestedICfg()).thenReturn(someICfg)
        val executor = create()

        val result = executor.prepareBatch(listOf(BatchAction.ProfileSwitch(100, 0, 0, profileName = "Lunch", timestamp = dateUtil.now() - T.days(2).msecs())))

        assertThat(result).isInstanceOf(MasterActionExecutor.PrepareResult.Error::class.java)
        verify(profileFunction, never()).createProfileSwitch(any(), any(), any(), any(), any(), any(), any(), any(), anyOrNull(), any(), any())
    }

    @Test
    fun prepareBatch_profileSwitch_timeAtTheEdgeOfTheBoundIsAccepted() = runTest {
        stubPassthroughConstraints()
        val store = mock<ProfileStore>()
        whenever(store.getSpecificProfile("Lunch")).thenReturn(mock())
        whenever(profileRepository.profile).thenReturn(MutableStateFlow(store))
        whenever(profileFunction.getRunningOrRequestedICfg()).thenReturn(someICfg)
        val executor = create()

        // The bound is inclusive; a whole day ahead is a legitimate "tomorrow at this time".
        val result = executor.prepareBatch(listOf(BatchAction.ProfileSwitch(100, 0, 0, profileName = "Lunch", timestamp = dateUtil.now() + Constants.PROFILE_SWITCH_TIME_OFFSET.last)))

        assertThat(result).isInstanceOf(MasterActionExecutor.PrepareResult.Preview::class.java)
    }

    /**
     * A switch the pump cannot take (a basal above its max basal) is refused before the confirmation. It used
     * to pass prepare, then createProfileSwitch returned null, and the caller was told it was done.
     */
    @Test
    fun prepareBatch_profileSwitchThePumpCannotTake_isRefusedBeforeTheConfirmation() = runTest {
        stubPassthroughConstraints()
        whenever(profileFunction.getProfile()).thenReturn(mock<EffectiveProfile>())
        whenever(profileFunction.profileSwitchRefusal(eq(60), eq(150), eq(0), any())).thenReturn("The pump cannot take it")
        val executor = create()

        val result = executor.prepareBatch(listOf(BatchAction.ProfileSwitch(percentage = 150, timeShiftHours = 0, durationMinutes = 60)))

        assertThat((result as MasterActionExecutor.PrepareResult.Error).message).isEqualTo("The pump cannot take it")
        verify(profileFunction, never()).createProfileSwitch(any(), any(), any(), any(), any(), anyOrNull(), any(), any())
    }

    /** Fine at prepare, refused when applied (a limit changed in between): the caller hears why. */
    @Test
    fun confirm_profileSwitchRefusedWhenApplied_reportsTheReason() = runTest {
        stubPassthroughConstraints()
        whenever(profileFunction.getProfile()).thenReturn(mock<EffectiveProfile>())
        whenever(profileFunction.getOriginalProfileName()).thenReturn("Test")
        whenever(profileFunction.profileSwitchRefusal(eq(60), eq(150), eq(0), any())).thenReturn(null, "The pump cannot take it")
        val executor = create()
        val prepared = executor.prepareBatch(listOf(BatchAction.ProfileSwitch(percentage = 150, timeShiftHours = 0, durationMinutes = 60))) as MasterActionExecutor.PrepareResult.Preview

        var failure: MasterActionExecutor.Failure? = null
        executor.confirm(prepared.bolusId, Sources.NSClient, { failure = it })

        assertThat(failure?.comment).isEqualTo("The pump cannot take it")
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
        assertThat(prepared).isInstanceOf(MasterActionExecutor.PrepareResult.Preview::class.java)
        assertThat((prepared as MasterActionExecutor.PrepareResult.Preview).carbs).isEqualTo(-10)
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
        assertThat((prepared as MasterActionExecutor.PrepareResult.Preview).carbs).isEqualTo(-5)
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
        assertThat(prepared).isEqualTo(MasterActionExecutor.PrepareResult.NoAction)
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
        assertThat(prepared).isEqualTo(MasterActionExecutor.PrepareResult.NoAction)
    }

    @Test
    fun prepareBatch_insulinActivate_parksAndConfirmAppliesViaCreateProfileSwitchWithNewInsulin() = runTest {
        stubPassthroughConstraints()
        // An active EPS profile is required to re-apply the new insulin onto.
        whenever(profileFunction.getProfile()).thenReturn(mock<ProfileSealed.EPS>())
        whenever(profileFunction.createProfileSwitchWithNewInsulin(any(), any())).thenReturn(true)
        val executor = create()
        val iCfg = ICfg(insulinLabel = "Rapid", insulinEndTime = 360, insulinPeakTime = 75, concentration = 1.0)

        val prepared = executor.prepareBatch(listOf(BatchAction.InsulinActivate(iCfg))) as MasterActionExecutor.PrepareResult.Preview
        val result = executor.confirm(prepared.bolusId, Sources.NSClient, { })

        assertThat(result).isEqualTo(MasterActionExecutor.ConfirmResult.Delivered)
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

        assertThat(result).isInstanceOf(MasterActionExecutor.PrepareResult.Error::class.java)
        verify(profileFunction, never()).createProfileSwitchWithNewInsulin(any(), any())
    }

    @Test
    fun prepareBatch_twoBatchesInTheSameMillisecond_getDistinctIdsAndBothCommit() = runTest {
        stubPassthroughConstraints()
        // dateUtil.now() is frozen in the test base — which is exactly the production race. The fill dialog fires
        // its insulin activation, site change and cartridge change as separate coroutines, so two prepares can land
        // in the same millisecond. When the id was `dateUtil.now()` they shared a key: the second park overwrote the
        // first, so one commit applied the WRONG batch and the other was rejected as NoPending.
        val executor = create()
        val cannula = BatchAction.TherapyEvent(teType = TE.Type.CANNULA_CHANGE, timestamp = 1_000L, source = Sources.FillDialog)
        val cartridge = BatchAction.TherapyEvent(teType = TE.Type.INSULIN_CHANGE, timestamp = 2_000L, source = Sources.FillDialog)

        val first = executor.prepareBatch(listOf(cannula)) as MasterActionExecutor.PrepareResult.Preview
        val second = executor.prepareBatch(listOf(cartridge)) as MasterActionExecutor.PrepareResult.Preview

        assertThat(first.bolusId).isNotEqualTo(second.bolusId)
        // Both parked batches must survive independently — neither may consume or evict the other.
        assertThat(executor.confirm(first.bolusId, Sources.FillDialog, { })).isEqualTo(MasterActionExecutor.ConfirmResult.Delivered)
        assertThat(executor.confirm(second.bolusId, Sources.FillDialog, { })).isEqualTo(MasterActionExecutor.ConfirmResult.Delivered)
    }

    @Test
    fun prepareBatch_namedProfileSwitch_appliesViaNamedCreateProfileSwitchFromMasterStore() = runTest {
        stubPassthroughConstraints()
        val store = mock<ProfileStore>()
        whenever(store.getSpecificProfile("Lunch")).thenReturn(mock())
        whenever(profileRepository.profile).thenReturn(MutableStateFlow(store))
        // A named switch has to record an insulin; here it comes from the insulin in force.
        whenever(profileFunction.getRunningOrRequestedICfg()).thenReturn(someICfg)
        whenever(profileFunction.createProfileSwitch(any(), any(), any(), any(), any(), any(), any(), any(), anyOrNull(), any(), any())).thenReturn(mock<PS>())
        val executor = create()

        val prepared = executor.prepareBatch(listOf(BatchAction.ProfileSwitch(110, 0, 60, profileName = "Lunch"))) as MasterActionExecutor.PrepareResult.Preview
        val result = executor.confirm(prepared.bolusId, Sources.NSClient, { })

        assertThat(result).isEqualTo(MasterActionExecutor.ConfirmResult.Delivered)
        // The named overload resolves the target from the MASTER's store (a client may relay a name the master owns).
        verify(profileFunction).createProfileSwitch(eq(store), eq("Lunch"), eq(60), eq(110), eq(0), any(), eq(Action.PROFILE_SWITCH), eq(Sources.NSClient), anyOrNull(), any(), eq(someICfg))
    }

    @Test
    fun prepareBatch_namedProfileSwitch_carriesTheCallerSuppliedICfgEvenWithNothingRunning() = runTest {
        stubPassthroughConstraints()
        val store = mock<ProfileStore>()
        whenever(store.getSpecificProfile("Lunch")).thenReturn(mock())
        whenever(profileRepository.profile).thenReturn(MutableStateFlow(store))
        // Nothing running and nothing pending — but the caller already asked the user (fill/prime, activation).
        whenever(profileFunction.getRunningOrRequestedICfg()).thenReturn(null)
        val chosen = ICfg(insulinLabel = "Chosen", insulinEndTime = 420, insulinPeakTime = 55, concentration = 1.0)
        val executor = create()

        val prepared = executor.prepareBatch(listOf(BatchAction.ProfileSwitch(110, 0, 60, profileName = "Lunch", iCfg = chosen))) as MasterActionExecutor.PrepareResult.Preview
        executor.confirm(prepared.bolusId, Sources.NSClient, { })

        // The user's choice wins over the (absent) in-force insulin and is what gets recorded.
        verify(profileFunction).createProfileSwitch(eq(store), eq("Lunch"), eq(60), eq(110), eq(0), any(), eq(Action.PROFILE_SWITCH), eq(Sources.NSClient), anyOrNull(), any(), eq(chosen))
    }

    @Test
    fun prepareBatch_namedProfileSwitch_noICfgAndNothingRunningOrPending_returnsError() = runTest {
        stubPassthroughConstraints()
        val store = mock<ProfileStore>()
        whenever(store.getSpecificProfile("Lunch")).thenReturn(mock())
        whenever(profileRepository.profile).thenReturn(MutableStateFlow(store))
        whenever(profileFunction.getRunningOrRequestedICfg()).thenReturn(null)
        val executor = create()

        val result = executor.prepareBatch(listOf(BatchAction.ProfileSwitch(110, 0, 60, profileName = "Lunch")))

        // Refuse rather than substitute an arbitrary insulin from the catalogue.
        assertThat(result).isInstanceOf(MasterActionExecutor.PrepareResult.Error::class.java)
        verify(profileFunction, never()).createProfileSwitch(any(), any(), any(), any(), any(), any(), any(), any(), anyOrNull(), any(), any())
    }

    // The running-vs-requested precedence itself belongs to ProfileFunction and is asserted in
    // ProfileFunctionImplTest; here it is a stubbed collaborator, so re-testing it would prove nothing.

    @Test
    fun prepareBatch_namedProfileSwitch_notInMasterStore_returnsError() = runTest {
        stubPassthroughConstraints()
        val store = mock<ProfileStore>()
        whenever(store.getSpecificProfile(any())).thenReturn(null)
        whenever(profileRepository.profile).thenReturn(MutableStateFlow(store))
        val executor = create()

        val result = executor.prepareBatch(listOf(BatchAction.ProfileSwitch(100, 0, 0, profileName = "Ghost")))

        assertThat(result).isInstanceOf(MasterActionExecutor.PrepareResult.Error::class.java)
    }

    // ---- prepareBatch() → confirm(): running-mode changes (the shared path for the phone screen, a client, and wear) ----

    @Test
    fun prepareBatch_runningMode_parksAndConfirmAppliesViaHandleRunningModeChange() = runTest {
        stubPassthroughConstraints()
        whenever(loop.allowedNextModes()).thenReturn(listOf(RM.Mode.CLOSED_LOOP))
        whenever(profileFunction.getProfile()).thenReturn(mock<EffectiveProfile>())
        val executor = create()

        val prepared = executor.prepareBatch(listOf(BatchAction.RunningMode(RM.Mode.CLOSED_LOOP))) as MasterActionExecutor.PrepareResult.Preview
        val result = executor.confirm(prepared.bolusId, Sources.NSClient, { })

        assertThat(result).isEqualTo(MasterActionExecutor.ConfirmResult.Delivered)
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

        assertThat(result).isInstanceOf(MasterActionExecutor.PrepareResult.Error::class.java)
        verify(loop, never()).handleRunningModeChange(any(), any(), any(), any(), any(), any())
    }

    @Test
    fun prepareBatch_runningMode_temporaryModeWithoutDuration_returnsError() = runTest {
        stubPassthroughConstraints()
        whenever(loop.allowedNextModes()).thenReturn(listOf(RM.Mode.SUSPENDED_BY_USER))
        val executor = create()

        val result = executor.prepareBatch(listOf(BatchAction.RunningMode(RM.Mode.SUSPENDED_BY_USER, durationMinutes = 0)))

        assertThat(result).isInstanceOf(MasterActionExecutor.PrepareResult.Error::class.java)
        verify(loop, never()).handleRunningModeChange(any(), any(), any(), any(), any(), any())
    }

    @Test
    fun prepareBatch_runningMode_disconnectWithDuration_appliesWithDuration() = runTest {
        stubPassthroughConstraints()
        whenever(loop.allowedNextModes()).thenReturn(listOf(RM.Mode.DISCONNECTED_PUMP))
        whenever(profileFunction.getProfile()).thenReturn(mock<EffectiveProfile>())
        val executor = create()

        val prepared = executor.prepareBatch(listOf(BatchAction.RunningMode(RM.Mode.DISCONNECTED_PUMP, durationMinutes = 60))) as MasterActionExecutor.PrepareResult.Preview
        val result = executor.confirm(prepared.bolusId, Sources.NSClient, { })

        assertThat(result).isEqualTo(MasterActionExecutor.ConfirmResult.Delivered)
        verify(loop).handleRunningModeChange(eq(RM.Mode.DISCONNECTED_PUMP), eq(Action.DISCONNECT), eq(Sources.NSClient), any(), eq(60), any())
    }

    @Test
    fun prepareBatch_runningMode_resumeFromDisconnect_usesReconnectAction() = runTest {
        stubPassthroughConstraints()
        whenever(loop.allowedNextModes()).thenReturn(listOf(RM.Mode.RESUME))
        whenever(loop.runningMode()).thenReturn(RM.Mode.DISCONNECTED_PUMP) // currently disconnected → RESUME is a pump reconnect
        whenever(profileFunction.getProfile()).thenReturn(mock<EffectiveProfile>())
        val executor = create()

        val prepared = executor.prepareBatch(listOf(BatchAction.RunningMode(RM.Mode.RESUME))) as MasterActionExecutor.PrepareResult.Preview
        val result = executor.confirm(prepared.bolusId, Sources.NSClient, { })

        assertThat(result).isEqualTo(MasterActionExecutor.ConfirmResult.Delivered)
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
        whenever(commandQueue.tempBasalPercent(any(), any(), any(), any(), any())).thenReturn(pumpEnactResultProvider().success(true))
        val executor = create()

        val prepared = executor.prepareBatch(listOf(BatchAction.TempBasal(rate = 150.0, isPercent = true, durationMinutes = 30))) as MasterActionExecutor.PrepareResult.Preview
        val result = executor.confirm(prepared.bolusId, Sources.NSClient, { })

        assertThat(result).isEqualTo(MasterActionExecutor.ConfirmResult.Delivered)
        verify(commandQueue, never()).bolus(anyOrNull())
        verify(commandQueue).tempBasalPercent(eq(150), eq(30), eq(true), any(), any())
    }

    /**
     * A temp basal dropped from the queue (an import clears it) reports `success = false` like a pump failure, and
     * the dialog would raise the full screen BOLUS_ERROR alarm for it. `cancelled` rides back on the result, so
     * confirm answers [MasterActionExecutor.ConfirmResult.Cancelled] — the comment still reaches onError, or the
     * caller would be told the rate was set.
     */
    @Test
    fun prepareBatch_tempBasalDroppedFromQueue_isCancelledNotAFailure() = runTest {
        stubPassthroughConstraints()
        whenever(runningModeGuard.rejectionMessage(any())).thenReturn(null)
        whenever(profileFunction.getProfile()).thenReturn(mock<EffectiveProfile>())
        whenever(constraintsChecker.applyBasalPercentConstraints(any(), any())).thenAnswer { it.getArgument<Constraint<Int>>(0) }
        val pump = mock<PumpWithConcentration>()
        whenever(activePlugin.activePump).thenReturn(pump)
        whenever(pump.isInitialized()).thenReturn(true)
        whenever(pump.pumpDescription).thenReturn(PumpDescription().also { it.isTempBasalCapable = true; it.tempBasalStyle = PumpDescription.PERCENT })
        whenever(commandQueue.tempBasalPercent(any(), any(), any(), any(), any()))
            .thenReturn(pumpEnactResultProvider().success(false).cancelled(true).comment("dropped by import"))
        val executor = create()

        val prepared = executor.prepareBatch(listOf(BatchAction.TempBasal(rate = 150.0, isPercent = true, durationMinutes = 30))) as MasterActionExecutor.PrepareResult.Preview
        var err: String? = null
        val result = executor.confirm(prepared.bolusId, Sources.NSClient, { err = it.comment })

        assertThat(result).isEqualTo(MasterActionExecutor.ConfirmResult.Cancelled)
        assertThat(err).isEqualTo("dropped by import")
    }

    /** A real pump failure keeps reporting Delivered + the error comment — only a deliberate drop is Cancelled. */
    @Test
    fun prepareBatch_tempBasalFailedOnPump_staysADeliveryFailure() = runTest {
        stubPassthroughConstraints()
        whenever(runningModeGuard.rejectionMessage(any())).thenReturn(null)
        whenever(profileFunction.getProfile()).thenReturn(mock<EffectiveProfile>())
        whenever(constraintsChecker.applyBasalPercentConstraints(any(), any())).thenAnswer { it.getArgument<Constraint<Int>>(0) }
        val pump = mock<PumpWithConcentration>()
        whenever(activePlugin.activePump).thenReturn(pump)
        whenever(pump.isInitialized()).thenReturn(true)
        whenever(pump.pumpDescription).thenReturn(PumpDescription().also { it.isTempBasalCapable = true; it.tempBasalStyle = PumpDescription.PERCENT })
        whenever(commandQueue.tempBasalPercent(any(), any(), any(), any(), any()))
            .thenReturn(pumpEnactResultProvider().success(false).comment("pump said no"))
        val executor = create()

        val prepared = executor.prepareBatch(listOf(BatchAction.TempBasal(rate = 150.0, isPercent = true, durationMinutes = 30))) as MasterActionExecutor.PrepareResult.Preview
        var err: String? = null
        val result = executor.confirm(prepared.bolusId, Sources.NSClient, { err = it.comment })

        assertThat(result).isEqualTo(MasterActionExecutor.ConfirmResult.Delivered)
        assertThat(err).isEqualTo("pump said no")
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

        assertThat(result).isInstanceOf(MasterActionExecutor.PrepareResult.Error::class.java)
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
        whenever(commandQueue.extendedBolus(any(), any())).thenReturn(pumpEnactResultProvider().success(true))
        val executor = create()

        val prepared = executor.prepareBatch(listOf(BatchAction.ExtendedBolus(insulin = 1.5, durationMinutes = 120))) as MasterActionExecutor.PrepareResult.Preview
        val result = executor.confirm(prepared.bolusId, Sources.NSClient, { })

        assertThat(result).isEqualTo(MasterActionExecutor.ConfirmResult.Delivered)
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
        whenever(commandQueue.tempBasalAbsolute(any(), any(), any(), any(), any())).thenReturn(pumpEnactResultProvider().success(true))
        val executor = create()

        val prepared = executor.prepareBatch(listOf(BatchAction.TempBasal(rate = 1.25, isPercent = false, durationMinutes = 45))) as MasterActionExecutor.PrepareResult.Preview
        val result = executor.confirm(prepared.bolusId, Sources.NSClient, { })

        assertThat(result).isEqualTo(MasterActionExecutor.ConfirmResult.Delivered)
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
        whenever(commandQueue.tempBasalPercent(any(), any(), any(), any(), any())).thenReturn(pumpEnactResultProvider().success(true))
        val executor = create()

        val prepared = executor.prepareBatch(listOf(BatchAction.TempBasal(rate = 0.0, isPercent = true, durationMinutes = 30))) as MasterActionExecutor.PrepareResult.Preview
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

        assertThat(result).isInstanceOf(MasterActionExecutor.PrepareResult.Error::class.java)
        verify(commandQueue, never()).extendedBolus(any(), any())
    }

    @Test
    fun prepareBatch_cancelTempBasal_parksAndConfirmCancelsViaCommandQueue() = runTest {
        stubPassthroughConstraints()
        val pump = mock<PumpWithConcentration>()
        whenever(activePlugin.activePump).thenReturn(pump)
        whenever(pump.isInitialized()).thenReturn(true)
        whenever(pump.pumpDescription).thenReturn(PumpDescription().also { it.isTempBasalCapable = true })
        whenever(commandQueue.cancelTempBasal(any(), any())).thenReturn(pumpEnactResultProvider().success(true))
        val executor = create()

        val prepared = executor.prepareBatch(listOf(BatchAction.CancelTempBasal)) as MasterActionExecutor.PrepareResult.Preview
        val result = executor.confirm(prepared.bolusId, Sources.NSClient, { })

        assertThat(result).isEqualTo(MasterActionExecutor.ConfirmResult.Delivered)
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
        whenever(commandQueue.cancelExtended()).thenReturn(pumpEnactResultProvider().success(true))
        val executor = create()

        val prepared = executor.prepareBatch(listOf(BatchAction.CancelExtendedBolus)) as MasterActionExecutor.PrepareResult.Preview
        val result = executor.confirm(prepared.bolusId, Sources.NSClient, { })

        assertThat(result).isEqualTo(MasterActionExecutor.ConfirmResult.Delivered)
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
        // The step is the interface default, read from the description; a plain mock would answer 0.0.
        whenever(pump.bolusStepSize(any())).thenCallRealMethod()
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
        assertThat(result).isInstanceOf(MasterActionExecutor.PrepareResult.Error::class.java)
        assertThat((result as MasterActionExecutor.PrepareResult.Error).message).isEqualTo("No insulin required")
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

        assertThat(result).isInstanceOf(MasterActionExecutor.PrepareResult.Error::class.java)
        assertThat((result as MasterActionExecutor.PrepareResult.Error).message).isEqualTo("Initializing…")
        verify(runningModeGuard, never()).rejectionMessage(any())
        verify(quickWizard, never()).get(any<String>())
    }

    // ---- a stored QuickWizard eCarbs amount is re-checked against the CURRENT carbs limit ----

    /** Carbs capped at [limit], the way SafetyMaxCarbs caps them; everything else passed through. */
    private fun stubCarbsLimit(limit: Int) {
        stubPassthroughConstraints()
        whenever(constraintsChecker.applyCarbsConstraints(any())).thenAnswer {
            ConstraintObject(it.getArgument<Constraint<Int>>(0).value().coerceAtMost(limit), aapsLogger)
        }
    }

    @Test
    fun prepareQuickWizard_storedECarbsAboveTheCurrentLimit_isRefused() = runTest {
        // The eCarbs amount lives IN the preset, so lowering the carbs limit afterwards (or importing settings)
        // leaves a stored value above it. Refuse the preset, like an over-limit `carbs` already is — never deliver
        // it uncapped, and never trim it silently behind the user's back.
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
        stubCarbsLimit(48)
        val entry = mock<QuickWizardEntry>()
        whenever(quickWizard.get("g")).thenReturn(entry)
        whenever(entry.carbs()).thenReturn(20)        // within the limit → clears the existing carbs check
        whenever(entry.eCarbsGrams()).thenReturn(100) // stored above the limit → must stop here
        whenever(entry.doCalc(anyOrNull(), anyOrNull(), anyOrNull())).thenReturn(mock<BolusWizard>())
        val executor = create()

        val result = executor.prepareQuickWizard("g")

        assertThat(result).isInstanceOf(MasterActionExecutor.PrepareResult.Error::class.java)
        // The SAME message an over-limit `carbs` produces — the two fields now fail identically.
        assertThat((result as MasterActionExecutor.PrepareResult.Error).message).isEqualTo(baseText.gs(CoreUiStrings.wizard_carbs_constraint))
        verify(commandQueue, never()).bolus(anyOrNull())
    }

    @Test
    fun prepareBatch_quickWizardECarbsAboveTheCurrentLimit_isRefused() = runTest {
        // Same preset, CARBS mode: it reaches the master through prepareBatch instead, so the guard lives there too.
        whenever(runningModeGuard.rejectionMessage(any())).thenReturn(null)
        stubCarbsLimit(48)
        whenever(quickWizard.get("qw-1")).thenReturn(mock<QuickWizardEntry>())
        val executor = create()
        val actions = listOf(
            BatchAction.Bolus(
                insulin = 0.0, carbs = 20, carbsTimeOffsetMinutes = 0, carbsDurationHours = 0, recordOnly = false,
                notes = "Pizza", timestamp = 0L, iCfg = null, quickWizardGuid = "qw-1", eCarbsGrams = 100, eCarbsDurationHours = 4
            )
        )

        val result = executor.prepareBatch(actions)

        assertThat(result).isInstanceOf(MasterActionExecutor.PrepareResult.Error::class.java)
        assertThat((result as MasterActionExecutor.PrepareResult.Error).message).isEqualTo(baseText.gs(CoreUiStrings.wizard_carbs_constraint))
    }

    @Test
    fun prepareBatch_quickWizardECarbsWithinTheLimit_stillPrepares() = runTest {
        // The guard must not block a preset that does fit — only one that no longer does.
        whenever(runningModeGuard.rejectionMessage(any())).thenReturn(null)
        stubCarbsLimit(48)
        whenever(quickWizard.get("qw-1")).thenReturn(mock<QuickWizardEntry>())
        val executor = create()
        val actions = listOf(
            BatchAction.Bolus(
                insulin = 0.0, carbs = 20, carbsTimeOffsetMinutes = 0, carbsDurationHours = 0, recordOnly = false,
                notes = "Pizza", timestamp = 0L, iCfg = null, quickWizardGuid = "qw-1", eCarbsGrams = 40, eCarbsDurationHours = 4
            )
        )

        assertThat(executor.prepareBatch(actions)).isInstanceOf(MasterActionExecutor.PrepareResult.Preview::class.java)
    }

    @Test
    fun prepareBatch_nonQuickWizardECarbsAboveTheLimit_isNotRefused() = runTest {
        // Deliberate scope: a wear or dialog eCarbs value was clamped by its own input field moments ago, so there is
        // no stale-preset problem to solve and refusing it would only break a live entry. Only a QuickWizard is checked.
        whenever(runningModeGuard.rejectionMessage(any())).thenReturn(null)
        stubCarbsLimit(48)
        val executor = create()
        val actions = listOf(
            BatchAction.Bolus(
                insulin = 0.0, carbs = 20, carbsTimeOffsetMinutes = 0, carbsDurationHours = 0, recordOnly = false,
                notes = "", timestamp = 0L, iCfg = null, eCarbsGrams = 100, eCarbsDurationHours = 4
            )
        )

        assertThat(executor.prepareBatch(actions)).isInstanceOf(MasterActionExecutor.PrepareResult.Preview::class.java)
        verify(quickWizard, never()).get(any<String>())
    }

    @Test
    fun confirm_asAdvisor_deliversCorrectionAdvisorNotCarbWizardBolus() = runTest {
        whenever(runningModeGuard.rejectionMessage(any())).thenReturn(null)
        whenever(commandQueue.bolus(anyOrNull())).thenReturn(pumpEnactResultProvider().success(true))
        val executor = create()
        // Parked WITH carbs, but the user took the high-BG advisor branch → correction-only delivery.
        executor.setPending(insulin = 1.5, carbs = 40, bolusCalculatorResult = null, bolusId = 999L)

        val result = executor.confirm(999L, Sources.NSClient, { }, asAdvisor = true)

        assertThat(result).isEqualTo(MasterActionExecutor.ConfirmResult.Delivered)
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
        whenever(commandQueue.bolus(anyOrNull())).thenReturn(pumpEnactResultProvider().success(false))
        val executor = create()
        var errorMsg: String? = null

        executor.deliverBolusAdvisor(
            insulin = 1.5,
            mgdlGlucose = 200.0,
            bolusCalculatorResult = null,
            notes = null,
            source = Sources.WizardDialog,
            onError = { errorMsg = it.comment }
        )

        assertThat(errorMsg).isNotNull()
        verify(automation, never()).scheduleAutomationEventEatReminder()
    }

    @Test
    fun bolus_onCommandFailure_postsUrgentBolusFailedAlarm() = runTest {
        whenever(runningModeGuard.rejectionMessage(any())).thenReturn(null)
        whenever(commandQueue.bolus(anyOrNull())).thenReturn(pumpEnactResultProvider().success(false))
        val executor = create()

        executor.deliverWizardBolus(
            insulin = 1.0, carbs = 0, carbTimeMinutes = 0, mgdlGlucose = null,
            bolusCalculatorResult = null, notes = null, source = Sources.QuickWizard, onError = { }
        )

        // The async delivery failure raises the single URGENT alarm from the executor (not the now-gone dialog).
        verify(notificationManager).post(
            eq(NotificationId.BOLUS_DELIVERY_FAILED), any<String>(), any<NotificationLevel>(), any<Int>(),
            anyOrNull<AlarmSound>(), any<List<NotificationAction>>(), anyOrNull<() -> Boolean>()
        )
    }

    @Test
    fun bolus_onCommandFailure_whenStopPressed_doesNotPostAlarm() = runTest {
        whenever(runningModeGuard.rejectionMessage(any())).thenReturn(null)
        whenever(commandQueue.bolus(anyOrNull())).thenReturn(pumpEnactResultProvider().success(false))
        whenever(bolusProgressData.isStopPressed).thenReturn(true)
        val executor = create()

        executor.deliverWizardBolus(
            insulin = 1.0, carbs = 0, carbTimeMinutes = 0, mgdlGlucose = null,
            bolusCalculatorResult = null, notes = null, source = Sources.QuickWizard, onError = { }
        )

        // A user-initiated cancel is not a failure: no URGENT alarm even though the command result is unsuccessful.
        verify(notificationManager, never()).post(
            eq(NotificationId.BOLUS_DELIVERY_FAILED), any<String>(), any<NotificationLevel>(), any<Int>(),
            anyOrNull<AlarmSound>(), any<List<NotificationAction>>(), anyOrNull<() -> Boolean>()
        )
    }

    /**
     * A bolus dropped from the queue on purpose (a settings import clears it) is not a pump failure, so it must
     * not ring the alarm — but it must NOT be silent either. Nothing re-sends a bolus: the user pressed the
     * button, no insulin was given, and the entry dialog is already gone.
     */
    @Test
    fun bolus_whenDroppedFromQueue_postsSilentCancelledNoticeNotTheAlarm() = runTest {
        whenever(runningModeGuard.rejectionMessage(any())).thenReturn(null)
        whenever(commandQueue.bolus(anyOrNull())).thenReturn(pumpEnactResultProvider().success(false).cancelled(true).comment("import"))
        val executor = create()

        executor.deliverWizardBolus(
            insulin = 1.0, carbs = 0, carbTimeMinutes = 0, mgdlGlucose = null,
            bolusCalculatorResult = null, notes = null, source = Sources.QuickWizard, onError = { }
        )

        verify(notificationManager, never()).post(
            eq(NotificationId.BOLUS_DELIVERY_FAILED), any<String>(), any<NotificationLevel>(), any<Int>(),
            anyOrNull<AlarmSound>(), any<List<NotificationAction>>(), anyOrNull<() -> Boolean>()
        )
        // BOLUS_CANCELLED is declared IMPORTANT, which is silent by definition — no sound is passed at the post site.
        verify(notificationManager).post(
            eq(NotificationId.BOLUS_CANCELLED), any<String>(), any<NotificationLevel>(), any<Int>(),
            isNull(), any<List<NotificationAction>>(), anyOrNull<() -> Boolean>()
        )
    }

    /**
     * The async bolus result arrives long after confirm() returned, so the RETURN value cannot carry it — the
     * callback is the only way the relay learns a bolus was dropped rather than failed. Without the flag the
     * master writes an ExecutionFailed ack and the paired client rings its own BOLUS_ERROR alarm for a bolus
     * that nothing tried to give.
     */
    @Test
    fun bolus_whenDroppedFromQueue_tellsTheCallerItWasCancelled() = runTest {
        whenever(runningModeGuard.rejectionMessage(any())).thenReturn(null)
        whenever(commandQueue.bolus(anyOrNull())).thenReturn(pumpEnactResultProvider().success(false).cancelled(true).comment("import"))
        val executor = create()

        var failure: MasterActionExecutor.Failure? = null
        executor.deliverWizardBolus(
            insulin = 1.0, carbs = 0, carbTimeMinutes = 0, mgdlGlucose = null,
            bolusCalculatorResult = null, notes = null, source = Sources.QuickWizard, onError = { failure = it }
        )

        assertThat(failure).isNotNull()
        assertThat(failure!!.cancelled).isTrue()
    }

    /** The same shape when the pump really failed — the caller must still be able to tell the two apart. */
    @Test
    fun bolus_whenPumpFailed_tellsTheCallerItWasNotCancelled() = runTest {
        whenever(runningModeGuard.rejectionMessage(any())).thenReturn(null)
        whenever(commandQueue.bolus(anyOrNull())).thenReturn(pumpEnactResultProvider().success(false).comment("pump said no"))
        val executor = create()

        var failure: MasterActionExecutor.Failure? = null
        executor.deliverWizardBolus(
            insulin = 1.0, carbs = 0, carbTimeMinutes = 0, mgdlGlucose = null,
            bolusCalculatorResult = null, notes = null, source = Sources.QuickWizard, onError = { failure = it }
        )

        assertThat(failure).isNotNull()
        assertThat(failure!!.cancelled).isFalse()
    }

    @Test
    fun deliverInsulin_recordsCorrectionBolusWithNoteOnUserEntry() = runTest {
        whenever(runningModeGuard.rejectionMessage(any())).thenReturn(null)
        whenever(commandQueue.bolus(anyOrNull())).thenReturn(pumpEnactResultProvider().success(true))
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

    @Test
    fun deliverFillBolus_onTubePump_deliversAPrimingBolus() = runTest {
        whenever(runningModeGuard.rejectionMessage(any())).thenReturn(null)
        whenever(commandQueue.bolus(anyOrNull())).thenReturn(pumpEnactResultProvider().success(true))
        var succeeded = false

        create().deliverFillBolus(0.5, null, Sources.FillDialog, onError = { }, onSuccess = { succeeded = true })

        val dbiCaptor = argumentCaptor<DetailedBolusInfo>()
        verify(commandQueue).bolus(dbiCaptor.capture())
        assertThat(dbiCaptor.firstValue.bolusType).isEqualTo(BS.Type.PRIMING)
        assertThat(dbiCaptor.firstValue.insulin).isEqualTo(0.5)
        assertThat(succeeded).isTrue()
    }

    /**
     * A patch pump primes in its own activation wizard and its cannula is already in the body. A PRIMING bolus
     * adds no IOB, so a prime here would be insulin the loop does not know about. The shared path refuses it,
     * whoever asks: the Fill dialog, the watch or a client.
     */
    @Test
    fun deliverFillBolus_onPumpThatPrimesItself_refusesAndSendsNothing() = runTest {
        whenever(runningModeGuard.rejectionMessage(any())).thenReturn(null)
        testPumpPlugin.pumpDescription.isRefillingCapable = false
        var failure: MasterActionExecutor.Failure? = null
        var succeeded = false

        create().deliverFillBolus(0.5, null, Sources.Wear, onError = { failure = it }, onSuccess = { succeeded = true })

        assertThat(failure?.comment).isEqualTo(baseText.gs(CoreUiStrings.prime_not_available))
        assertThat(succeeded).isFalse()
        verify(commandQueue, never()).bolus(anyOrNull())
        verify(uel, never()).log(any<Action>(), any(), anyOrNull(), any<List<ValueWithUnit>>())
    }

    // ----- Removing records and calibrations: the master does them, whoever asked -----

    private val bolusRemoval = BatchAction.Remove(BatchAction.Remove.Kind.BOLUS, listOf(BatchAction.Remove.Ref(nsId = "ns1")))

    @Test
    fun prepareBatch_remove_showsTheMastersLinesAndConfirmRemovesWithoutAnyDose() = runTest {
        stubPassthroughConstraints()
        val line = ConfirmationLine(ConfirmationRole.PRIMARY, "Insulin: 2.00 U")
        whenever(masterRecordWriter.planRemovals(listOf(bolusRemoval))).thenReturn(MasterRecordWriter.RemovalPlan(listOf(line), found = 1, notHave = 0))
        val executor = create()

        val prepared = executor.prepareBatch(listOf(bolusRemoval)) as MasterActionExecutor.PrepareResult.Preview
        val result = executor.confirm(prepared.bolusId, Sources.NSClient, { })

        assertThat(prepared.lines).containsExactly(line)
        assertThat(result).isEqualTo(MasterActionExecutor.ConfirmResult.Delivered)
        verify(masterRecordWriter).remove(listOf(bolusRemoval), Sources.NSClient)
        verify(commandQueue, never()).bolus(anyOrNull())
    }

    @Test
    fun prepareBatch_remove_removesNothingUntilTheUserConfirms() = runTest {
        stubPassthroughConstraints()
        whenever(masterRecordWriter.planRemovals(any())).thenReturn(MasterRecordWriter.RemovalPlan(emptyList(), found = 1, notHave = 0))

        create().prepareBatch(listOf(bolusRemoval))

        verify(masterRecordWriter, never()).remove(any(), any())
    }

    @Test
    fun prepareBatch_remove_ofRecordsTheMasterDoesNotHave_isRefusedWithTheReason() = runTest {
        stubPassthroughConstraints()
        whenever(masterRecordWriter.planRemovals(any())).thenReturn(MasterRecordWriter.RemovalPlan(emptyList(), found = 0, notHave = 2))
        whenever(masterRecordWriter.notHaveText(2)).thenReturn("not here")

        val result = create().prepareBatch(listOf(bolusRemoval))

        assertThat(result).isEqualTo(MasterActionExecutor.PrepareResult.Error("not here"))
    }

    @Test
    fun prepareBatch_removeStartEvents_confirmRemovesThemOnTheMaster() = runTest {
        stubPassthroughConstraints()
        val line = ConfirmationLine(ConfirmationRole.PRIMARY, "Remove record: AndroidAPS started")
        whenever(masterRecordWriter.removeStartEventsLine()).thenReturn(line)
        val executor = create()

        val prepared = executor.prepareBatch(listOf(BatchAction.RemoveStartEvents)) as MasterActionExecutor.PrepareResult.Preview
        executor.confirm(prepared.bolusId, Sources.NSClient, { })

        assertThat(prepared.lines).containsExactly(line)
        verify(masterRecordWriter).removeStartEvents(Sources.NSClient)
    }

    @Test
    fun prepareBatch_calibration_isRefusedWithTheMastersReason() = runTest {
        stubPassthroughConstraints()
        whenever(masterRecordWriter.calibrationRefusal(BatchAction.Calibration(120.0))).thenReturn("no session")

        val result = create().prepareBatch(listOf(BatchAction.Calibration(120.0)))

        assertThat(result).isEqualTo(MasterActionExecutor.PrepareResult.Error("no session"))
    }

    @Test
    fun prepareBatch_calibration_confirmStoresItOnTheMaster() = runTest {
        stubPassthroughConstraints()
        whenever(masterRecordWriter.calibrationRefusal(BatchAction.Calibration(120.0))).thenReturn(null)
        whenever(masterRecordWriter.calibrationLines(BatchAction.Calibration(120.0))).thenReturn(listOf(ConfirmationLine(ConfirmationRole.PRIMARY, "Calibration: 120")))
        val executor = create()

        val prepared = executor.prepareBatch(listOf(BatchAction.Calibration(120.0))) as MasterActionExecutor.PrepareResult.Preview
        executor.confirm(prepared.bolusId, Sources.NSClient, { })

        verify(masterRecordWriter).addCalibration(eq(BatchAction.Calibration(120.0)), eq(Sources.NSClient), any())
    }

    // ---- #5381: no SMB between the calculation of a bolus and its confirmation ----

    @Test
    fun prepareBatch_bolus_pausesSmbsAndDropsAWaitingSmb() = runTest {
        stubPassthroughConstraints()
        whenever(runningModeGuard.rejectionMessage(any())).thenReturn(null)

        create().prepareBatch(listOf(bolusAction))

        // The waiting SMB was calculated without this bolus, and no new one may start before the user confirms.
        verify(commandQueue).dropWaitingSmb()
        assertThat(smbPause.isActive()).isTrue()
    }

    @Test
    fun prepareBatch_whileAnSmbIsBeingDelivered_isRefusedBeforeTheDialogCloses() = runTest {
        stubPassthroughConstraints()
        whenever(runningModeGuard.rejectionMessage(any())).thenReturn(null)
        whenever(commandQueue.isRunning(Command.CommandType.SMB_BOLUS)).thenReturn(true)
        val executor = create()

        val result = executor.prepareBatch(listOf(bolusAction))

        assertThat(result).isEqualTo(MasterActionExecutor.PrepareResult.Error("An SMB is being delivered right now. Try again when it has finished."))
        // Nothing parked and no pause left behind: the SMBs of the loop are not held up by a refused bolus.
        assertThat(smbPause.isActive()).isFalse()
    }

    @Test
    fun prepareBatch_withoutInsulinForThePump_neverWaitsForAnSmb() = runTest {
        stubPassthroughConstraints()
        whenever(commandQueue.isRunning(Command.CommandType.SMB_BOLUS)).thenReturn(true)
        val executor = create()
        val recordOnly = bolusAction.copy(recordOnly = true, iCfg = ICfg("Pen", 75, 6.0, 1.0))

        // A temp target, and a pen dose that is only recorded, never reach the pump.
        assertThat(executor.prepareBatch(listOf(targetAction))).isInstanceOf(MasterActionExecutor.PrepareResult.Preview::class.java)
        assertThat(executor.prepareBatch(listOf(recordOnly))).isInstanceOf(MasterActionExecutor.PrepareResult.Preview::class.java)
        assertThat(smbPause.isActive()).isFalse()
        verify(commandQueue, never()).dropWaitingSmb()
    }

    @Test
    fun confirm_handsThePauseOverToTheQueue() = runTest {
        stubPassthroughConstraints()
        whenever(runningModeGuard.rejectionMessage(any())).thenReturn(null)
        whenever(commandQueue.bolus(anyOrNull())).thenReturn(pumpEnactResultProvider().success(true))
        val executor = create()
        val prepared = executor.prepareBatch(listOf(bolusAction)) as MasterActionExecutor.PrepareResult.Preview

        assertThat(executor.confirm(prepared.bolusId, Sources.InsulinDialog, { })).isEqualTo(MasterActionExecutor.ConfirmResult.Delivered)

        // Still on for the moment it takes to put the bolus into the queue, which holds back SMBs from then on ...
        assertThat(smbPause.isActive()).isTrue()
        // ... and not for the rest of the two minutes.
        whenever(dateUtil.now()).thenReturn(now + SmbPause.HAND_OVER.inWholeMilliseconds)
        assertThat(smbPause.isActive()).isFalse()
    }

    @Test
    fun confirm_afterThePauseRanOut_givesNothing() = runTest {
        stubPassthroughConstraints()
        whenever(runningModeGuard.rejectionMessage(any())).thenReturn(null)
        val executor = create()
        val prepared = executor.prepareBatch(listOf(bolusAction)) as MasterActionExecutor.PrepareResult.Preview
        // The loop may have given an SMB since then that the dose does not include.
        whenever(dateUtil.now()).thenReturn(now + SmbPause.MAX_DURATION.inWholeMilliseconds)

        val result = executor.confirm(prepared.bolusId, Sources.NSClient, { })

        assertThat(result).isEqualTo(MasterActionExecutor.ConfirmResult.NoPending)
        verify(commandQueue, never()).bolus(anyOrNull())
    }

    @Test
    fun discard_endsThePauseAtOnce() = runTest {
        stubPassthroughConstraints()
        whenever(runningModeGuard.rejectionMessage(any())).thenReturn(null)
        val executor = create()
        val prepared = executor.prepareBatch(listOf(bolusAction)) as MasterActionExecutor.PrepareResult.Preview

        executor.discard(prepared.bolusId)

        assertThat(smbPause.isActive()).isFalse()
        assertThat(executor.confirm(prepared.bolusId, Sources.InsulinDialog, { })).isEqualTo(MasterActionExecutor.ConfirmResult.NoPending)
    }

    @Test
    fun confirm_whenTheRoleNoLongerAllowsIt_endsThePause() = runTest {
        stubPassthroughConstraints()
        whenever(runningModeGuard.rejectionMessage(any())).thenReturn(null)
        val executor = create()
        val prepared = executor.prepareBatch(listOf(bolusAction), RemoteCaller("a", ClientRole.Caregiver)) as MasterActionExecutor.PrepareResult.Preview

        val result = executor.confirm(prepared.bolusId, Sources.NSClient, { }, caller = RemoteCaller("a", ClientRole.ReadOnly))

        assertThat(result).isEqualTo(MasterActionExecutor.ConfirmResult.NotAllowed)
        assertThat(smbPause.isActive()).isFalse()
    }

    @Test
    fun prepareWizard_pausesBeforeItCalculates_andARefusalEndsThePause() = runTest {
        whenever(commandQueue.isRunning(Command.CommandType.SMB_BOLUS)).thenReturn(true)

        val refused = preparedManualWizard(calculated = 2.0, afterConstraints = 2.0)

        assertThat(refused).isEqualTo(MasterActionExecutor.PrepareResult.Error("An SMB is being delivered right now. Try again when it has finished."))
        // Refused before the calculation: the SMB is not in IOB yet.
        verify(bolusWizardProvider, never()).invoke()
        assertThat(smbPause.isActive()).isFalse()
    }

    @Test
    fun prepareWizard_noInsulinRequired_leavesNoPause() = runTest {
        val result = preparedManualWizard(calculated = 0.0, afterConstraints = 0.0, carbs = 0)

        assertThat(result).isInstanceOf(MasterActionExecutor.PrepareResult.Error::class.java)
        assertThat(smbPause.isActive()).isFalse()
    }

    @Test
    fun prepareWizard_aDose_keepsThePauseUntilConfirmed() = runTest {
        val result = preparedManualWizard(calculated = 2.0, afterConstraints = 2.0)

        assertThat(result).isInstanceOf(MasterActionExecutor.PrepareResult.Preview::class.java)
        assertThat(smbPause.isActive()).isTrue()
    }
}

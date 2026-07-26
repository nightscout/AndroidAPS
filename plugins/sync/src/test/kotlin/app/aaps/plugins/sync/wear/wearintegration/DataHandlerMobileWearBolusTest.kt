package app.aaps.plugins.sync.wear.wearintegration

import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.RM
import app.aaps.core.data.model.TT
import app.aaps.core.data.pump.defs.PumpDescription
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ui.ConfirmationLine
import app.aaps.core.data.ui.ConfirmationRole
import app.aaps.core.interfaces.aps.AutosensDataStore
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.automation.Automation
import app.aaps.core.interfaces.bolus.BatchAction
import app.aaps.core.interfaces.bolus.BatchExecutor
import app.aaps.core.interfaces.bolus.WizardBolusExecutor
import app.aaps.core.interfaces.bolus.WizardExecutor
import app.aaps.core.interfaces.clientcontrol.ActionProgress
import app.aaps.core.interfaces.clientcontrol.FailureReason
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.maintenance.ImportExportPrefs
import app.aaps.core.interfaces.nsclient.ProcessedDeviceStatusData
import app.aaps.core.interfaces.pump.PumpStatusProvider
import app.aaps.core.interfaces.pump.PumpWithConcentration
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.receivers.ReceiverStatusStore
import app.aaps.core.interfaces.rx.events.EventMobileToWear
import app.aaps.core.interfaces.rx.weardata.EventData
import app.aaps.core.interfaces.rx.weardata.EventData.RunningModeList.AvailableRunningMode
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.TrendCalculator
import app.aaps.core.keys.BooleanKey
import app.aaps.core.objects.runningMode.RunningModeGuard
import app.aaps.core.objects.wizard.QuickWizard
import app.aaps.core.objects.wizard.QuickWizardEntry
import app.aaps.core.objects.wizard.QuickWizardMode
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.timeout
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.whenever

/**
 * Tests for the unified wear **manual-bolus** ([DataHandlerMobile.handleBolusPreCheck]) and
 * **eCarbs** ([DataHandlerMobile.handleECarbsPreCheck]) precheck handlers.
 *
 * These two were migrated off their bespoke local capping + concatenated confirm-strings + echoed
 * amounts onto the role-transparent [BatchExecutor] (master→local prepare/commit, client→signed
 * round-trip). The behaviour this locks in:
 * - the master caps/parks (executor is the single authority) — the handler only adapts the wear
 *   command into a [BatchAction.Bolus] and ships the master's [ConfirmAction.lines] verbatim,
 * - the confirm round-trip carries **only the parked `bolusId`** (consume-once), never an echoed
 *   amount that a stale watch could resend,
 * - a `Prepared` is forwarded as a `ConfirmAction`; a `Rejected`/`Unconfirmed` becomes a `sendError`,
 * - on a client ([Config.AAPSCLIENT]) the handler emits a `ContactingMaster` spinner first and defers
 *   the confirm (no false local success); on a master it does neither.
 *
 * The handlers are `internal` (not private) so they can be driven directly here, without scaffolding
 * the full RxBus wiring around DataHandlerMobile's 30+ constructor deps. The emitted [EventMobileToWear]
 * is captured off the real [RxBusImpl] (a synchronous `onNext`).
 */
class DataHandlerMobileWearBolusTest : TestBaseWithProfile() {

    @Mock private lateinit var loop: Loop
    @Mock private lateinit var processedDeviceStatusData: ProcessedDeviceStatusData
    @Mock private lateinit var receiverStatusStore: ReceiverStatusStore
    @Mock private lateinit var quickWizard: QuickWizard
    @Mock private lateinit var trendCalculator: TrendCalculator
    @Mock private lateinit var commandQueue: CommandQueue
    @Mock private lateinit var uiInteraction: UiInteraction
    @Mock private lateinit var persistenceLayer: PersistenceLayer
    @Mock private lateinit var importExportPrefs: ImportExportPrefs
    @Mock private lateinit var pumpStatusProvider: PumpStatusProvider
    @Mock private lateinit var runningModeGuard: RunningModeGuard
    @Mock private lateinit var wizardBolusExecutor: WizardBolusExecutor
    @Mock private lateinit var batchExecutor: BatchExecutor
    @Mock private lateinit var wizardExecutor: WizardExecutor
    @Mock private lateinit var pump: PumpWithConcentration
    @Mock private lateinit var automation: Automation

    private lateinit var sut: DataHandlerMobile

    @BeforeEach fun prepare() {
        sut = DataHandlerMobile(
            aapsSchedulers, context, rxBus, aapsLogger, rh, preferences, config,
            iobCobCalculator, processedTbrEbData, smbGlucoseStatusProvider, profileFunction, profileUtil,
            loop, processedDeviceStatusData, receiverStatusStore, quickWizard, trendCalculator, dateUtil,
            constraintsChecker, activePlugin, insulin, commandQueue, fabricPrivacy, uiInteraction,
            persistenceLayer, importExportPrefs, decimalFormatter, pumpStatusProvider,
            ch, runningModeGuard, wizardBolusExecutor, batchExecutor, wizardExecutor
        )
        sut.automation = automation
        // Confirm-title + error-title + client-reject string all go through the single-arg gs().
        whenever(rh.gs(any<Int>())).thenReturn("CONFIRM")
        whenever(activePlugin.activePump).thenReturn(pump)
        whenever(pump.isInitialized()).thenReturn(true)
    }

    /** Capture the single [ConfirmAction] the handler ships to the watch via [EventMobileToWear]. */
    private inline fun capturedConfirm(block: () -> Unit): EventData.ConfirmAction {
        var captured: EventData? = null
        val d = rxBus.toObservable(EventMobileToWear::class.java).subscribe { captured = it.payload }
        block()
        d.dispose()
        return captured as EventData.ConfirmAction
    }

    /** Capture EVERY [EventMobileToWear] payload (e.g. a ContactingMaster spinner followed by the ConfirmAction). */
    private inline fun captureAll(block: () -> Unit): List<EventData> {
        val out = mutableListOf<EventData>()
        val d = rxBus.toObservable(EventMobileToWear::class.java).subscribe { out += it.payload }
        block()
        d.dispose()
        return out
    }

    // Bolus / eCarbs / TT / PS / RM all relay through the role-transparent BatchExecutor (master→local / client→round-trip).
    private suspend fun stubBatchPrepared(bolusId: Long, lines: List<ConfirmationLine>) =
        whenever(batchExecutor.prepare(any(), any(), any())).thenReturn(ActionProgress.Prepared(bolusId, lines))

    // QuickWizard + full Wizard relay through the role-transparent WizardExecutor.
    private suspend fun stubWizardPrepared(bolusId: Long, lines: List<ConfirmationLine>) =
        whenever(wizardExecutor.prepare(any(), any())).thenReturn(ActionProgress.Prepared(bolusId, lines))

    @Test fun `bolus precheck relays via BatchExecutor and ships bolusId plus master lines`() = runTest {
        stubBatchPrepared(
            bolusId = 4242L,
            lines = listOf(ConfirmationLine(ConfirmationRole.BOLUS, "Bolus: 1.00 U"), ConfirmationLine(ConfirmationRole.CARBS, "Carbs: 10 g"))
        )

        val confirm = capturedConfirm { sut.handleBolusPreCheck(EventData.ActionBolusPreCheck(insulin = 1.0, carbs = 10)) }

        // Role-transparent BatchExecutor is the single capping/parking authority — driven with a FIXED, immediate bolus.
        val captor = argumentCaptor<List<BatchAction>>()
        verifyBlocking(batchExecutor) { prepare(captor.capture(), any(), any()) }
        val bolus = captor.firstValue.single() as BatchAction.Bolus
        assertThat(bolus.insulin).isEqualTo(1.0)
        assertThat(bolus.carbs).isEqualTo(10)
        assertThat(bolus.carbsTimeOffsetMinutes).isEqualTo(0)
        assertThat(bolus.carbsDurationHours).isEqualTo(0)
        assertThat(bolus.recordOnly).isFalse()
        // Insulin no longer goes through the LOCAL executor — it relays.
        verifyBlocking(wizardBolusExecutor, never()) { prepareBatch(any()) }

        // The round-trip carries only the parked bolusId + the master's lines — no echoed amount, no concatenated message.
        assertThat(confirm.returnCommand).isEqualTo(EventData.ActionBolusConfirmed(4242L))
        assertThat(confirm.message).isEmpty()
        assertThat(confirm.lines).containsExactly(
            EventData.ConfirmActionLine("BOLUS", "Bolus: 1.00 U"),
            EventData.ConfirmActionLine("CARBS", "Carbs: 10 g")
        ).inOrder()
    }

    @Test fun `bolus precheck rejected by the relay becomes a sendError to the watch`() = runTest {
        whenever(batchExecutor.prepare(any(), any(), any())).thenReturn(ActionProgress.Rejected(FailureReason.ExecutionFailed, "boom"))

        val sent = capturedConfirm { sut.handleBolusPreCheck(EventData.ActionBolusPreCheck(insulin = 1.0, carbs = 0)) }

        assertThat(sent.returnCommand).isInstanceOf(EventData.Error::class.java)
        assertThat(sent.message).isEqualTo("boom")
    }

    @Test fun `insulin bolus on a client relays to the master instead of a local refusal`() = runTest {
        whenever(config.AAPSCLIENT).thenReturn(true)
        // Master offline → the role-transparent relay returns NotReachable (the gate moved inside BatchExecutor).
        whenever(batchExecutor.prepare(any(), any(), any())).thenReturn(ActionProgress.Rejected(FailureReason.NotReachable))

        val sent = capturedConfirm { sut.handleBolusPreCheck(EventData.ActionBolusPreCheck(insulin = 1.0, carbs = 0)) }

        // No blanket local refusal any more — the handler attempts the relay; it did NOT go to the local executor.
        verifyBlocking(batchExecutor) { prepare(any(), any(), any()) }
        verifyBlocking(wizardBolusExecutor, never()) { prepareBatch(any()) }
        assertThat(sent.returnCommand).isInstanceOf(EventData.Error::class.java)
    }

    @Test fun `carbs-only bolus also relays through the role-transparent executor`() = runTest {
        whenever(config.AAPSCLIENT).thenReturn(true)
        stubBatchPrepared(7L, lines = listOf(ConfirmationLine(ConfirmationRole.CARBS, "Carbs: 10 g")))

        val confirm = capturedConfirm { sut.handleBolusPreCheck(EventData.ActionBolusPreCheck(insulin = 0.0, carbs = 10)) }

        verifyBlocking(batchExecutor) { prepare(any(), any(), any()) }
        assertThat(confirm.returnCommand).isEqualTo(EventData.ActionBolusConfirmed(7L))
    }

    // --- Watch-on-client feedback: ContactingMaster spinner + deferred confirm (no false success) ---

    @Test fun `bolus precheck on a client emits ContactingMaster and defers the confirm`() = runTest {
        whenever(config.AAPSCLIENT).thenReturn(true)
        stubBatchPrepared(1L, lines = listOf(ConfirmationLine(ConfirmationRole.BOLUS, "Bolus: 1.0 U")))

        val events = captureAll { sut.handleBolusPreCheck(EventData.ActionBolusPreCheck(insulin = 1.0, carbs = 0)) }

        // Spinner emitted before the round-trip; the confirm is deferred so the watch won't flash a false success.
        assertThat(events.any { it is EventData.ContactingMaster }).isTrue()
        assertThat(events.filterIsInstance<EventData.ConfirmAction>().single().deferConfirm).isTrue()
    }

    @Test fun `bolus precheck on a master neither contacts nor defers`() = runTest {
        // config.AAPSCLIENT defaults to false → local instant prepare, success shown locally on the watch.
        stubBatchPrepared(1L, lines = listOf(ConfirmationLine(ConfirmationRole.BOLUS, "Bolus: 1.0 U")))

        val events = captureAll { sut.handleBolusPreCheck(EventData.ActionBolusPreCheck(insulin = 1.0, carbs = 0)) }

        assertThat(events.none { it is EventData.ContactingMaster }).isTrue()
        assertThat(events.filterIsInstance<EventData.ConfirmAction>().single().deferConfirm).isFalse()
    }

    @Test fun `bolus confirm relays the parked id through BatchExecutor commit`() {
        // The confirm runs through the async onEvent chain → timeout-verify (mirrors the other wiring tests).
        // (Applied → RemoteDelivered to the watch is onCommitResult logic, gated on AAPSCLIENT; not captured here.)
        runBlocking { whenever(batchExecutor.commit(any(), any(), any(), any())).thenReturn(ActionProgress.Applied) }

        rxBus.send(EventData.ActionBolusConfirmed(5L))

        verifyBlocking(batchExecutor, timeout(2000)) { commit(eq(5L), any(), any(), any()) }
    }

    @Test fun `ecarbs precheck maps to a fixed carbs-only bolus carrying offset plus duration with an ECarbs confirm`() = runTest {
        stubBatchPrepared(
            99L,
            lines = listOf(ConfirmationLine(ConfirmationRole.CARBS, "Carbs: 20 g"), ConfirmationLine(ConfirmationRole.NORMAL, "Duration: 3 h"))
        )

        val confirm = capturedConfirm { sut.handleECarbsPreCheck(EventData.ActionECarbsPreCheck(carbs = 20, carbsTimeShift = 15, duration = 3)) }

        val captor = argumentCaptor<List<BatchAction>>()
        verifyBlocking(batchExecutor) { prepare(captor.capture(), any(), any()) }
        val bolus = captor.firstValue.single() as BatchAction.Bolus
        assertThat(bolus.insulin).isEqualTo(0.0)
        assertThat(bolus.carbs).isEqualTo(20)
        assertThat(bolus.carbsTimeOffsetMinutes).isEqualTo(15)
        assertThat(bolus.carbsDurationHours).isEqualTo(3)

        assertThat(confirm.returnCommand).isEqualTo(EventData.ActionECarbsConfirmed(99L))
        assertThat(confirm.lines).containsExactly(
            EventData.ConfirmActionLine("CARBS", "Carbs: 20 g"),
            EventData.ConfirmActionLine("NORMAL", "Duration: 3 h")
        ).inOrder()
    }

    // --- Temp target (fully unified onto the shared prepareBatch/confirm + applyTempTarget path) -----------

    @Test fun `manual temp target relays a TempTarget batch and ships its bolusId + master lines`() = runTest {
        whenever(profileFunction.getUnits()).thenReturn(GlucoseUnit.MGDL)
        stubBatchPrepared(555L, lines = listOf(ConfirmationLine(ConfirmationRole.NORMAL, "Temporary target: 100 – 120 mg/dl (30 mins)")))

        val confirm = capturedConfirm {
            sut.handleTempTargetPreCheck(
                EventData.ActionTempTargetPreCheck(EventData.ActionTempTargetPreCheck.TempTargetCommand.MANUAL, isMgdl = true, duration = 30, low = 100.0, high = 120.0)
            )
        }

        // Routed through the role-transparent BatchExecutor (mg/dL range, reason WEAR) → the MASTER applies it via
        // applyTempTarget (local on master, BatchPrepare round-trip on a client); the wear ✓ commits by bolusId.
        verifyBlocking(batchExecutor) { prepare(eq(listOf(BatchAction.TempTarget(TT.Reason.WEAR.text, 100.0, 120.0, 30, 0))), any(), any()) }
        assertThat(confirm.returnCommand).isEqualTo(EventData.ActionTempTargetConfirmed(555L))
        assertThat(confirm.lines).containsExactly(EventData.ConfirmActionLine("NORMAL", "Temporary target: 100 – 120 mg/dl (30 mins)"))
    }

    @Test fun `cancel temp target relays a zero-duration TempTarget batch`() = runTest {
        whenever(profileFunction.getUnits()).thenReturn(GlucoseUnit.MGDL)
        stubBatchPrepared(7L, lines = listOf(ConfirmationLine(ConfirmationRole.NORMAL, "Temporary target: Cancel")))

        val confirm = capturedConfirm {
            sut.handleTempTargetPreCheck(EventData.ActionTempTargetPreCheck(EventData.ActionTempTargetPreCheck.TempTargetCommand.CANCEL))
        }

        verifyBlocking(batchExecutor) { prepare(eq(listOf(BatchAction.TempTarget(TT.Reason.WEAR.text, 0.0, 0.0, 0, 0))), any(), any()) }
        assertThat(confirm.returnCommand).isEqualTo(EventData.ActionTempTargetConfirmed(7L))
    }

    @Test fun `manual temp target with mismatched units sends an error, not a relay`() = runTest {
        whenever(profileFunction.getUnits()).thenReturn(GlucoseUnit.MGDL) // profile mg/dL but the action says mmol

        val sent = capturedConfirm {
            sut.handleTempTargetPreCheck(
                EventData.ActionTempTargetPreCheck(EventData.ActionTempTargetPreCheck.TempTargetCommand.MANUAL, isMgdl = false, duration = 30, low = 5.5, high = 6.5)
            )
        }

        verifyBlocking(batchExecutor, never()) { prepare(any(), any(), any()) }
        assertThat(sent.returnCommand).isInstanceOf(EventData.Error::class.java)
    }

    // --- Profile switch (unified onto the shared prepareBatch/confirm + applyProfileSwitch path) -----------

    @Test fun `profile switch precheck relays a ProfileSwitch batch and ships its bolusId + master lines`() = runTest {
        stubBatchPrepared(321L, lines = listOf(ConfirmationLine(ConfirmationRole.PRIMARY, "Profile: Test")))

        val confirm = capturedConfirm {
            sut.handleProfileSwitchPreCheck(EventData.ActionProfileSwitchPreCheck(timeShift = 2, percentage = 120, duration = 60))
        }

        // The wear command maps to a single ProfileSwitch batch action; the MASTER validates/parks and the ✓ commits by bolusId.
        verifyBlocking(batchExecutor) { prepare(eq(listOf(BatchAction.ProfileSwitch(120, 2, 60))), any(), any()) }
        assertThat(confirm.returnCommand).isEqualTo(EventData.ActionProfileSwitchConfirmed(321L))
        assertThat(confirm.lines).containsExactly(EventData.ConfirmActionLine("PRIMARY", "Profile: Test"))
    }

    @Test fun `profile switch precheck rejected by the relay becomes a sendError to the watch`() = runTest {
        whenever(batchExecutor.prepare(any(), any(), any())).thenReturn(ActionProgress.Rejected(FailureReason.ExecutionFailed, "no profile"))

        val sent = capturedConfirm { sut.handleProfileSwitchPreCheck(EventData.ActionProfileSwitchPreCheck(timeShift = 0, percentage = 100, duration = 30)) }

        assertThat(sent.returnCommand).isInstanceOf(EventData.Error::class.java)
        assertThat(sent.message).isEqualTo("no profile")
    }

    // --- Running mode (unified onto the shared negotiate → prepareBatch/confirm + applyRunningMode path) ---

    /** Negotiate the available modes and return the master-issued nonce + the wear-tile list (to pick an index). */
    private suspend fun negotiateRunningModes(allowed: List<RM.Mode>): EventData.RunningModeList {
        whenever(pump.pumpDescription).thenReturn(PumpDescription())
        whenever(profileFunction.isProfileValid(any())).thenReturn(true)
        whenever(loop.allowedNextModes()).thenReturn(allowed)
        var list: EventData.RunningModeList? = null
        val d = rxBus.toObservable(EventMobileToWear::class.java).subscribe { (it.payload as? EventData.RunningModeList)?.let { l -> list = l } }
        sut.handleAvailableRunningModes()
        d.dispose()
        return list!!
    }

    @Test fun `running mode selected relays a RunningMode batch and ships its bolusId + master lines`() = runTest {
        val list = negotiateRunningModes(listOf(RM.Mode.CLOSED_LOOP, RM.Mode.OPEN_LOOP))
        val idx = list.states.indexOfFirst { it.state == AvailableRunningMode.RunningMode.LOOP_CLOSED }
        stubBatchPrepared(555L, lines = listOf(ConfirmationLine(ConfirmationRole.PRIMARY, "Running mode: Closed Loop")))

        val confirm = capturedConfirm { sut.handleRunningModeSelected(EventData.RunningModeSelected(list.timeStamp, idx, null)) }

        // The selected tile maps to a single RunningMode batch action; the MASTER re-validates/parks and the ✓ commits by bolusId.
        verifyBlocking(batchExecutor) { prepare(eq(listOf(BatchAction.RunningMode(RM.Mode.CLOSED_LOOP, 0))), any(), any()) }
        assertThat(confirm.returnCommand).isEqualTo(EventData.RunningModeConfirmed(555L))
        assertThat(confirm.lines).containsExactly(EventData.ConfirmActionLine("PRIMARY", "Running mode: Closed Loop"))
    }

    @Test fun `running mode selected with a stale nonce is rejected before the relay`() = runTest {
        negotiateRunningModes(listOf(RM.Mode.CLOSED_LOOP))

        val sent = capturedConfirm { sut.handleRunningModeSelected(EventData.RunningModeSelected(timeStamp = 1L, index = 0, duration = null)) }

        assertThat(sent.returnCommand).isInstanceOf(EventData.Error::class.java)
        verifyBlocking(batchExecutor, never()) { prepare(any(), any(), any()) }
    }

    @Test fun `running mode confirmed commits the parked bolusId via the relay`() = runTest {
        // The post-confirm tile refresh short-circuits when no profile is valid (no pumpDescription stub needed).
        whenever(batchExecutor.commit(any(), any(), any(), any())).thenReturn(ActionProgress.Applied)
        whenever(profileFunction.isProfileValid(any())).thenReturn(false)

        sut.handleRunningModeConfirmed(EventData.RunningModeConfirmed(777L))

        verifyBlocking(batchExecutor) { commit(eq(777L), eq(Sources.Wear), any(), any()) }
    }

    // --- Full wizard (unified onto the shared prepareWizard/confirm + master-authored lines) ---------------

    @Test fun `wizard precheck relays via WizardExecutor and ships its bolusId + master lines`() = runTest {
        // The full watch wizard recomputes through the role-transparent WizardExecutor (master→local prepareWizard /
        // client→WizardPrepare round-trip), rendering the master-authored lines (no bespoke WizardResultActivity).
        val ads = mock<AutosensDataStore>()
        whenever(iobCobCalculator.ads).thenReturn(ads)
        whenever(ads.actualBg()).thenReturn(mock()) // BG present (the caller supplies inputs.bg; null-checked here)
        whenever(profileFunction.getUnits()).thenReturn(GlucoseUnit.MGDL)
        whenever(preferences.get(any<BooleanKey>())).thenReturn(true) // WearWizard* toggles
        stubWizardPrepared(555L, lines = listOf(ConfirmationLine(ConfirmationRole.BOLUS, "Bolus: 1.50 U")))

        val confirm = capturedConfirm { sut.handleWizardPreCheck(EventData.ActionWizardPreCheck(carbs = 30, percentage = 100)) }

        verifyBlocking(wizardExecutor) { prepare(any<WizardExecutor.WizardSource.Manual>(), any()) }
        assertThat(confirm.returnCommand).isEqualTo(EventData.ActionWizardConfirmed(555L))
        assertThat(confirm.lines).containsExactly(EventData.ConfirmActionLine("BOLUS", "Bolus: 1.50 U"))
    }

    @Test fun `wizard precheck with no master BG sends an error without reaching the relay`() = runTest {
        val ads = mock<AutosensDataStore>()
        whenever(iobCobCalculator.ads).thenReturn(ads)
        whenever(ads.actualBg()).thenReturn(null)

        val sent = capturedConfirm { sut.handleWizardPreCheck(EventData.ActionWizardPreCheck(carbs = 0, percentage = 100)) }

        assertThat(sent.returnCommand).isInstanceOf(EventData.Error::class.java)
        verifyBlocking(wizardExecutor, never()) { prepare(any(), any()) }
    }

    // --- QuickWizard tile modes (branch on entry.mode() exactly like the phone's MainViewModel) -----------
    //
    // The wear QuickWizard handler used to ALWAYS recompute via WizardExecutor, so a fixed INSULIN button
    // computed a wizard dose and a CARBS-only button could deliver correction insulin. It now branches:
    // INSULIN/CARBS → fixed BatchExecutor bolus (ActionBolusConfirmed); WIZARD → recompute (ActionWizardConfirmed).

    /** A mocked synced [QuickWizardEntry] resolved by [guid], with the given mode + fixed amounts. */
    private fun stubQuickWizard(guid: String, mode: QuickWizardMode, insulin: Double = 0.0, carbs: Int = 0, text: String = "QW"): QuickWizardEntry {
        val entry = mock<QuickWizardEntry>()
        whenever(entry.mode()).thenReturn(mode)
        whenever(entry.insulin()).thenReturn(insulin)
        whenever(entry.carbs()).thenReturn(carbs)
        whenever(entry.buttonText()).thenReturn(text)
        whenever(quickWizard.get(guid)).thenReturn(entry)
        return entry
    }

    @Test fun `quick wizard INSULIN mode relays a fixed insulin batch with an ActionBolusConfirmed`() = runTest {
        stubQuickWizard("g1", QuickWizardMode.INSULIN, insulin = 1.5, text = "Bolus")
        stubBatchPrepared(11L, lines = listOf(ConfirmationLine(ConfirmationRole.BOLUS, "Bolus: 1.50 U")))

        val confirm = capturedConfirm { sut.handleQuickWizardPreCheck(EventData.ActionQuickWizardPreCheck("g1")) }

        val captor = argumentCaptor<List<BatchAction>>()
        verifyBlocking(batchExecutor) { prepare(captor.capture(), any(), any()) }
        val bolus = captor.firstValue.single() as BatchAction.Bolus
        assertThat(bolus.insulin).isEqualTo(1.5)
        assertThat(bolus.carbs).isEqualTo(0)
        assertThat(bolus.quickWizardGuid).isEqualTo("g1") // the MASTER marks the entry used on commit via this guid (SOT)
        // A fixed button must NOT recompute the wizard (the bug).
        verifyBlocking(wizardExecutor, never()) { prepare(any(), any()) }
        assertThat(confirm.returnCommand).isEqualTo(EventData.ActionBolusConfirmed(11L))
    }

    @Test fun `quick wizard CARBS mode relays a fixed carbs batch with an ActionBolusConfirmed`() = runTest {
        stubQuickWizard("g2", QuickWizardMode.CARBS, carbs = 20, text = "Carbs")
        stubBatchPrepared(12L, lines = listOf(ConfirmationLine(ConfirmationRole.CARBS, "Carbs: 20 g")))

        val confirm = capturedConfirm { sut.handleQuickWizardPreCheck(EventData.ActionQuickWizardPreCheck("g2")) }

        val captor = argumentCaptor<List<BatchAction>>()
        verifyBlocking(batchExecutor) { prepare(captor.capture(), any(), any()) }
        val bolus = captor.firstValue.single() as BatchAction.Bolus
        // Carbs-only: zero insulin (the wizard recompute could have injected a correction bolus).
        assertThat(bolus.insulin).isEqualTo(0.0)
        assertThat(bolus.carbs).isEqualTo(20)
        assertThat(bolus.quickWizardGuid).isEqualTo("g2") // the MASTER marks the entry used on commit via this guid (SOT)
        verifyBlocking(wizardExecutor, never()) { prepare(any(), any()) }
        assertThat(confirm.returnCommand).isEqualTo(EventData.ActionBolusConfirmed(12L))
    }

    @Test fun `quick wizard WIZARD mode recomputes via WizardExecutor with an ActionWizardConfirmed`() = runTest {
        stubQuickWizard("g3", QuickWizardMode.WIZARD, carbs = 30, text = "Wizard")
        stubWizardPrepared(13L, lines = listOf(ConfirmationLine(ConfirmationRole.BOLUS, "Bolus: 2.00 U")))

        val confirm = capturedConfirm { sut.handleQuickWizardPreCheck(EventData.ActionQuickWizardPreCheck("g3")) }

        verifyBlocking(wizardExecutor) { prepare(any<WizardExecutor.WizardSource.QuickWizard>(), any()) }
        verifyBlocking(batchExecutor, never()) { prepare(any(), any(), any()) }
        assertThat(confirm.returnCommand).isEqualTo(EventData.ActionWizardConfirmed(13L))
    }

    @Test fun `quick wizard with an unknown guid falls through to the wizard path for a proper not-available error`() = runTest {
        // quickWizard.get(guid) returns null (entry deleted/unsynced) → no mode to branch on; route to WizardExecutor,
        // which ships a "quick wizard not available" error rather than silently dropping the tap.
        whenever(quickWizard.get("gone")).thenReturn(null)
        stubWizardPrepared(99L, lines = listOf(ConfirmationLine(ConfirmationRole.BOLUS, "Bolus: 1.00 U")))

        val confirm = capturedConfirm { sut.handleQuickWizardPreCheck(EventData.ActionQuickWizardPreCheck("gone")) }

        verifyBlocking(wizardExecutor) { prepare(any<WizardExecutor.WizardSource.QuickWizard>(), any()) }
        verifyBlocking(batchExecutor, never()) { prepare(any(), any(), any()) }
        assertThat(confirm.returnCommand).isEqualTo(EventData.ActionWizardConfirmed(99L))
    }

    @Test fun `quick wizard fixed-mode tags the batch with the guid and never marks the entry locally`() = runTest {
        // SOT: the guid travels in the batch so the MASTER marks the entry used in confirm(); this device must NOT write
        // the synced QuickWizard pref itself (on a client that pushes it back over the round-trip → "Update settings …
        // Another action is already in progress").
        val entry = stubQuickWizard("g4", QuickWizardMode.INSULIN, insulin = 1.0, text = "Bolus")
        stubBatchPrepared(14L, lines = listOf(ConfirmationLine(ConfirmationRole.BOLUS, "Bolus: 1.00 U")))
        whenever(batchExecutor.commit(any(), any(), any(), any())).thenReturn(ActionProgress.Applied)

        val captor = argumentCaptor<List<BatchAction>>()
        capturedConfirm { sut.handleQuickWizardPreCheck(EventData.ActionQuickWizardPreCheck("g4")) }
        verifyBlocking(batchExecutor) { prepare(captor.capture(), any(), any()) }
        rxBus.send(EventData.ActionBolusConfirmed(14L))
        verifyBlocking(batchExecutor, timeout(2000)) { commit(eq(14L), any(), any(), any()) } // the commit handler ran

        assertThat((captor.firstValue.single() as BatchAction.Bolus).quickWizardGuid).isEqualTo("g4")
        verify(entry, never()).markAsUsed() // …and it did NOT mark locally — the master does, via the guid
    }

    // --- Subscription wiring (the onEvent / onEventSync helpers actually register each type) --------------
    //
    // The tests above drive the handler methods directly. These two instead post the event onto the real
    // RxBus and assert the handler runs — locking in that the helper-based subscriptions in init {} are
    // wired (a dropped subscription is a mechanical refactor error the compiler can't catch).

    @Test fun `onEventSync dispatches a posted SnoozeAlert to its handler`() {
        // onEventSync subscribes directly; the trampoline io scheduler runs it inline.
        rxBus.send(EventData.SnoozeAlert(0L))

        verify(uiInteraction).stopAlarm("Muted from wear")
    }

    @Test fun `onEvent dispatches a posted ActionBolusPreCheck to the suspend handler`() {
        // onEvent wraps the handler in rxCompletable, which runs the coroutine off the posting thread —
        // hence a timeout verify rather than a synchronous capture. The unstubbed prepare() returns the
        // mockito default; we only assert it was reached.
        rxBus.send(EventData.ActionBolusPreCheck(insulin = 1.0, carbs = 10))

        verifyBlocking(batchExecutor, timeout(2000)) { prepare(any(), any(), any()) }
    }

    @Test fun `onEvent dispatches a posted ActionProfileSwitchPreCheck to the handler`() {
        rxBus.send(EventData.ActionProfileSwitchPreCheck(timeShift = 0, percentage = 110, duration = 30))

        verifyBlocking(batchExecutor, timeout(2000)) { prepare(any(), any(), any()) }
    }
}

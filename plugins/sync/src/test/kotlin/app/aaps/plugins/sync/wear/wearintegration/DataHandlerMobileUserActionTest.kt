package app.aaps.plugins.sync.wear.wearintegration

import app.aaps.core.data.model.RM
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.automation.Automation
import app.aaps.core.interfaces.automation.AutomationEvent
import app.aaps.core.interfaces.bolus.BatchExecutor
import app.aaps.core.interfaces.bolus.WizardBolusExecutor
import app.aaps.core.interfaces.bolus.WizardExecutor
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.maintenance.ImportExportPrefs
import app.aaps.core.interfaces.nsclient.ProcessedDeviceStatusData
import app.aaps.core.interfaces.profile.EffectiveProfile
import app.aaps.core.interfaces.pump.PumpStatusProvider
import app.aaps.core.interfaces.pump.PumpWithConcentration
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.receivers.ReceiverStatusStore
import app.aaps.core.interfaces.rx.weardata.EventData
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.TrendCalculator
import app.aaps.core.objects.runningMode.RunningModeGuard
import app.aaps.core.objects.wizard.QuickWizard
import app.aaps.plugins.sync.R
import app.aaps.shared.tests.TestBaseWithProfile
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.whenever

/**
 * Focused tests for the wear `UserAction` confirm handler in [DataHandlerMobile].
 *
 * Locks in two recently-fixed behaviors that have no UI assertion path:
 * - Every failure mode of [DataHandlerMobile.handleUserActionConfirmed] must `sendError` so the
 *   watch UI doesn't silently dismiss the confirm dialog (the chain previously had no `else`).
 * - After the suspending `canRun()` returns true, the handler must re-verify the same event
 *   instance is still in the cache before calling `processEvent`. A concurrent removal during
 *   the canRun() suspension would otherwise let an orphan event execute.
 *
 * `handleUserActionConfirmed` was promoted to `internal` so this test can drive it directly
 * without scaffolding the full RxBus wiring around DataHandlerMobile's 32 constructor deps.
 */
class DataHandlerMobileUserActionTest : TestBaseWithProfile() {

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
    @Mock private lateinit var event: AutomationEvent
    @Mock private lateinit var profile: EffectiveProfile

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
        // @Inject lateinit field — Dagger is not running, set manually.
        sut.automation = automation
        // Happy-path gating preconditions. Suspend mocks wrapped in runBlocking — matches the
        // project convention used in SmsCommunicatorPluginTest etc.
        whenever(activePlugin.activePump).thenReturn(pump)
        whenever(pump.isInitialized()).thenReturn(true)
        runBlocking {
            whenever(loop.runningMode()).thenReturn(RM.Mode.OPEN_LOOP)
            whenever(profileFunction.getProfile()).thenReturn(profile)
        }
        whenever(rh.gs(any<Int>(), any<String>())).thenReturn("err")
        whenever(rh.gs(any<Int>())).thenReturn("err")
        whenever(event.id).thenReturn("evt-uuid")
        whenever(event.userAction).thenReturn(true)
        whenever(event.isEnabled).thenReturn(true)
    }

    /** All gating preconditions OK and canRun()==true and re-fetch matches → processEvent runs, no error sent. */
    @Test fun `confirmed runs processEvent on the happy path`() = runTest {
        whenever(automation.findEventById("evt-uuid")).thenReturn(event)
        whenever(event.canRun()).thenReturn(true)

        sut.handleUserActionConfirmed(EventData.ActionUserActionConfirmed("evt-uuid", "title"))

        verifyBlocking(automation) { processEvent(event) }
        verify(rh, never()).gs(R.string.user_action_not_available, "title")
    }

    /** Event missing → sendError fires, processEvent NOT called. Was silently dropped before fix #3. */
    @Test fun `confirmed sends error when event not found`() = runTest {
        whenever(automation.findEventById("missing")).thenReturn(null)

        sut.handleUserActionConfirmed(EventData.ActionUserActionConfirmed("missing", "title"))

        verifyBlocking(automation, never()) { processEvent(any()) }
        verify(rh).gs(R.string.user_action_not_available, "title")
    }

    /** Event exists but userAction flag flipped off mid-flight → sendError fires. */
    @Test fun `confirmed sends error when userAction is false`() = runTest {
        whenever(automation.findEventById("evt-uuid")).thenReturn(event)
        whenever(event.userAction).thenReturn(false)

        sut.handleUserActionConfirmed(EventData.ActionUserActionConfirmed("evt-uuid", "title"))

        verifyBlocking(automation, never()) { processEvent(any()) }
        verify(rh).gs(R.string.user_action_not_available, "title")
    }

    /** Event exists and userAction=true but disabled → sendError fires. */
    @Test fun `confirmed sends error when event is disabled`() = runTest {
        whenever(automation.findEventById("evt-uuid")).thenReturn(event)
        whenever(event.isEnabled).thenReturn(false)

        sut.handleUserActionConfirmed(EventData.ActionUserActionConfirmed("evt-uuid", "title"))

        verifyBlocking(automation, never()) { processEvent(any()) }
        verify(rh).gs(R.string.user_action_not_available, "title")
    }

    /** canRun() returns false → sendError fires, processEvent NOT called. */
    @Test fun `confirmed sends error when canRun is false`() = runTest {
        whenever(automation.findEventById("evt-uuid")).thenReturn(event)
        whenever(event.canRun()).thenReturn(false)

        sut.handleUserActionConfirmed(EventData.ActionUserActionConfirmed("evt-uuid", "title"))

        verifyBlocking(automation, never()) { processEvent(any()) }
        verify(rh).gs(R.string.user_action_not_available, "title")
    }

    /**
     * canRun() returns true but the post-canRun re-fetch returns a DIFFERENT instance (event was
     * removed and re-added during the suspension) → processEvent must NOT fire, sendError must.
     * Regression guard for the canRun race fix.
     */
    @Test fun `confirmed refuses to process when event identity changes during canRun`() = runTest {
        val replacement: AutomationEvent = org.mockito.kotlin.mock()
        whenever(replacement.id).thenReturn("evt-uuid")
        whenever(replacement.userAction).thenReturn(true)
        whenever(replacement.isEnabled).thenReturn(true)
        // First call returns the original event (initial lookup); second returns a different
        // instance (post-canRun identity re-verify) — same id, different object.
        whenever(automation.findEventById("evt-uuid"))
            .thenReturn(event)
            .thenReturn(replacement)
        whenever(event.canRun()).thenReturn(true)

        sut.handleUserActionConfirmed(EventData.ActionUserActionConfirmed("evt-uuid", "title"))

        verifyBlocking(automation, never()) { processEvent(any()) }
        verify(rh).gs(R.string.user_action_not_available, "title")
    }

    /** Same race re-check but the re-fetch returns null (event removed during canRun). */
    @Test fun `confirmed refuses to process when event removed during canRun`() = runTest {
        whenever(automation.findEventById("evt-uuid"))
            .thenReturn(event)
            .thenReturn(null)
        whenever(event.canRun()).thenReturn(true)

        sut.handleUserActionConfirmed(EventData.ActionUserActionConfirmed("evt-uuid", "title"))

        verifyBlocking(automation, never()) { processEvent(any()) }
        verify(rh).gs(R.string.user_action_not_available, "title")
    }

    /** Pump not initialized → outer guard fails, lookup not attempted. */
    @Test fun `confirmed sends pump-unavailable error when pump not initialized`() = runTest {
        whenever(pump.isInitialized()).thenReturn(false)

        sut.handleUserActionConfirmed(EventData.ActionUserActionConfirmed("evt-uuid", "title"))

        verify(automation, never()).findEventById(any())
        verifyBlocking(automation, never()) { processEvent(any()) }
    }
}

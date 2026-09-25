package app.aaps.plugins.sync.wear.wearintegration

import app.aaps.core.data.model.ActiveSceneState
import app.aaps.core.data.model.Scene
import app.aaps.core.data.model.SceneLifecycle
import app.aaps.core.data.ui.ConfirmationRole
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.automation.Automation
import app.aaps.core.interfaces.bolus.BatchExecutor
import app.aaps.core.interfaces.bolus.WizardBolusExecutor
import app.aaps.core.interfaces.bolus.WizardExecutor
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.maintenance.ImportExportPrefs
import app.aaps.core.interfaces.nsclient.ProcessedDeviceStatusData
import app.aaps.core.interfaces.pump.PumpStatusProvider
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.receivers.ReceiverStatusStore
import app.aaps.core.interfaces.rx.events.EventMobileToWear
import app.aaps.core.interfaces.rx.weardata.EventData
import app.aaps.core.interfaces.scenes.ActiveSceneSync
import app.aaps.core.interfaces.scenes.SceneActions
import app.aaps.core.interfaces.scenes.SceneAutomationApi
import app.aaps.core.interfaces.scenes.SceneChainResolver
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.TrendCalculator
import app.aaps.core.objects.runningMode.RunningModeGuard
import app.aaps.core.objects.wizard.QuickWizard
import app.aaps.core.ui.CoreUiStrings
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.anyVararg
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.whenever

/**
 * The scene stop confirmation the phone builds for the watch, and the active-scene state it
 * sends for the tile. Both grew a follow-up: the tile offers "Skip to" beside "End" when the
 * active scene has a follow-up that can start, and the confirm says which of the two the wearer
 * is about to do.
 *
 * The handler is `internal` so it can be driven here without the RxBus wiring; the emitted
 * [EventMobileToWear] is captured off the real bus, as in [DataHandlerMobileWearBolusTest].
 */
class DataHandlerMobileSceneTest : TestBaseWithProfile() {

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
    @Mock private lateinit var automation: Automation
    @Mock private lateinit var scenes: SceneAutomationApi
    @Mock private lateinit var sceneActions: SceneActions
    @Mock private lateinit var activeSceneSync: ActiveSceneSync
    @Mock private lateinit var sceneChainResolver: SceneChainResolver

    private lateinit var sut: DataHandlerMobile

    private val sleep = Scene(id = "sleep", name = "Sleep")
    private val wakeUp = Scene(id = "wake", name = "Wake up")

    @BeforeEach
    fun prepare() {
        sut = DataHandlerMobile(
            context, rxBus, aapsLogger, rh, preferences, config,
            iobCobCalculator, processedTbrEbData, smbGlucoseStatusProvider, profileFunction, profileUtil,
            loop, processedDeviceStatusData, receiverStatusStore, quickWizard, trendCalculator, dateUtil,
            constraintsChecker, activePlugin, commandQueue, fabricPrivacy, uiInteraction,
            persistenceLayer, importExportPrefs, decimalFormatter, pumpStatusProvider,
            ch, runningModeGuard, wizardBolusExecutor, batchExecutor, wizardExecutor
        )
        sut.automation = automation
        sut.scenes = scenes
        sut.sceneActions = sceneActions
        sut.activeSceneSync = activeSceneSync
        sut.sceneChainResolver = sceneChainResolver
        whenever(rh.gs(CoreUiStrings.scenes)).thenReturn("Scenes")
        whenever(rh.gs(CoreUiStrings.scene_ended)).thenReturn("Scene ended")
        whenever(rh.gs(CoreUiStrings.error)).thenReturn("Error")
        whenever(rh.gs(CoreUiStrings.scene_end_active)).thenReturn("End active scene")
        whenever(rh.gs(CoreUiStrings.scene_skip_to_label)).thenReturn("Skip to")
        whenever(rh.gs(eq(CoreUiStrings.scene_end_follow_up_not_started), anyVararg())).thenReturn("Follow-up Wake up will not start")
        whenever(scenes.hasSceneToStop()).thenReturn(true)
    }

    private fun activeScene(scene: Scene = sleep, lifecycle: SceneLifecycle = SceneLifecycle.ACTIVE) {
        whenever(activeSceneSync.getActiveState()).thenReturn(ActiveSceneState(scene = scene, activatedAt = 1_000L, durationMs = 3_600_000L, lifecycle = lifecycle))
    }

    /** Captures what the handler ships to the watch; UNDISPATCHED because RxBus has no replay. */
    private fun collectMobileToWear(onPayload: (EventData) -> Unit): Job =
        CoroutineScope(Dispatchers.Unconfined).launch(start = CoroutineStart.UNDISPATCHED) {
            rxBus.toFlow(EventMobileToWear::class).collect { onPayload(it.payload) }
        }

    private inline fun captured(block: () -> Unit): EventData {
        var captured: EventData? = null
        val job = collectMobileToWear { captured = it }
        block()
        job.cancel()
        return captured!!
    }

    private fun EventData.ConfirmAction.roles() = lines.map { it.role }
    private fun EventData.ConfirmAction.texts() = lines.map { it.text }

    @Test
    fun `nothing to stop becomes an error to the watch`() = runTest {
        whenever(scenes.hasSceneToStop()).thenReturn(false)
        val sent = captured { sut.handleSceneStopPreCheck(EventData.ActionSceneStopPreCheck()) } as EventData.ConfirmAction
        assertThat(sent.returnCommand).isInstanceOf(EventData.Error::class.java)
    }

    @Test
    fun `end confirm names the scene and, without a follow-up, says only end`() = runTest {
        activeScene()
        whenever(sceneChainResolver.resolveRunnableChainTarget(any())).thenReturn(null)
        val sent = captured { sut.handleSceneStopPreCheck(EventData.ActionSceneStopPreCheck()) } as EventData.ConfirmAction
        assertThat(sent.roles()).containsExactly(ConfirmationRole.NORMAL.name, ConfirmationRole.SCENE.name).inOrder()
        assertThat(sent.texts()).containsExactly("End active scene", "Sleep").inOrder()
        assertThat(sent.returnCommand).isEqualTo(EventData.ActionSceneStopConfirmed(triggerChain = false))
    }

    @Test
    fun `end confirm says which follow-up will not start`() = runTest {
        activeScene()
        whenever(sceneChainResolver.resolveRunnableChainTarget(any())).thenReturn(wakeUp)
        val sent = captured { sut.handleSceneStopPreCheck(EventData.ActionSceneStopPreCheck()) } as EventData.ConfirmAction
        assertThat(sent.roles()).containsExactly(ConfirmationRole.NORMAL.name, ConfirmationRole.SCENE.name, ConfirmationRole.INFO.name).inOrder()
        assertThat(sent.texts()).containsExactly("End active scene", "Sleep", "Follow-up Wake up will not start").inOrder()
        assertThat(sent.returnCommand).isEqualTo(EventData.ActionSceneStopConfirmed(triggerChain = false))
    }

    @Test
    fun `skip confirm names both scenes and carries the chain flag back`() = runTest {
        activeScene()
        whenever(sceneChainResolver.resolveRunnableChainTarget(any())).thenReturn(wakeUp)
        val sent = captured { sut.handleSceneStopPreCheck(EventData.ActionSceneStopPreCheck(triggerChain = true)) } as EventData.ConfirmAction
        assertThat(sent.roles()).containsExactly(ConfirmationRole.NORMAL.name, ConfirmationRole.SCENE.name, ConfirmationRole.NORMAL.name, ConfirmationRole.SCENE.name).inOrder()
        assertThat(sent.texts()).containsExactly("End active scene", "Sleep", "Skip to", "Wake up").inOrder()
        assertThat(sent.returnCommand).isEqualTo(EventData.ActionSceneStopConfirmed(triggerChain = true))
    }

    @Test
    fun `skip asked for a follow-up that is gone falls back to a plain end`() = runTest {
        // The tile offered Skip; the follow-up was disabled or deleted before the wearer tapped
        activeScene()
        whenever(sceneChainResolver.resolveRunnableChainTarget(any())).thenReturn(null)
        val sent = captured { sut.handleSceneStopPreCheck(EventData.ActionSceneStopPreCheck(triggerChain = true)) } as EventData.ConfirmAction
        assertThat(sent.texts()).containsExactly("End active scene", "Sleep").inOrder()
        assertThat(sent.returnCommand).isEqualTo(EventData.ActionSceneStopConfirmed(triggerChain = false))
    }

    @Test
    fun `a client resolves the follow-up from the catalog and defers the confirm`() = runTest {
        whenever(config.AAPSCLIENT).thenReturn(true)
        activeScene()
        whenever(sceneChainResolver.resolveCatalogChainTarget(any())).thenReturn(wakeUp)
        val sent = captured { sut.handleSceneStopPreCheck(EventData.ActionSceneStopPreCheck(triggerChain = true)) } as EventData.ConfirmAction
        assertThat(sent.texts()).containsExactly("End active scene", "Sleep", "Skip to", "Wake up").inOrder()
        assertThat(sent.deferConfirm).isTrue()
        verifyBlocking(sceneChainResolver, never()) { resolveRunnableChainTarget(any()) }
    }

    @Test
    fun `active scene state carries name, end and follow-up for the tile`() = runTest {
        activeScene()
        whenever(sceneChainResolver.resolveRunnableChainTarget(any())).thenReturn(wakeUp)
        val sent = captured { sut.sendActiveSceneState(active = true) }
        assertThat(sent).isEqualTo(EventData.ActiveSceneState(active = true, sceneName = "Sleep", endTime = 3_601_000L, chainTargetName = "Wake up"))
    }

    @Test
    fun `an expired scene keeps its name but offers no follow-up`() = runTest {
        // The master's expiry already dealt with the follow-up; the banner can still be ended from the watch
        activeScene(lifecycle = SceneLifecycle.EXPIRED)
        val sent = captured { sut.sendActiveSceneState(active = true) }
        assertThat(sent).isEqualTo(EventData.ActiveSceneState(active = true, sceneName = "Sleep", endTime = 3_601_000L, chainTargetName = null))
        verifyBlocking(sceneChainResolver, never()) { resolveRunnableChainTarget(any()) }
    }

    @Test
    fun `no active scene sends the bare flag`() = runTest {
        val sent = captured { sut.sendActiveSceneState(active = false) }
        assertThat(sent).isEqualTo(EventData.ActiveSceneState(active = false))
        verify(activeSceneSync, never()).getActiveState()
    }
}

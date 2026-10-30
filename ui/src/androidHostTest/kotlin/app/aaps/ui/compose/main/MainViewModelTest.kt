package app.aaps.ui.compose.main

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import app.aaps.core.data.model.ActiveSceneState
import app.aaps.core.data.model.Scene
import app.aaps.core.data.model.SceneEndAction
import app.aaps.core.interfaces.alerts.EatReminder
import app.aaps.core.interfaces.alerts.EatReminderAction
import app.aaps.core.interfaces.alerts.EatReminderState
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.automation.Automation
import app.aaps.core.interfaces.bolus.BatchExecutor
import app.aaps.core.interfaces.bolus.WizardExecutor
import app.aaps.core.interfaces.clientcontrol.ActionProgress
import app.aaps.core.interfaces.clientcontrol.ClientControlActionDispatcher
import app.aaps.core.interfaces.clientcontrol.FailureReason
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.configuration.InitProgress
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.overview.graph.OverviewDataCache
import app.aaps.core.interfaces.overview.graph.ProfileDisplayData
import app.aaps.core.interfaces.overview.graph.RunningModeDisplayData
import app.aaps.core.interfaces.overview.graph.TbrDisplayData
import app.aaps.core.interfaces.overview.graph.TempTargetDisplayData
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.protection.ProtectionCheck
import app.aaps.core.interfaces.protection.ProtectionResult
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.Event
import app.aaps.core.interfaces.rx.events.EventShowDialog
import app.aaps.core.interfaces.scenes.ActiveSceneSync
import app.aaps.core.interfaces.scenes.SceneActions
import app.aaps.core.interfaces.scenes.SceneChainResolver
import app.aaps.core.interfaces.scenes.SceneStore
import app.aaps.core.interfaces.sync.NsClient
import app.aaps.core.interfaces.ui.UrlOpener
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.StringNonKey
import app.aaps.core.keys.interfaces.AppPlatform
import app.aaps.core.keys.interfaces.ClientRole
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.keys.interfaces.VisibilityContext
import app.aaps.core.objects.wizard.QuickWizard
import app.aaps.ui.compose.quickLaunch.QuickLaunchAction
import app.aaps.ui.compose.quickLaunch.QuickLaunchResolver
import app.aaps.ui.compose.quickLaunch.QuickLaunchSerializer
import app.aaps.ui.compose.quickLaunch.ResolvedQuickLaunchItem
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
internal class MainViewModelTest {

    @Mock private lateinit var activePlugin: ActivePlugin
    @Mock private lateinit var config: Config
    @Mock private lateinit var urlOpener: UrlOpener
    @Mock private lateinit var preferences: Preferences
    @Mock private lateinit var fabricPrivacy: FabricPrivacy
    @Mock private lateinit var rh: ResourceHelper
    @Mock private lateinit var dateUtil: DateUtil
    @Mock private lateinit var overviewDataCache: OverviewDataCache
    @Mock private lateinit var iobCobCalculator: IobCobCalculator
    @Mock private lateinit var profileFunction: ProfileFunction
    @Mock private lateinit var constraintChecker: ConstraintsChecker
    @Mock private lateinit var quickWizard: QuickWizard
    @Mock private lateinit var automation: Automation
    @Mock private lateinit var persistenceLayer: PersistenceLayer
    @Mock private lateinit var aapsLogger: AAPSLogger
    @Mock private lateinit var quickLaunchResolver: QuickLaunchResolver
    @Mock private lateinit var wizardExecutor: WizardExecutor
    @Mock private lateinit var batchExecutor: BatchExecutor
    @Mock private lateinit var uel: UserEntryLogger
    @Mock private lateinit var loop: Loop
    @Mock private lateinit var protectionCheck: ProtectionCheck
    @Mock private lateinit var sceneActions: SceneActions
    @Mock private lateinit var sceneChainTargetResolver: SceneChainResolver
    @Mock private lateinit var sceneStore: SceneStore
    @Mock private lateinit var activeSceneManager: ActiveSceneSync
    @Mock private lateinit var rxBus: RxBus
    @Mock private lateinit var nsClient: NsClient
    @Mock private lateinit var visibilityContext: VisibilityContext
    @Mock private lateinit var eatReminder: EatReminder
    @Mock private lateinit var clientControlDispatcher: ClientControlActionDispatcher

    private lateinit var sut: MainViewModel

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        // StandardTestDispatcher defers every init { ... launchIn(viewModelScope) } collector and the
        // WhileSubscribed/Eagerly stateIn launches, so construction only touches the flow GETTERS below —
        // all of which are StateFlow-typed and must be stubbed non-null or construction NPEs.
        Dispatchers.setMain(StandardTestDispatcher())

        // Reachability / pairing signals read as fields at construction.
        whenever(nsClient.masterReachable).thenReturn(MutableStateFlow(true))
        whenever(nsClient.masterOrPairedClientFlow).thenReturn(MutableStateFlow(true))
        // The role filter of the toolbar reads these. A mock returns null for them, not the interface default.
        whenever(nsClient.clientRole).thenReturn(MutableStateFlow(ClientRole.Admin))
        whenever(visibilityContext.clientRole).thenReturn(ClientRole.Admin)

        // Cache flows folded into chipStateFlow's combine (getters evaluated eagerly as combine args).
        whenever(overviewDataCache.calcProgressFlow).thenReturn(MutableStateFlow(0))
        whenever(overviewDataCache.tempTargetFlow).thenReturn(MutableStateFlow<TempTargetDisplayData?>(null))
        whenever(overviewDataCache.profileFlow).thenReturn(MutableStateFlow<ProfileDisplayData?>(null))
        whenever(overviewDataCache.runningModeFlow).thenReturn(MutableStateFlow<RunningModeDisplayData?>(null))
        whenever(overviewDataCache.tbrFlow).thenReturn(MutableStateFlow<TbrDisplayData?>(null))
        whenever(quickWizard.changes).thenReturn(MutableStateFlow(0))
        // App init, also folded into chipStateFlow: the QuickWizard items are built once it completes.
        whenever(config.initProgressFlow).thenReturn(MutableStateFlow(InitProgress(done = true)))

        // Active scene state and the scene catalog, read as fields (activeSceneState, sceneExpired.map,
        // activeSceneChainTargetName's combine).
        whenever(activeSceneManager.activeSceneState).thenReturn(MutableStateFlow<ActiveSceneState?>(null))
        whenever(sceneStore.scenesFlow).thenReturn(MutableStateFlow(""))
        whenever(eatReminder.state).thenReturn(MutableStateFlow<EatReminderState?>(null))

        // Preference observers created (launchIn deferred) in init.
        whenever(preferences.observe(BooleanKey.GeneralSimpleMode)).thenReturn(MutableStateFlow(true))
        whenever(preferences.observe(BooleanKey.ApsUseSmb)).thenReturn(MutableStateFlow(false))
        whenever(preferences.observe(StringNonKey.QuickLaunchActions)).thenReturn(MutableStateFlow(""))
        // Toolbar actions are valid unless a test says otherwise. The view model built here refreshes its
        // toolbar when the first runTest runs the deferred collectors, and an unstubbed isValid (false)
        // would then remove every action from the saved toolbar.
        whenever(quickLaunchResolver.isValid(any())).thenReturn(true)

        sut = buildSut()
    }

    /** The one place the constructor is called, so a test can re-stub a flow and build its own instance. */
    private fun buildSut() = MainViewModel(
        activePlugin, config, urlOpener, preferences, fabricPrivacy, rh, dateUtil,
        overviewDataCache, iobCobCalculator, profileFunction, constraintChecker, quickWizard,
        automation, persistenceLayer, aapsLogger, quickLaunchResolver, wizardExecutor,
        batchExecutor, uel, loop, protectionCheck, sceneActions, sceneChainTargetResolver,
        sceneStore, activeSceneManager, rxBus, nsClient, visibilityContext,
        CoroutineScope(UnconfinedTestDispatcher()), eatReminder, clientControlDispatcher
    )

    @Test
    fun `eat reminder buttons go through the dispatcher and change the timer on the master`() = runTest {
        // execute() runs the local action on a master; the round trip on a client is the dispatcher's business.
        whenever(rh.gs(any<TextRef>())).thenReturn("Change eat reminder")
        whenever(clientControlDispatcher.execute(any(), any(), any())).doSuspendableAnswer {
            it.getArgument<suspend () -> ActionProgress>(2).invoke()
        }

        sut.extendEatReminder()
        sut.stopEatReminder()
        advanceUntilIdle()

        val commands = argumentCaptor<ClientControlActionDispatcher.Command>()
        verify(clientControlDispatcher, times(2)).execute(commands.capture(), any(), any())
        assertThat(commands.allValues).containsExactly(
            ClientControlActionDispatcher.Command.EatReminderControl(EatReminderAction.EXTEND),
            ClientControlActionDispatcher.Command.EatReminderControl(EatReminderAction.STOP)
        ).inOrder()
        verify(eatReminder).extend()
        verify(eatReminder).stop()
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `default uiState exposes initial values`() {
        // uiState is WhileSubscribed with no collector → stays at the MainUiState() initialValue.
        val state = sut.uiState.value
        assertThat(state.isSimpleMode).isTrue()
        assertThat(state.isDrawerOpen).isFalse()
        // Null, not DISABLED_LOOP: until something is read from the database the running mode is
        // unknown, and the chip draws that as a question mark rather than claiming the loop is off.
        assertThat(state.runningMode).isNull()
        assertThat(state.tempTargetState).isEqualTo(TempTargetChipState.None)
        assertThat(state.quickWizardItems).isEmpty()
    }

    @Test
    fun `actionConfirmation starts null and dismiss keeps it null`() {
        assertThat(sut.actionConfirmation.value).isNull()
        sut.dismissActionConfirmation()
        assertThat(sut.actionConfirmation.value).isNull()
    }

    @Test
    fun `reachability flows are exposed from nsClient`() {
        assertThat(sut.masterReachable.value).isTrue()
        assertThat(sut.masterOrPairedClient.value).isTrue()
    }

    @Test
    fun `formatDuration delegates to dateUtil`() {
        whenever(dateUtil.timeRemainingString(any(), any())).thenReturn("1h 30m")
        assertThat(sut.formatDuration(5_400_000L)).isEqualTo("1h 30m")
    }

    /**
     * The banner names the follow-up scene. The edit lock covers the running scene but not its
     * follow-up, so that one can be renamed, disabled or deleted while the banner is on screen. The
     * name must follow the catalog, or the banner promises a scene that will not start.
     */
    @Test
    fun `the banner follow-up follows the scene catalog`() = runTest {
        val scene = Scene(id = "s1", name = "Night", endAction = SceneEndAction.ChainScene("s2"))
        val catalog = MutableStateFlow("v1")
        whenever(activeSceneManager.activeSceneState).thenReturn(
            MutableStateFlow<ActiveSceneState?>(ActiveSceneState(scene = scene, activatedAt = 1000L, durationMs = 0L))
        )
        whenever(sceneStore.scenesFlow).thenReturn(catalog)
        whenever(sceneChainTargetResolver.resolveCatalogChainTarget(scene)).thenReturn(Scene(id = "s2", name = "Cooldown"))
        sut = buildSut()
        advanceUntilIdle()
        assertThat(sut.activeSceneChainTargetName.value).isEqualTo("Cooldown")

        // The follow-up is disabled while the scene runs: the resolver no longer finds it.
        whenever(sceneChainTargetResolver.resolveCatalogChainTarget(scene)).thenReturn(null)
        catalog.value = "v2"
        advanceUntilIdle()
        assertThat(sut.activeSceneChainTargetName.value).isNull()
    }

    /**
     * The About dialog's "don't kill my app" button, which is Android's problem and nobody else's.
     *
     * The dialog moved into shared code and the gate did not come with it, so iOS and desktop drew a
     * button that opened `dontkillmyapp.com/apple` and `dontkillmyapp.com/windows-11` - addresses
     * that are not pages, for a thing those systems do not do.
     */
    /**
     * The Exit row, which iOS must not offer.
     *
     * `IosAppExit` refuses on purpose - Apple records a self-terminating app as a crash - so the row
     * ran `exitApp`, wrote an `EXIT_AAPS` user entry, and then nothing happened.
     */
    @Test
    fun `the exit row is offered everywhere except iOS`() {
        whenever(config.platform).thenReturn(AppPlatform.Android)
        assertThat(sut.showExit).isTrue()

        whenever(config.platform).thenReturn(AppPlatform.Desktop)
        assertThat(sut.showExit).isTrue()

        whenever(config.platform).thenReturn(AppPlatform.Ios)
        assertThat(sut.showExit).isFalse()
    }

    @Test
    fun `the battery help button is offered only on Android`() {
        whenever(config.platform).thenReturn(AppPlatform.Android)
        assertThat(sut.showBatteryHelp).isTrue()

        whenever(config.platform).thenReturn(AppPlatform.Ios)
        assertThat(sut.showBatteryHelp).isFalse()

        whenever(config.platform).thenReturn(AppPlatform.Desktop)
        assertThat(sut.showBatteryHelp).isFalse()
    }

    /**
     * A QuickWizard entry on the toolbar follows its overview button: outside its time window or in its
     * cool-down it is hidden. It stays in the saved toolbar, so it comes back when it is active again.
     */
    @Test
    fun `the toolbar hides a QuickWizard entry that is not active and keeps it saved`() = runTest {
        val qw = QuickLaunchAction.QuickWizardAction("qw-1")
        val configButton = QuickLaunchAction.QuickLaunchConfig
        val json = QuickLaunchSerializer.toJson(listOf(QuickLaunchAction.Wizard, qw, configButton))
        var qwActive = false
        val changes = MutableStateFlow(0)
        whenever(preferences.observe(StringNonKey.QuickLaunchActions)).thenReturn(MutableStateFlow(json))
        whenever(quickWizard.changes).thenReturn(changes)
        whenever(quickLaunchResolver.isShownNow(any())).thenAnswer { it.getArgument<QuickLaunchAction>(0) != qw || qwActive }
        whenever(quickLaunchResolver.resolveItem(any())).thenAnswer {
            ResolvedQuickLaunchItem(action = it.getArgument(0), label = "item", icon = Icons.Default.Add)
        }
        sut = buildSut()
        backgroundScope.launch { sut.quickLaunchItems.collect {} }
        runCurrent()

        assertThat(sut.quickLaunchItems.value.map { it.action }).containsExactly(QuickLaunchAction.Wizard, configButton).inOrder()

        // The time window opens (or the cool-down ends) and the entry is used or edited: it is back.
        qwActive = true
        changes.value = 1
        runCurrent()

        assertThat(sut.quickLaunchItems.value.map { it.action }).containsExactly(QuickLaunchAction.Wizard, qw, configButton).inOrder()
        verify(preferences, never()).put(eq(StringNonKey.QuickLaunchActions), any<String>())
    }

    /** Answers every protection request with [outcome]. */
    private fun resolveProtection(outcome: ProtectionResult) {
        whenever(protectionCheck.requestProtection(any(), any())).thenAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            val onResult = invocation.arguments[1] as (ProtectionResult) -> Unit
            onResult(outcome)
        }
    }

    /**
     * A scene changes the profile, the temp target, the loop mode and SMB, so it needs the same bolus
     * protection as doing those things by hand. The overview scenes sheet started one with no check (#5429).
     */
    @Test
    fun `a scene is not prepared when bolus protection is refused`() = runTest {
        resolveProtection(ProtectionResult.DENIED)

        sut.requestSceneConfirmation("s1")
        advanceUntilIdle()

        verify(protectionCheck).requestProtection(eq(ProtectionCheck.Protection.BOLUS), any())
        verify(sceneActions, never()).prepareStart(any(), anyOrNull())
    }

    @Test
    fun `a scene is prepared and confirmed once bolus protection is granted`() = runTest {
        resolveProtection(ProtectionResult.GRANTED)
        whenever(rh.gs(any<TextRef>())).thenReturn("Scene")
        whenever(sceneActions.prepareStart("s1")).thenReturn(ActionProgress.Prepared(id = 3L))

        sut.requestSceneConfirmation("s1")
        advanceUntilIdle()

        val sent = argumentCaptor<Event>()
        verify(rxBus).send(sent.capture())
        assertThat(sent.firstValue).isInstanceOf(EventShowDialog.OkCancel::class.java)
    }

    /** Ending a scene reverts what it changed, and "Skip to" starts the next scene. */
    @Test
    fun `ending a scene from the banner needs bolus protection`() = runTest {
        resolveProtection(ProtectionResult.DENIED)
        sut.requestSceneDeactivation()
        advanceUntilIdle()
        verify(activeSceneManager, never()).getActiveState()
        assertThat(sut.actionConfirmation.value).isNull()

        resolveProtection(ProtectionResult.GRANTED)
        sut.requestSceneDeactivation()
        advanceUntilIdle()
        verify(activeSceneManager).getActiveState()
        verify(protectionCheck, times(2)).requestProtection(eq(ProtectionCheck.Protection.BOLUS), any())
    }

    /**
     * A switch the pump cannot take can pass the prepare and be refused by the commit. The toolbar dialog
     * closed as if the switch was done; now it says why.
     */
    @Test
    fun `a profile switch from the toolbar that the commit refuses says why`() = runTest {
        whenever(rh.gs(any<TextRef>())).thenReturn("Profile switch")
        whenever(batchExecutor.prepare(any(), any(), any())).thenReturn(ActionProgress.Prepared(id = 7L))
        whenever(batchExecutor.commit(eq(7L), any(), any(), any()))
            .thenReturn(ActionProgress.Rejected(FailureReason.ExecutionFailed, "The pump cannot take it"))

        sut.requestProfileConfirmation("Basal", 150, 60)
        advanceUntilIdle()
        val confirmation = argumentCaptor<Event>()
        verify(rxBus).send(confirmation.capture())
        (confirmation.firstValue as EventShowDialog.OkCancel).onOk()
        advanceUntilIdle()

        val sent = argumentCaptor<Event>()
        verify(rxBus, times(2)).send(sent.capture())
        assertThat((sent.lastValue as EventShowDialog.Ok).message).isEqualTo("The pump cannot take it")
    }
}

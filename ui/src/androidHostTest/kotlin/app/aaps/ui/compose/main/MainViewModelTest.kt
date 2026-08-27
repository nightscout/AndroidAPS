package app.aaps.ui.compose.main

import app.aaps.core.data.model.ActiveSceneState
import app.aaps.core.data.model.RM
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.automation.Automation
import app.aaps.core.interfaces.bolus.BatchExecutor
import app.aaps.core.interfaces.bolus.WizardExecutor
import app.aaps.core.interfaces.configuration.Config
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
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.scenes.ActiveSceneSync
import app.aaps.core.interfaces.scenes.SceneActions
import app.aaps.core.interfaces.scenes.SceneChainResolver
import app.aaps.core.interfaces.sync.NsClient
import app.aaps.core.interfaces.ui.IconsProvider
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.StringNonKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.VisibilityContext
import app.aaps.core.objects.wizard.QuickWizard
import app.aaps.ui.compose.quickLaunch.QuickLaunchResolver
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
internal class MainViewModelTest {

    @Mock private lateinit var activePlugin: ActivePlugin
    @Mock private lateinit var config: Config
    @Mock private lateinit var preferences: Preferences
    @Mock private lateinit var fabricPrivacy: FabricPrivacy
    @Mock private lateinit var iconsProvider: IconsProvider
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
    @Mock private lateinit var activeSceneManager: ActiveSceneSync
    @Mock private lateinit var rxBus: RxBus
    @Mock private lateinit var nsClient: NsClient
    @Mock private lateinit var visibilityContext: VisibilityContext

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

        // Cache flows folded into chipStateFlow's combine (getters evaluated eagerly as combine args).
        whenever(overviewDataCache.calcProgressFlow).thenReturn(MutableStateFlow(0))
        whenever(overviewDataCache.tempTargetFlow).thenReturn(MutableStateFlow<TempTargetDisplayData?>(null))
        whenever(overviewDataCache.profileFlow).thenReturn(MutableStateFlow<ProfileDisplayData?>(null))
        whenever(overviewDataCache.runningModeFlow).thenReturn(MutableStateFlow<RunningModeDisplayData?>(null))
        whenever(overviewDataCache.tbrFlow).thenReturn(MutableStateFlow<TbrDisplayData?>(null))
        whenever(quickWizard.changes).thenReturn(MutableStateFlow(0))

        // Active scene state read as fields (activeSceneState + sceneExpired.map).
        whenever(activeSceneManager.activeSceneState).thenReturn(MutableStateFlow<ActiveSceneState?>(null))

        // Preference observers created (launchIn deferred) in init.
        whenever(preferences.observe(BooleanKey.GeneralSimpleMode)).thenReturn(MutableStateFlow(true))
        whenever(preferences.observe(BooleanKey.ApsUseSmb)).thenReturn(MutableStateFlow(false))
        whenever(preferences.observe(StringNonKey.QuickLaunchActions)).thenReturn(MutableStateFlow(""))

        sut = MainViewModel(
            activePlugin, config, preferences, fabricPrivacy, iconsProvider, rh, dateUtil,
            overviewDataCache, iobCobCalculator, profileFunction, constraintChecker, quickWizard,
            automation, persistenceLayer, aapsLogger, quickLaunchResolver, wizardExecutor,
            batchExecutor, uel, loop, protectionCheck, sceneActions, sceneChainTargetResolver,
            activeSceneManager, rxBus, nsClient, visibilityContext,
            CoroutineScope(UnconfinedTestDispatcher())
        )
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `default uiState exposes initial values`() {
        // uiState is WhileSubscribed with no collector → stays at the MainUiState() initialValue.
        val state = sut.uiState.value
        assertThat(state.isSimpleMode).isTrue()
        assertThat(state.isDrawerOpen).isFalse()
        assertThat(state.runningMode).isEqualTo(RM.Mode.DISABLED_LOOP)
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
}

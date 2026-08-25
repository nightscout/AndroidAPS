package app.aaps.ui.compose.scenesSheet

import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.automation.Automation
import app.aaps.core.interfaces.automation.AutomationEvent
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventInitializationChanged
import app.aaps.core.interfaces.rx.events.EventLoopUpdateGui
import app.aaps.core.interfaces.rx.events.EventPumpStatusChanged
import app.aaps.core.interfaces.rx.events.EventRefreshOverview
import app.aaps.core.interfaces.scenes.SceneActions
import app.aaps.core.interfaces.scenes.SceneStore
import app.aaps.core.interfaces.sync.NsClient
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
internal class ScenesViewModelTest {

    @Mock private lateinit var automation: Automation
    @Mock private lateinit var activePlugin: ActivePlugin
    @Mock private lateinit var loop: Loop
    @Mock private lateinit var profileFunction: ProfileFunction
    @Mock private lateinit var config: Config
    @Mock private lateinit var rxBus: RxBus
    @Mock private lateinit var sceneRepository: SceneStore
    @Mock private lateinit var sceneActions: SceneActions
    @Mock private lateinit var rh: ResourceHelper
    @Mock private lateinit var nsClient: NsClient

    private lateinit var sut: ScenesViewModel

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        // StandardTestDispatcher does NOT run the init{}-launched refreshState() coroutine (no advanceUntilIdle),
        // so construction stays clean and we assert the default uiState. The cold flow chains built by
        // setupEventListeners() are, however, wired synchronously and must be stubbed to non-null flows.
        Dispatchers.setMain(StandardTestDispatcher())
        whenever(nsClient.masterReachable).thenReturn(MutableStateFlow(true))
        whenever(automation.events).thenReturn(MutableStateFlow<List<AutomationEvent>>(emptyList()))
        whenever(sceneRepository.scenesFlow).thenReturn(MutableStateFlow(""))
        whenever(rxBus.toFlow(EventRefreshOverview::class.java)).thenReturn(emptyFlow())
        whenever(rxBus.toFlow(EventPumpStatusChanged::class.java)).thenReturn(emptyFlow())
        whenever(rxBus.toFlow(EventLoopUpdateGui::class.java)).thenReturn(emptyFlow())
        whenever(rxBus.toFlow(EventInitializationChanged::class.java)).thenReturn(emptyFlow())
        sut = ScenesViewModel(
            automation, activePlugin, loop, profileFunction, config, rxBus,
            sceneRepository, sceneActions, rh, nsClient
        )
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `default uiState has no items and no scene items`() {
        val state = sut.uiState.value
        assertThat(state.items).isEmpty()
        assertThat(state.sceneItems).isEmpty()
    }
}

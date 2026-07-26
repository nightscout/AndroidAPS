package app.aaps.ui.compose.scenes

import app.aaps.core.data.model.ActiveSceneState
import app.aaps.core.data.model.Scene
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.profile.ProfileRepository
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventInitializationChanged
import app.aaps.core.interfaces.rx.events.EventLoopUpdateGui
import app.aaps.core.interfaces.rx.events.EventPumpStatusChanged
import app.aaps.core.interfaces.rx.events.EventRefreshOverview
import app.aaps.core.interfaces.scenes.ActiveSceneSync
import app.aaps.core.interfaces.scenes.SceneActions
import app.aaps.core.interfaces.scenes.SceneChainResolver
import app.aaps.core.interfaces.scenes.SceneStore
import app.aaps.core.interfaces.sync.NsClient
import app.aaps.core.interfaces.utils.DateUtil
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
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
internal class SceneListViewModelTest {

    @Mock private lateinit var sceneRepository: SceneStore
    @Mock private lateinit var activeSceneManager: ActiveSceneSync
    @Mock private lateinit var persistenceLayer: PersistenceLayer
    @Mock private lateinit var profileRepository: ProfileRepository
    @Mock private lateinit var rh: ResourceHelper
    @Mock private lateinit var rxBus: RxBus
    @Mock private lateinit var dateUtil: DateUtil
    @Mock private lateinit var config: Config
    @Mock private lateinit var sceneActions: SceneActions
    @Mock private lateinit var sceneChainTargetResolver: SceneChainResolver
    @Mock private lateinit var nsClient: NsClient

    private lateinit var sut: SceneListViewModel

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        // StandardTestDispatcher defers the init{} viewModelScope.launch + launchIn collectors (no advanceUntilIdle),
        // so construction only builds the cold flow chains — we test the synchronous methods against the default state.
        Dispatchers.setMain(StandardTestDispatcher())
        // Cold-flow / initial-value reads evaluated during construction:
        whenever(sceneRepository.scenesFlow).thenReturn(MutableStateFlow(""))
        whenever(sceneRepository.getScenes()).thenReturn(emptyList())
        whenever(activeSceneManager.activeSceneState).thenReturn(MutableStateFlow<ActiveSceneState?>(null))
        whenever(nsClient.masterReachable).thenReturn(MutableStateFlow(false))
        // init references these event flows synchronously (onEach before the deferred launchIn):
        whenever(rxBus.toFlow(EventPumpStatusChanged::class.java)).thenReturn(emptyFlow())
        whenever(rxBus.toFlow(EventLoopUpdateGui::class.java)).thenReturn(emptyFlow())
        whenever(rxBus.toFlow(EventInitializationChanged::class.java)).thenReturn(emptyFlow())
        whenever(rxBus.toFlow(EventRefreshOverview::class.java)).thenReturn(emptyFlow())
        sut = SceneListViewModel(
            sceneRepository, activeSceneManager, persistenceLayer, profileRepository, rh, rxBus,
            dateUtil, config, sceneActions, sceneChainTargetResolver, nsClient
        )
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `default state exposes empty scenes, no invalid ids and no dialog`() {
        assertThat(sut.scenes.value).isEmpty()
        assertThat(sut.invalidSceneIds.value).isEmpty()
        assertThat(sut.dialogState.value).isNull()
        assertThat(sut.activeSceneState.value).isNull()
    }

    @Test
    fun `dismissDialog clears the dialog state`() {
        sut.dismissDialog()

        assertThat(sut.dialogState.value).isNull()
    }

    @Test
    fun `toggleEnabled with unknown scene id is a no-op`() {
        // getScene defaults to null on the mock → early return, nothing saved.
        sut.toggleEnabled("missing")

        verify(sceneRepository, never()).saveScene(any())
    }

    @Test
    fun `toggleEnabled flips isEnabled and saves the scene`() {
        val scene = Scene(id = "s1", name = "Morning", isEnabled = true)
        whenever(sceneRepository.getScene("s1")).thenReturn(scene)

        sut.toggleEnabled("s1")

        verify(sceneRepository).saveScene(scene.copy(isEnabled = false))
    }
}

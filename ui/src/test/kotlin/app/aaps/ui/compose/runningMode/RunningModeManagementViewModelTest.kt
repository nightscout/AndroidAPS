package app.aaps.ui.compose.runningMode

import app.aaps.core.data.model.EPS
import app.aaps.core.data.model.RM
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.bolus.BatchExecutor
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.Translator
import app.aaps.core.keys.interfaces.Preferences
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
internal class RunningModeManagementViewModelTest {

    @Mock private lateinit var loop: Loop
    @Mock private lateinit var activePlugin: ActivePlugin
    @Mock private lateinit var profileFunction: ProfileFunction
    @Mock private lateinit var translator: Translator
    @Mock private lateinit var preferences: Preferences
    @Mock private lateinit var persistenceLayer: PersistenceLayer
    @Mock private lateinit var aapsLogger: AAPSLogger
    @Mock private lateinit var rxBus: RxBus
    @Mock private lateinit var rh: ResourceHelper
    @Mock private lateinit var dateUtil: DateUtil
    @Mock private lateinit var config: Config
    @Mock private lateinit var batchExecutor: BatchExecutor

    private lateinit var sut: RunningModeManagementViewModel

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        // loadState() launch + the observer launchIns are deferred by StandardTestDispatcher; the observed flows
        // are still built, so they must be non-null.
        Dispatchers.setMain(StandardTestDispatcher())
        whenever(persistenceLayer.observeChanges(RM::class.java)).thenReturn(emptyFlow())
        whenever(persistenceLayer.observeChanges(EPS::class.java)).thenReturn(emptyFlow())
        sut = RunningModeManagementViewModel(
            loop, activePlugin, profileFunction, translator, preferences, persistenceLayer, aapsLogger,
            rxBus, rh, dateUtil, config, batchExecutor, CoroutineScope(UnconfinedTestDispatcher())
        )
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `default uiState is loading with no allowed transitions`() {
        val state = sut.uiState.value
        assertThat(state.currentMode).isEqualTo(RM.Mode.DISABLED_LOOP)
        assertThat(state.allowedNextModes).isEmpty()
        assertThat(state.isLoading).isTrue()
    }
}

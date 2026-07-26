package app.aaps.ui.compose.profileManagement.viewmodels

import app.aaps.core.data.model.EPS
import app.aaps.core.interfaces.bolus.BatchExecutor
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileRepository
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.profile.SingleProfile
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.compose.ScreenMode
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
internal class ProfileManagementViewModelTest {

    @Mock private lateinit var profileRepository: ProfileRepository
    @Mock private lateinit var profileFunction: ProfileFunction
    @Mock private lateinit var rh: ResourceHelper
    @Mock private lateinit var dateUtil: DateUtil
    @Mock private lateinit var aapsLogger: AAPSLogger
    @Mock private lateinit var activePlugin: ActivePlugin
    @Mock private lateinit var profileUtil: ProfileUtil
    @Mock private lateinit var decimalFormatter: DecimalFormatter
    @Mock private lateinit var persistenceLayer: PersistenceLayer
    @Mock private lateinit var preferences: Preferences
    @Mock private lateinit var config: Config
    @Mock private lateinit var batchExecutor: BatchExecutor
    @Mock private lateinit var rxBus: RxBus

    private lateinit var sut: ProfileManagementViewModel

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        // StandardTestDispatcher defers the init{} observers' launchIn and the stateIn collector
        // (no advanceUntilIdle), so uiState stays at its default value and we test the synchronous
        // methods. The cold-flow chains that init builds synchronously must still be non-null:
        //  - profileRepository.profiles is read via .value.map{} in observeNewProfileSelection()
        //  - persistenceLayer.observeChanges(EPS) is built in observeActiveProfileForAutoNavigation()
        //    and in the uiState combine.
        Dispatchers.setMain(StandardTestDispatcher())
        whenever(profileRepository.profiles).thenReturn(MutableStateFlow(emptyList<SingleProfile>()))
        whenever(persistenceLayer.observeChanges(EPS::class.java)).thenReturn(emptyFlow())
        sut = ProfileManagementViewModel(
            profileRepository, profileFunction, rh, dateUtil, aapsLogger, activePlugin,
            profileUtil, decimalFormatter, persistenceLayer, preferences, config,
            batchExecutor, rxBus, CoroutineScope(UnconfinedTestDispatcher())
        )
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `default uiState is loading, in EDIT mode and empty`() {
        val state = sut.uiState.value
        assertThat(state.isLoading).isTrue()
        assertThat(state.screenMode).isEqualTo(ScreenMode.EDIT)
        assertThat(state.profileNames).isEmpty()
        assertThat(state.currentProfileIndex).isEqualTo(0)
        assertThat(state.activeProfileSwitch).isNull()
    }

    @Test
    fun `getReuseValues returns null when there is no active profile switch`() {
        assertThat(sut.getReuseValues()).isNull()
    }

    @Test
    fun `isPumpCompatible returns true when there is no profile at the index`() {
        assertThat(sut.isPumpCompatible(0, 100)).isTrue()
    }
}

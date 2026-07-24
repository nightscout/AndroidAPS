package app.aaps.ui.compose.insulinManagement

import app.aaps.core.data.model.EPS
import app.aaps.core.data.model.ICfg
import app.aaps.core.interfaces.bolus.BatchExecutor
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.insulin.ConcentrationType
import app.aaps.core.interfaces.insulin.InsulinManager
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.profile.EffectiveProfile
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileRepository
import app.aaps.core.interfaces.profile.SingleProfile
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.HardLimits
import app.aaps.core.keys.StringNonKey
import app.aaps.core.keys.interfaces.Preferences
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
internal class InsulinManagementViewModelTest {

    @Mock private lateinit var insulinManager: InsulinManager
    @Mock private lateinit var preferences: Preferences
    @Mock private lateinit var profileFunction: ProfileFunction
    @Mock private lateinit var dateUtil: DateUtil
    @Mock private lateinit var hardLimits: HardLimits
    @Mock private lateinit var uel: UserEntryLogger
    @Mock private lateinit var rh: ResourceHelper
    @Mock private lateinit var rxBus: RxBus
    @Mock private lateinit var persistenceLayer: PersistenceLayer
    @Mock private lateinit var profileRepository: ProfileRepository
    @Mock private lateinit var config: Config
    @Mock private lateinit var batchExecutor: BatchExecutor

    // Held so tests can advance the ViewModel's Main-dispatched coroutines (loadData / observeConfigChanges).
    private val testDispatcher = StandardTestDispatcher()
    // The InsulinConfiguration observe() flow; tests emit on it to drive onExternalConfigChange().
    private val configFlow = MutableStateFlow("")

    private lateinit var sut: InsulinManagementViewModel

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        // StandardTestDispatcher defers the init{}-launched loadData() coroutine (no advanceUntilIdle),
        // so construction stays clean and we test the synchronous editor setters against the default state.
        Dispatchers.setMain(testDispatcher)
        // Synchronous field read in init.
        whenever(preferences.get(StringNonKey.InsulinConfiguration)).thenReturn("")
        // Cold flow chains built synchronously in observeProfileChanges()/observeConfigChanges().
        whenever(profileRepository.profiles).thenReturn(MutableStateFlow(emptyList<SingleProfile>()))
        whenever(persistenceLayer.observeChanges(EPS::class.java)).thenReturn(emptyFlow())
        whenever(preferences.observe(StringNonKey.InsulinConfiguration)).thenReturn(configFlow)
        sut = InsulinManagementViewModel(
            insulinManager, preferences, profileFunction, dateUtil, hardLimits, uel,
            rh, rxBus, persistenceLayer, profileRepository, config, batchExecutor,
            CoroutineScope(UnconfinedTestDispatcher())
        )
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `default uiState has empty nickname, U100 and default dia`() {
        val state = sut.uiState.value
        assertThat(state.editorNickname).isEqualTo("")
        assertThat(state.editorConcentration).isEqualTo(ConcentrationType.U100)
        assertThat(state.editorDiaHours).isEqualTo(5.0)
        assertThat(state.autoNameEnabled).isTrue()
        assertThat(state.externalUpdatePending).isFalse()
        assertThat(state.pendingNavigation).isNull()
    }

    @Test
    fun `updateEditorNickname sets the name and disables auto-name`() {
        sut.updateEditorNickname("Humalog")

        val state = sut.uiState.value
        assertThat(state.editorNickname).isEqualTo("Humalog")
        assertThat(state.autoNameEnabled).isFalse()
    }

    @Test
    fun `updateEditorConcentration sets the concentration`() {
        sut.updateEditorConcentration(ConcentrationType.U200)

        assertThat(sut.uiState.value.editorConcentration).isEqualTo(ConcentrationType.U200)
    }

    @Test
    fun `updateEditorDia sets the dia hours`() {
        sut.updateEditorDia(7.5)

        assertThat(sut.uiState.value.editorDiaHours).isEqualTo(7.5)
    }

    @Test
    fun `dismissPendingNavigation clears any pending navigation`() {
        sut.dismissPendingNavigation()

        assertThat(sut.uiState.value.pendingNavigation).isNull()
    }

    @Test
    fun `dismissExternalUpdate clears the external-update flag`() {
        sut.dismissExternalUpdate()

        assertThat(sut.uiState.value.externalUpdatePending).isFalse()
    }

    // --- Issue B: Activate FAB gate — activeConcentration must be null (not a defaulted 1.0) when unknown ---

    @Test
    fun `activeConcentration is null when there is no active profile`() {
        whenever(insulinManager.insulins).thenReturn(arrayListOf(icfg(concentration = 1.0)))
        whenever(insulinManager.insulinIndex(anyOrNull())).thenReturn(0)
        // getProfile() defaults to null → activeConcentration must stay null, never fall back to 1.0 (== U100).
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(sut.uiState.value.activeConcentration).isNull()
    }

    @Test
    fun `activeConcentration reflects the running insulin concentration`() {
        val running = mock<EffectiveProfile>()
        whenever(running.iCfg).thenReturn(icfg(concentration = 2.0))
        runBlocking { whenever(profileFunction.getProfile()).thenReturn(running) }
        whenever(insulinManager.insulins).thenReturn(arrayListOf(icfg(concentration = 1.0)))
        whenever(insulinManager.insulinIndex(anyOrNull())).thenReturn(0)
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(sut.uiState.value.activeConcentration).isEqualTo(2.0)
    }

    // --- Issue A: the master's own store echo must NOT raise the "a client changed it" popup ---

    @Test
    fun `self-echo of own store does not raise external-update popup on a standalone master`() {
        whenever(config.AAPSCLIENT).thenReturn(false)
        whenever(insulinManager.insulins).thenReturn(arrayListOf(icfg(1.0, "Rapid")))
        whenever(insulinManager.insulinIndex(anyOrNull())).thenReturn(0)
        testDispatcher.scheduler.advanceUntilIdle() // run init loadData + subscribe the config observer

        sut.updateEditorNickname("Humalog") // create an unsaved edit → hasUnsavedChanges() == true

        // The VM's own store stamps lastStoredConfiguration and writes the same string to the pref.
        whenever(insulinManager.lastStoredConfiguration).thenReturn("cfg-self")
        whenever(preferences.get(StringNonKey.InsulinConfiguration)).thenReturn("cfg-self")
        configFlow.value = "cfg-self"
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(sut.uiState.value.externalUpdatePending).isFalse()
    }

    @Test
    fun `genuine external change still raises the popup on a master with unsaved edits`() {
        whenever(config.AAPSCLIENT).thenReturn(false)
        whenever(insulinManager.insulins).thenReturn(arrayListOf(icfg(1.0, "Rapid")))
        whenever(insulinManager.insulinIndex(anyOrNull())).thenReturn(0)
        whenever(insulinManager.lastStoredConfiguration).thenReturn("cfg-local") // NOT the incoming value
        testDispatcher.scheduler.advanceUntilIdle()

        sut.updateEditorNickname("Humalog") // unsaved edit

        whenever(preferences.get(StringNonKey.InsulinConfiguration)).thenReturn("cfg-external")
        configFlow.value = "cfg-external"
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(sut.uiState.value.externalUpdatePending).isTrue()
    }

    private fun icfg(concentration: Double, nickname: String = "Rapid"): ICfg =
        ICfg(insulinLabel = "$nickname 75m 5h", peak = 75, dia = 5.0, concentration = concentration).also {
            it.insulinNickname = nickname
        }
}

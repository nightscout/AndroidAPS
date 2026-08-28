package app.aaps.ui.compose.stats.viewmodels

import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.stats.DexcomTirCalculator
import app.aaps.core.interfaces.stats.TddCalculator
import app.aaps.core.interfaces.stats.TirCalculator
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.ui.activityMonitor.ActivityMonitor
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations

@OptIn(ExperimentalCoroutinesApi::class)
internal class StatsViewModelTest {

    @Mock private lateinit var tddCalculator: TddCalculator
    @Mock private lateinit var tirCalculator: TirCalculator
    @Mock private lateinit var dexcomTirCalculator: DexcomTirCalculator
    @Mock private lateinit var activityMonitor: ActivityMonitor
    @Mock private lateinit var persistenceLayer: PersistenceLayer
    @Mock private lateinit var rh: ResourceHelper
    @Mock private lateinit var uel: UserEntryLogger
    @Mock private lateinit var dateUtil: DateUtil
    @Mock private lateinit var profileUtil: ProfileUtil
    @Mock private lateinit var preferences: Preferences

    private lateinit var sut: StatsViewModel

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        // StandardTestDispatcher defers every init{}-launched loadXxxStats() coroutine (no advanceUntilIdle),
        // so construction stays clean and we test the synchronous update/toggle/dialog methods against the
        // default state. init only reads preferences.get(...) which return primitives (default false/0 on
        // unstubbed mocks) -> tddCycleExpanded is false, so loadCyclePatternData() is never invoked.
        Dispatchers.setMain(StandardTestDispatcher())
        sut = StatsViewModel(
            tddCalculator, tirCalculator, dexcomTirCalculator, activityMonitor, persistenceLayer,
            rh, uel, dateUtil, profileUtil, preferences
        )
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `default uiState has dialogs hidden and zero cycle offset`() {
        val state = sut.uiState.value
        assertThat(state.showRecalculateDialog).isFalse()
        assertThat(state.showResetActivityDialog).isFalse()
        assertThat(state.tddCycleOffset).isEqualTo(0)
    }

    @Test
    fun `toggleTddExpanded flips the expanded flag`() {
        val initial = sut.uiState.value.tddExpanded
        sut.toggleTddExpanded()
        assertThat(sut.uiState.value.tddExpanded).isEqualTo(!initial)
        sut.toggleTddExpanded()
        assertThat(sut.uiState.value.tddExpanded).isEqualTo(initial)
    }

    @Test
    fun `toggleTirExpanded flips the expanded flag`() {
        val initial = sut.uiState.value.tirExpanded
        sut.toggleTirExpanded()
        assertThat(sut.uiState.value.tirExpanded).isEqualTo(!initial)
    }

    @Test
    fun `toggleDexcomTirExpanded flips the expanded flag`() {
        val initial = sut.uiState.value.dexcomTirExpanded
        sut.toggleDexcomTirExpanded()
        assertThat(sut.uiState.value.dexcomTirExpanded).isEqualTo(!initial)
    }

    @Test
    fun `toggleActivityExpanded flips the expanded flag`() {
        val initial = sut.uiState.value.activityExpanded
        sut.toggleActivityExpanded()
        assertThat(sut.uiState.value.activityExpanded).isEqualTo(!initial)
    }

    @Test
    fun `recalculate dialog is shown then dismissed`() {
        sut.showRecalculateDialog()
        assertThat(sut.uiState.value.showRecalculateDialog).isTrue()
        sut.dismissRecalculateDialog()
        assertThat(sut.uiState.value.showRecalculateDialog).isFalse()
    }

    @Test
    fun `reset activity dialog is shown then dismissed`() {
        sut.showResetActivityDialog()
        assertThat(sut.uiState.value.showResetActivityDialog).isTrue()
        sut.dismissResetActivityDialog()
        assertThat(sut.uiState.value.showResetActivityDialog).isFalse()
    }
}

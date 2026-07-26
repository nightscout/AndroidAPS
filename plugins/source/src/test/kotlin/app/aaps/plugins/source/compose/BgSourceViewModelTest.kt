package app.aaps.plugins.source.compose

import app.aaps.core.data.model.GV
import app.aaps.core.data.model.SourceSensor
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.ui.R
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
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
internal class BgSourceViewModelTest {

    @Mock private lateinit var persistenceLayer: PersistenceLayer
    @Mock private lateinit var rh: ResourceHelper
    @Mock private lateinit var dateUtil: DateUtil
    @Mock private lateinit var profileUtil: ProfileUtil
    @Mock private lateinit var aapsLogger: AAPSLogger
    @Mock private lateinit var rxBus: RxBus

    private lateinit var sut: BgSourceViewModel

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        // viewModelScope is created from Dispatchers.Main.immediate; provide a test main dispatcher
        // so VM construction and its launchIn(viewModelScope) collectors settle in-line.
        Dispatchers.setMain(UnconfinedTestDispatcher())
        // observeBgChanges() collects this in init; an empty flow completes immediately and never
        // re-triggers loadData(). The initial loadData()'s background IO launch is intentionally left
        // unstubbed: it never mutates the structural fields asserted below.
        whenever(persistenceLayer.observeChanges(GV::class.java)).thenReturn(emptyFlow())
        sut = BgSourceViewModel(persistenceLayer, rh, dateUtil, profileUtil, aapsLogger, rxBus)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun gv(id: Long, timestamp: Long) =
        GV(
            id = id,
            timestamp = timestamp,
            raw = null,
            value = 100.0,
            trendArrow = TrendArrow.FLAT,
            noise = null,
            sourceSensor = SourceSensor.DEXCOM_NATIVE_UNKNOWN
        )

    @Test
    fun `default uiState is not in removing mode and has no selection`() {
        val state = sut.uiState.value
        assertThat(state.isRemovingMode).isFalse()
        assertThat(state.selectedItems).isEmpty()
        assertThat(state.glucoseValues).isEmpty()
        assertThat(state.duplicateIds).isEmpty()
        assertThat(state.historyHours).isEqualTo(36L)
    }

    @Test
    fun `enterSelectionMode selects the item and enables removing mode`() {
        val item = gv(id = 1L, timestamp = 1_000L)

        sut.enterSelectionMode(item)

        val state = sut.uiState.value
        assertThat(state.isRemovingMode).isTrue()
        assertThat(state.selectedItems).containsExactly(item)
    }

    @Test
    fun `exitSelectionMode clears selection and disables removing mode`() {
        sut.enterSelectionMode(gv(id = 1L, timestamp = 1_000L))

        sut.exitSelectionMode()

        val state = sut.uiState.value
        assertThat(state.isRemovingMode).isFalse()
        assertThat(state.selectedItems).isEmpty()
    }

    @Test
    fun `toggleSelection adds an item and then removes it`() {
        val first = gv(id = 1L, timestamp = 1_000L)
        val second = gv(id = 2L, timestamp = 2_000L)
        sut.enterSelectionMode(first)

        sut.toggleSelection(second)
        assertThat(sut.uiState.value.selectedItems).containsExactly(first, second)

        sut.toggleSelection(second)
        assertThat(sut.uiState.value.selectedItems).containsExactly(first)
    }

    @Test
    fun `loadMoreData extends the history range by 24 hours`() {
        sut.loadMoreData()

        assertThat(sut.uiState.value.historyHours).isEqualTo(60L)
    }

    @Test
    fun `getDeleteConfirmationMessage is empty when nothing is selected`() {
        assertThat(sut.getDeleteConfirmationMessage()).isEqualTo("")
    }

    @Test
    fun `getDeleteConfirmationMessage uses the plural string for multiple selection`() {
        whenever(rh.gs(R.string.confirm_remove_multiple_items, 2)).thenReturn("Remove 2 items")
        sut.enterSelectionMode(gv(id = 1L, timestamp = 1_000L))
        sut.toggleSelection(gv(id = 2L, timestamp = 2_000L))

        assertThat(sut.getDeleteConfirmationMessage()).isEqualTo("Remove 2 items")
    }
}

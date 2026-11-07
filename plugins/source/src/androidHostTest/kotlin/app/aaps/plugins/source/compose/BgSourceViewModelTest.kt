package app.aaps.plugins.source.compose

import app.aaps.core.data.model.GV
import app.aaps.core.data.model.IDs
import app.aaps.core.data.model.SourceSensor
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.bolus.BatchAction
import app.aaps.core.interfaces.bolus.BatchExecutor
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.shared.tests.generatedTextResolver
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
internal class BgSourceViewModelTest {

    @Mock private lateinit var persistenceLayer: PersistenceLayer
    @Mock private lateinit var dateUtil: DateUtil
    @Mock private lateinit var profileUtil: ProfileUtil
    @Mock private lateinit var aapsLogger: AAPSLogger
    @Mock private lateinit var rxBus: RxBus
    @Mock private lateinit var batchExecutor: BatchExecutor
    @Mock private lateinit var config: Config

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
        whenever(persistenceLayer.observeChanges(GV::class)).thenReturn(emptyFlow())
        sut = BgSourceViewModel(persistenceLayer, generatedTextResolver(), dateUtil, profileUtil, aapsLogger, rxBus, batchExecutor, config)
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
            sourceSensor = SourceSensor.DEXCOM_UNKNOWN
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
    fun `formatGlucoseValue delegates to profileUtil`() {
        whenever(profileUtil.fromMgdlToStringInUnits(120.0)).thenReturn("6.7")
        assertThat(sut.formatGlucoseValue(120.0)).isEqualTo("6.7")
    }

    /** The master removes the readings: a BG removed only on a client would still be used by the loop there. */
    @Test
    fun `requestDelete asks the master to remove the selected readings by their ids`() = runTest {
        sut.enterSelectionMode(gv(id = 1L, timestamp = 1_000L).copy(ids = IDs(nightscoutId = "gv1")))

        sut.requestDelete()

        val actions = argumentCaptor<List<BatchAction>>()
        verify(batchExecutor).prepare(actions.capture(), eq(Sources.BgFragment), any())
        assertThat(actions.firstValue).containsExactly(
            BatchAction.Remove(BatchAction.Remove.Kind.GLUCOSE_VALUE, listOf(BatchAction.Remove.Ref(nsId = "gv1", localId = 1L)))
        )
    }
}

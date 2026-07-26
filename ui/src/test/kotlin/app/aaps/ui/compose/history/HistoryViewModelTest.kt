package app.aaps.ui.compose.history

import app.aaps.core.interfaces.overview.OverviewData
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.workflow.CalculationSignalsEmitter
import app.aaps.core.interfaces.workflow.CalculationWorkflow
import app.aaps.ui.compose.overview.graphs.GraphViewModel
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
internal class HistoryViewModelTest {

    @Mock private lateinit var historyScope: HistoryScope
    @Mock private lateinit var calculationWorkflow: CalculationWorkflow
    @Mock private lateinit var dateUtil: DateUtil
    @Mock private lateinit var graphViewModelFactory: GraphViewModel.Factory
    @Mock private lateinit var signals: CalculationSignalsEmitter
    @Mock private lateinit var overviewData: OverviewData

    private lateinit var sut: HistoryViewModel

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(StandardTestDispatcher())
        // The init block runs synchronously at construction: the `progress` field reads
        // historyScope.signals.progress, and buildState()/runCalculation() read overviewData —
        // both must be non-null or construction NPEs.
        whenever(historyScope.signals).thenReturn(signals)
        whenever(signals.progress).thenReturn(MutableStateFlow(0))
        whenever(historyScope.overviewData).thenReturn(overviewData)
        sut = HistoryViewModel(historyScope, calculationWorkflow, dateUtil, graphViewModelFactory)
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `default uiState is empty date and at-now, progress starts idle`() {
        val state = sut.uiState.value
        // fromTime == 0L -> dateText stays blank; toTime(0) >= now(0) -> isAtNow true.
        assertThat(state.dateText).isEqualTo("")
        assertThat(state.isAtNow).isTrue()
        assertThat(sut.progress.value).isEqualTo(0)
    }

    @Test
    fun `onPause stops the history calculation`() {
        sut.onPause()

        verify(calculationWorkflow).stopCalculation(CalculationWorkflow.HISTORY_CALCULATION, "onPause")
    }

    @Test
    fun `selectedDateMillis returns the timestamp from dateUtil`() {
        whenever(dateUtil.getTimestampWithCurrentTimeOfDay(any())).thenReturn(999L)

        assertThat(sut.selectedDateMillis()).isEqualTo(999L)
    }
}

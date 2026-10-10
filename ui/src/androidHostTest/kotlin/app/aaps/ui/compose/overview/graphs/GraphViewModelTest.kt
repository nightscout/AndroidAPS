package app.aaps.ui.compose.overview.graphs

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.overview.graph.BgDataPoint
import app.aaps.core.interfaces.overview.graph.BgRange
import app.aaps.core.interfaces.overview.graph.BgType
import app.aaps.core.interfaces.overview.graph.GraphConfig
import app.aaps.core.interfaces.overview.graph.GraphConfigRepository
import app.aaps.core.interfaces.overview.graph.OverviewDataCache
import app.aaps.core.interfaces.overview.graph.TimeRange
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.UnitDoubleKey
import app.aaps.core.keys.interfaces.Preferences
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
internal class GraphViewModelTest {

    @Mock private lateinit var cache: OverviewDataCache
    @Mock private lateinit var graphConfigRepository: GraphConfigRepository
    @Mock private lateinit var aapsLogger: AAPSLogger
    @Mock private lateinit var preferences: Preferences
    @Mock private lateinit var dateUtil: DateUtil
    @Mock private lateinit var rh: ResourceHelper

    private lateinit var sut: GraphViewModel

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        // StandardTestDispatcher defers the init{} launchIn observers (no advanceUntilIdle), so we test
        // the synchronous field initializers / setter methods against the default state.
        Dispatchers.setMain(StandardTestDispatcher())
        // Field initializer for _chartConfigFlow reads these synchronously at construction.
        whenever(preferences.get(UnitDoubleKey.OverviewHighMark)).thenReturn(180.0)
        whenever(preferences.get(UnitDoubleKey.OverviewLowMark)).thenReturn(72.0)
        // init{} builds a cold flow chain (.drop(1)...) on each observed key — must be non-null.
        whenever(preferences.observe(UnitDoubleKey.OverviewHighMark)).thenReturn(MutableStateFlow(180.0))
        whenever(preferences.observe(UnitDoubleKey.OverviewLowMark)).thenReturn(MutableStateFlow(72.0))
        // fullWindow = false: the overview behaviour, where the axis hugs the data.
        sut = GraphViewModel(cache, false, graphConfigRepository, aapsLogger, preferences, dateUtil, rh)
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    /**
     * A prediction must never set the left edge of the axis - issue #5111.
     *
     * Predictions are future points. When the glucose and bucketed flows are empty and a loop run
     * has already published some, taking the minimum of everything put the left edge at roughly
     * now: the axis became [now, now + horizon], the current time sat pinned to the left with no
     * past behind it, the prediction was the only thing drawn, and it could not be scrolled back
     * because that was the whole range. The next reading refilled the glucose flow and dragged the
     * edge back, which is why it looked transient. Reported as happening after the app had been
     * offline for a while and started loading data again.
     */
    @Test
    fun `predictions alone do not pull the left edge of the axis up to now`() = runTest {
        val now = 1_700_000_000_000L
        val windowStart = now - 24 * 3600_000L
        whenever(dateUtil.now()).thenReturn(now)
        // No glucose and no bucketed data - the state after a spell offline - but the last loop run
        // left predictions behind, all of them in the future.
        val predictions = listOf(
            BgDataPoint(timestamp = now + 5 * 60_000L, value = 7.0, range = BgRange.IN_RANGE, type = BgType.IOB_PREDICTION),
            BgDataPoint(timestamp = now + 60 * 60_000L, value = 6.0, range = BgRange.IN_RANGE, type = BgType.IOB_PREDICTION)
        )
        whenever(cache.bgReadingsFlow).thenReturn(MutableStateFlow(emptyList()))
        whenever(cache.bucketedDataFlow).thenReturn(MutableStateFlow(emptyList()))
        whenever(cache.predictionsFlow).thenReturn(MutableStateFlow(predictions))
        whenever(cache.timeRangeFlow).thenReturn(MutableStateFlow(TimeRange(fromTime = windowStart, toTime = now, endTime = now + 2 * 3600_000L)))
        whenever(graphConfigRepository.graphConfigFlow).thenReturn(MutableStateFlow(GraphConfig()))
        val vm = GraphViewModel(cache, false, graphConfigRepository, aapsLogger, preferences, dateUtil, rh)

        val range = vm.derivedTimeRange.filterNotNull().first()

        // The window's own start, not the first prediction.
        assertThat(range.first).isEqualTo(windowStart)
        // And the predictions still get to push the right edge out past the cached end.
        assertThat(range.second).isAtLeast(now)
    }

    @Test
    fun `chart config reflects the high and low mark preferences`() {
        val config = sut.chartConfigFlow.value
        assertThat(config.highMark).isEqualTo(180.0)
        assertThat(config.lowMark).isEqualTo(72.0)
    }

    @Test
    fun `onGraphInteraction records the interaction timestamp`() {
        // The time comes from DateUtil rather than the wall clock, so it can be pinned here instead
        // of only being asserted as "some number above zero".
        whenever(dateUtil.now()).thenReturn(1_700_000_000_000L)

        assertThat(sut.lastInteractionMs).isEqualTo(0L)
        sut.onGraphInteraction()
        assertThat(sut.lastInteractionMs).isEqualTo(1_700_000_000_000L)
    }
}

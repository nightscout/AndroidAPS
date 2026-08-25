package app.aaps.ui.compose.overview.graphs

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.overview.graph.GraphConfigRepository
import app.aaps.core.interfaces.overview.graph.OverviewDataCache
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.UnitDoubleKey
import app.aaps.core.keys.interfaces.Preferences
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
        sut = GraphViewModel(cache, graphConfigRepository, aapsLogger, preferences, dateUtil, rh)
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `chart config reflects the high and low mark preferences`() {
        val config = sut.chartConfigFlow.value
        assertThat(config.highMark).isEqualTo(180.0)
        assertThat(config.lowMark).isEqualTo(72.0)
    }

    @Test
    fun `onGraphInteraction records a non-zero interaction timestamp`() {
        assertThat(sut.lastInteractionMs).isEqualTo(0L)
        sut.onGraphInteraction()
        assertThat(sut.lastInteractionMs).isGreaterThan(0L)
    }
}

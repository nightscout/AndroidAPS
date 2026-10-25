package app.aaps.ui.compose.stats.viewmodels

import app.aaps.core.data.model.BS
import app.aaps.core.data.model.CA
import app.aaps.core.data.model.EB
import app.aaps.core.data.model.EPS
import app.aaps.core.data.model.GV
import app.aaps.core.data.model.SourceSensor
import app.aaps.core.data.model.TB
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.stats.DexcomTirCalculator
import app.aaps.core.interfaces.stats.TddCalculator
import app.aaps.core.interfaces.stats.TirCalculator
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.StringNonKey
import app.aaps.core.keys.UnitDoubleKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.ui.compose.stats.AgpStatsRange
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
internal class StatsViewModelTest {

    @Mock private lateinit var tddCalculator: TddCalculator
    @Mock private lateinit var tirCalculator: TirCalculator
    @Mock private lateinit var dexcomTirCalculator: DexcomTirCalculator
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
        // StandardTestDispatcher defers every loadXxxStats() coroutine (no advanceUntilIdle), so we test the
        // synchronous tab/update/toggle/dialog methods against the default state. init loads nothing (only the
        // tab on screen is loaded, from onStart/selectTab) and reads preferences that are unstubbed here
        // (false/0/null) -> the first tab, so loadCyclePatternData() runs only when a test opens the Cycle tab.
        Dispatchers.setMain(StandardTestDispatcher())
        // init observes these. Unstubbed they are null, and a test that lets coroutines run would throw
        // an uncaught exception that fails an unrelated test after it.
        whenever(persistenceLayer.observeChanges(BS::class)).thenReturn(emptyFlow())
        whenever(persistenceLayer.observeChanges(CA::class)).thenReturn(emptyFlow())
        whenever(persistenceLayer.observeChanges(TB::class)).thenReturn(emptyFlow())
        whenever(persistenceLayer.observeChanges(EB::class)).thenReturn(emptyFlow())
        whenever(persistenceLayer.observeChanges(EPS::class)).thenReturn(emptyFlow())
        sut = StatsViewModel(
            tddCalculator, tirCalculator, dexcomTirCalculator, persistenceLayer,
            rh, uel, dateUtil, profileUtil, preferences
        )
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `default uiState has dialog hidden and zero cycle offset`() {
        val state = sut.uiState.value
        assertThat(state.showRecalculateDialog).isFalse()
        assertThat(state.tddCycleOffset).isEqualTo(0)
    }

    @Test
    fun `screen opens on the first tab when none was used yet`() {
        assertThat(sut.uiState.value.selectedTab).isEqualTo(StatsTab.INSULIN)
    }

    @Test
    fun `screen opens on the tab used last`() {
        whenever(preferences.get(StringNonKey.StatsTab)).thenReturn(StatsTab.GLUCOSE.name)
        val viewModel = StatsViewModel(
            tddCalculator, tirCalculator, dexcomTirCalculator, persistenceLayer,
            rh, uel, dateUtil, profileUtil, preferences
        )
        assertThat(viewModel.uiState.value.selectedTab).isEqualTo(StatsTab.GLUCOSE)
    }

    @Test
    fun `selecting a tab shows it and remembers it`() {
        sut.selectTab(StatsTab.TIME_IN_RANGE)
        assertThat(sut.uiState.value.selectedTab).isEqualTo(StatsTab.TIME_IN_RANGE)
        verify(preferences).put(StringNonKey.StatsTab, StatsTab.TIME_IN_RANGE.name)
    }

    @Test
    fun `the cycle pattern is calculated only when its tab is opened`() = runBlocking<Unit> {
        // Coroutines run here, so the 168 day calculation really starts once the tab is opened
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val viewModel = StatsViewModel(
            tddCalculator, tirCalculator, dexcomTirCalculator, persistenceLayer,
            rh, uel, dateUtil, profileUtil, preferences
        )
        verifyBlocking(tddCalculator, never()) { calculate(any<Long>(), any<Long>(), any<Boolean>()) }

        viewModel.selectTab(StatsTab.CYCLE)
        // Waited for, so no coroutine of this test is still running when the next test starts
        withTimeout(10_000) { viewModel.uiState.first { !it.tddCycleLoading } }
        verifyBlocking(tddCalculator, atLeastOnce()) { calculate(any<Long>(), any<Long>(), any<Boolean>()) }
    }

    @Test
    fun `opening the glucose tab loads AGP for the selected range`() {
        sut.selectTab(StatsTab.GLUCOSE)
        assertThat(sut.uiState.value.agpRange).isEqualTo(AgpStatsRange.TODAY)
        assertThat(sut.uiState.value.agpLoading).isTrue()
    }

    @Test
    fun `selecting an AGP range immediately sets loading and the selected range`() {
        sut.loadAgpStats(AgpStatsRange.DAYS_7)
        assertThat(sut.uiState.value.agpRange).isEqualTo(AgpStatsRange.DAYS_7)
        assertThat(sut.uiState.value.agpLoading).isTrue()
        assertThat(sut.uiState.value.agpError).isFalse()
        sut.loadAgpStats(AgpStatsRange.DAYS_90)
        assertThat(sut.uiState.value.agpRange).isEqualTo(AgpStatsRange.DAYS_90)
    }

    @Test
    fun `AGP loads the selected range using the overview thresholds`() = runBlocking {
        val now = 1_700_000_000_000L
        val range = AgpStatsRange.DAYS_7
        val reading = GV(
            timestamp = now,
            raw = null,
            value = 120.0,
            trendArrow = TrendArrow.NONE,
            noise = null,
            sourceSensor = SourceSensor.UNKNOWN
        )
        whenever(dateUtil.now()).thenReturn(now)
        whenever(preferences.get(UnitDoubleKey.OverviewLowMark)).thenReturn(72.0)
        whenever(preferences.get(UnitDoubleKey.OverviewHighMark)).thenReturn(180.0)
        whenever(profileUtil.convertToMgdlDetect(72.0)).thenReturn(72.0)
        whenever(profileUtil.convertToMgdlDetect(180.0)).thenReturn(180.0)
        whenever(persistenceLayer.getBgReadingsDataFromTimeToTime(range.startTime(now), now, true)).thenReturn(listOf(reading))
        Dispatchers.setMain(UnconfinedTestDispatcher())

        sut.loadAgpStats(range)

        val state = withTimeout(10_000) { sut.uiState.first { !it.agpLoading } }
        assertThat(state.agpError).isFalse()
        assertThat(state.agpStatsData?.readingCount).isEqualTo(1)
        assertThat(state.agpStatsData?.averageMgdl).isEqualTo(120.0)
        assertThat(state.agpStatsData?.lowMgdl).isEqualTo(72.0)
        assertThat(state.agpStatsData?.highMgdl).isEqualTo(180.0)
    }

    @Test
    fun `AGP reports a load error and can retry`() = runBlocking {
        val now = 1_700_000_000_000L
        val range = AgpStatsRange.DAYS_30
        whenever(dateUtil.now()).thenReturn(now)
        whenever(persistenceLayer.getBgReadingsDataFromTimeToTime(range.startTime(now), now, true))
            .thenThrow(IllegalStateException("Database unavailable"))
            .thenReturn(emptyList())
        Dispatchers.setMain(UnconfinedTestDispatcher())

        sut.loadAgpStats(range)
        val failed = withTimeout(10_000) { sut.uiState.first { !it.agpLoading } }
        assertThat(failed.agpError).isTrue()

        sut.loadAgpStats(range)
        val retried = withTimeout(10_000) { sut.uiState.first { !it.agpLoading } }
        assertThat(retried.agpError).isFalse()
        assertThat(retried.agpStatsData?.readingCount).isEqualTo(0)
    }

    @Test
    fun `recalculate dialog is shown then dismissed`() {
        sut.showRecalculateDialog()
        assertThat(sut.uiState.value.showRecalculateDialog).isTrue()
        sut.dismissRecalculateDialog()
        assertThat(sut.uiState.value.showRecalculateDialog).isFalse()
    }
}

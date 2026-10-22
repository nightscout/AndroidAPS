package app.aaps.ui.compose.stats.viewmodels

import app.aaps.core.data.model.GV
import app.aaps.core.data.model.SourceSensor
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.stats.DexcomTirCalculator
import app.aaps.core.interfaces.stats.TddCalculator
import app.aaps.core.interfaces.stats.TirCalculator
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.UnitDoubleKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.ui.compose.stats.AgpStatsRange
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
        // StandardTestDispatcher defers every init{}-launched loadXxxStats() coroutine (no advanceUntilIdle),
        // so construction stays clean and we test the synchronous update/toggle/dialog methods against the
        // default state. init only reads preferences.get(...) which return primitives (default false/0 on
        // unstubbed mocks) -> tddCycleExpanded is false, so loadCyclePatternData() is never invoked.
        Dispatchers.setMain(StandardTestDispatcher())
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
    fun `AGP starts expanded with the today range`() {
        assertThat(sut.uiState.value.agpExpanded).isTrue()
        assertThat(sut.uiState.value.agpRange).isEqualTo(AgpStatsRange.TODAY)
    }

    @Test
    fun `toggleAgpExpanded flips the expanded flag without changing the range`() {
        sut.loadAgpStats(AgpStatsRange.DAYS_30)
        sut.toggleAgpExpanded()
        assertThat(sut.uiState.value.agpExpanded).isFalse()
        assertThat(sut.uiState.value.agpRange).isEqualTo(AgpStatsRange.DAYS_30)
        sut.toggleAgpExpanded()
        assertThat(sut.uiState.value.agpExpanded).isTrue()
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
    fun `toggleDexcomTirExpanded flips the expanded flag`() {
        val initial = sut.uiState.value.dexcomTirExpanded
        sut.toggleDexcomTirExpanded()
        assertThat(sut.uiState.value.dexcomTirExpanded).isEqualTo(!initial)
    }

    @Test
    fun `recalculate dialog is shown then dismissed`() {
        sut.showRecalculateDialog()
        assertThat(sut.uiState.value.showRecalculateDialog).isTrue()
        sut.dismissRecalculateDialog()
        assertThat(sut.uiState.value.showRecalculateDialog).isFalse()
    }
}

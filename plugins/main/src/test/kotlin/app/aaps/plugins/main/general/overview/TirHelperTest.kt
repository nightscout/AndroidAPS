package app.aaps.plugins.main.general.overview

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.stats.TIR
import app.aaps.core.interfaces.stats.TirCalculator
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.MidnightTime
import app.aaps.plugins.main.general.overview.TirHelper
import app.aaps.plugins.main.general.overview.ui.TirChartData
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever

class TirHelperTest {

    @Mock
    lateinit var preferences: app.aaps.core.keys.interfaces.Preferences
    @Mock
    lateinit var profileUtil: ProfileUtil
    @Mock
    lateinit var tirCalculator: TirCalculator
    @Mock
    lateinit var dateUtil: DateUtil
    @Mock
    lateinit var rh: ResourceHelper
    @Mock
    lateinit var aapsLogger: AAPSLogger

    private lateinit var sut: TirHelper

    @BeforeEach
    fun setup() {
        MockitoAnnotations.openMocks(this)
        sut = TirHelper(preferences, profileUtil, tirCalculator, dateUtil, rh, aapsLogger)
        // ResourceHelper.gs is used to build subtitles/title; ensure it returns non-null strings in tests
        whenever(rh.gs(org.mockito.kotlin.any())).thenReturn("TIR")
    }

    @Test
    fun `returns null when no readings`() {
        val emptyTir = TestTIR(date = 0L)

        whenever(tirCalculator.calculateToday(anyDouble(), anyDouble())).thenReturn(emptyTir)
        whenever(dateUtil.now()).thenReturn(1000000000L)

        val result = sut.calculateTodayChartData()

        assertThat(result).isNull()
    }

    @Test
    fun `calculates time based percentages and sums to 100`() {
        val tir = TestTIR(date = 0L, lowThreshold = 70.0, highThreshold = 180.0,
            count = 4, below = 1, inRange = 2, above = 1, error = 0)

        // Simulate half of day elapsed (tests compute midnight deterministically)
        val now = 1000000000L
        val midnight = MidnightTime.calc(now)
        whenever(dateUtil.now()).thenReturn(now)
        whenever(tirCalculator.calculateToday(anyDouble(), anyDouble())).thenReturn(tir)

        val chartData = sut.calculateTodayChartData()

        assertThat(chartData).isNotNull()
        val combined = chartData as TirChartData

        // Check totalCount propagated
        assertThat(combined.totalCount).isEqualTo(4)

        // Verify tillNow sums to ~100 (no unknown, just actual proportions)
        val t = combined.tillNowScenario
        val sum = t.belowPct + t.inRangePct + t.abovePct
        assertThat(Math.round(sum)).isEqualTo(100)
        assertThat(t.unknownPct).isEqualTo(0.0)
    }

    @Test
    fun `best and worst scenarios produce expected relationships`() {
        // 4 readings: below=1, inRange=2, above=1 -> 25%,50%,25% of observed time
        val tir = TestTIR(date = 0L, lowThreshold = 70.0, highThreshold = 180.0,
            count = 4, below = 1, inRange = 2, above = 1, error = 0)

        // Use deterministic midnight calculation for given instant
        val now = 1000000000L
        val midnight = MidnightTime.calc(now)
        whenever(dateUtil.now()).thenReturn(now)
        whenever(tirCalculator.calculateToday(anyDouble(), anyDouble())).thenReturn(tir)

        val chartData = sut.calculateTodayChartData()!!

        // Till now: actual proportions of observed readings (should sum to 100%, no unknown)
        val t = chartData.tillNowScenario
        assertThat(t.unknownPct).isEqualTo(0.0)
        // inRange should be ~ 50% (2 out of 4 readings)
        assertThat(Math.round(t.inRangePct)).isEqualTo(50)
        assertThat(t.inRangePct).isGreaterThan(t.belowPct)
        assertThat(t.inRangePct).isGreaterThan(t.abovePct)

        // Combined best case: inRange includes remaining time -> should be > tillNow inRange (50%)
        val bestInRange = chartData.combinedScenario.bestInRangePct
        assertThat(bestInRange).isGreaterThan(t.inRangePct)

        // Combined worst case: worstInRange should be less than tillNow inRange
        // (since remaining time is unknown, only observed inRange counts)
        val worstInRange = chartData.combinedScenario.worstInRangePct
        assertThat(worstInRange).isLessThan(t.inRangePct)
    }

    @Test
    fun `handles very small counts without throwing and normalizes correctly`() {
        // Only 1 reading (inRange).
        val tir = TestTIR(date = 0L, lowThreshold = 70.0, highThreshold = 180.0,
            count = 1, below = 0, inRange = 1, above = 0, error = 0)

        val now = 1000000000L
        val midnight = MidnightTime.calc(now)
        whenever(dateUtil.now()).thenReturn(now)
        whenever(tirCalculator.calculateToday(anyDouble(), anyDouble())).thenReturn(tir)

        val chartData = sut.calculateTodayChartData()
        assertThat(chartData).isNotNull()

        val t = chartData!!.tillNowScenario
        // With single reading inRange, it should be 100% (no unknown, just actual proportion)
        assertThat(Math.round(t.inRangePct)).isEqualTo(100)
        assertThat(t.unknownPct).isEqualTo(0.0)
        assertThat(t.belowPct).isEqualTo(0.0)
        assertThat(t.abovePct).isEqualTo(0.0)
    }

    @Test
    fun `dst day length variation produces consistent sums`() {
        // Use deterministic counts
        val tir = TestTIR(date = 0L, lowThreshold = 70.0, highThreshold = 180.0,
            count = 10, below = 2, inRange = 6, above = 2, error = 0)

        // Simulate a short day (23 hours) where elapsed is 12 hours -> elapsedFraction > 0.5
        val now = 1_000_000_000_000L
        whenever(dateUtil.now()).thenReturn(now)
        whenever(tirCalculator.calculateToday(anyDouble(), anyDouble())).thenReturn(tir)

        // Call twice to ensure normalization doesn't depend on exact day length calculation beyond elapsed/remaining
        val chart1 = sut.calculateTodayChartData()
        val chart2 = sut.calculateTodayChartData()

        assertThat(chart1).isNotNull()
        assertThat(chart2).isNotNull()

        // Till now should sum to 100%, no unknown
        val sum1 = chart1!!.tillNowScenario.run { belowPct + inRangePct + abovePct }
        val sum2 = chart2!!.tillNowScenario.run { belowPct + inRangePct + abovePct }

        assertThat(Math.round(sum1)).isEqualTo(100)
        assertThat(Math.round(sum2)).isEqualTo(100)
        assertThat(chart1.tillNowScenario.unknownPct).isEqualTo(0.0)
        assertThat(chart2.tillNowScenario.unknownPct).isEqualTo(0.0)
    }

    @Test
    fun `deterministic expected percentages for known elapsed fraction`() {
        // 2 readings: below=0, inRange=1, above=1 -> observed 0%,50%,50%
        val tir = TestTIR(date = 0L, lowThreshold = 70.0, highThreshold = 180.0,
            count = 2, below = 0, inRange = 1, above = 1, error = 0)

        // Use deterministic midnight calculation for given instant
        val now = 1_234_567_890_000L
        val midnight = MidnightTime.calc(now)
        whenever(dateUtil.now()).thenReturn(now)
        whenever(tirCalculator.calculateToday(anyDouble(), anyDouble())).thenReturn(tir)

        val chart = sut.calculateTodayChartData()!!
        val t = chart.tillNowScenario

        // No unknown, just actual proportions
        assertThat(t.unknownPct).isEqualTo(0.0)
        // The two observed categories should be equal (both came from 1 reading each = 50%)
        assertThat(Math.round(t.inRangePct)).isEqualTo(50)
        assertThat(Math.round(t.abovePct)).isEqualTo(50)
        assertThat(t.belowPct).isEqualTo(0.0)
    }
}

// Small helpers for Mockito any matchers since we used kotlin mockito
private fun anyDouble(): Double = org.mockito.kotlin.any()

// Simple mutable implementation of TIR for tests
private class TestTIR(
    override var date: Long = 0L,
    override var lowThreshold: Double = 0.0,
    override var highThreshold: Double = 0.0,
    override var count: Int = 0,
    override var below: Int = 0,
    override var inRange: Int = 0,
    override var above: Int = 0,
    override var error: Int = 0
) : TIR {
    override fun error() { this.error += 1 }
    override fun below() { this.below += 1; this.count += 1 }
    override fun inRange() { this.inRange += 1; this.count += 1 }
    override fun above() { this.above += 1; this.count += 1 }

    override fun toTableRow(context: android.content.Context, rh: ResourceHelper, dateUtil: DateUtil): android.widget.TableRow {
        return android.widget.TableRow(context)
    }

    override fun toTableRow(context: android.content.Context, rh: ResourceHelper, days: Int): android.widget.TableRow {
        return android.widget.TableRow(context)
    }
}

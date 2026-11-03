package app.aaps.ui.compose.stats

import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.GV
import app.aaps.core.data.model.SourceSensor
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.utils.MidnightTime
import kotlin.math.round
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

class AgpStatsDataTest {

    @Test
    fun mixedReadingsUseTheCorrectBands() {
        val values = listOf(40.0, 60.0, 100.0, 200.0, 300.0)
        val data = calculateAgpStatsData(values.mapIndexed { index, value -> glucose(index.toLong(), value) }, 70.0, 180.0)

        assertEquals(AgpTirBreakdown(20.0, 20.0, 20.0, 20.0, 20.0), data.tir)
        assertEquals(20.0, data.tightRangePercent)
        assertEquals(140.0, data.averageMgdl)
        assertEquals(100.0, data.medianMgdl)
        assertEquals((140.0 + 46.7) / 28.7, data.eA1cPercent)
        assertEquals(3.31 + 0.02392 * 140.0, data.gmiPercent)
    }

    @Test
    fun customThresholdsDoNotCountReadingsTwice() {
        val values = listOf(40.0, 50.0, 100.0, 260.0, 300.0)
        val data = calculateAgpStatsData(values.mapIndexed { index, value -> glucose(index.toLong(), value) }, 45.0, 280.0)

        assertEquals(AgpTirBreakdown(20.0, 0.0, 60.0, 0.0, 20.0), data.tir)
    }

    @Test
    fun emptyAndInvalidReadingsDoNotProduceStatistics() {
        assertEquals(AgpStatsData(70.0, 180.0), calculateAgpStatsData(emptyList(), 70.0, 180.0))
        val invalid = glucose(0L, 100.0).copy(isValid = false)
        val data = calculateAgpStatsData(listOf(glucose(1L, 38.0), invalid), 70.0, 180.0)
        assertEquals(0, data.readingCount)
        assertTrue(data.hourlyPercentiles.isEmpty())
    }

    @Test
    fun hourlyPercentilesUseLocalHoursAndLinearInterpolation() {
        val start = MidnightTime.calc(1_700_000_000_000L) + T.hours(6).msecs()
        val readings = listOf(40.0, 60.0, 80.0, 100.0, 120.0).mapIndexed { index, value ->
            glucose(start + T.mins(index * 5L).msecs(), value)
        }
        val data = calculateAgpStatsData(readings.reversed(), 70.0, 180.0)
        val hour = Instant.fromEpochMilliseconds(start).toLocalDateTime(TimeZone.currentSystemDefault()).hour

        assertEquals(listOf(AgpHourlyPercentile(hour, 48.0, 60.0, 80.0, 100.0, 112.0)), data.hourlyPercentiles)
    }

    @Test
    fun availableDaysUseReadingCadenceAndDoNotFillGaps() {
        val readings = List(144) { index -> glucose(T.mins(index * 5L).msecs(), 100.0) }
        val data = calculateAgpStatsData(readings, 70.0, 180.0)
        assertEquals(0.5, data.availableDays, 0.0001)
        assertEquals(0.0, data.standardDeviationMgdl)
        assertEquals(0.0, data.coefficientOfVariation)
        val withGap = readings + glucose(T.days(3).msecs(), 100.0)
        assertEquals(145.0 / 288.0, calculateAgpStatsData(withGap, 70.0, 180.0).availableDays, 0.0001)
    }

    @Test
    fun axesCoverTheHighestValueWithReadableTicks() {
        val mgdlAxis = calculateAgpYAxis(180.0, GlucoseUnit.MGDL)
        assertEquals(listOf(200.0, 160.0, 120.0, 80.0, 40.0), mgdlAxis.ticksMgdl)
        val mmolAxis = calculateAgpYAxis(180.0, GlucoseUnit.MMOL)
        assertTrue(mmolAxis.maximumMgdl >= 180.0)
        mmolAxis.ticksMgdl.forEach {
            val mmol = it * Constants.MGDL_TO_MMOLL
            assertEquals(round(mmol), mmol, 0.0001)
        }
    }

    @Test
    fun rangesStartAtMidnightOrTheSelectedDuration() {
        val now = 1_700_000_000_000L
        assertEquals(MidnightTime.calc(now), AgpStatsRange.TODAY.startTime(now))
        assertEquals(now - T.hours(24).msecs(), AgpStatsRange.HOURS_24.startTime(now))
        assertEquals(now - T.days(7).msecs(), AgpStatsRange.DAYS_7.startTime(now))
        assertEquals(now - T.days(30).msecs(), AgpStatsRange.DAYS_30.startTime(now))
        assertEquals(now - T.days(90).msecs(), AgpStatsRange.DAYS_90.startTime(now))
    }

    private fun glucose(timestamp: Long, value: Double) = GV(
        timestamp = timestamp,
        raw = null,
        value = value,
        trendArrow = TrendArrow.NONE,
        noise = null,
        sourceSensor = SourceSensor.UNKNOWN
    )
}

package app.aaps.ui.compose.stats

import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.GV
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.utils.MidnightTime
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.sqrt
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

enum class AgpStatsRange {
    TODAY,
    HOURS_24,
    DAYS_7,
    DAYS_30,
    DAYS_90;

    fun startTime(now: Long): Long = when (this) {
        TODAY    -> MidnightTime.calc(now)
        HOURS_24 -> now - T.hours(24).msecs()
        DAYS_7   -> now - T.days(7).msecs()
        DAYS_30  -> now - T.days(30).msecs()
        DAYS_90  -> now - T.days(90).msecs()
    }
}

data class AgpTirBreakdown(
    val veryLow: Double = 0.0,
    val low: Double = 0.0,
    val inRange: Double = 0.0,
    val high: Double = 0.0,
    val veryHigh: Double = 0.0
)

data class AgpHourlyPercentile(
    val hour: Int,
    val p10Mgdl: Double,
    val p25Mgdl: Double,
    val medianMgdl: Double,
    val p75Mgdl: Double,
    val p90Mgdl: Double
)

internal data class AgpYAxis(
    val minimumMgdl: Double,
    val maximumMgdl: Double,
    val ticksMgdl: List<Double>
)

internal fun calculateAgpYAxis(highestMgdl: Double, units: GlucoseUnit): AgpYAxis {
    val minimumMgdl = 40.0
    return when (units) {
        GlucoseUnit.MGDL -> {
            val initialMaximumMgdl = ceil(highestMgdl / 50.0).coerceAtLeast(2.0) * 50.0
            val tickStepMgdl = (ceil((initialMaximumMgdl - minimumMgdl) / 40.0) * 10.0).coerceAtLeast(10.0)
            val maximumMgdl = minimumMgdl + tickStepMgdl * 4
            AgpYAxis(
                minimumMgdl = minimumMgdl,
                maximumMgdl = maximumMgdl,
                ticksMgdl = (0..4).map { index -> maximumMgdl - tickStepMgdl * index }
            )
        }

        GlucoseUnit.MMOL -> {
            val minimumMmol = floor(minimumMgdl * Constants.MGDL_TO_MMOLL)
            val highestMmol = ceil(highestMgdl * Constants.MGDL_TO_MMOLL)
            val tickStepMmol = ceil((highestMmol - minimumMmol) / 4.0).coerceAtLeast(1.0)
            val maximumMmol = minimumMmol + tickStepMmol * 4
            AgpYAxis(
                minimumMgdl = minimumMmol * Constants.MMOLL_TO_MGDL,
                maximumMgdl = maximumMmol * Constants.MMOLL_TO_MGDL,
                ticksMgdl = (0..4).map { index -> (maximumMmol - tickStepMmol * index) * Constants.MMOLL_TO_MGDL }
            )
        }
    }
}

data class AgpStatsData(
    val lowMgdl: Double,
    val highMgdl: Double,
    val readingCount: Int = 0,
    val availableDays: Double = 0.0,
    val averageMgdl: Double = 0.0,
    val medianMgdl: Double = 0.0,
    val standardDeviationMgdl: Double = 0.0,
    val coefficientOfVariation: Double = 0.0,
    val eA1cPercent: Double = 0.0,
    val gmiPercent: Double = 0.0,
    val tightRangePercent: Double = 0.0,
    val tir: AgpTirBreakdown = AgpTirBreakdown(),
    val hourlyPercentiles: List<AgpHourlyPercentile> = emptyList()
)

internal fun calculateAgpStatsData(
    readings: List<GV>,
    lowMgdl: Double,
    highMgdl: Double
): AgpStatsData {
    val valid = readings.filter { it.isValid && it.value >= MIN_VALID_BG }.sortedBy { it.timestamp }
    if (valid.isEmpty()) return AgpStatsData(lowMgdl, highMgdl)

    val values = valid.map { it.value }
    val sorted = values.sorted()
    val average = values.average()
    val standardDeviation = sqrt(values.sumOf { value -> (value - average) * (value - average) } / values.size)
    val gaps = valid.zipWithNext { first, second -> second.timestamp - first.timestamp }
        .filter { it in MIN_CADENCE_MS..MAX_CADENCE_MS }
        .sorted()
    val cadence = gaps.getOrNull(gaps.size / 2) ?: DEFAULT_CADENCE_MS
    val sampleDays = valid.size * cadence.toDouble() / DAY_MS
    val spanDays = (valid.last().timestamp - valid.first().timestamp + cadence).toDouble() / DAY_MS
    val zone = TimeZone.currentSystemDefault()

    return AgpStatsData(
        lowMgdl = lowMgdl,
        highMgdl = highMgdl,
        readingCount = valid.size,
        availableDays = minOf(sampleDays, spanDays),
        averageMgdl = average,
        medianMgdl = percentile(sorted, 0.5),
        standardDeviationMgdl = standardDeviation,
        coefficientOfVariation = standardDeviation / average * 100.0,
        eA1cPercent = (average + 46.7) / 28.7,
        gmiPercent = 3.31 + 0.02392 * average,
        tightRangePercent = percentage(values.count { it in TIGHT_LOW_MGDL..TIGHT_HIGH_MGDL }, values.size),
        tir = AgpTirBreakdown(
            veryLow = percentage(values.count { it < minOf(VERY_LOW_MGDL, lowMgdl) }, values.size),
            low = percentage(values.count { it >= VERY_LOW_MGDL && it < lowMgdl }, values.size),
            inRange = percentage(values.count { it in lowMgdl..highMgdl }, values.size),
            high = percentage(values.count { it > highMgdl && it <= VERY_HIGH_MGDL }, values.size),
            veryHigh = percentage(values.count { it > maxOf(VERY_HIGH_MGDL, highMgdl) }, values.size)
        ),
        hourlyPercentiles = valid
            .groupBy { Instant.fromEpochMilliseconds(it.timestamp).toLocalDateTime(zone).hour }
            .entries
            .sortedBy { it.key }
            .map { (hour, readingsForHour) ->
                val hourlyValues = readingsForHour.map { it.value }.sorted()
                AgpHourlyPercentile(
                    hour = hour,
                    p10Mgdl = percentile(hourlyValues, 0.10),
                    p25Mgdl = percentile(hourlyValues, 0.25),
                    medianMgdl = percentile(hourlyValues, 0.50),
                    p75Mgdl = percentile(hourlyValues, 0.75),
                    p90Mgdl = percentile(hourlyValues, 0.90)
                )
            }
    )
}

private fun percentile(sorted: List<Double>, fraction: Double): Double {
    val position = fraction * sorted.lastIndex
    val lower = position.toInt()
    val upper = (lower + 1).coerceAtMost(sorted.lastIndex)
    val weight = position - lower
    return sorted[lower] + (sorted[upper] - sorted[lower]) * weight
}

private fun percentage(count: Int, total: Int): Double = count * 100.0 / total

private const val MIN_VALID_BG = 39.0
private const val VERY_LOW_MGDL = 54.0
private const val VERY_HIGH_MGDL = 250.0
private const val TIGHT_LOW_MGDL = 70.0
private const val TIGHT_HIGH_MGDL = 140.0
private const val MINUTE_MS = 60_000L
private const val DEFAULT_CADENCE_MS = 5 * MINUTE_MS
private const val MIN_CADENCE_MS = MINUTE_MS
private const val MAX_CADENCE_MS = 30 * MINUTE_MS
private const val DAY_MS = 24L * 60L * 60L * 1000L

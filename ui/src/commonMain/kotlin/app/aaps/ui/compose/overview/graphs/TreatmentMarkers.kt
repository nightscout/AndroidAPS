package app.aaps.ui.compose.overview.graphs

import app.aaps.core.interfaces.overview.graph.BgDataPoint
import app.aaps.core.interfaces.overview.graph.BolusGraphPoint
import app.aaps.core.interfaces.overview.graph.BolusType
import app.aaps.core.interfaces.overview.graph.CalibrationGraphPoint
import app.aaps.core.interfaces.overview.graph.CarbsGraphPoint
import app.aaps.core.interfaces.overview.graph.TherapyEventGraphPoint
import app.aaps.core.interfaces.overview.graph.TherapyEventType
import kotlin.math.abs

/**
 * Placement of the treatment markers drawn inside the BG graph. Pure functions, no Compose and no
 * Vico, so they can be tested on every platform.
 *
 * A marker sits at the glucose value of the moment it happened: insulin grows upward from the
 * reading, carbs downward, with a small space between them: a meal entered with its bolus reads as
 * one marker, but the two halves stay apart instead of merging into a block. The marker is drawn
 * over the curve and hides the one reading it sits on — under the curve the carbs half was covered
 * by the reading dots instead.
 */

/** Max gap to the nearest reading for a marker to be placed on the curve. Three reading intervals. */
internal const val MARKER_MAX_READING_GAP_MS = 15 * 60_000L

/** A bolus and a carbs entry this close apart are one meal and are drawn at the same height. */
internal const val MARKER_PAIR_MS = 5 * 60_000L

/** Marker size, from the amount relative to the other markers of the same kind. */
enum class MarkerTier { SMALL, MEDIUM, LARGE }

/** One treatment marker. [x] is minutes from minTimestamp, [y] is in the BG axis' units. */
data class TreatmentMarker(val x: Double, val y: Double, val tier: MarkerTier)

/** The markers of one rebuild, split by kind because each kind has its own shape and color. */
data class TreatmentMarkers(
    val insulin: List<TreatmentMarker>,
    val carbs: List<TreatmentMarker>,
    val calibrations: List<Pair<Double, Double>>
)

/**
 * Every measured glucose value in the loaded range, as (timestamp, value in the user's units):
 * the finger value of a calibration pair, and the BG checks that carry a value (a meter value
 * entered here, or the same thing arriving from Nightscout). These are drawn at their own value
 * inside the BG graph, so they also widen the Y axis like a reading does.
 */
internal fun measuredGlucosePoints(
    calibrations: List<CalibrationGraphPoint>,
    therapyEvents: List<TherapyEventGraphPoint>
): List<Pair<Long, Double>> =
    calibrations
        .filter { it.isValid }
        .map { it.timestamp to it.fingerstickUserUnits } +
        therapyEvents
            .filter { it.eventType == TherapyEventType.MBG || it.eventType == TherapyEventType.FINGER_STICK }
            .mapNotNull { event -> event.glucoseUserUnits?.let { event.timestamp to it } }

/**
 * Glucose at [timestamp], interpolated between the readings on each side of it.
 *
 * Returns null when the nearest reading is further away than [MARKER_MAX_READING_GAP_MS] — in a
 * sensor gap there is no honest height for a marker, so the caller falls back to the bottom of the
 * graph instead of inventing a value. [readings] does not have to be sorted.
 */
internal fun interpolatedBgAt(timestamp: Long, readings: List<BgDataPoint>): Double? {
    if (readings.isEmpty()) return null
    val sorted = readings.sortedBy { it.timestamp }
    val before = sorted.lastOrNull { it.timestamp <= timestamp }
    val after = sorted.firstOrNull { it.timestamp >= timestamp }
    val gapBefore = before?.let { timestamp - it.timestamp } ?: Long.MAX_VALUE
    val gapAfter = after?.let { it.timestamp - timestamp } ?: Long.MAX_VALUE
    if (minOf(gapBefore, gapAfter) > MARKER_MAX_READING_GAP_MS) return null
    return when {
        before == null                      -> after?.value
        after == null                       -> before.value
        after.timestamp == before.timestamp -> before.value
        // A long sensor gap around the treatment: interpolating across it would draw a marker at a
        // height nobody measured. Use the side that is close instead.
        gapBefore > MARKER_MAX_READING_GAP_MS ||
            gapAfter > MARKER_MAX_READING_GAP_MS -> if (gapBefore <= gapAfter) before.value else after.value

        else                                -> {
            val span = (after.timestamp - before.timestamp).toDouble()
            before.value + (after.value - before.value) * ((timestamp - before.timestamp) / span)
        }
    }
}

/**
 * Size tier of [amount] against the largest marker of the same kind in the loaded range, in thirds.
 *
 * Measured from zero, not from the smallest amount: 0.20 U and 0.25 U are the same dose to a
 * reader, and splitting the span between them would draw one as the smallest marker and the other
 * as the largest. Against the biggest dose of the day both come out the same size, and a 1 U
 * correction next to a 10 U meal bolus still reads as small.
 *
 * Taken from the whole loaded range, not from the visible window: a marker that changes size while
 * the graph is scrolled looks like a glitch.
 */
internal fun tierOf(amount: Double, amounts: List<Double>): MarkerTier {
    val max = amounts.maxOrNull() ?: return MarkerTier.MEDIUM
    if (max <= 0.0) return MarkerTier.MEDIUM
    return when {
        amount < max / 3.0       -> MarkerTier.SMALL
        amount < 2.0 * max / 3.0 -> MarkerTier.MEDIUM
        else                     -> MarkerTier.LARGE
    }
}

/**
 * Builds the markers for the BG graph.
 *
 * Only manual boluses are drawn: an SMB every few minutes would turn the curve into a picket
 * fence, and SMBs are on the IOB graph with their own markers. Insulin sits [gap] above the
 * reading and carbs the same distance below it, growing away from each other, so a meal with its
 * bolus is one marker with a thin seam at the reading. Calibrations and BG checks are drawn at
 * their own measured value, which is the information they carry, with no interpolation.
 *
 * @param readings the series the curve is drawn from, used for the height of a marker
 * @param gap half the space between the two halves of a meal, in the axis' units
 * @param fallbackY height for a marker with no reading near it
 */
fun treatmentMarkers(
    boluses: List<BolusGraphPoint>,
    carbs: List<CarbsGraphPoint>,
    calibrations: List<CalibrationGraphPoint>,
    therapyEvents: List<TherapyEventGraphPoint>,
    readings: List<BgDataPoint>,
    minTimestamp: Long,
    minX: Double,
    maxX: Double,
    minY: Double,
    maxY: Double,
    gap: Double,
    fallbackY: Double
): TreatmentMarkers {
    val validBoluses = boluses.filter { it.isValid && it.bolusType == BolusType.NORMAL }
    val validCarbs = carbs.filter { it.isValid }
    val bolusTimes = validBoluses.map { it.timestamp }
    val carbsTimes = validCarbs.map { it.timestamp }
    val bolusAmounts = validBoluses.map { it.amount }
    val carbsAmounts = validCarbs.map { it.amount }

    /**
     * Height of the pair a treatment belongs to. Both halves of one meal are drawn at the height of
     * the earlier entry, so they line up into a clean diamond instead of a sheared one.
     */
    fun pairedY(timestamp: Long, partners: List<Long>): Double {
        val partner = partners
            .filter { abs(it - timestamp) <= MARKER_PAIR_MS }
            .minByOrNull { abs(it - timestamp) }
        val anchor = if (partner != null && partner < timestamp) partner else timestamp
        return interpolatedBgAt(anchor, readings) ?: fallbackY
    }

    fun inRange(x: Double) = x in minX..maxX

    val insulin = validBoluses.mapNotNull { bolus ->
        val x = timestampToX(bolus.timestamp, minTimestamp)
        if (!inRange(x)) return@mapNotNull null
        TreatmentMarker(
            x = x,
            y = (pairedY(bolus.timestamp, carbsTimes) + gap).coerceIn(minY, maxY),
            tier = tierOf(bolus.amount, bolusAmounts)
        )
    }
    val carbsMarkers = validCarbs.mapNotNull { entry ->
        val x = timestampToX(entry.timestamp, minTimestamp)
        if (!inRange(x)) return@mapNotNull null
        TreatmentMarker(
            x = x,
            y = (pairedY(entry.timestamp, bolusTimes) - gap).coerceIn(minY, maxY),
            tier = tierOf(entry.amount, carbsAmounts)
        )
    }

    val calibrationPoints = measuredGlucosePoints(calibrations, therapyEvents).mapNotNull { (timestamp, value) ->
        val x = timestampToX(timestamp, minTimestamp)
        if (!inRange(x)) return@mapNotNull null
        x to value.coerceIn(minY, maxY)
    }

    // Sorted by x: Vico needs every series in ascending x order
    return TreatmentMarkers(
        insulin = insulin.sortedBy { it.x },
        carbs = carbsMarkers.sortedBy { it.x },
        calibrations = calibrationPoints.sortedBy { it.first }
    )
}

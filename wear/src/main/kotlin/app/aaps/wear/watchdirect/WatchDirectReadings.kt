package app.aaps.wear.watchdirect

import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.interfaces.rx.weardata.EventData
import java.util.Locale
import kotlin.math.abs

/**
 * The rules for readings the watch takes from the sensor itself (direct-to-watch). No Android here,
 * so all of it is tested on the JVM.
 *
 * The phone and the watch get the same reading within seconds. The phone's copy is richer (its delta
 * comes from the whole history, and it carries the phone's corrections), so it wins for the same
 * reading. The watch's own copy only shows when it is newer than anything from the phone, that is
 * when the phone is away.
 *
 * Every value built here has [EventData.SingleBg.fromSensor] set. Such a value is for the faces,
 * complications and tiles of this watch only: nothing in the watch app sends it to the phone.
 *
 * Adapted from `G7WatchReadings` in nightscout/AndroidAPS pull request 5397 (commit `bec1b16ba20`),
 * see the NOTICE file of this module.
 */
object WatchDirectReadings {

    /** Two values closer than this are the same sensor reading, seen by the phone and by the watch. */
    const val SAME_READING_MS = 60_000L

    /** Graph entries older than this are dropped when the watch adds its own. */
    const val GRAPH_WINDOW_MS = 24 * 60 * 60_000L

    /** Below this the low is urgent, whatever the phone's low line is. */
    const val URGENT_LOW_MGDL = 55.0

    /** Used until the phone has sent its own lines. */
    const val DEFAULT_LOW_MGDL = 70.0
    const val DEFAULT_HIGH_MGDL = 180.0

    /** The phone counts as away when it has sent no reading for this long. */
    const val PHONE_SILENT_MS = 15 * 60_000L

    /** How often a low that goes on is raised again while the phone stays away. */
    const val LOW_REPEAT_MS = 30 * 60_000L
    const val URGENT_LOW_REPEAT_MS = 15 * 60_000L

    /** The sensor's own range. A value outside it is not shown. */
    private const val MIN_MGDL = 40.0
    private const val MAX_MGDL = 400.0

    /** True for a value the face may show. */
    fun isUsable(reading: WatchSensorReading): Boolean = reading.mgdl in MIN_MGDL..MAX_MGDL && reading.timestampMs > 0

    /**
     * The arrow for the sensor's rate of change: one step per mg/dL per minute. The same thresholds as
     * the phone's ingest of this sensor (`DexcomOnePlusIngest.trendArrowFor`).
     */
    fun trendArrow(slopeMgdlPerMin: Double?): TrendArrow = when {
        slopeMgdlPerMin == null -> TrendArrow.NONE
        slopeMgdlPerMin >= 3.0  -> TrendArrow.DOUBLE_UP
        slopeMgdlPerMin >= 2.0  -> TrendArrow.SINGLE_UP
        slopeMgdlPerMin >= 1.0  -> TrendArrow.FORTY_FIVE_UP
        slopeMgdlPerMin > -1.0  -> TrendArrow.FLAT
        slopeMgdlPerMin > -2.0  -> TrendArrow.FORTY_FIVE_DOWN
        slopeMgdlPerMin > -3.0  -> TrendArrow.SINGLE_DOWN
        else                    -> TrendArrow.DOUBLE_DOWN
    }

    /**
     * One sensor reading as the watch face shows it. Units and the high and low lines come from the
     * phone's last reading ([phoneTemplate]); the delta from the reading about five minutes before,
     * in [history] (mg/dL, from either source).
     */
    fun toSingleBg(reading: WatchSensorReading, phoneTemplate: EventData.SingleBg?, history: List<EventData.SingleBg>): EventData.SingleBg {
        val mgdl = reading.mgdl
        val mmol = phoneTemplate?.glucoseUnits == GlucoseUnit.MMOL.asText
        val units = if (mmol) GlucoseUnit.MMOL else GlucoseUnit.MGDL
        val high = phoneTemplate?.high?.takeIf { it > 0 } ?: DEFAULT_HIGH_MGDL
        val low = phoneTemplate?.low?.takeIf { it > 0 } ?: DEFAULT_LOW_MGDL
        val delta = previous(reading.timestampMs, history)?.let { mgdl - it.sgv }
        val shortDelta = delta?.let { deltaString(it, mmol, detailed = false) } ?: NO_DELTA
        val longDelta = delta?.let { deltaString(it, mmol, detailed = true) } ?: NO_DELTA
        return EventData.SingleBg(
            dataset = 0,
            timeStamp = reading.timestampMs,
            sgvString = if (mmol) format("%.1f", mgdl * Constants.MGDL_TO_MMOLL) else format("%.0f", mgdl),
            glucoseUnits = units.asText,
            slopeArrow = trendArrow(reading.slopeMgdlPerMin).symbol,
            delta = shortDelta,
            deltaDetailed = longDelta,
            avgDelta = shortDelta,
            avgDeltaDetailed = longDelta,
            sgvLevel = if (mgdl > high) 1L else if (mgdl < low) -1L else 0L,
            sgv = mgdl,
            high = high,
            low = low,
            deltaMgdl = delta,
            avgDeltaMgdl = delta,
            fromSensor = true
        )
    }

    /** The reading 3 to 7 minutes before [timestamp] closest to 5 minutes, for the delta. */
    private fun previous(timestamp: Long, history: List<EventData.SingleBg>): EventData.SingleBg? =
        history.filter { timestamp - it.timeStamp in 3 * 60_000L..7 * 60_000L && it.sgv > 0 }
            .minByOrNull { abs(timestamp - it.timeStamp - 5 * 60_000L) }

    private fun deltaString(deltaMgdl: Double, mmol: Boolean, detailed: Boolean): String {
        val sign = if (deltaMgdl >= 0) "+" else "-"
        val value = abs(deltaMgdl)
        return sign + when {
            mmol && detailed -> format("%.2f", value * Constants.MGDL_TO_MMOLL)
            mmol             -> format("%.1f", value * Constants.MGDL_TO_MMOLL)
            detailed         -> format("%.1f", value)
            else             -> format("%.0f", value)
        }
    }

    private fun format(pattern: String, value: Double) = String.format(Locale.getDefault(), pattern, value)

    /**
     * Which reading the watch face shows: [stored] or [incoming]. The newest one, except that the
     * phone's copy of the same reading wins over the watch's own.
     */
    fun choose(stored: EventData.SingleBg, incoming: EventData.SingleBg): EventData.SingleBg = when {
        incoming.fromSensor -> if (stored.timeStamp >= incoming.timeStamp - SAME_READING_MS) stored else incoming
        else                -> if (stored.fromSensor && stored.timeStamp > incoming.timeStamp + SAME_READING_MS) stored else incoming
    }

    /** [entries] with [added] put in where no reading of the same time is yet, and old ones dropped. */
    fun mergeIntoGraph(entries: List<EventData.SingleBg>, added: List<EventData.SingleBg>): ArrayList<EventData.SingleBg> {
        val merged = ArrayList(entries)
        for (bg in added) {
            if (merged.none { abs(it.timeStamp - bg.timeStamp) < SAME_READING_MS }) merged += bg
        }
        val newest = merged.maxOfOrNull { it.timeStamp } ?: return merged
        merged.removeAll { it.timeStamp < newest - GRAPH_WINDOW_MS }
        merged.sortBy { it.timeStamp }
        return merged
    }

    enum class Low { LOW, URGENT_LOW }

    /** The low a reading of [mgdl] is, against the phone's [lowLineMgdl]; null when it is not low. */
    fun lowLevel(mgdl: Double, lowLineMgdl: Double): Low? = when {
        mgdl < URGENT_LOW_MGDL -> Low.URGENT_LOW
        mgdl < lowLineMgdl     -> Low.LOW
        else                   -> null
    }

    /**
     * True when the watch raises its own low alarm now. Only while the phone is away, because the
     * phone owns the alarms otherwise. A new or deeper low goes off at once; one that goes on is
     * repeated.
     */
    fun shouldRaise(level: Low?, phoneSilent: Boolean, lastRaised: Low?, lastRaisedAt: Long, now: Long): Boolean {
        if (level == null || !phoneSilent) return false
        if (lastRaised == null || level.ordinal > lastRaised.ordinal) return true
        val repeat = if (level == Low.URGENT_LOW) URGENT_LOW_REPEAT_MS else LOW_REPEAT_MS
        return now - lastRaisedAt >= repeat
    }

    private const val NO_DELTA = "--"
}

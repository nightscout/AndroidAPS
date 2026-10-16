package app.aaps.wear.g7

import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.interfaces.rx.weardata.EventData
import java.util.Locale
import kotlin.math.abs

/**
 * The rules for readings the watch takes from the sensor itself. No Android here, so all of it is tested
 * on the JVM.
 *
 * The phone and the watch get the same reading in the same second. The phone's copy is richer (its delta
 * comes from the whole history), so it wins for the same reading. The watch's own copy only shows when
 * it is newer than anything from the phone, that is when the phone is away.
 */
object G7WatchReadings {

    /** Two values closer than this are the same sensor reading, seen by the phone and by the watch. */
    const val SAME_READING_MS = 60_000L

    /** Graph entries older than this are dropped when the watch adds its own. */
    const val GRAPH_WINDOW_MS = 24 * 60 * 60_000L

    /** Below this the low is urgent, whatever the phone's low line is. */
    const val URGENT_LOW_MGDL = 55.0

    /** Used until the phone has sent its own low line. */
    const val DEFAULT_LOW_MGDL = 70.0
    const val DEFAULT_HIGH_MGDL = 180.0

    /** The phone counts as not reachable when it has sent no reading for this long. */
    const val PHONE_SILENT_MS = 15 * 60_000L

    /** How often a low that goes on is raised again while the phone stays away. */
    const val LOW_REPEAT_MS = 30 * 60_000L
    const val URGENT_LOW_REPEAT_MS = 15 * 60_000L

    /**
     * One sensor reading as the watch face shows it. Units and the high and low lines come from the
     * phone's last reading ([phoneTemplate]); the delta from the reading about five minutes before, in
     * [history] (mg/dL, from either source).
     */
    fun toSingleBg(timestamp: Long, mgdl: Double, arrow: TrendArrow, phoneTemplate: EventData.SingleBg?, history: List<EventData.SingleBg>): EventData.SingleBg {
        val mmol = phoneTemplate?.glucoseUnits == GlucoseUnit.MMOL.asText
        val units = if (mmol) GlucoseUnit.MMOL else GlucoseUnit.MGDL
        val high = phoneTemplate?.high?.takeIf { it > 0 } ?: DEFAULT_HIGH_MGDL
        val low = phoneTemplate?.low?.takeIf { it > 0 } ?: DEFAULT_LOW_MGDL
        val delta = previous(timestamp, history)?.let { mgdl - it.sgv }
        return EventData.SingleBg(
            dataset = 0,
            timeStamp = timestamp,
            sgvString = if (mmol) format("%.1f", mgdl * Constants.MGDL_TO_MMOLL) else format("%.0f", mgdl),
            glucoseUnits = units.asText,
            slopeArrow = arrow.symbol,
            delta = delta?.let { deltaString(it, mmol, detailed = false) } ?: "--",
            deltaDetailed = delta?.let { deltaString(it, mmol, detailed = true) } ?: "--",
            avgDelta = delta?.let { deltaString(it, mmol, detailed = false) } ?: "--",
            avgDeltaDetailed = delta?.let { deltaString(it, mmol, detailed = true) } ?: "--",
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
     * Which reading the watch face shows: [stored] or [incoming]. The newest one, except that the phone's
     * copy of the same reading wins over the watch's own.
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

    /** The low a sensor reading of [mgdl] is, against the phone's [lowLineMgdl]; null when it is not low. */
    fun lowLevel(mgdl: Double, lowLineMgdl: Double): Low? = when {
        mgdl < URGENT_LOW_MGDL -> Low.URGENT_LOW
        mgdl < lowLineMgdl     -> Low.LOW
        else                   -> null
    }

    /**
     * True when the watch raises its own low alarm now. Only while the phone is away, since the phone
     * owns the alarms otherwise. A new or deeper low goes off at once; one that goes on is repeated.
     */
    fun shouldRaise(level: Low?, phoneSilent: Boolean, lastRaised: Low?, lastRaisedAt: Long, now: Long): Boolean {
        if (level == null || !phoneSilent) return false
        if (lastRaised == null || level.ordinal > lastRaised.ordinal) return true
        val repeat = if (level == Low.URGENT_LOW) URGENT_LOW_REPEAT_MS else LOW_REPEAT_MS
        return now - lastRaisedAt >= repeat
    }
}

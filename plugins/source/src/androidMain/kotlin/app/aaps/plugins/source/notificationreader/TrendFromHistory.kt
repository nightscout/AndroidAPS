package app.aaps.plugins.source.notificationreader

import app.aaps.core.data.model.GV
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.data.time.T

/**
 * The trend arrow of a reading from a notification, worked out from the readings stored before it.
 *
 * A notification gives the reader only the number: [NotificationParser] removes an arrow character to
 * read it. So every reading was stored with [TrendArrow.NONE], and Nightscout, every app that follows
 * it, the BG list and the watches that send the stored arrow showed no trend (#5648). The main screen
 * was not affected, it works out its own trend.
 *
 * The slope is taken against the newest stored reading that is at least 4 minutes older, so it covers
 * about 5 minutes for 1-minute and 5-minute sensors alike. Against the reading of 1 minute before, one
 * step of 0.1 mmol/L (1.8 mg/dL) would already give an arrow. A reading older than [maxAge] says
 * nothing about the trend now, so without a newer one the reading has no arrow, as before.
 */
internal object TrendFromHistory {

    /** How far back [arrow] looks. */
    val maxAge = T.mins(15).msecs()

    private val minAge = T.mins(4).msecs()

    /**
     * The arrow of a reading of [valueMgdl] at [timestamp]. [history] are the stored readings, in any
     * order. Only the ones from [maxAge] to 4 minutes before [timestamp] are used.
     */
    fun arrow(timestamp: Long, valueMgdl: Double, history: List<GV>): TrendArrow {
        val previous = history
            .filter { (timestamp - it.timestamp) in minAge..maxAge }
            .maxByOrNull { it.timestamp }
            ?: return TrendArrow.NONE
        val minutes = (timestamp - previous.timestamp).toDouble() / T.mins(1).msecs()
        return TrendArrow.fromSlope((valueMgdl - previous.value) / minutes)
    }
}

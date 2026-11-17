package app.aaps.ui.compose.overview

import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.getAndUpdate

/**
 * Keeps values that are not a finite number (NaN or infinite) out of the graphs, and reports where
 * they came from.
 *
 * The graph library cannot draw such a value. One NaN in a series made the whole axis NaN, Vico threw
 * "Corner size in Px can't be negative (... NaN)" while drawing a point, and the main screen crashed
 * on every start while the loop went on in the background (issue #5525).
 *
 * Dropping the point keeps the screen usable, but dropping it silently would also hide the bug that
 * made the value: the crash was the only report we had. So every series that has such a value is
 * sent to Crashlytics as a non-fatal error that names the series. That tells which data source or
 * calculation to look at. [FabricPrivacy.logException] writes it to the AAPS log too.
 *
 * Each series is reported once for the life of this object (one per cache): the graphs are rebuilt
 * every few minutes, and the same stored value would otherwise send a report every time.
 */
internal class NonFiniteGraphValues(private val fabricPrivacy: FabricPrivacy) {

    private val reportedSeries = MutableStateFlow(emptySet<String>())

    /**
     * [points] without the ones whose [value] is NaN or infinite. A null value means "no value" (for
     * example a therapy event without glucose) and is kept. Returns [points] itself when all are fine.
     */
    fun <T> drop(series: String, points: List<T>, timestamp: (T) -> Long, value: (T) -> Double?): List<T> {
        val firstBad = points.firstOrNull { isNonFinite(value(it)) } ?: return points
        val kept = points.filterNot { isNonFinite(value(it)) }
        report(series, points.size - kept.size, timestamp(firstBad), value(firstBad))
        return kept
    }

    private fun isNonFinite(value: Double?): Boolean = value != null && !value.isFinite()

    private fun report(series: String, count: Int, firstTimestamp: Long, firstValue: Double?) {
        if (series in reportedSeries.getAndUpdate { it + series }) return
        fabricPrivacy.logException(
            NonFiniteGraphValueException("Graph series '$series': $count value(s) not drawn, not a finite number. First: $firstValue at $firstTimestamp")
        )
    }
}

/** What [NonFiniteGraphValues] reports. A type of its own, so these reports are easy to find in Crashlytics. */
internal class NonFiniteGraphValueException(message: String) : IllegalStateException(message)

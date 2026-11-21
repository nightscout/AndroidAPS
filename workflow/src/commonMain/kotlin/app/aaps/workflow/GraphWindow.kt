package app.aaps.workflow

import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.overview.OverviewData
import app.aaps.core.interfaces.overview.liveGraphWindowEnd
import kotlinx.datetime.TimeZone
import kotlin.math.max
import kotlin.time.Instant

/**
 * Where the graph window ends, and which phase may change it.
 *
 * `TimeRange.endTime` is the right edge of the data the overview needs: `toTime` plus the prediction
 * horizon of the last loop run. Two phases of one run write it, and only one of them knows the
 * horizon:
 *
 * - [PostCalculationRunner] runs the loop, so it **owns** `endTime`. It writes the real value on
 *   every pass: longer while there are predictions, back to `toTime` when there are none.
 * - [PrepareGraphDataRunner] runs first and only moves the window along, so it may **widen but
 *   never narrow** `endTime`. It used to write `endTime = toTime`, which threw the horizon away for
 *   the rest of its own pass - and the IOB, activity and BGI series are built in that pass, so they
 *   stopped at the next full hour while the axis went on to the newest prediction.
 *
 * Keeping the two apart is what [keptEndTime] is for.
 *
 * `toTime` follows the clock, so a live run moves it first, before it picks anything with it, see
 * [moveLiveWindowTo].
 */

/**
 * Moves the window of the live graph on to [now], at the start of a live run (#5614).
 *
 * The prepare pass picks the readings, the 5 minute values and the IOB series with `toTime`, and only the post
 * pass at the end of the run moved it on. So the first run after the clock passed the old `toTime` drew with the
 * window of the run before: everything after it was missing until the next reading. That is once an hour, and
 * after a gap in the readings also the readings filled in for the gap. A loop change hid it often, because it
 * starts another run at once.
 *
 * Only forwards: moving it back, after a clock or time zone change, is left to the post pass, as before. The
 * width the post pass gave the window is kept, and `endTime` is only widened to the new `toTime`, never
 * narrowed (see [keptEndTime]). Not for the history browser, which sets its own window.
 */
internal fun OverviewData.moveLiveWindowTo(now: Instant, zone: TimeZone) {
    val newToTime = liveGraphWindowEnd(now, zone)
    if (newToTime <= toTime) return
    val width = toTime - fromTime
    toTime = newToTime
    fromTime = newToTime - width
    endTime = max(endTime, newToTime)
}

/**
 * How many of the window's [Constants.GRAPH_TIME_RANGE_HOURS] hours are given to the future.
 *
 * Only the amount of history to fetch is derived from this, so that the window keeps its width as
 * the horizon grows. It is **not** a bound on `endTime`: the predictions are not clamped, the axis
 * follows the newest of them, and a series that stopped at a clamped `endTime` would end before the
 * axis does.
 */
internal const val PREDICTION_WINDOW_HOURS = 2

/**
 * The `endTime` a prepare pass should write for a window ending at [toTime], given the
 * [previousEndTime] already in the cache (null once the cache has been reset).
 *
 * Never earlier than [toTime], and never earlier than a horizon that is already known - that is the
 * whole point. The upper guard is only there for a clock or time zone change that moves [toTime]
 * backwards by hours: a real horizon is at most 48 five minute predictions, so just under four hours
 * (`DetermineBasalSMB` caps every prediction array at 48), and a value beyond that is not a horizon.
 * Keeping it would make this pass walk hours of extra five minute steps. The post phase writes the
 * true value at the end of the same run, so a kept value that is slightly too wide costs one window.
 */
internal fun keptEndTime(previousEndTime: Long?, toTime: Long): Long =
    (previousEndTime ?: toTime).coerceIn(toTime, toTime + T.hours(MAX_KEPT_HORIZON_HOURS).msecs())

private const val MAX_KEPT_HORIZON_HOURS = 6L

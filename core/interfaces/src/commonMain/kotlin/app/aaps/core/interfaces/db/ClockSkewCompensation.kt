package app.aaps.core.interfaces.db

import app.aaps.core.data.model.TimeStamped
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.utils.DateUtil
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch

/**
 * Maximum NTP-drift between an APS phone and an AAPSCLIENT phone that we are
 * willing to compensate for. NTP-synced devices stay within ~1s; cellular vs
 * Wi-Fi handoff may briefly add a couple of seconds. 5s is a generous ceiling.
 *
 * Future-stamped rows beyond this gap are treated as legitimately scheduled
 * (e.g. profile switches with future start) and left alone.
 */
private const val SKEW_TOLERANCE_MS = 5_000L

/**
 * On AAPSCLIENT: when an emission contains a [TimeStamped] item whose
 * `timestamp` lies slightly in the future relative to local now (typical of
 * clock drift between the writing APS phone and this client), schedule a
 * delayed re-emission so the collector re-reads DB state once local time
 * crosses the row timestamp. Active-at queries (`WHERE timestamp <= now`)
 * otherwise exclude the new row and the UI keeps showing the previous state
 * until the next user interaction.
 *
 * The compensation is bounded by [SKEW_TOLERANCE_MS]. Larger gaps are treated
 * as legitimately scheduled future events and ignored — query semantics
 * elsewhere remain unchanged.
 *
 * No-op on the APS phone — it is the writer, so its own rows can't be
 * future-stamped relative to itself.
 */
fun <E : TimeStamped> Flow<List<E>>.compensateForClockSkew(
    config: Config,
    dateUtil: DateUtil
): Flow<List<E>> {
    if (!config.AAPSCLIENT) return this
    return channelFlow {
        collect { items ->
            send(items)
            // Empty list: maxOfOrNull returns null → 0L - now is a large negative number,
            // safely outside the 1..SKEW_TOLERANCE_MS range so no re-emission is scheduled.
            val maxFutureGap = (items.maxOfOrNull { it.timestamp } ?: 0L) - dateUtil.now()
            if (maxFutureGap in 1..SKEW_TOLERANCE_MS) {
                // +500ms buffer covers the DB-write → Room-notify → Flow-emission round trip
                // so the collector's re-read sees the now-current row instead of racing it.
                launch {
                    delay(maxFutureGap + 500L)
                    send(items)
                }
            }
        }
    }
}

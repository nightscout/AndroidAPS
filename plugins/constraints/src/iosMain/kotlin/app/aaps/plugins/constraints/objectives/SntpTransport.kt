package app.aaps.plugins.constraints.objectives

import kotlin.time.TimeSource

/**
 * Sends one SNTP request and waits for the answer: the part of the iOS time check that talks to the
 * network. It is kept apart so [IosSntpClient] can be tested without a network, and so the arithmetic
 * stays in the shared [SntpPacket].
 */
interface SntpTransport {

    /**
     * Sends a request to [host] on [port], and returns the answer with the clock readings around it.
     * Returns null when no answer came within [timeoutMs], for any reason.
     *
     * [buildRequest] gets the wall clock time at the moment the request goes out, so the time written
     * into the packet and the time used in the arithmetic are the same reading.
     */
    suspend fun exchange(host: String, port: Int, timeoutMs: Long, buildRequest: (requestTime: Long) -> ByteArray): SntpExchange?
}

/**
 * One request and its answer.
 *
 * [requestTime] is the wall clock when the request was sent. [requestTicks] and [responseTicks] are
 * [monotonicMillis] readings. The time between them is the network delay that the SNTP arithmetic
 * corrects for, and it must not change if the wall clock is set while the request is out.
 */
class SntpExchange(
    val response: ByteArray,
    val requestTime: Long,
    val requestTicks: Long,
    val responseTicks: Long
)

private val monotonicOrigin = TimeSource.Monotonic.markNow()

/**
 * Milliseconds from a clock that only moves forward. The iOS counterpart of `elapsedRealtime()` in
 * `JvmSntpClient`. Only differences between two readings mean anything.
 */
internal fun monotonicMillis(): Long = monotonicOrigin.elapsedNow().inWholeMilliseconds

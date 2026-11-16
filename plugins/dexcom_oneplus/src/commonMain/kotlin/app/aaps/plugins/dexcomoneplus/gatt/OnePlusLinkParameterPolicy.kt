package app.aaps.plugins.dexcomoneplus.gatt

/**
 * When the driver asks Android for its normal link parameters (`CONNECTION_PRIORITY_BALANCED`).
 *
 * A ONE+ sensor can store "preferred connection parameters" (PPCP) that allow a high peripheral
 * latency: on a pre-soak sensor seen in the field, 50-75 ms with latency 40. Android reads them at
 * the end of pairing and applies them on every later connect to the bonded sensor. With latency 40
 * the sensor may skip 40 connection events, so each request and answer takes about 2.5 s instead of
 * 0.1-0.2 s, and the sensor hangs up before the EGV arrives. The pairing session itself still runs on
 * Android's defaults, which is why each new pairing gave exactly one reading and then nothing.
 *
 * The rule only acts when that state is seen: Android reported a link with peripheral latency above
 * 0. A link that is already normal (latency 0, as every production sensor in the field logs) gets no
 * request at all, so phones where the connection works see no change.
 *
 * At most [MAX_REQUESTS_PER_LINK] requests per link. Android can apply the sensor's values again after
 * its short fast interval for service discovery, so one request may not be enough; the cap makes sure
 * a phone that never honours the request is not asked again and again.
 */
internal object OnePlusLinkParameterPolicy {

    const val MAX_REQUESTS_PER_LINK = 3

    /**
     * True when the driver should ask for BALANCED after Android reported [peripheralLatency] for the
     * current link. [requestsThisLink] is how many times it already asked on this link.
     */
    fun requestNormalAfterUpdate(peripheralLatency: Int, requestsThisLink: Int): Boolean =
        peripheralLatency > 0 && requestsThisLink < MAX_REQUESTS_PER_LINK

    /** Human readable link parameters for the log: Android reports the interval in 1.25 ms and the timeout in 10 ms units. */
    fun describe(interval: Int, latency: Int, timeout: Int, status: Int): String =
        "interval=${interval * 125 / 100.0}ms latency=$latency timeout=${timeout * 10}ms status=$status"
}

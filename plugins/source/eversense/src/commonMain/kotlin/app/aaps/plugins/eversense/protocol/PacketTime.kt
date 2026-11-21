package app.aaps.plugins.eversense.protocol

import kotlin.time.Clock

/**
 * The time the protocol writes into packets. A test runs one block against a fixed time with [withClock]; the
 * driver itself never sets it. One clock for the whole process: tests run one after the other.
 */
internal object PacketTime {

    private var fixed: (() -> Long)? = null

    fun nowMillis(): Long = fixed?.invoke() ?: Clock.System.now().toEpochMilliseconds()

    /**
     * How far ahead of the phone's clock a reading may be dated. A full sync sets the transmitter's clock when it is
     * more than 10 s off, so a reading later than this is a wrong time. Taken, it would be the "last reading" and
     * every real reading would be refused as not newer until the clock passed it.
     */
    const val MAX_AHEAD_MS = 2 * 60_000L

    /** True when [timestamp] is later than the phone's clock allows for a reading. */
    fun isAhead(timestamp: Long): Boolean = timestamp > nowMillis() + MAX_AHEAD_MS

    fun <T> withClock(nowMillis: () -> Long, operation: () -> T): T {
        val previous = fixed
        fixed = nowMillis
        return try {
            operation()
        } finally {
            fixed = previous
        }
    }
}

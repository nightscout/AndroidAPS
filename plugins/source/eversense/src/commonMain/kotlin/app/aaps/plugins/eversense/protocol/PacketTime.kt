package app.aaps.plugins.eversense.protocol

import kotlin.time.Clock

/**
 * The time the protocol writes into packets. A test runs one block against a fixed time with [withClock]; the
 * driver itself never sets it. One clock for the whole process: tests run one after the other.
 */
internal object PacketTime {

    private var fixed: (() -> Long)? = null

    fun nowMillis(): Long = fixed?.invoke() ?: Clock.System.now().toEpochMilliseconds()

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

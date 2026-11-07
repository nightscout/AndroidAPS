package app.aaps.plugins.eversense.protocol

import java.time.Clock

/** A scoped clock lets one command run against a supplied time source without global mutation. */
internal object PacketTime {
    private val clock = ThreadLocal<Clock>()
    fun nowMillis(): Long = clock.get()?.millis() ?: System.currentTimeMillis()
    fun <T> withClock(source: Clock, operation: () -> T): T {
        val previous = clock.get()
        clock.set(source)
        return try { operation() } finally {
            if (previous == null) clock.remove() else clock.set(previous)
        }
    }
}

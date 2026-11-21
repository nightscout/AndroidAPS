package app.aaps.pump.aaruy.common.util

import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/** As `SystemClock.sleep`: an interrupt does not end the sleep, and the thread is interrupted again after it. */
internal actual fun sleepBlocking(ms: Long) {
    val end = TimeSource.Monotonic.markNow() + ms.milliseconds
    var interrupted = false
    while (end.hasNotPassedNow()) {
        try {
            Thread.sleep((-end.elapsedNow()).inWholeMilliseconds.coerceAtLeast(1))
        } catch (_: InterruptedException) {
            interrupted = true
        }
    }
    if (interrupted) Thread.currentThread().interrupt()
}

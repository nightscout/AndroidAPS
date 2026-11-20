package app.aaps.plugins.libre3.reconnect

import app.aaps.core.interfaces.concurrent.AapsLock
import app.aaps.core.interfaces.concurrent.withLock

/**
 * Budget for Bluetooth scan starts, shared by every Libre 3 slot in this process.
 *
 * Android allows about five `startScan` calls per thirty seconds per app. Over that, the platform
 * fails in silence: `startScan` still reports success and the callback simply never reports a
 * device. The symptom looks exactly like a sensor out of range, so a driver that keeps trying makes
 * it worse instead of better.
 *
 * One session opens with a twenty second scan, and two slots that both retry after three seconds
 * can reach about twenty starts in thirty seconds, four times over the quota. So the starts are
 * counted here and a start that has no room waits instead.
 *
 * [MAX_SCAN_STARTS] is four and not five, so one start per window is left for the rest of AAPS.
 *
 * ⚠️ ASYNC IMPACT: every method is synchronized and none of them waits. The caller decides whether
 * it may sleep, and only the slot's own Bluetooth thread ever does.
 */
object Libre3ScanBudget {

    /** How many scan starts this app allows itself per window. */
    const val MAX_SCAN_STARTS = 4

    /** The window the platform counts scan starts in. */
    const val WINDOW_MS = 30_000L

    /**
     * Guards [starts]. An [AapsLock] rather than `@Synchronized`, which is JVM only, and reentrant
     * the same way the object monitor was, so a guarded method may still call another one.
     */
    private val lock = AapsLock()

    private val starts = ArrayDeque<Long>()

    /**
     * Takes one start slot when there is room.
     *
     * Taking the slot and counting it is one step on purpose. Two slots on their own threads could
     * both pass the same free slot if the check and the count were apart.
     *
     * @return true when a scan may start now. It then counts against the window.
     */
    fun tryAcquire(nowMs: Long): Boolean = lock.withLock {
        prune(nowMs)
        if (starts.size >= MAX_SCAN_STARTS) return false
        starts.addLast(nowMs)
        return true
    }

    /** How long to wait before a start would be allowed, 0 when one is allowed now. */
    fun waitMsUntilNextStart(nowMs: Long): Long = lock.withLock {
        prune(nowMs)
        if (starts.size < MAX_SCAN_STARTS) return 0L
        return (WINDOW_MS - (nowMs - starts.first())).coerceAtLeast(0L)
    }

    /** How many starts are still inside the window. For logs and tests. */
    fun startsInWindow(nowMs: Long): Int = lock.withLock {
        prune(nowMs)
        return starts.size
    }

    /** Test hook: forget the window. */
    fun reset() = lock.withLock {
        starts.clear()
    }

    private fun prune(nowMs: Long) {
        // A clock that jumped backwards drops its entries too, so a bad time cannot wedge the budget.
        while (starts.isNotEmpty() && (nowMs - starts.first() >= WINDOW_MS || nowMs < starts.first())) {
            starts.removeFirst()
        }
    }
}

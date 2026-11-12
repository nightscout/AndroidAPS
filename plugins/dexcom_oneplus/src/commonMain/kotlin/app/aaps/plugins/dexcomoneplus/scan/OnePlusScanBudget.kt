package app.aaps.plugins.dexcomoneplus.scan

import app.aaps.core.interfaces.concurrent.AapsLock
import app.aaps.core.interfaces.concurrent.withLock

/**
 * Process-wide LE scan start budget shared by **all** ONE+ slots (production + staging) and the UI
 * discovery scan.
 *
 * Android silently stops delivering results when an app starts more than
 * [MAX_STARTS_PER_WINDOW_PLATFORM] LE scans within [WINDOW_MS] (`ScanManager`
 * "app is scanning too frequently"): no `onScanFailed`, no error — the callback simply never fires.
 *
 * The quota counts scan **registrations**, not scan time, so one long window is far cheaper than
 * several short ones. The persistent ADV wait therefore uses
 * `OnePlusBleSessionCyclePolicy.PERSISTENT_ADV_SCAN_MS` (20 s) rather than the per-OEM
 * `OemDeviceProfile.preConnectScanMs`: about 3 starts/min per slot, so even two slots plus the UI
 * discovery scan stay inside the platform's 10/min.
 *
 * ⚠️ This used to read "roughly every 8.25 s (~7.3 starts/min), which is under the quota". That
 * only ever held for the Samsung profile (`preConnectScanMs` 8 s). Generic used 3 s and Pixel 2 s,
 * giving 3.25 s / 2.25 s cycles — 18 and 27 starts/min, i.e. a *single* slot structurally over the
 * quota. The CUBOT field log of 2026-08-20 measured 18.3 starts/min, 13 platform throttle events,
 * and the budget deferring its own scans for 54 % of the wait. Keep any future window/delay change
 * consistent with the arithmetic above.
 *
 * This budget keeps one slot free for the UI discovery scan, so opening the Start screen can never
 * be starved by the background waits either.
 *
 * ⚠️ ASYNC IMPACT: [waitMsFor] / [record] take [lock] and are non-blocking; the *caller* decides
 * whether it may sleep (background BLE executor: yes — UI thread: never, it only [record]s).
 */
object OnePlusScanBudget {

    /** Platform accounting window for the "scanning too frequently" throttle. */
    const val WINDOW_MS = 30_000L

    /** What the platform actually allows per [WINDOW_MS]. */
    const val MAX_STARTS_PER_WINDOW_PLATFORM = 5

    /** What we allow ourselves — one slot kept in reserve for the UI discovery scan. */
    const val MAX_STARTS_PER_WINDOW = MAX_STARTS_PER_WINDOW_PLATFORM - 1

    /** Longest single sleep a waiting caller does before re-checking (keeps `stop` responsive). */
    const val MAX_SLEEP_SLICE_MS = 500L

    /**
     * Guards everything below. An [AapsLock] rather than `@Synchronized`, which is JVM only, so
     * this budget stays in shared code. It is reentrant, like the object monitor it
     * replaces, which [reserve] relies on when it calls [waitMsFor].
     */
    private val lock = AapsLock()

    private val starts = ArrayDeque<Long>()

    /** Elapsed-time until which no start is granted at all — see [blockFor]. */
    private var blockedUntilMs = 0L

    /**
     * Hold every scan start back for [durationMs], on top of the normal quota.
     *
     * Used when a caller has good reason to believe the OS is silently refusing our scans: the model
     * of the platform quota is then known to be too optimistic on this device, and the only safe
     * reaction is to scan less until the platform window has certainly rolled over.
     */
    fun blockFor(nowMs: Long, durationMs: Long) = lock.withLock {
        val until = nowMs + durationMs.coerceAtLeast(0L)
        if (until > blockedUntilMs) blockedUntilMs = until
    }

    /**
     * Milliseconds to wait before another `startScan` may be issued (0 = free slot available now).
     * Pure function of the recorded history — safe to call from any thread.
     */
    fun waitMsFor(nowMs: Long): Long = lock.withLock {
        prune(nowMs)
        val blocked = (blockedUntilMs - nowMs).coerceAtLeast(0L)
        if (blocked > 0L) return@withLock blocked
        if (starts.size < MAX_STARTS_PER_WINDOW) return@withLock 0L
        val oldest = starts.first()
        (WINDOW_MS - (nowMs - oldest)).coerceAtLeast(0L)
    }

    /** Book a scan start at [nowMs]. Callers must invoke this for **every** `startScan`. */
    fun record(nowMs: Long) = lock.withLock {
        prune(nowMs)
        starts.addLast(nowMs)
    }

    /**
     * Atomically take a start slot: returns 0 with the start booked, or the milliseconds to wait
     * before retrying. Checking [waitMsFor] and then [record] separately would let two slots racing
     * on their own BLE executors both pass the same free slot.
     */
    fun reserve(nowMs: Long): Long = lock.withLock {
        val wait = waitMsFor(nowMs)
        if (wait > 0L) return@withLock wait
        starts.addLast(nowMs)
        0L
    }

    /** Recorded starts still inside the platform window (diagnostics / tests). */
    fun startsInWindow(nowMs: Long): Int = lock.withLock {
        prune(nowMs)
        starts.size
    }

    /** Test seam — drops the recorded history. */
    fun reset() = lock.withLock {
        starts.clear()
        blockedUntilMs = 0L
    }

    private fun prune(nowMs: Long) {
        // Also drops entries from a backwards clock jump so a bad timestamp cannot wedge the budget.
        while (starts.isNotEmpty() && (nowMs - starts.first() >= WINDOW_MS || nowMs < starts.first())) {
            starts.removeFirst()
        }
    }
}

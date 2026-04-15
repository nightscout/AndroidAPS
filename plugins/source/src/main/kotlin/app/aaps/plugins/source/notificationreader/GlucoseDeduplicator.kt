package app.aaps.plugins.source.notificationreader

import app.aaps.plugins.source.notificationreader.GlucoseDeduplicator.Companion.SNAP_UP_CONSECUTIVE
import app.aaps.plugins.source.notificationreader.GlucoseDeduplicator.Companion.snapGapToKnownIntervalMs
import org.json.JSONArray
import org.json.JSONObject

/**
 * Per-package deduplication for glucose readings extracted from CGM notifications.
 *
 * The same CGM notification is often re-posted multiple times during a single sensor cycle,
 * producing duplicate readings. This class enforces a per-package interval window and, when a
 * notification arrives early but carries a *different* value, treats it as evidence that the
 * effective sensor cadence is shorter than currently assumed (snap-down).
 *
 * Adaptation rules:
 *  - **Snap down** (shorter interval) — single sample is enough. Triggered either by a within-window
 *    notification with a new value, or by a gap that snaps to a known interval shorter than current.
 *  - **Snap up** (longer interval) — requires [SNAP_UP_CONSECUTIVE] consecutive gaps that snap to the
 *    same larger known interval, with no shorter gaps interrupting. This avoids treating one missed
 *    reading as a sensor-cadence change.
 *
 * Known intervals: 1, 3, 5, 15 minutes (mapped via [snapGapToKnownIntervalMs]).
 */
class GlucoseDeduplicator(
    private val packageConfig: PackageConfig,
    private val store: StateStore,
    private val defaultIntervalMs: Long = DEFAULT_INTERVAL_MS
) {

    interface StateStore {

        fun load(): String?
        fun save(json: String)
    }

    private data class State(
        var lastAcceptedTimestamp: Long,
        var lastValueMgdl: Int,
        var intervalMs: Long,
        var pendingLongerIntervalMs: Long,
        var consecutiveLongGapCount: Int
    )

    private val states: MutableMap<String, State> = loadStates()

    /**
     * Returns true if the reading should be accepted (and persists state). Returns false for
     * a detected duplicate. Caller must only invoke this after parsing a valid glucose value.
     */
    @Synchronized
    fun process(packageName: String, valueMgdl: Int, now: Long): Boolean {
        val state = states[packageName]
        if (state == null) {
            val seed = packageConfig.intervalForPackage(packageName, defaultIntervalMs)
            states[packageName] = State(now, valueMgdl, seed, 0L, 0)
            persist()
            return true
        }

        val gap = now - state.lastAcceptedTimestamp
        val threshold = state.intervalMs - state.intervalMs / 5
        val sameValue = valueMgdl == state.lastValueMgdl

        if (gap < threshold) {
            if (sameValue) return false
            // Different value despite short gap → real reading, snap down to the observed gap.
            val snapped = snapGapToKnownIntervalMs(gap)
            if (snapped < state.intervalMs) {
                state.intervalMs = snapped
            }
            state.pendingLongerIntervalMs = 0L
            state.consecutiveLongGapCount = 0
        } else {
            val snapped = snapGapToKnownIntervalMs(gap)
            when {
                snapped < state.intervalMs -> {
                    state.intervalMs = snapped
                    state.pendingLongerIntervalMs = 0L
                    state.consecutiveLongGapCount = 0
                }

                snapped > state.intervalMs -> {
                    if (snapped == state.pendingLongerIntervalMs) {
                        state.consecutiveLongGapCount++
                        if (state.consecutiveLongGapCount >= SNAP_UP_CONSECUTIVE) {
                            state.intervalMs = snapped
                            state.pendingLongerIntervalMs = 0L
                            state.consecutiveLongGapCount = 0
                        }
                    } else {
                        state.pendingLongerIntervalMs = snapped
                        state.consecutiveLongGapCount = 1
                    }
                }

                else                       -> {
                    state.pendingLongerIntervalMs = 0L
                    state.consecutiveLongGapCount = 0
                }
            }
        }

        state.lastAcceptedTimestamp = now
        state.lastValueMgdl = valueMgdl
        persist()
        return true
    }

    /** Currently-effective interval for a package (for diagnostics/tests). */
    fun currentIntervalMs(packageName: String): Long =
        states[packageName]?.intervalMs
            ?: packageConfig.intervalForPackage(packageName, defaultIntervalMs)

    private fun persist() {
        val root = JSONArray()
        for ((pkg, s) in states) {
            root.put(
                JSONObject()
                    .put("p", pkg)
                    .put("t", s.lastAcceptedTimestamp)
                    .put("v", s.lastValueMgdl)
                    .put("i", s.intervalMs)
                    .put("pi", s.pendingLongerIntervalMs)
                    .put("c", s.consecutiveLongGapCount)
            )
        }
        store.save(root.toString())
    }

    private fun loadStates(): MutableMap<String, State> {
        val raw = store.load() ?: return mutableMapOf()
        if (raw.isBlank()) return mutableMapOf()
        return try {
            val arr = JSONArray(raw)
            val map = mutableMapOf<String, State>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                map[o.getString("p")] = State(
                    lastAcceptedTimestamp = o.getLong("t"),
                    lastValueMgdl = o.getInt("v"),
                    intervalMs = o.getLong("i"),
                    pendingLongerIntervalMs = o.optLong("pi", 0L),
                    consecutiveLongGapCount = o.optInt("c", 0)
                )
            }
            map
        } catch (_: Exception) {
            mutableMapOf()
        }
    }

    companion object {

        const val DEFAULT_INTERVAL_MS = 5 * 60_000L
        const val SNAP_UP_CONSECUTIVE = 3

        /**
         * Snap a measured gap to the nearest known sensor interval using fixed thresholds.
         * Boundaries chosen between known values so jitter (a few seconds either way of the true
         * interval) stays within the correct band.
         */
        fun snapGapToKnownIntervalMs(gapMs: Long): Long = when {
            gapMs <= 2 * 60_000L  -> 1 * 60_000L
            gapMs <= 4 * 60_000L  -> 3 * 60_000L
            gapMs <= 10 * 60_000L -> 5 * 60_000L
            else                  -> 15 * 60_000L
        }
    }
}

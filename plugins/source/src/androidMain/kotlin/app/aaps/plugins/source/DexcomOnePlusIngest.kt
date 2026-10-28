package app.aaps.plugins.source

import app.aaps.core.data.model.GV
import app.aaps.core.data.model.SourceSensor
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.interfaces.concurrent.AapsLock
import app.aaps.core.interfaces.concurrent.withLock
import app.aaps.plugins.dexcomoneplus.OnePlusGlucoseSample
import app.aaps.plugins.dexcomoneplus.OnePlusWarmupState
import kotlin.concurrent.Volatile
import kotlin.math.abs

/**
 * Pure mapping / gate helpers for Dexcom ONE+ → [GV] ingest.
 * Kept beside [DexcomOnePlusPlugin] so UI edits stay out of the insert path.
 *
 * Dedup mirrors Ob1 `getForPreciseTimestamp(..., 4 min)` so backfill + live EGV
 * (and EGV re-poll) do not double-insert near-duplicate points.
 *
 * Provenance: NightscoutFoundation/xDrip `Ob1G5CollectionService.getForPreciseTimestamp`
 * at pin `1e86d9a2a52577ed2c30fbf7b69d75fd56e6918f` (GPL-3.0). See plugins/dexcom_oneplus/NOTICE.
 */
internal object DexcomOnePlusIngest {

    /** Ob1 precise-timestamp window. */
    const val DEDUP_WINDOW_MS: Long = 4L * 60L * 1000L

    private const val RECENT_CAP: Int = 64

    /**
     * One lock for the whole dedup state.
     *
     * Was `synchronized(Any())`, which is JVM only. [AapsLock.withLock] is inline and reentrant, so
     * the `return` statements inside the guarded blocks below still mean what they meant before.
     */
    private val lock = AapsLock()
    private val recentTimestampsMs = ArrayDeque<Long>()
    private val recentSequences = LinkedHashSet<Long>()

    /**
     * Persistent monotonic high-water mark of the last accepted EGV sequence, survived across
     * process restarts via [seed] (rehydrated from `OnePlusSensorStore`). -1 = nothing recorded yet.
     * Prevents an app update / restart from re-ingesting already-stored readings (which the loop
     * rejects as duplicates), because the in-memory [recentSequences] alone is wiped on restart.
     */
    @Volatile
    private var lastAcceptedSequence: Long = -1L

    /**
     * Rehydrate the dedup state after a process restart, so backfilled/re-read EGVs are not
     * re-inserted. [lastSequence] is the persisted high-water mark (-1 if none); [recentTimestampsMs]
     * are recent already-stored reading timestamps (from the DB) to re-seed the near-duplicate window.
     */
    fun seed(lastSequence: Long, recentTimestampsMs: List<Long>) {
        lock.withLock {
            lastAcceptedSequence = lastSequence
            this.recentTimestampsMs.clear()
            recentTimestampsMs.takeLast(RECENT_CAP).forEach { this.recentTimestampsMs.addLast(it) }
        }
    }

    /**
     * Block PersistenceLayer inserts only while the protocol/UI reports `WARMING`.
     * PAIRING / IDLE must not block — otherwise attach-to-ready sensors never ingest.
     */
    fun isWarmupBlockingIngest(phase: OnePlusWarmupState.Phase): Boolean =
        phase == OnePlusWarmupState.Phase.WARMING

    /**
     * @return true if this sample should be inserted (not a near-duplicate of a recent one).
     */
    fun shouldAccept(sample: OnePlusGlucoseSample): Boolean {
        lock.withLock {
            val seq = sample.sequence
            // Persistent floor: reject anything at or below the last accepted sequence — survives
            // restarts, so backfill/re-reads after an app update are not re-inserted.
            if (seq != null && lastAcceptedSequence >= 0 && seq <= lastAcceptedSequence) {
                return false
            }
            if (seq != null && recentSequences.contains(seq)) {
                return false
            }
            val ts = sample.timestampMs
            for (prev in recentTimestampsMs) {
                if (abs(prev - ts) < DEDUP_WINDOW_MS) {
                    return false
                }
            }
            recentTimestampsMs.addLast(ts)
            if (seq != null) {
                recentSequences.add(seq)
                lastAcceptedSequence = maxOf(lastAcceptedSequence, seq)
            }
            while (recentTimestampsMs.size > RECENT_CAP) {
                recentTimestampsMs.removeFirst()
            }
            while (recentSequences.size > RECENT_CAP) {
                val first = recentSequences.first()
                recentSequences.remove(first)
            }
            return true
        }
    }

    fun mapToGv(sample: OnePlusGlucoseSample): GV =
        GV(
            timestamp = sample.timestampMs,
            value = sample.mgdl,
            raw = null,
            noise = null,
            trendArrow = trendArrowFor(sample.trendSlopeMgdlPerMin),
            sourceSensor = SourceSensor.DEXCOM_ONEPLUS_NATIVE,
        )

    /**
     * Turns the sensor's rate of change into the arrow AAPS stores and draws.
     *
     * The sensor reports a slope in mg/dL per minute, not an arrow name. This used to be handed
     * straight to `TrendArrow.fromString`, which compares against labels like "Flat" and
     * "FortyFiveUp": a number never matched one, so every ONE+ reading was stored as
     * [TrendArrow.NONE] and the glucose history drew the invalid-arrow icon on all of them.
     *
     * Thresholds are the usual Dexcom ones: one arrow step per mg/dL/min, doubling above three.
     * A null slope (the sensor's own invalid marker) stays [TrendArrow.NONE] — honest, since no
     * direction was measured.
     */
    fun trendArrowFor(slopeMgdlPerMin: Double?): TrendArrow = when {
        slopeMgdlPerMin == null   -> TrendArrow.NONE
        slopeMgdlPerMin >= 3.0    -> TrendArrow.DOUBLE_UP
        slopeMgdlPerMin >= 2.0    -> TrendArrow.SINGLE_UP
        slopeMgdlPerMin >= 1.0    -> TrendArrow.FORTY_FIVE_UP
        slopeMgdlPerMin > -1.0    -> TrendArrow.FLAT
        slopeMgdlPerMin > -2.0    -> TrendArrow.FORTY_FIVE_DOWN
        slopeMgdlPerMin > -3.0    -> TrendArrow.SINGLE_DOWN
        else                      -> TrendArrow.DOUBLE_DOWN
    }

    /**
     * Reset all dedup state (in-memory windows + persistent sequence floor). Call when the loop's
     * sensor changes — e.g. promoting a staging sensor to production — because the new sensor's EGV
     * sequence counter restarts from a low value and must not be rejected against the old floor.
     */
    fun reset() {
        lock.withLock {
            recentTimestampsMs.clear()
            recentSequences.clear()
            lastAcceptedSequence = -1L
        }
    }

    /** Test-only: clear in-memory dedup state. */
    internal fun clearDedupForTests() = reset()
}

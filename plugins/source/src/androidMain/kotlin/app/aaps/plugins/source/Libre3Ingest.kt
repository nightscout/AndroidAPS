package app.aaps.plugins.source

import app.aaps.core.data.model.GV
import app.aaps.core.data.model.SourceSensor
import app.aaps.core.data.model.TrendArrow
import app.aaps.plugins.libre3.Libre3GlucoseSample
import app.aaps.plugins.libre3.Libre3WarmupState
import kotlin.math.abs

/**
 * Pure gate and mapping helpers for Libre 3 to [GV] ingest. Kept beside `Libre3NativePlugin` so
 * later UI work stays out of the insert path.
 *
 * Two gates guard the loop, and they are on purpose in two different places:
 *
 * - The driver decides whether a reading is **usable at all**, because only it can see the sensor
 *   state, the data quality mark and the sensor life.
 * - This object decides whether a usable reading is **new**, and refuses anything outside the
 *   range that a sensor is allowed to report. That second check is a safety net: a mistake
 *   anywhere upstream must not be able to put a nonsense number into the loop.
 */
internal object Libre3Ingest {

    /**
     * Two readings closer than this in time are treated as the same reading.
     *
     * A Libre 3 speaks **once a minute**, and every one of those readings is a new measurement, not
     * a repeat. What protects against a repeat is the sensor's own life counter a few lines below:
     * a reading whose counter is not higher than the last accepted one can never be inserted. This
     * window is only there for the case where the very same reading arrives twice within seconds,
     * for instance when a message is delivered again after a hiccup on the link.
     *
     * It used to be four minutes, which threw away three readings out of four: a field log showed
     * 75, then 74, then 73 mg/dL dropped as "repeated" between two inserts. Keep it well under a
     * minute, or the driver quietly becomes a five minute sensor again.
     */
    const val DEDUP_WINDOW_MS: Long = 30L * 1000L

    private const val RECENT_CAP: Int = 64

    private val lock = Any()
    private val recentTimestampsMs = ArrayDeque<Long>()

    /**
     * Highest sensor life counter that was accepted so far. A sample must be newer than this, so a
     * repeated or replayed reading can never be inserted twice. -1 means nothing accepted yet.
     */
    @Volatile
    private var lastAcceptedLifeCount: Int = -1

    /**
     * Block inserts only while the driver reports `WARMING`. PAIRING and IDLE must not block,
     * otherwise a sensor that is already past warm-up would never deliver glucose.
     */
    fun isWarmupBlockingIngest(phase: Libre3WarmupState.Phase): Boolean =
        phase == Libre3WarmupState.Phase.WARMING

    /**
     * Lowest and highest number a Libre 3 may report once it has been mapped into the shown range.
     * Anything outside is not a glucose value and must never reach the loop.
     */
    const val MIN_MGDL = 39.0
    const val MAX_MGDL = 501.0

    /**
     * Rebuilds what is known after a restart, so an app update cannot insert readings that are
     * already stored.
     *
     * @param lastLifeCount the highest life counter that was accepted before, or -1 when none.
     * @param recentTimestampsMs times of readings that are already in the database.
     */
    fun seed(lastLifeCount: Int, recentTimestampsMs: List<Long>) {
        synchronized(lock) {
            lastAcceptedLifeCount = lastLifeCount
            this.recentTimestampsMs.clear()
            recentTimestampsMs.takeLast(RECENT_CAP).forEach { this.recentTimestampsMs.addLast(it) }
        }
    }

    /**
     * @return true when this sample should be inserted, that is it is a new reading and its
     *   number is one a sensor may really report.
     */
    fun shouldAccept(sample: Libre3GlucoseSample): Boolean {
        if (sample.mgdl < MIN_MGDL || sample.mgdl > MAX_MGDL) return false
        synchronized(lock) {
            if (lastAcceptedLifeCount >= 0 && sample.lifeCount <= lastAcceptedLifeCount) return false
            val ts = sample.timestampMs
            for (previous in recentTimestampsMs) {
                if (abs(previous - ts) < DEDUP_WINDOW_MS) return false
            }
            recentTimestampsMs.addLast(ts)
            lastAcceptedLifeCount = maxOf(lastAcceptedLifeCount, sample.lifeCount)
            while (recentTimestampsMs.size > RECENT_CAP) recentTimestampsMs.removeFirst()
            return true
        }
    }

    fun mapToGv(sample: Libre3GlucoseSample): GV =
        GV(
            timestamp = sample.timestampMs,
            value = sample.mgdl,
            raw = null,
            noise = null,
            trendArrow = TrendArrow.fromString(sample.trendArrowRaw),
            sourceSensor = SourceSensor.LIBRE_3_NATIVE,
        )

    /** The highest life counter that was accepted so far, or -1 when there is none. */
    fun lastAcceptedLifeCount(): Int = synchronized(lock) { lastAcceptedLifeCount }

    /** Forget everything that was accepted. Used when a new sensor starts its own life counter. */
    fun reset() {
        synchronized(lock) {
            recentTimestampsMs.clear()
            lastAcceptedLifeCount = -1
        }
    }
}

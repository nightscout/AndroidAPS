package app.aaps.plugins.source

import app.aaps.core.interfaces.source.CgmSensorLifecycle
import app.aaps.core.interfaces.source.StagingState
import app.aaps.plugins.libre3.Libre3GlucoseSample

/**
 * One collected pre-soak reading.
 *
 * It is never stored and never published: the pre-soak slot never feeds the loop. It lives in this
 * module so the pre-soak curve can be drawn without a new module dependency.
 */
data class Libre3PresoakPoint(val timestampMs: Long, val mgdl: Double)

/**
 * The rules of the Libre 3 pre-soak slot, without Android and without the DI graph.
 *
 * It is kept out of [Libre3NativePlugin] so the state machine can be unit tested on its own.
 */
internal object Libre3Staging {

    private const val HOUR_MS = 60L * 60L * 1000L
    private const val MINUTE_MS = 60L * 1000L

    /** Sensor life used when the NFC scan did not report a wear time. A Libre 3 runs 14 days. */
    const val DEFAULT_LIFE_MS = 14L * 24L * HOUR_MS

    /** End of life grace. A sensor that still reads past its stored end is still a sensor. */
    const val SENSOR_GRACE_MS = 12L * HOUR_MS

    /** End of life window: the user should start the next sensor. */
    const val END_OF_LIFE_MS = 12L * HOUR_MS

    /**
     * How many readings the pre-soak slot needs before it is shown as ready.
     *
     * This is **not** a soak gate. A Libre 3 speaks once a minute, so five readings is five
     * minutes. It only means "the sensor really talks", so the promote button is never offered for a sensor that has produced nothing.
     */
    const val STAGING_MIN_VALID_READINGS = 5

    /** How many pre-soak readings the curve keeps: 24 hours at one a minute. */
    const val CURVE_CAP = 1440

    /**
     * Whether two slots would hold one and the same physical sensor.
     *
     * Serial **or** MAC is enough to say "same sensor": the serial is known from the NFC patch info
     * read, before any activation command is sent, and the MAC is what a stored session is keyed
     * on. Both comparisons ignore case, because a serial reaches a store from an NFC parse and a
     * MAC from an NFC parse or a scan hit, and only some of those use capitals.
     */
    fun isSameSensor(storedSerial: String?, storedMac: String?, serial: String?, mac: String?): Boolean =
        sameText(storedSerial, serial) || sameText(storedMac, mac)

    /** True only when both sides really carry a value and they mean the same thing. */
    private fun sameText(stored: String?, other: String?): Boolean {
        val left = stored?.trim()?.takeIf { it.isNotEmpty() } ?: return false
        val right = other?.trim()?.takeIf { it.isNotEmpty() } ?: return false
        return left.equals(right, ignoreCase = true)
    }

    /**
     * Sensor life counted from the moment the sensor was activated.
     *
     * @param activatedAtMs when the sensor was started, in phone time. 0 means it is not known and
     *   no lifecycle is reported.
     * @param wearMinutes whole sensor life read by the NFC scan. Null or zero falls back to
     *   [DEFAULT_LIFE_MS], which also covers a Libre 3 Plus on the safe side.
     */
    fun computeLifecycle(
        activatedAtMs: Long,
        wearMinutes: Int?,
        nowMs: Long,
    ): CgmSensorLifecycle? {
        if (activatedAtMs <= 0L) return null
        val lifeMs = wearMinutes?.takeIf { it > 0 }?.let { it * MINUTE_MS } ?: DEFAULT_LIFE_MS
        val age = (nowMs - activatedAtMs).coerceAtLeast(0L)
        val expires = activatedAtMs + lifeMs + SENSOR_GRACE_MS
        val remaining = expires - nowMs
        return CgmSensorLifecycle(
            startedAtEpochMs = activatedAtMs,
            ageMs = age,
            endOfLife = remaining < END_OF_LIFE_MS,
        )
    }

    /**
     * State of the pre-soak slot, as the status card reads it.
     *
     * There is no time argument on purpose. The user already pays real wear time for the soak, so
     * the app must not veto on top of that; soak time and reading count are shown as information
     * only.
     *
     * @param present a pre-soak sensor is running.
     * @param warming that sensor has not left warm-up yet, so it has sent no glucose at all.
     * @param validReadingCount good readings collected so far.
     */
    fun computeStagingState(
        present: Boolean,
        warming: Boolean,
        validReadingCount: Int,
    ): StagingState {
        if (!present) return StagingState.ABSENT
        if (warming) return StagingState.WARMUP
        return if (validReadingCount >= STAGING_MIN_VALID_READINGS) StagingState.READY
        else StagingState.SETTLING
    }

    /** What the pre-soak slot knows about its warm-up after one driver phase — see [applyWarmupPhase]. */
    data class StagingWarmupDecision(val warmupDone: Boolean, val warming: Boolean)

    /**
     * Feeds one driver warm-up phase into the pre-soak warm-up latch.
     *
     * Leaving warm-up is an event that is latched once, never read again from the live phase: a
     * healthy sensor reconnects for its whole life, and reading a connect as "still warming up"
     * would keep the slot in [StagingState.WARMUP] for ever, so the promote button would never
     * appear.
     *
     * @param warmupDoneBefore the latch as it stands, restored from the store across restarts.
     * @param readyPhase the driver says this sensor has finished warm-up.
     */
    fun applyWarmupPhase(warmupDoneBefore: Boolean, readyPhase: Boolean): StagingWarmupDecision {
        val done = warmupDoneBefore || readyPhase
        return StagingWarmupDecision(warmupDone = done, warming = !done)
    }

    /**
     * Should this pre-soak sample go into the curve?
     *
     * This is a private repeat guard for the pre-soak slot only. It must **not** be [Libre3Ingest]:
     * that object is process wide, its floor is keyed on the sensor life counter,
     * and a pre-soak sensor restarts that counter at 0. Sharing it would either swallow every
     * pre-soak reading or lower the production floor and let a repeated production reading through.
     *
     * @param lastLifeCount highest pre-soak life counter accepted so far, -1 when there is none.
     */
    fun acceptForCurve(lastLifeCount: Int, sample: Libre3GlucoseSample): Boolean =
        sample.mgdl >= Libre3Ingest.MIN_MGDL &&
            sample.mgdl <= Libre3Ingest.MAX_MGDL &&
            (lastLifeCount < 0 || sample.lifeCount > lastLifeCount)
}

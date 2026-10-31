package app.aaps.plugins.source

import app.aaps.core.interfaces.source.CgmSensorLifecycle
import app.aaps.core.interfaces.source.StagingState

/**
 * Pure helpers + tunable constants for the Dexcom ONE+ dual-sensor (pre-soak / staging) feature.
 * Kept out of [DexcomOnePlusPlugin] so the lifecycle / staging state machine is unit-testable without
 * the DI graph or Android.
 *
 * ⚠️ The time constants below are tunable and kept on the safe side.
 */
internal object DexcomOnePlusStaging {

    private const val HOUR_MS = 60L * 60L * 1000L

    /**
     * Whether a slot's stored MAC describes the transmitter at [deviceAddress].
     *
     * The rule the cross-slot guard rests on. One sensor must never be paired in both slots at once:
     * the two GATT clients share a single ACL link, both subscribe to the same Auth and ExtraData
     * characteristics, and the transmitter sees one interleaved stream of two KEKS handshakes. A
     * field log showed both sides dying inside libkeks 1.6 s after the second connect.
     *
     * Case-insensitive because a MAC reaches the stores from several places — a live scan hit, an
     * applicator parse, a restored session — and only some of them upper-case it.
     */
    fun isSameTransmitter(storedMac: String?, deviceAddress: String): Boolean {
        val stored = storedMac?.trim()?.takeIf { it.isNotEmpty() } ?: return false
        val target = deviceAddress.trim().takeIf { it.isNotEmpty() } ?: return false
        return stored.equals(target, ignoreCase = true)
    }

    /** Nominal ONE+/G7 sensor life. */
    const val SENSOR_LIFE_MS = 10L * 24L * HOUR_MS

    /** End-of-life grace extension. */
    const val SENSOR_GRACE_MS = 12L * HOUR_MS

    /** End-of-life window: prompt the user to start a new sensor. */
    const val END_OF_LIFE_MS = 12L * HOUR_MS

    /** Minimum staging duration before promotion is allowed. */
    const val STAGING_MIN_SETTLE_MS = 12L * HOUR_MS

    /** Minimum count of valid staging EGVs before promotion is allowed. */
    const val STAGING_MIN_VALID_EGV = 6

    /**
     * How recent the staging sensor's last reading must be for an EARLY promotion (4 missed readings
     * on a 5-minute sensor). The normal path already proves the sensor lives through a 12 h soak; an
     * early promotion has no such proof, so the loop must not be handed a sensor that went silent.
     */
    const val STAGING_MAX_EGV_AGE_MS = 20L * 60L * 1000L

    /**
     * Derive a [CgmSensorLifecycle] from a sensor session start. Returns null when the start is
     * unknown (0) so the status screen simply shows nothing special.
     */
    fun computeLifecycle(sessionStartMs: Long, nowMs: Long): CgmSensorLifecycle? {
        if (sessionStartMs <= 0L) return null
        val age = (nowMs - sessionStartMs).coerceAtLeast(0L)
        val remaining = sessionStartMs + SENSOR_LIFE_MS + SENSOR_GRACE_MS - nowMs
        return CgmSensorLifecycle(
            startedAtEpochMs = sessionStartMs,
            ageMs = age,
            endOfLife = remaining < END_OF_LIFE_MS,
        )
    }

    /**
     * Coarse staging slot state for the status screen card + promote gating.
     *
     * @param present     a staging sensor session exists.
     * @param warming     the staging sensor is warming up / (re)connecting (no glucose yet).
     * @param sessionStartMs staging session start (0 = unknown).
     * @param validEgvCount valid staging EGVs collected so far.
     */
    fun computeStagingState(
        present: Boolean,
        warming: Boolean,
        sessionStartMs: Long,
        validEgvCount: Int,
        nowMs: Long,
    ): StagingState {
        if (!present) return StagingState.ABSENT
        if (warming) return StagingState.WARMUP
        val settledLongEnough = sessionStartMs > 0L && (nowMs - sessionStartMs) >= STAGING_MIN_SETTLE_MS
        return if (settledLongEnough && validEgvCount >= STAGING_MIN_VALID_EGV) StagingState.READY
        else StagingState.SETTLING
    }

    /**
     * Milliseconds left before the staging sensor has soaked [STAGING_MIN_SETTLE_MS].
     *
     * Null when there is no staging sensor, when its start is unknown, or when the wait is over, so
     * a caller cannot show a stale "ready in". It says nothing about the valid-reading count:
     * [computeStagingState] still decides when promotion is allowed.
     */
    fun settleRemainingMs(present: Boolean, sessionStartMs: Long, nowMs: Long): Long? {
        if (!present || sessionStartMs <= 0L) return null
        return (STAGING_MIN_SETTLE_MS - (nowMs - sessionStartMs)).takeIf { it > 0L }
    }

    /** What the staging slot knows about its warm-up after one driver phase — see [applyWarmupPhase]. */
    data class StagingWarmupDecision(val warmupDone: Boolean, val warming: Boolean)

    /**
     * May the user be OFFERED an early promotion — promoting the staging sensor before its soak ends,
     * because the production sensor stopped early or has to be replaced now?
     *
     * This is only about showing the button and about the matching guard in the promote path. It never
     * relaxes the evidence: the sensor must have produced [STAGING_MIN_VALID_EGV] valid readings and a
     * reading no older than [STAGING_MAX_EGV_AGE_MS], so a silent or dead sensor can never take over
     * the loop. What it does drop is the 12 h soak, which is a quality gate, not a safety one — the
     * user is told the readings will be less reliable for the first hours.
     */
    fun canPromoteEarly(validEgvCount: Int, lastValueAtEpochMs: Long?, nowMs: Long): Boolean {
        if (validEgvCount < STAGING_MIN_VALID_EGV) return false
        if (lastValueAtEpochMs == null || lastValueAtEpochMs <= 0L) return false
        val age = nowMs - lastValueAtEpochMs
        return age in 0L..STAGING_MAX_EGV_AGE_MS
    }

    /**
     * Feed one driver warm-up phase into the staging warm-up latch.
     *
     * Leaving warm-up is an EVENT that is latched once, never re-derived from the live phase: a
     * healthy sensor re-connects on every radio cycle (~5 min) for its whole life, so reading
     * CONNECTING / RECONNECTING as "still warming up" kept the slot in [StagingState.WARMUP] forever —
     * the settling countdown was never shown and the promote button (READY only) stayed unreachable.
     *
     * A slot that has not passed warm-up yet counts as warming whatever the driver is doing right now,
     * including a failed or idle radio: a sensor that never warmed up must not be shown as settling,
     * which would display a 12 h stabilisation countdown for a sensor that has produced nothing.
     *
     * @param warmupDoneBefore the latch as it stands (restored from the store across restarts).
     * @param readyPhase       the driver reports the sensor has finished warm-up.
     */
    fun applyWarmupPhase(warmupDoneBefore: Boolean, readyPhase: Boolean): StagingWarmupDecision {
        val done = warmupDoneBefore || readyPhase
        return StagingWarmupDecision(warmupDone = done, warming = !done)
    }
}

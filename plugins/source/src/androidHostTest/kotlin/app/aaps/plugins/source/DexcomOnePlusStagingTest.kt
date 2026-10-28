package app.aaps.plugins.source

import app.aaps.core.interfaces.source.StagingState
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class DexcomOnePlusStagingTest {

    private val now = 1_000_000_000_000L
    private val hour = 60L * 60L * 1000L

    // ---- computeLifecycle ----

    @Test
    fun `lifecycle is null when session start unknown`() {
        assertThat(DexcomOnePlusStaging.computeLifecycle(0L, now)).isNull()
    }

    @Test
    fun `fresh sensor is not end of life`() {
        val lc = DexcomOnePlusStaging.computeLifecycle(now - 1 * hour, now)!!
        assertThat(lc.endOfLife).isFalse()
        assertThat(lc.ageMs).isEqualTo(1 * hour)
        assertThat(lc.startedAtEpochMs).isEqualTo(now - 1 * hour)
    }

    @Test
    fun `sensor near expiry is end of life`() {
        // start so that remaining < END_OF_LIFE_MS (12 h): life+grace = 10 d + 12 h, leave 6 h.
        val start = now - (DexcomOnePlusStaging.SENSOR_LIFE_MS + DexcomOnePlusStaging.SENSOR_GRACE_MS - 6 * hour)
        val lc = DexcomOnePlusStaging.computeLifecycle(start, now)!!
        assertThat(lc.endOfLife).isTrue()
    }

    // ---- computeStagingState ----

    @Test
    fun `staging absent when no sensor`() {
        assertThat(DexcomOnePlusStaging.computeStagingState(present = false, warming = false, sessionStartMs = 0L, validEgvCount = 0, nowMs = now))
            .isEqualTo(StagingState.ABSENT)
    }

    @Test
    fun `staging warmup while warming`() {
        assertThat(DexcomOnePlusStaging.computeStagingState(present = true, warming = true, sessionStartMs = now, validEgvCount = 0, nowMs = now))
            .isEqualTo(StagingState.WARMUP)
    }

    @Test
    fun `staging settling before minimum settle time`() {
        assertThat(DexcomOnePlusStaging.computeStagingState(present = true, warming = false, sessionStartMs = now - 1 * hour, validEgvCount = 10, nowMs = now))
            .isEqualTo(StagingState.SETTLING)
    }

    @Test
    fun `staging ready after settle time with enough valid egv`() {
        assertThat(DexcomOnePlusStaging.computeStagingState(present = true, warming = false, sessionStartMs = now - 13 * hour, validEgvCount = 6, nowMs = now))
            .isEqualTo(StagingState.READY)
    }

    @Test
    fun `staging still settling when settle time reached but not enough valid egv`() {
        assertThat(DexcomOnePlusStaging.computeStagingState(present = true, warming = false, sessionStartMs = now - 13 * hour, validEgvCount = 5, nowMs = now))
            .isEqualTo(StagingState.SETTLING)
    }

    // ---- canPromoteEarly (explicit early promotion) ----

    @Test
    fun `early promotion is offered when the sensor reads enough and reads now`() {
        assertThat(
            DexcomOnePlusStaging.canPromoteEarly(
                validEgvCount = DexcomOnePlusStaging.STAGING_MIN_VALID_EGV,
                lastValueAtEpochMs = now - 5 * 60_000L,
                nowMs = now,
            ),
        ).isTrue()
    }

    @Test
    fun `early promotion needs the same evidence as the normal path`() {
        assertThat(
            DexcomOnePlusStaging.canPromoteEarly(
                validEgvCount = DexcomOnePlusStaging.STAGING_MIN_VALID_EGV - 1,
                lastValueAtEpochMs = now,
                nowMs = now,
            ),
        ).isFalse()
    }

    @Test
    fun `a sensor that went silent is never offered for early promotion`() {
        // The 12 h soak is what normally proves the sensor lives; without it, freshness must.
        assertThat(
            DexcomOnePlusStaging.canPromoteEarly(
                validEgvCount = 50,
                lastValueAtEpochMs = now - DexcomOnePlusStaging.STAGING_MAX_EGV_AGE_MS - 1,
                nowMs = now,
            ),
        ).isFalse()
    }

    @Test
    fun `a reading exactly at the age limit still counts`() {
        assertThat(
            DexcomOnePlusStaging.canPromoteEarly(
                validEgvCount = 10,
                lastValueAtEpochMs = now - DexcomOnePlusStaging.STAGING_MAX_EGV_AGE_MS,
                nowMs = now,
            ),
        ).isTrue()
    }

    @Test
    fun `no reading at all blocks early promotion`() {
        assertThat(DexcomOnePlusStaging.canPromoteEarly(validEgvCount = 10, lastValueAtEpochMs = null, nowMs = now)).isFalse()
        assertThat(DexcomOnePlusStaging.canPromoteEarly(validEgvCount = 10, lastValueAtEpochMs = 0L, nowMs = now)).isFalse()
    }

    @Test
    fun `a reading stamped in the future cannot unlock early promotion`() {
        assertThat(
            DexcomOnePlusStaging.canPromoteEarly(validEgvCount = 10, lastValueAtEpochMs = now + 60_000L, nowMs = now),
        ).isFalse()
    }

    // ---- applyWarmupPhase (warm-up latch) ----

    @Test
    fun `connecting during warm-up keeps the slot warming`() {
        val d = DexcomOnePlusStaging.applyWarmupPhase(warmupDoneBefore = false, readyPhase = false)

        assertThat(d.warmupDone).isFalse()
        assertThat(d.warming).isTrue()
    }

    @Test
    fun `ready ends the warm-up`() {
        val d = DexcomOnePlusStaging.applyWarmupPhase(warmupDoneBefore = false, readyPhase = true)

        assertThat(d.warmupDone).isTrue()
        assertThat(d.warming).isFalse()
    }

    @Test
    fun `a later reconnect cycle cannot send a settled slot back to warm-up`() {
        // This is the defect the latch fixes: a healthy sensor reconnects every radio cycle for life.
        val d = DexcomOnePlusStaging.applyWarmupPhase(warmupDoneBefore = true, readyPhase = false)

        assertThat(d.warmupDone).isTrue()
        assertThat(d.warming).isFalse()
    }

    @Test
    fun `a sensor that failed before warm-up is never shown as settling`() {
        // A dead sensor must not display a 12 h stabilisation countdown it can never finish.
        val d = DexcomOnePlusStaging.applyWarmupPhase(warmupDoneBefore = false, readyPhase = false)
        val state = DexcomOnePlusStaging.computeStagingState(
            present = true,
            warming = d.warming,
            sessionStartMs = now - 13 * hour,
            validEgvCount = 0,
            nowMs = now,
        )

        assertThat(state).isEqualTo(StagingState.WARMUP)
    }

    @Test
    fun `a settled slot still reaches ready after a reconnect cycle`() {
        val d = DexcomOnePlusStaging.applyWarmupPhase(warmupDoneBefore = true, readyPhase = false)
        val state = DexcomOnePlusStaging.computeStagingState(
            present = true,
            warming = d.warming,
            sessionStartMs = now - 13 * hour,
            validEgvCount = DexcomOnePlusStaging.STAGING_MIN_VALID_EGV,
            nowMs = now,
        )

        assertThat(state).isEqualTo(StagingState.READY)
    }

    // ---- isSameTransmitter: the cross-slot guard's rule ----

    @Test
    fun `same transmitter whatever the case the mac was stored in`() {
        assertThat(DexcomOnePlusStaging.isSameTransmitter("c8:4e:07:b2:31:1f", "C8:4E:07:B2:31:1F")).isTrue()
        assertThat(DexcomOnePlusStaging.isSameTransmitter("C8:4E:07:B2:31:1F", "c8:4e:07:b2:31:1f")).isTrue()
        assertThat(DexcomOnePlusStaging.isSameTransmitter(" C8:4E:07:B2:31:1F ", "C8:4E:07:B2:31:1F")).isTrue()
    }

    @Test
    fun `different transmitters are not the same`() {
        assertThat(DexcomOnePlusStaging.isSameTransmitter("C8:4E:07:B2:31:1F", "D1:22:9C:4E:80:74")).isFalse()
    }

    @Test
    fun `an empty slot never claims a transmitter`() {
        // A slot with nothing stored must not block a start: that would make the guard refuse the
        // first pairing of every sensor.
        assertThat(DexcomOnePlusStaging.isSameTransmitter(null, "C8:4E:07:B2:31:1F")).isFalse()
        assertThat(DexcomOnePlusStaging.isSameTransmitter("", "C8:4E:07:B2:31:1F")).isFalse()
        assertThat(DexcomOnePlusStaging.isSameTransmitter("   ", "C8:4E:07:B2:31:1F")).isFalse()
    }

    @Test
    fun `a blank target never matches a stored sensor`() {
        assertThat(DexcomOnePlusStaging.isSameTransmitter("C8:4E:07:B2:31:1F", "")).isFalse()
        assertThat(DexcomOnePlusStaging.isSameTransmitter("C8:4E:07:B2:31:1F", "  ")).isFalse()
    }

    // ---- settleRemainingMs ----

    @Test
    fun `settle countdown is the time left to 12 hours`() {
        assertThat(DexcomOnePlusStaging.settleRemainingMs(present = true, sessionStartMs = now - 3 * hour, nowMs = now))
            .isEqualTo(9 * hour)
    }

    @Test
    fun `settle countdown is null once the soak is long enough`() {
        assertThat(DexcomOnePlusStaging.settleRemainingMs(present = true, sessionStartMs = now - 12 * hour, nowMs = now)).isNull()
        assertThat(DexcomOnePlusStaging.settleRemainingMs(present = true, sessionStartMs = now - 20 * hour, nowMs = now)).isNull()
    }

    @Test
    fun `settle countdown is null without a staging sensor or a start`() {
        assertThat(DexcomOnePlusStaging.settleRemainingMs(present = false, sessionStartMs = now - 3 * hour, nowMs = now)).isNull()
        assertThat(DexcomOnePlusStaging.settleRemainingMs(present = true, sessionStartMs = 0L, nowMs = now)).isNull()
    }
}

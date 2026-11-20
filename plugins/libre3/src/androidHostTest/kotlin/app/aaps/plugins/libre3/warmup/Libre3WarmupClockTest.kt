package app.aaps.plugins.libre3.warmup

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/** Where a sensor is in its life, worked out from its own minute counter. */
class Libre3WarmupClockTest {

    private val libre3 = 20160
    private val libre3Plus = 21600

    @Test
    fun `a fresh sensor is warming up and counts down`() {
        val clock = Libre3WarmupClock(lifeCountMinutes = 0, warmupMinutes = 60, wearMinutes = libre3Plus)

        assertThat(clock.phase).isEqualTo(Libre3LifePhase.WARMUP)
        assertThat(clock.remainingWarmupMinutes).isEqualTo(60)
        assertThat(clock.remainingWarmupMs).isEqualTo(3_600_000L)
        assertThat(clock.isWarmingUp).isTrue()
    }

    @Test
    fun `the countdown follows the sensor minute counter`() {
        val clock = Libre3WarmupClock(lifeCountMinutes = 45, warmupMinutes = 60)

        assertThat(clock.remainingWarmupMinutes).isEqualTo(15)
    }

    @Test
    fun `the countdown stops at zero and never goes below`() {
        val clock = Libre3WarmupClock(lifeCountMinutes = 500, warmupMinutes = 60)

        assertThat(clock.remainingWarmupMinutes).isEqualTo(0)
        assertThat(clock.isWarmupComplete).isTrue()
        assertThat(clock.isWarmingUp).isFalse()
    }

    @Test
    fun `a sensor that finished warming up is active`() {
        val clock = Libre3WarmupClock(lifeCountMinutes = 61, warmupMinutes = 60, wearMinutes = libre3Plus)

        assertThat(clock.phase).isEqualTo(Libre3LifePhase.ACTIVE)
    }

    @Test
    fun `a sensor that reached its whole life is finished`() {
        val clock = Libre3WarmupClock(lifeCountMinutes = libre3, warmupMinutes = 60, wearMinutes = libre3)

        assertThat(clock.phase).isEqualTo(Libre3LifePhase.EXPIRED)
        assertThat(clock.isExpired).isTrue()
        assertThat(clock.isWarmingUp).isFalse()
        assertThat(clock.remainingWearMinutes).isEqualTo(0)
    }

    @Test
    fun `Libre 3 and Libre 3 Plus differ only by how long they run`() {
        val three = Libre3WarmupClock(lifeCountMinutes = 20200, warmupMinutes = 60, wearMinutes = libre3)
        val threePlus = Libre3WarmupClock(lifeCountMinutes = 20200, warmupMinutes = 60, wearMinutes = libre3Plus)

        assertThat(three.isExpired).isTrue()
        assertThat(threePlus.isExpired).isFalse()
        assertThat(threePlus.remainingWearMinutes).isEqualTo(1400)
    }

    @Test
    fun `a sensor whose whole life is unknown never counts as finished`() {
        val clock = Libre3WarmupClock(lifeCountMinutes = 99999, warmupMinutes = 60, wearMinutes = null)

        assertThat(clock.isExpired).isFalse()
        assertThat(clock.remainingWearMinutes).isNull()
    }

    @Test
    fun `a reading time is counted from the moment the sensor started`() {
        val activatedAt = 1_777_216_508_000L

        val sampleTime = Libre3WarmupClock.sampleTimeMs(activatedAt, lifeCountMinutes = 120)

        assertThat(sampleTime).isEqualTo(activatedAt + 120 * 60_000L)
    }

    @Test
    fun `the start of a sensor can be worked out backwards from one reading`() {
        val sampleTime = 1_777_223_708_000L

        val activatedAt = Libre3WarmupClock.activationTimeFromReading(sampleTime, lifeCountMinutes = 120)

        assertThat(activatedAt).isEqualTo(sampleTime - 120 * 60_000L)
        assertThat(Libre3WarmupClock.sampleTimeMs(activatedAt, 120)).isEqualTo(sampleTime)
    }

    @Test
    fun `a stored start that the sensor disagrees with is dropped`() {
        val sampleTime = 1_777_223_708_000L
        val honest = Libre3WarmupClock.activationTimeFromReading(sampleTime, 120)

        assertThat(Libre3WarmupClock.anchorLooksWrong(honest, sampleTime, 120)).isFalse()
        // Ten minutes out is still trusted, a whole day out is not.
        assertThat(Libre3WarmupClock.anchorLooksWrong(honest + 10 * 60_000L, sampleTime, 120)).isFalse()
        assertThat(Libre3WarmupClock.anchorLooksWrong(honest + 24 * 3_600_000L, sampleTime, 120)).isTrue()
    }

    @Test
    fun `the check must be fed the arrival time, not a time built from the stored start`() {
        // Feeding it sampleTimeMs(...) would compare the stored start with itself. It would always
        // answer "fine", the repair would never fire, and a sensor adopted mid-life would keep a
        // wrong start for ever. This test states that trap out loud.
        val wrongStoredStart = 1_777_000_000_000L
        val builtFromTheStoredStart = Libre3WarmupClock.sampleTimeMs(wrongStoredStart, 120)

        assertThat(Libre3WarmupClock.anchorLooksWrong(wrongStoredStart, builtFromTheStoredStart, 120)).isFalse()

        // With the real arrival time, the same wrong stored start is caught.
        val realArrival = wrongStoredStart + 120 * 60_000L + 24 * 3_600_000L
        assertThat(Libre3WarmupClock.anchorLooksWrong(wrongStoredStart, realArrival, 120)).isTrue()
    }

    @Test
    fun `no stored start at all is treated as unknown`() {
        assertThat(Libre3WarmupClock.anchorLooksWrong(0L, 1_777_223_708_000L, 120)).isTrue()
    }
}

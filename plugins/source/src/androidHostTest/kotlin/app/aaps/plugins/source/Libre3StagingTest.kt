package app.aaps.plugins.source

import app.aaps.core.interfaces.source.StagingState
import app.aaps.plugins.libre3.Libre3GlucoseSample
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * The rules of the Libre 3 pre-soak slot, without Android.
 */
class Libre3StagingTest {

    private val now = 1_777_216_508_000L
    private val hour = 60L * 60L * 1000L
    private val day = 24L * hour

    private fun sample(mgdl: Double, lifeCount: Int) =
        Libre3GlucoseSample(mgdl = mgdl, timestampMs = now, lifeCount = lifeCount)

    @Test
    fun `lifecycle is null when the activation time is not known`() {
        assertThat(Libre3Staging.computeLifecycle(0L, null, now)).isNull()
    }

    @Test
    fun `a sensor one hour old is not end of life and its age is one hour`() {
        val lifecycle = Libre3Staging.computeLifecycle(now - hour, null, now)!!

        assertThat(lifecycle.endOfLife).isFalse()
        assertThat(lifecycle.ageMs).isEqualTo(hour)
    }

    @Test
    fun `the start is the activation time`() {
        val lifecycle = Libre3Staging.computeLifecycle(now - 13 * hour, null, now)!!

        assertThat(lifecycle.startedAtEpochMs).isEqualTo(now - 13 * hour)
        assertThat(lifecycle.ageMs).isEqualTo(13 * hour)
    }

    @Test
    fun `a sensor within twelve hours of its end is end of life`() {
        val activatedAt = now - (Libre3Staging.DEFAULT_LIFE_MS + Libre3Staging.SENSOR_GRACE_MS - 6 * hour)
        val lifecycle = Libre3Staging.computeLifecycle(activatedAt, null, now)!!

        assertThat(lifecycle.endOfLife).isTrue()
    }

    /** Activation time that leaves exactly [Libre3Staging.END_OF_LIFE_MS] before the end of a [lifeMs] sensor. */
    private fun activatedAtOnEndOfLifeEdge(lifeMs: Long) =
        now - (lifeMs + Libre3Staging.SENSOR_GRACE_MS - Libre3Staging.END_OF_LIFE_MS)

    @Test
    fun `an unknown wear time falls back to fourteen days`() {
        val edge = activatedAtOnEndOfLifeEdge(14 * day)

        assertThat(Libre3Staging.computeLifecycle(edge, null, now)!!.endOfLife).isFalse()
        assertThat(Libre3Staging.computeLifecycle(edge - 1, null, now)!!.endOfLife).isTrue()
    }

    @Test
    fun `a Libre 3 Plus wear time moves the end out by one day`() {
        val edge = activatedAtOnEndOfLifeEdge(14 * day)

        assertThat(Libre3Staging.computeLifecycle(edge - 1, null, now)!!.endOfLife).isTrue()
        assertThat(Libre3Staging.computeLifecycle(edge - 1, 15 * 24 * 60, now)!!.endOfLife).isFalse()
        val plusEdge = activatedAtOnEndOfLifeEdge(15 * day)
        assertThat(Libre3Staging.computeLifecycle(plusEdge - 1, 15 * 24 * 60, now)!!.endOfLife).isTrue()
    }

    @Test
    fun `a wear time of zero falls back too`() {
        val edge = activatedAtOnEndOfLifeEdge(14 * day)

        assertThat(Libre3Staging.computeLifecycle(edge, 0, now)!!.endOfLife).isFalse()
        assertThat(Libre3Staging.computeLifecycle(edge - 1, 0, now)!!.endOfLife).isTrue()
    }

    @Test
    fun `the slot is absent when there is no pre-soak sensor`() {
        assertThat(Libre3Staging.computeStagingState(present = false, warming = false, validReadingCount = 99))
            .isEqualTo(StagingState.ABSENT)
    }

    @Test
    fun `the slot is in warm-up while it is warming`() {
        assertThat(Libre3Staging.computeStagingState(present = true, warming = true, validReadingCount = 0))
            .isEqualTo(StagingState.WARMUP)
    }

    @Test
    fun `the slot is settling below five readings`() {
        assertThat(Libre3Staging.computeStagingState(present = true, warming = false, validReadingCount = 4))
            .isEqualTo(StagingState.SETTLING)
    }

    @Test
    fun `the slot is ready at exactly five readings and stays ready above`() {
        assertThat(Libre3Staging.computeStagingState(present = true, warming = false, validReadingCount = 5))
            .isEqualTo(StagingState.READY)
        assertThat(Libre3Staging.computeStagingState(present = true, warming = false, validReadingCount = 500))
            .isEqualTo(StagingState.READY)
    }

    @Test
    fun `the slot state has no time argument, so no soak gate can creep back in`() {
        val parameterNames = Libre3Staging::class.java
            .methods
            .first { it.name.startsWith("computeStagingState") }
            .parameterTypes
            .map { it.simpleName }

        assertThat(parameterNames).containsExactly("boolean", "boolean", "int").inOrder()
    }

    @Test
    fun `a latched warm-up stays done through a reconnect`() {
        val decision = Libre3Staging.applyWarmupPhase(warmupDoneBefore = true, readyPhase = false)

        assertThat(decision.warmupDone).isTrue()
        assertThat(decision.warming).isFalse()
    }

    @Test
    fun `the first ready phase latches the warm-up`() {
        val decision = Libre3Staging.applyWarmupPhase(warmupDoneBefore = false, readyPhase = true)

        assertThat(decision.warmupDone).isTrue()
        assertThat(decision.warming).isFalse()
    }

    @Test
    fun `a slot that never warmed up still counts as warming on a silent radio`() {
        val decision = Libre3Staging.applyWarmupPhase(warmupDoneBefore = false, readyPhase = false)

        assertThat(decision.warmupDone).isFalse()
        assertThat(decision.warming).isTrue()
    }

    @Test
    fun `the same serial is the same sensor even with another MAC`() {
        assertThat(
            Libre3Staging.isSameSensor("MH0123456", "AA:BB:CC:DD:EE:01", "MH0123456", "AA:BB:CC:DD:EE:99")
        ).isTrue()
    }

    @Test
    fun `the same MAC is the same sensor even with another serial`() {
        assertThat(
            Libre3Staging.isSameSensor("MH0123456", "AA:BB:CC:DD:EE:01", "MH0999999", "AA:BB:CC:DD:EE:01")
        ).isTrue()
    }

    @Test
    fun `two different sensors are not the same sensor`() {
        assertThat(
            Libre3Staging.isSameSensor("MH0123456", "AA:BB:CC:DD:EE:01", "MH0999999", "AA:BB:CC:DD:EE:99")
        ).isFalse()
    }

    @Test
    fun `capitals do not make a different sensor`() {
        assertThat(
            Libre3Staging.isSameSensor("mh0123456", "aa:bb:cc:dd:ee:01", "MH0123456", "ZZ:ZZ:ZZ:ZZ:ZZ:ZZ")
        ).isTrue()
        assertThat(
            Libre3Staging.isSameSensor("ZZZ", "aa:bb:cc:dd:ee:01", "YYY", "AA:BB:CC:DD:EE:01")
        ).isTrue()
    }

    @Test
    fun `a missing or blank value never matches`() {
        assertThat(Libre3Staging.isSameSensor(null, null, "MH0123456", "AA:BB:CC:DD:EE:01")).isFalse()
        assertThat(Libre3Staging.isSameSensor("MH0123456", "AA:BB:CC:DD:EE:01", null, null)).isFalse()
        assertThat(Libre3Staging.isSameSensor("  ", "  ", "  ", "  ")).isFalse()
    }

    @Test
    fun `the first pre-soak sample is taken`() {
        assertThat(Libre3Staging.acceptForCurve(-1, sample(120.0, 70))).isTrue()
    }

    @Test
    fun `the same life counter is not taken twice`() {
        assertThat(Libre3Staging.acceptForCurve(70, sample(120.0, 70))).isFalse()
    }

    @Test
    fun `a lower life counter is refused`() {
        assertThat(Libre3Staging.acceptForCurve(70, sample(120.0, 69))).isFalse()
    }

    @Test
    fun `a number a sensor may not report is refused`() {
        assertThat(Libre3Staging.acceptForCurve(-1, sample(Libre3Ingest.MIN_MGDL - 1.0, 70))).isFalse()
        assertThat(Libre3Staging.acceptForCurve(-1, sample(Libre3Ingest.MAX_MGDL + 1.0, 70))).isFalse()
        assertThat(Libre3Staging.acceptForCurve(-1, sample(Libre3Ingest.MIN_MGDL, 70))).isTrue()
        assertThat(Libre3Staging.acceptForCurve(-1, sample(Libre3Ingest.MAX_MGDL, 70))).isTrue()
    }
}

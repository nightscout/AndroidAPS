package app.aaps.plugins.source

import app.aaps.core.data.model.SourceSensor
import app.aaps.plugins.libre3.Libre3GlucoseSample
import app.aaps.plugins.libre3.Libre3WarmupState
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * What is allowed to reach the loop.
 *
 * These are safety rules, not style: everything that passes here is a number AAPS may dose on.
 */
class Libre3IngestTest {

    private val startMs = 1_777_216_508_000L

    private fun sample(mgdl: Double = 120.0, lifeCount: Int = 100, timestampMs: Long = startMs) =
        Libre3GlucoseSample(mgdl = mgdl, timestampMs = timestampMs, lifeCount = lifeCount)

    @BeforeEach
    fun setup() {
        Libre3Ingest.reset()
    }

    @Test
    fun `warm-up blocks every reading`() {
        assertThat(Libre3Ingest.isWarmupBlockingIngest(Libre3WarmupState.Phase.WARMING)).isTrue()
    }

    @Test
    fun `pairing and idle do not block, so a sensor already past warm-up still reports`() {
        // A sensor taken over mid-life is never in the warming phase. Blocking on these two would
        // mean it could never deliver anything.
        assertThat(Libre3Ingest.isWarmupBlockingIngest(Libre3WarmupState.Phase.PAIRING)).isFalse()
        assertThat(Libre3Ingest.isWarmupBlockingIngest(Libre3WarmupState.Phase.IDLE)).isFalse()
        assertThat(Libre3Ingest.isWarmupBlockingIngest(Libre3WarmupState.Phase.READY)).isFalse()
        assertThat(Libre3Ingest.isWarmupBlockingIngest(Libre3WarmupState.Phase.RECONNECTING)).isFalse()
    }

    @Test
    fun `a first reading is accepted`() {
        assertThat(Libre3Ingest.shouldAccept(sample())).isTrue()
    }

    @Test
    fun `the same reading is never inserted twice`() {
        Libre3Ingest.shouldAccept(sample(lifeCount = 100))

        assertThat(Libre3Ingest.shouldAccept(sample(lifeCount = 100))).isFalse()
    }

    @Test
    fun `an older reading is refused`() {
        Libre3Ingest.shouldAccept(sample(lifeCount = 100))

        assertThat(Libre3Ingest.shouldAccept(sample(lifeCount = 99, timestampMs = startMs + 600_000L))).isFalse()
    }

    @Test
    fun `the same reading delivered twice within seconds is treated as one`() {
        Libre3Ingest.shouldAccept(sample(lifeCount = 100, timestampMs = startMs))

        val tooClose = sample(lifeCount = 101, timestampMs = startMs + 29_000L)
        assertThat(Libre3Ingest.shouldAccept(tooClose)).isFalse()
    }

    /**
     * The sensor speaks once a minute and every one of those readings must reach the loop. This is
     * the test that keeps the driver from quietly becoming a five minute sensor again.
     */
    @Test
    fun `every reading of a sensor that speaks once a minute is accepted`() {
        var accepted = 0
        for (minute in 0 until 20) {
            val reading = sample(lifeCount = 100 + minute, timestampMs = startMs + minute * 60_000L)
            if (Libre3Ingest.shouldAccept(reading)) accepted++
        }

        assertThat(accepted).isEqualTo(20)
    }

    @Test
    fun `a number no sensor may report never reaches the loop`() {
        assertThat(Libre3Ingest.shouldAccept(sample(mgdl = 0.0))).isFalse()
        assertThat(Libre3Ingest.shouldAccept(sample(mgdl = 38.0))).isFalse()
        assertThat(Libre3Ingest.shouldAccept(sample(mgdl = 502.0))).isFalse()
        assertThat(Libre3Ingest.shouldAccept(sample(mgdl = -1.0))).isFalse()
    }

    @Test
    fun `the two ends of the allowed range are accepted`() {
        assertThat(Libre3Ingest.shouldAccept(sample(mgdl = 39.0, lifeCount = 1))).isTrue()
        assertThat(Libre3Ingest.shouldAccept(sample(mgdl = 501.0, lifeCount = 2, timestampMs = startMs + 600_000L)))
            .isTrue()
    }

    @Test
    fun `a restart does not insert readings that are already stored`() {
        Libre3Ingest.seed(lastLifeCount = 500, recentTimestampsMs = listOf(startMs))

        assertThat(Libre3Ingest.shouldAccept(sample(lifeCount = 400, timestampMs = startMs + 600_000L))).isFalse()
        assertThat(Libre3Ingest.shouldAccept(sample(lifeCount = 500, timestampMs = startMs + 600_000L))).isFalse()
        assertThat(Libre3Ingest.shouldAccept(sample(lifeCount = 501, timestampMs = startMs + 600_000L))).isTrue()
    }

    @Test
    fun `a new sensor starts counting again after a reset`() {
        Libre3Ingest.shouldAccept(sample(lifeCount = 20000))

        Libre3Ingest.reset()

        assertThat(Libre3Ingest.shouldAccept(sample(lifeCount = 1, timestampMs = startMs + 600_000L))).isTrue()
    }

    @Test
    fun `a reading is written with the native Libre 3 tag`() {
        val gv = Libre3Ingest.mapToGv(sample(mgdl = 142.0))

        assertThat(gv.sourceSensor).isEqualTo(SourceSensor.LIBRE_3_NATIVE)
        assertThat(gv.value).isEqualTo(142.0)
        assertThat(gv.timestamp).isEqualTo(startMs)
    }

    @Test
    fun `the follower Libre 3 tag is never used for a native reading`() {
        val gv = Libre3Ingest.mapToGv(sample())

        assertThat(gv.sourceSensor).isNotEqualTo(SourceSensor.LIBRE_3)
        assertThat(gv.sourceSensor.text).isEqualTo("AAPS-Libre3")
    }
}

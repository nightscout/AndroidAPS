package app.aaps.plugins.source

import app.aaps.core.data.model.SourceSensor
import app.aaps.core.data.model.TrendArrow
import app.aaps.plugins.dexcomoneplus.OnePlusGlucoseSample
import app.aaps.plugins.dexcomoneplus.OnePlusWarmupState
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DexcomOnePlusIngestTest {

    @BeforeEach
    fun clearDedup() {
        DexcomOnePlusIngest.clearDedupForTests()
    }

    @Test
    fun `mapToGv uses native source sensor and trend`() {
        val sample = OnePlusGlucoseSample(
            mgdl = 118.0,
            timestampMs = 1_700_000_000_000L,
            trendSlopeMgdlPerMin = 1.2,
            sequence = 42L,
        )

        val gv = DexcomOnePlusIngest.mapToGv(sample)

        assertThat(gv.timestamp).isEqualTo(1_700_000_000_000L)
        assertThat(gv.value).isEqualTo(118.0)
        assertThat(gv.raw).isNull()
        assertThat(gv.noise).isNull()
        assertThat(gv.trendArrow).isEqualTo(TrendArrow.FORTY_FIVE_UP)
        assertThat(gv.sourceSensor).isEqualTo(SourceSensor.DEXCOM_ONEPLUS_NATIVE)
    }

    @Test
    fun `mapToGv keeps NONE when the sensor sent no usable trend`() {
        val gv = DexcomOnePlusIngest.mapToGv(
            OnePlusGlucoseSample(mgdl = 90.0, timestampMs = 1L, trendSlopeMgdlPerMin = null)
        )
        assertThat(gv.trendArrow).isEqualTo(TrendArrow.NONE)
    }

    /**
     * The regression this covers: the parser reports a slope in mg/dL per minute, and that used to
     * be matched against arrow *names*. Nothing matched, so every reading arrived as NONE and the
     * glucose history showed the invalid-arrow icon for the whole ONE+ list.
     */
    @Test
    fun `a slope the parser really produces never degrades to NONE`() {
        listOf(-5.0, -2.5, -1.4, -0.5, 0.0, 0.9, 1.2, 2.4, 4.0).forEach { slope ->
            val gv = DexcomOnePlusIngest.mapToGv(
                OnePlusGlucoseSample(mgdl = 118.0, timestampMs = 1L, trendSlopeMgdlPerMin = slope)
            )
            assertThat(gv.trendArrow).isNotEqualTo(TrendArrow.NONE)
        }
    }

    @Test
    fun `trendArrowFor maps slope to arrow at every boundary`() {
        assertThat(DexcomOnePlusIngest.trendArrowFor(null)).isEqualTo(TrendArrow.NONE)
        assertThat(DexcomOnePlusIngest.trendArrowFor(4.0)).isEqualTo(TrendArrow.DOUBLE_UP)
        assertThat(DexcomOnePlusIngest.trendArrowFor(3.0)).isEqualTo(TrendArrow.DOUBLE_UP)
        assertThat(DexcomOnePlusIngest.trendArrowFor(2.9)).isEqualTo(TrendArrow.SINGLE_UP)
        assertThat(DexcomOnePlusIngest.trendArrowFor(2.0)).isEqualTo(TrendArrow.SINGLE_UP)
        assertThat(DexcomOnePlusIngest.trendArrowFor(1.9)).isEqualTo(TrendArrow.FORTY_FIVE_UP)
        assertThat(DexcomOnePlusIngest.trendArrowFor(1.0)).isEqualTo(TrendArrow.FORTY_FIVE_UP)
        assertThat(DexcomOnePlusIngest.trendArrowFor(0.9)).isEqualTo(TrendArrow.FLAT)
        assertThat(DexcomOnePlusIngest.trendArrowFor(0.0)).isEqualTo(TrendArrow.FLAT)
        assertThat(DexcomOnePlusIngest.trendArrowFor(-0.9)).isEqualTo(TrendArrow.FLAT)
        assertThat(DexcomOnePlusIngest.trendArrowFor(-1.0)).isEqualTo(TrendArrow.FORTY_FIVE_DOWN)
        assertThat(DexcomOnePlusIngest.trendArrowFor(-1.9)).isEqualTo(TrendArrow.FORTY_FIVE_DOWN)
        assertThat(DexcomOnePlusIngest.trendArrowFor(-2.0)).isEqualTo(TrendArrow.SINGLE_DOWN)
        assertThat(DexcomOnePlusIngest.trendArrowFor(-2.9)).isEqualTo(TrendArrow.SINGLE_DOWN)
        assertThat(DexcomOnePlusIngest.trendArrowFor(-3.0)).isEqualTo(TrendArrow.DOUBLE_DOWN)
        assertThat(DexcomOnePlusIngest.trendArrowFor(-5.0)).isEqualTo(TrendArrow.DOUBLE_DOWN)
    }

    @Test
    fun `warmup WARMING blocks ingest`() {
        assertThat(DexcomOnePlusIngest.isWarmupBlockingIngest(OnePlusWarmupState.Phase.WARMING)).isTrue()
        assertThat(DexcomOnePlusIngest.isWarmupBlockingIngest(OnePlusWarmupState.Phase.READY)).isFalse()
        assertThat(DexcomOnePlusIngest.isWarmupBlockingIngest(OnePlusWarmupState.Phase.IDLE)).isFalse()
        assertThat(DexcomOnePlusIngest.isWarmupBlockingIngest(OnePlusWarmupState.Phase.PAIRING)).isFalse()
        assertThat(DexcomOnePlusIngest.isWarmupBlockingIngest(OnePlusWarmupState.Phase.FAILED)).isFalse()
    }

    @Test
    fun `shouldAccept drops near-duplicate timestamps`() {
        val a = OnePlusGlucoseSample(mgdl = 100.0, timestampMs = 1_000_000L)
        val near = OnePlusGlucoseSample(mgdl = 101.0, timestampMs = 1_000_000L + 60_000L)
        val far = OnePlusGlucoseSample(mgdl = 102.0, timestampMs = 1_000_000L + DexcomOnePlusIngest.DEDUP_WINDOW_MS)
        assertThat(DexcomOnePlusIngest.shouldAccept(a)).isTrue()
        assertThat(DexcomOnePlusIngest.shouldAccept(near)).isFalse()
        assertThat(DexcomOnePlusIngest.shouldAccept(far)).isTrue()
    }

    @Test
    fun `shouldAccept drops duplicate sequence`() {
        val a = OnePlusGlucoseSample(mgdl = 110.0, timestampMs = 10L, sequence = 7L)
        val b = OnePlusGlucoseSample(mgdl = 111.0, timestampMs = 10L + DexcomOnePlusIngest.DEDUP_WINDOW_MS, sequence = 7L)
        assertThat(DexcomOnePlusIngest.shouldAccept(a)).isTrue()
        assertThat(DexcomOnePlusIngest.shouldAccept(b)).isFalse()
    }

    @Test
    fun `seed sequence floor rejects re-reads after a restart`() {
        // Simulate rehydration after an app update: last accepted sequence was 100.
        DexcomOnePlusIngest.seed(lastSequence = 100L, recentTimestampsMs = emptyList())
        // A backfilled/re-read reading at or below the floor is rejected even with a fresh timestamp.
        assertThat(DexcomOnePlusIngest.shouldAccept(OnePlusGlucoseSample(mgdl = 100.0, timestampMs = 5_000_000L, sequence = 100L))).isFalse()
        assertThat(DexcomOnePlusIngest.shouldAccept(OnePlusGlucoseSample(mgdl = 100.0, timestampMs = 5_000_000L, sequence = 99L))).isFalse()
        // A genuinely new reading above the floor is accepted.
        assertThat(DexcomOnePlusIngest.shouldAccept(OnePlusGlucoseSample(mgdl = 100.0, timestampMs = 6_000_000L, sequence = 101L))).isTrue()
    }

    @Test
    fun `seed timestamps reject recomputed re-reads within window`() {
        // The parser recomputes ts = now - age on each read, so a re-read after restart lands at a
        // slightly different timestamp that DB dedup (exact match) misses. Seeding recent stored
        // timestamps re-arms the near-duplicate window so the re-read is still dropped.
        DexcomOnePlusIngest.seed(lastSequence = -1L, recentTimestampsMs = listOf(2_000_000L))
        val reRead = OnePlusGlucoseSample(mgdl = 120.0, timestampMs = 2_000_000L + 30_000L) // 30 s off, no sequence
        assertThat(DexcomOnePlusIngest.shouldAccept(reRead)).isFalse()
    }
}

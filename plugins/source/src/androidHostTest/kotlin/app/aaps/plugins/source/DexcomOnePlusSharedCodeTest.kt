package app.aaps.plugins.source

import app.aaps.core.data.model.SourceSensor
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.interfaces.source.CgmWarmupStatus
import app.aaps.plugins.dexcomoneplus.OnePlusGlucoseSample
import app.aaps.plugins.dexcomoneplus.OnePlusWarmupState
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The Dexcom ONE+ ingest gate and warm-up mapping, written with kotlin.test only.
 *
 * The ingest gate is guarded by `AapsLock` rather than `synchronized`, so this code does not
 * depend on the JVM. Test names stay free of commas, so the class can move to a shared test source
 * set unchanged when the driver gets more targets.
 */
class DexcomOnePlusSharedCodeTest {

    @BeforeTest
    fun clearDedup() = DexcomOnePlusIngest.reset()

    @AfterTest
    fun clearDedupAfter() = DexcomOnePlusIngest.reset()

    // ---- ingest dedup, which now runs under AapsLock rather than synchronized ----

    @Test
    fun `a first reading is accepted and the same one again is not`() {
        val sample = OnePlusGlucoseSample(mgdl = 120.0, timestampMs = 1_000_000L, sequence = 7L)
        assertTrue(DexcomOnePlusIngest.shouldAccept(sample))
        assertFalse(DexcomOnePlusIngest.shouldAccept(sample))
    }

    @Test
    fun `a reading inside the dedup window is dropped and one outside it is taken`() {
        val first = OnePlusGlucoseSample(mgdl = 120.0, timestampMs = 1_000_000L, sequence = 1L)
        assertTrue(DexcomOnePlusIngest.shouldAccept(first))
        val tooClose = first.copy(timestampMs = first.timestampMs + DexcomOnePlusIngest.DEDUP_WINDOW_MS - 1, sequence = 2L)
        assertFalse(DexcomOnePlusIngest.shouldAccept(tooClose))
        val farEnough = first.copy(timestampMs = first.timestampMs + DexcomOnePlusIngest.DEDUP_WINDOW_MS, sequence = 3L)
        assertTrue(DexcomOnePlusIngest.shouldAccept(farEnough))
    }

    @Test
    fun `a sequence at or below the seeded high water mark is refused`() {
        DexcomOnePlusIngest.seed(lastSequence = 10L, recentTimestampsMs = emptyList())
        assertFalse(DexcomOnePlusIngest.shouldAccept(OnePlusGlucoseSample(mgdl = 100.0, timestampMs = 5_000_000L, sequence = 10L)))
        assertTrue(DexcomOnePlusIngest.shouldAccept(OnePlusGlucoseSample(mgdl = 100.0, timestampMs = 5_000_000L, sequence = 11L)))
    }

    @Test
    fun `reset clears the floor so a new sensor low sequence is taken again`() {
        DexcomOnePlusIngest.seed(lastSequence = 500L, recentTimestampsMs = emptyList())
        val fresh = OnePlusGlucoseSample(mgdl = 100.0, timestampMs = 9_000_000L, sequence = 3L)
        assertFalse(DexcomOnePlusIngest.shouldAccept(fresh))
        DexcomOnePlusIngest.reset()
        assertTrue(DexcomOnePlusIngest.shouldAccept(fresh))
    }

    @Test
    fun `ingest is blocked only while the sensor is warming`() {
        assertTrue(DexcomOnePlusIngest.isWarmupBlockingIngest(OnePlusWarmupState.Phase.WARMING))
        assertFalse(DexcomOnePlusIngest.isWarmupBlockingIngest(OnePlusWarmupState.Phase.PAIRING))
        assertFalse(DexcomOnePlusIngest.isWarmupBlockingIngest(OnePlusWarmupState.Phase.IDLE))
        assertFalse(DexcomOnePlusIngest.isWarmupBlockingIngest(OnePlusWarmupState.Phase.READY))
    }

    // ---- the slope to arrow mapping ----

    @Test
    fun `a missing slope stays NONE rather than becoming flat`() {
        assertEquals(TrendArrow.NONE, DexcomOnePlusIngest.trendArrowFor(null))
    }

    @Test
    fun `each slope step maps to its arrow`() {
        assertEquals(TrendArrow.DOUBLE_UP, DexcomOnePlusIngest.trendArrowFor(3.0))
        assertEquals(TrendArrow.SINGLE_UP, DexcomOnePlusIngest.trendArrowFor(2.0))
        assertEquals(TrendArrow.FORTY_FIVE_UP, DexcomOnePlusIngest.trendArrowFor(1.0))
        assertEquals(TrendArrow.FLAT, DexcomOnePlusIngest.trendArrowFor(0.0))
        assertEquals(TrendArrow.FORTY_FIVE_DOWN, DexcomOnePlusIngest.trendArrowFor(-1.5))
        assertEquals(TrendArrow.SINGLE_DOWN, DexcomOnePlusIngest.trendArrowFor(-2.5))
        assertEquals(TrendArrow.DOUBLE_DOWN, DexcomOnePlusIngest.trendArrowFor(-3.0))
    }

    @Test
    fun `a mapped reading carries the native source sensor`() {
        val gv = DexcomOnePlusIngest.mapToGv(
            OnePlusGlucoseSample(mgdl = 142.0, timestampMs = 1_700_000_000_000L, trendSlopeMgdlPerMin = 1.2, sequence = 4L)
        )
        assertEquals(SourceSensor.DEXCOM_ONEPLUS_NATIVE, gv.sourceSensor)
        assertEquals(142.0, gv.value)
        assertEquals(1_700_000_000_000L, gv.timestamp)
        assertEquals(TrendArrow.FORTY_FIVE_UP, gv.trendArrow)
    }

    // ---- warm-up mapping ----

    @Test
    fun `a terminal warm-up phase shows nothing`() {
        assertNull(DexcomOnePlusWarmupMapper.toCgmWarmupStatus(OnePlusWarmupState(OnePlusWarmupState.Phase.READY)))
        assertNull(DexcomOnePlusWarmupMapper.toCgmWarmupStatus(OnePlusWarmupState(OnePlusWarmupState.Phase.IDLE)))
        assertNull(DexcomOnePlusWarmupMapper.toCgmWarmupStatus(OnePlusWarmupState(OnePlusWarmupState.Phase.FAILED)))
    }

    @Test
    fun `warming carries the countdown and a total so the ring can fill`() {
        val status = DexcomOnePlusWarmupMapper.toCgmWarmupStatus(
            OnePlusWarmupState(OnePlusWarmupState.Phase.WARMING, remainingMs = 600_000L)
        )
        assertTrue(status != null && status.active)
        assertEquals(CgmWarmupStatus.Phase.WARMING, status.phase)
        assertEquals(600_000L, status.remainingMs)
        assertTrue((status.totalMs ?: 0L) > 0L)
    }

    @Test
    fun `a reconnect without a deadline has no countdown total`() {
        val status = DexcomOnePlusWarmupMapper.toCgmWarmupStatus(
            OnePlusWarmupState(OnePlusWarmupState.Phase.RECONNECTING)
        )
        assertEquals(CgmWarmupStatus.Phase.RECONNECTING, status?.phase)
        assertNull(status?.totalMs)
    }
}

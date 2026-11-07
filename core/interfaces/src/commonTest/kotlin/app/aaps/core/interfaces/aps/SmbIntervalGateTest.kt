package app.aaps.core.interfaces.aps

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SmbIntervalGateTest {

    private val now = 1_700_000_000_000L
    private val interval = 3
    private val intervalMs = interval * 60_000L

    @Test
    fun `a bolus inside the interval is too soon`() {
        assertTrue(SmbIntervalGate.tooSoon(now - 60_000L, interval, now))
    }

    @Test
    fun `a bolus 7 s short of the interval is too soon`() {
        assertTrue(SmbIntervalGate.tooSoon(now - intervalMs + 7_000L, interval, now))
    }

    @Test
    fun `a bolus exactly at the tolerance is not too soon`() {
        assertFalse(SmbIntervalGate.tooSoon(now - intervalMs + SmbIntervalGate.TOLERANCE_MS, interval, now))
    }

    @Test
    fun `a bolus 5 s short of the interval is not too soon`() {
        assertFalse(SmbIntervalGate.tooSoon(now - intervalMs + 5_000L, interval, now))
    }

    @Test
    fun `a bolus older than the interval is not too soon`() {
        assertFalse(SmbIntervalGate.tooSoon(now - intervalMs - 1L, interval, now))
    }

    @Test
    fun `no bolus at all is not too soon`() {
        assertFalse(SmbIntervalGate.tooSoon(0L, 10, now))
    }
}

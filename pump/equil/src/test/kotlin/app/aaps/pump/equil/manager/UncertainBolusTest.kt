package app.aaps.pump.equil.manager

import app.aaps.core.data.model.BS
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The log entries are as a real pump wrote them on 2026-11-08: a bolus starts with its speed and ends with speed 0. */
class UncertainBolusTest {

    private val sent = 1_000_000L

    private fun bolus() = UncertainBolus(
        requested = 2.0, unitsPerSecond = 1.0 / 40, indexBefore = 1500, sentAt = sent, decideAfter = sent + 83_000,
        bolusType = BS.Type.NORMAL, pumpId = 1L, recordTime = 2L
    )

    @Test
    fun `an index counts as after also when the log starts again at 1`() {
        assertTrue(UncertainBolus.isAfter(1501, 1500))
        assertFalse(UncertainBolus.isAfter(1500, 1500))
        assertFalse(UncertainBolus.isAfter(1499, 1500))
        assertTrue(UncertainBolus.isAfter(3, 1998))
        assertFalse(UncertainBolus.isAfter(1998, 3))
    }

    @Test
    fun `a bolus from before the command is not this one`() {
        val bolus = bolus()
        bolus.onLogEntry(1500, 4, 0, 0, 14400, sent - 60_000)
        bolus.onLogEntry(1499, 4, 0, 0, 0, sent - 40_000)
        assertNull(bolus.inLog())
    }

    @Test
    fun `a bolus starts with its speed and ends with speed 0`() {
        val bolus = bolus()
        bolus.onLogEntry(1501, 4, 0, 0, 14400, sent)
        bolus.onLogEntry(1502, 4, 0, 0, 0, sent + 20_000)
        assertEquals(sent, bolus.inLog()?.start)
        assertEquals(sent + 20_000, bolus.inLog()?.end)
    }

    @Test
    fun `a basal change during the bolus does not end it`() {
        val bolus = bolus()
        bolus.onLogEntry(1501, 4, 0, 0, 14400, sent)
        bolus.onLogEntry(1502, 4, 11, 0, 0, sent + 10_000) // the temp basal runs out
        bolus.onLogEntry(1503, 4, 0, 0, 14400, sent + 10_000)
        assertNull(bolus.inLog()?.end)
        bolus.onLogEntry(1504, 4, 0, 0, 0, sent + 80_000)
        assertEquals(sent + 80_000, bolus.inLog()?.end)
    }

    @Test
    fun `an empty reservoir ends it without a status entry`() {
        val empty = bolus()
        empty.onLogEntry(1501, 4, 0, 0, 14400, sent)
        empty.onLogEntry(1502, 4, 1, 2, 0, sent + 50_000)
        assertEquals(sent + 50_000, empty.inLog()?.end)
    }

    @Test
    fun `a blockage does not end it but its end entry does`() {
        val blocked = bolus()
        blocked.onLogEntry(1501, 4, 0, 0, 14400, sent)
        blocked.onLogEntry(1502, 4, 2, 2, 0, sent + 30_000)
        assertNull(blocked.inLog()?.end)
        blocked.onLogEntry(1503, 4, 0, 0, 0, sent + 80_000)
        assertEquals(sent + 80_000, blocked.inLog()?.end)
    }

    @Test
    fun `of two boluses in the log the one sent then is this one`() {
        val bolus = bolus()
        bolus.onLogEntry(1501, 4, 0, 0, 14400, sent - 30_000)
        bolus.onLogEntry(1502, 4, 0, 0, 0, sent - 10_000)
        bolus.onLogEntry(1503, 4, 0, 0, 14400, sent + 1_000)
        bolus.onLogEntry(1504, 4, 0, 0, 0, sent + 41_000)
        assertEquals(sent + 1_000, bolus.inLog()?.start)
        assertEquals(sent + 41_000, bolus.inLog()?.end)
    }
}

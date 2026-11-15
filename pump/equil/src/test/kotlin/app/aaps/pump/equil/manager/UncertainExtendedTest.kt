package app.aaps.pump.equil.manager

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** The start entry is as a real pump wrote it on 2026-11-10: status (4,0,0) with largeRate 160 for 0.5 U in 30 min. */
class UncertainExtendedTest {

    private val sent = 1_000_000L

    @Test
    fun `a start is done when the log has its speed after the command`() {
        val start = UncertainExtended(start = true, largeRate = 160, indexBefore = 1566, sentAt = sent)

        start.onLogEntry(index = 1566, port = 4, type = 0, largeRate = 160, time = sent - 60_000)
        assertNull(start.doneAt)
        start.onLogEntry(index = 1567, port = 4, type = 0, largeRate = 160, time = sent + 1000)

        assertEquals(sent + 1000, start.doneAt)
    }

    @Test
    fun `another speed or another entry is not the start`() {
        val start = UncertainExtended(start = true, largeRate = 160, indexBefore = 1566, sentAt = sent)

        start.onLogEntry(index = 1567, port = 4, type = 0, largeRate = 14400, time = sent + 1000)
        start.onLogEntry(index = 1568, port = 4, type = 10, largeRate = 160, time = sent + 2000)

        assertNull(start.doneAt)
    }

    @Test
    fun `a cancel is done at the first status entry with speed 0`() {
        val cancel = UncertainExtended(start = false, largeRate = 0, indexBefore = 1999, sentAt = sent)

        cancel.onLogEntry(index = 2000, port = 4, type = 0, largeRate = 160, time = sent + 500)
        cancel.onLogEntry(index = 1, port = 4, type = 0, largeRate = 0, time = sent + 1000)
        cancel.onLogEntry(index = 2, port = 4, type = 0, largeRate = 0, time = sent + 9000)

        assertEquals(sent + 1000, cancel.doneAt)
    }
}

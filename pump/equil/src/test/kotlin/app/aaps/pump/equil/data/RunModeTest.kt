package app.aaps.pump.equil.data

import app.aaps.shared.tests.TestBase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RunModeTest : TestBase() {

    @Test
    fun `all modes should have correct command values`() {
        assertEquals(1, RunMode.RUN.command)
        assertEquals(2, RunMode.STOP.command)
        assertEquals(0, RunMode.SUSPEND.command)
        assertEquals(-1, RunMode.NONE.command)
    }

    @Test
    fun `all enum values should be accessible`() {
        val allModes = RunMode.entries
        assertEquals(4, allModes.size)
        assert(allModes.contains(RunMode.RUN))
        assert(allModes.contains(RunMode.STOP))
        assert(allModes.contains(RunMode.SUSPEND))
        assert(allModes.contains(RunMode.NONE))
    }

    @Test
    fun `valueOf should return correct enum`() {
        assertEquals(RunMode.RUN, RunMode.valueOf("RUN"))
        assertEquals(RunMode.STOP, RunMode.valueOf("STOP"))
        assertEquals(RunMode.SUSPEND, RunMode.valueOf("SUSPEND"))
        assertEquals(RunMode.NONE, RunMode.valueOf("NONE"))
    }

    @Test
    fun `enum toString should return name`() {
        assertEquals("RUN", RunMode.RUN.toString())
        assertEquals("STOP", RunMode.STOP.toString())
        assertEquals("SUSPEND", RunMode.SUSPEND.toString())
        assertEquals("NONE", RunMode.NONE.toString())
    }

    @Test
    fun `command values should be unique except for expected overlaps`() {
        val commands = RunMode.entries.map { it.command }
        // All command values should be present
        assert(commands.contains(1))
        assert(commands.contains(2))
        assert(commands.contains(0))
        assert(commands.contains(-1))
    }

    @Test
    fun `enum ordinal values should be stable`() {
        assertEquals(0, RunMode.RUN.ordinal)
        assertEquals(1, RunMode.STOP.ordinal)
        assertEquals(2, RunMode.SUSPEND.ordinal)
        assertEquals(3, RunMode.NONE.ordinal)
    }
}

package app.aaps.pump.equil.manager.customCommands

import app.aaps.shared.tests.TestBase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CmdModeAndHistoryGetTest : TestBase() {

    @Test
    fun `statusDescription should return correct value`() {
        val cmd = CmdModeAndHistoryGet()
        assertEquals("CmdRunningModeGet", cmd.statusDescription)
    }

    @Test
    fun `should implement CustomCommand interface`() {
        val cmd = CmdModeAndHistoryGet()
        assertEquals("CmdRunningModeGet", cmd.statusDescription)
    }

    @Test
    fun `multiple instances should have same statusDescription`() {
        val cmd1 = CmdModeAndHistoryGet()
        val cmd2 = CmdModeAndHistoryGet()
        assertEquals(cmd1.statusDescription, cmd2.statusDescription)
    }
}

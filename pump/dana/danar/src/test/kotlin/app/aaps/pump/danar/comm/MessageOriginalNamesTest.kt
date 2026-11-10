package app.aaps.pump.danar.comm

import app.aaps.shared.tests.TestBase
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class MessageOriginalNamesTest : TestBase() {

    @Test
    fun getNameTest() {
        Assertions.assertEquals("CMD_CONNECT", MessageOriginalNames.getName(0x3001))
    }

    /** A pump can send a command with no name here. Throwing for it ended the reader of the link. */
    @Test
    fun aCommandWithNoNameGetsOneFromItsCode() {
        Assertions.assertEquals("UNKNOWN_00A5", MessageOriginalNames.getName(0x00A5))
    }
}
package app.aaps.pump.danaR.comm

import app.aaps.pump.danar.comm.MessageOriginalNames
import app.aaps.shared.tests.TestBase
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class MessageOriginalNamesTest : TestBase() {

    @Test
    fun getNameTest() {
        Assertions.assertEquals("CMD_CONNECT", MessageOriginalNames.getName(0x3001))
    }
}
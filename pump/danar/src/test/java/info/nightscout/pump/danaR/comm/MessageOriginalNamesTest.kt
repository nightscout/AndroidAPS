package info.nightscout.pump.danaR.comm

import info.nightscout.androidaps.danar.comm.MessageOriginalNames
import info.nightscout.sharedtests.TestBase
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class MessageOriginalNamesTest : TestBase() {

    @Test
    fun getNameTest() {
        Assertions.assertEquals("CMD_CONNECT", MessageOriginalNames.getName(0x3001))
    }
}
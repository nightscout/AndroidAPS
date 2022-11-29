package info.nightscout.androidaps.plugins.pump.danaR.comm

import info.nightscout.androidaps.TestBase
import info.nightscout.androidaps.danar.comm.MessageOriginalNames
import org.junit.Assert
import org.junit.jupiter.api.Test

class MessageOriginalNamesTest : TestBase() {

    @Test
    fun getNameTest() {
        Assert.assertEquals("CMD_CONNECT", MessageOriginalNames.getName(0x3001))
    }
}
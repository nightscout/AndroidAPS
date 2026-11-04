package app.aaps.pump.danarkorean.comm

import app.aaps.pump.danar.comm.DanaRTestBase
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class MessageHashTableRKoreanTest : DanaRTestBase() {

    @Test fun runTest() {
        val messageHashTable = MessageHashTableRKorean(injector)
        val testMessage = messageHashTable.findMessage(0x41f2)
        Assertions.assertEquals("CMD_HISTORY_ALL", testMessage.messageName)
    }
}
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

    /**
     * A message the table does not have gets the received command, and handling it only logs it. Its name used to throw
     * "Unknown command: 0000", which ended the reader of the link (a real Korean DanaR, after a history read).
     */
    @Test fun aMessageTheTableDoesNotHaveIsOnlyLogged() {
        val messageHashTable = MessageHashTableRKorean(injector)

        val unknown = messageHashTable.findMessage(0x31F1)
        unknown.handleMessage(createArray(10, 0.toByte()))

        Assertions.assertEquals(0x31F1, unknown.command)
        Assertions.assertEquals("CMD_HISTORY_STOP", unknown.messageName)
        Assertions.assertEquals("UNKNOWN_ABCD", messageHashTable.findMessage(0xABCD).messageName)
    }
}
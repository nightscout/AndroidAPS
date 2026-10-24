package app.aaps.pump.danarv2.comm

import app.aaps.pump.danar.comm.DanaRTestBase
import app.aaps.pump.danar.comm.MessageBase
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class MessageHashTableRv2Test : DanaRTestBase() {

    @Test
    fun runTest() {
        val messageHashTableRv2 = MessageHashTableRv2(injector)
        val forTesting: MessageBase = MsgStatusAPSV2(injector)
        val testPacket: MessageBase = messageHashTableRv2.findMessage(forTesting.command)
        Assertions.assertEquals(0xE001, testPacket.command.toLong())
        // try putting another command
        val testMessage = MessageBase(injector)
        testMessage.setCommand(0xE005)
        messageHashTableRv2.put(testMessage)
        Assertions.assertEquals(0xE005, messageHashTableRv2.findMessage(0xE005).command.toLong())
    }
}
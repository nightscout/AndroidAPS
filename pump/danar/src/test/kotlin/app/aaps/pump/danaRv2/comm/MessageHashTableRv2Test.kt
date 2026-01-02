package app.aaps.pump.danaRv2.comm

import app.aaps.core.objects.constraints.ConstraintObject
import app.aaps.pump.danaR.comm.DanaRTestBase
import app.aaps.pump.danar.comm.MessageBase
import app.aaps.pump.danarv2.comm.MessageHashTableRv2
import app.aaps.pump.danarv2.comm.MsgStatusAPSV2
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever

class MessageHashTableRv2Test : DanaRTestBase() {

    @Test
    fun runTest() {
        whenever(constraintChecker.applyBolusConstraints(any())).thenReturn(ConstraintObject(0.0, aapsLogger))
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
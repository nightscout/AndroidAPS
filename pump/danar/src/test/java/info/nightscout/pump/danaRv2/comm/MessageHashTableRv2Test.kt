package info.nightscout.pump.danaRv2.comm

import info.nightscout.androidaps.danaRv2.comm.MessageHashTableRv2
import info.nightscout.androidaps.danaRv2.comm.MsgStatusAPS_v2
import info.nightscout.androidaps.danar.comm.MessageBase
import info.nightscout.interfaces.constraints.Constraint
import info.nightscout.pump.danaR.comm.DanaRTestBase
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`

class MessageHashTableRv2Test : DanaRTestBase() {

    @Test
    fun runTest() {
        `when`(constraintChecker.applyBolusConstraints(anyObject())).thenReturn(Constraint(0.0))
        val messageHashTableRv2 = MessageHashTableRv2(injector)
        val forTesting: MessageBase = MsgStatusAPS_v2(injector)
        val testPacket: MessageBase = messageHashTableRv2.findMessage(forTesting.command)
        Assertions.assertEquals(0xE001, testPacket.command.toLong())
        // try putting another command
        val testMessage = MessageBase(injector)
        testMessage.setCommand(0xE005)
        messageHashTableRv2.put(testMessage)
        Assertions.assertEquals(0xE005, messageHashTableRv2.findMessage(0xE005).command.toLong())
    }
}
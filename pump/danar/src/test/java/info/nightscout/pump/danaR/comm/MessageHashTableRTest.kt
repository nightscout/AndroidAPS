package info.nightscout.pump.danaR.comm

import info.nightscout.androidaps.danar.comm.MessageHashTableR
import info.nightscout.core.constraints.ConstraintObject
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.Mockito

class MessageHashTableRTest : DanaRTestBase() {

    @Test fun runTest() {
        Mockito.`when`(constraintChecker.applyBolusConstraints(anyObject())).thenReturn(ConstraintObject(0.0, aapsLogger))
        val messageHashTable = MessageHashTableR(injector)
        val testMessage = messageHashTable.findMessage(0x41f2)
        Assertions.assertEquals("CMD_HISTORY_ALL", testMessage.messageName)
    }
}
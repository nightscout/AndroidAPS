package info.nightscout.pump.danaR.comm

import app.aaps.core.main.constraints.ConstraintObject
import info.nightscout.androidaps.danar.comm.MessageHashTableR
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
package info.nightscout.pump.danaRKorean.comm

import info.nightscout.androidaps.danaRKorean.comm.MessageHashTableRKorean
import info.nightscout.core.constraints.ConstraintObject
import info.nightscout.pump.danaR.comm.DanaRTestBase
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.Mockito

class MessageHashTableRKoreanTest : DanaRTestBase() {

    @Test fun runTest() {
        Mockito.`when`(constraintChecker.applyBolusConstraints(anyObject())).thenReturn(ConstraintObject(0.0, aapsLogger))
        val messageHashTable = MessageHashTableRKorean(injector)
        val testMessage = messageHashTable.findMessage(0x41f2)
        Assertions.assertEquals("CMD_HISTORY_ALL", testMessage.messageName)
    }
}
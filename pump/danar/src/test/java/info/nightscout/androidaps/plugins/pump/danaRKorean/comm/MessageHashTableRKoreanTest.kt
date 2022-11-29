package info.nightscout.androidaps.plugins.pump.danaRKorean.comm

import info.nightscout.androidaps.danaRKorean.comm.MessageHashTableRKorean
import info.nightscout.androidaps.plugins.pump.danaR.comm.DanaRTestBase
import info.nightscout.interfaces.constraints.Constraint
import org.junit.Assert
import org.junit.jupiter.api.Test
import org.mockito.Mockito

class MessageHashTableRKoreanTest : DanaRTestBase() {

    @Test fun runTest() {
        Mockito.`when`(constraintChecker.applyBolusConstraints(anyObject())).thenReturn(Constraint(0.0))
        val messageHashTable = MessageHashTableRKorean(injector)
        val testMessage = messageHashTable.findMessage(0x41f2)
        Assert.assertEquals("CMD_HISTORY_ALL", testMessage.messageName)
    }
}
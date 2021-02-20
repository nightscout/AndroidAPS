package info.nightscout.androidaps.plugins.pump.danaRKorean.comm

import info.nightscout.androidaps.danaRKorean.comm.MessageHashTableRKorean
import info.nightscout.androidaps.interfaces.Constraint
import info.nightscout.androidaps.plugins.pump.danaR.comm.DanaRTestBase
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
class MessageHashTableRKoreanTest : DanaRTestBase() {

    @Test fun runTest() {
        Mockito.`when`(constraintChecker.applyBolusConstraints(anyObject())).thenReturn(Constraint(0.0))
        val messageHashTable = MessageHashTableRKorean(injector)
        val testMessage = messageHashTable.findMessage(0x41f2)
        Assert.assertEquals("CMD_HISTORY_ALL", testMessage.messageName)
    }
}
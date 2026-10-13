package app.aaps.pump.danarkorean.comm

import app.aaps.core.objects.constraints.ConstraintObject
import app.aaps.pump.danar.comm.DanaRTestBase
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever

class MessageHashTableRKoreanTest : DanaRTestBase() {

    @Test fun runTest() {
        whenever(constraintChecker.applyBolusConstraints(any())).thenReturn(ConstraintObject(0.0, aapsLogger))
        val messageHashTable = MessageHashTableRKorean(injector)
        val testMessage = messageHashTable.findMessage(0x41f2)
        Assertions.assertEquals("CMD_HISTORY_ALL", testMessage.messageName)
    }
}
package app.aaps.pump.danaR.comm

import app.aaps.core.objects.constraints.ConstraintObject
import app.aaps.pump.danar.comm.MsgSetExtendedBolusStart
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever

class MsgSetExtendedBolusStartTest : DanaRTestBase() {

    @Test fun runTest() {
        whenever(constraintChecker.applyBolusConstraints(any())).thenReturn(ConstraintObject(0.0, aapsLogger))
        val packet = MsgSetExtendedBolusStart(injector, 2.0, 2.toByte())

        // test message decoding
        packet.handleMessage(createArray(34, 7.toByte()))
        Assertions.assertEquals(true, packet.failed)
    }

}
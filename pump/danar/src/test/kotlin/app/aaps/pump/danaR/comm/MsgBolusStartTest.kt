package app.aaps.pump.danaR.comm

import app.aaps.core.objects.constraints.ConstraintObject
import app.aaps.pump.danar.comm.MsgBolusStart
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever

class MsgBolusStartTest : DanaRTestBase() {

    @Test fun runTest() {
        whenever(constraintChecker.applyBolusConstraints(any())).thenReturn(ConstraintObject(0.0, aapsLogger))
        val packet = MsgBolusStart(injector, 1.0)

        // test message decoding
        val array = ByteArray(100)

        putByteToArray(array, 0, 1)
        packet.handleMessage(array)
        Assertions.assertEquals(true, packet.failed)

        putByteToArray(array, 0, 2)
        packet.handleMessage(array)
        Assertions.assertEquals(false, packet.failed)
    }
}
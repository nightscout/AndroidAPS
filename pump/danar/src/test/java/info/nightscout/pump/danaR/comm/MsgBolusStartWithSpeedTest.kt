package info.nightscout.pump.danaR.comm

import app.aaps.core.main.constraints.ConstraintObject
import info.nightscout.androidaps.danar.comm.MsgBolusStartWithSpeed
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.Mockito

class MsgBolusStartWithSpeedTest : DanaRTestBase() {

    @Test fun runTest() {
        Mockito.`when`(constraintChecker.applyBolusConstraints(anyObject())).thenReturn(ConstraintObject(0.0, aapsLogger))
        val packet = MsgBolusStartWithSpeed(injector, 0.0, 0)

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
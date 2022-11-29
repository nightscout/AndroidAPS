package info.nightscout.androidaps.plugins.pump.danaR.comm

import info.nightscout.androidaps.danar.comm.MsgBolusStartWithSpeed
import info.nightscout.interfaces.constraints.Constraint
import org.junit.Assert
import org.junit.jupiter.api.Test
import org.mockito.Mockito

class MsgBolusStartWithSpeedTest : DanaRTestBase() {

    @Test fun runTest() {
        Mockito.`when`(constraintChecker.applyBolusConstraints(anyObject())).thenReturn(Constraint(0.0))
        val packet = MsgBolusStartWithSpeed(injector, 0.0, 0)

        // test message decoding
        val array = ByteArray(100)

        putByteToArray(array, 0, 1)
        packet.handleMessage(array)
        Assert.assertEquals(true, packet.failed)

        putByteToArray(array, 0, 2)
        packet.handleMessage(array)
        Assert.assertEquals(false, packet.failed)
    }
}
package info.nightscout.androidaps.plugins.pump.danaR.comm

import info.nightscout.androidaps.danar.comm.MsgSetExtendedBolusStart
import info.nightscout.interfaces.constraints.Constraint
import org.junit.Assert
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`

class MsgSetExtendedBolusStartTest : DanaRTestBase() {

    @Test fun runTest() {
        `when`(constraintChecker.applyBolusConstraints(anyObject())).thenReturn(Constraint(0.0))
        val packet = MsgSetExtendedBolusStart(injector, 2.0, 2.toByte())

        // test message decoding
        packet.handleMessage(createArray(34, 7.toByte()))
        Assert.assertEquals(true, packet.failed)
    }

}
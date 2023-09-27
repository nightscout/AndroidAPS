package info.nightscout.pump.danaR.comm

import app.aaps.core.main.constraints.ConstraintObject
import info.nightscout.androidaps.danar.comm.MsgSetExtendedBolusStart
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`

class MsgSetExtendedBolusStartTest : DanaRTestBase() {

    @Test fun runTest() {
        `when`(constraintChecker.applyBolusConstraints(anyObject())).thenReturn(ConstraintObject(0.0, aapsLogger))
        val packet = MsgSetExtendedBolusStart(injector, 2.0, 2.toByte())

        // test message decoding
        packet.handleMessage(createArray(34, 7.toByte()))
        Assertions.assertEquals(true, packet.failed)
    }

}
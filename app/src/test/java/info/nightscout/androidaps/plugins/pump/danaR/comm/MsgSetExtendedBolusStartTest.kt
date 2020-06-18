package info.nightscout.androidaps.plugins.pump.danaR.comm

import info.nightscout.androidaps.danar.comm.MsgSetExtendedBolusStart
import info.nightscout.androidaps.interfaces.Constraint
import info.nightscout.androidaps.plugins.configBuilder.ConstraintChecker
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.`when`
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
@PrepareForTest(ConstraintChecker::class)
class MsgSetExtendedBolusStartTest : DanaRTestBase() {

    @Test fun runTest() {
        `when`(constraintChecker.applyBolusConstraints(anyObject())).thenReturn(Constraint(0.0))
        val packet = MsgSetExtendedBolusStart(injector, 2.0, 2.toByte())

        // test message decoding
        packet.handleMessage(createArray(34, 7.toByte()))
        Assert.assertEquals(true, packet.failed)
    }

}
package info.nightscout.androidaps.plugins.pump.danaR.comm

import info.nightscout.androidaps.danar.comm.MsgBolusStart
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
class MsgBolusStartTest : DanaRTestBase() {

    @Test fun runTest() {
        `when`(constraintChecker.applyBolusConstraints(Constraint(anyObject()))).thenReturn(Constraint(0.0))
        val packet = MsgBolusStart(injector, 1.0)

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
package info.nightscout.androidaps.plugins.pump.danaR.comm

import info.nightscout.androidaps.danar.comm.MsgSetExtendedBolusStop
import org.junit.Assert
import org.junit.jupiter.api.Test

class MsgSetExtendedBolusStopTest : DanaRTestBase() {

    @Test fun runTest() {
        val packet = MsgSetExtendedBolusStop(injector)

        // test message decoding
        packet.handleMessage(createArray(34, 7.toByte()))
        Assert.assertEquals(true, packet.failed)
    }
}
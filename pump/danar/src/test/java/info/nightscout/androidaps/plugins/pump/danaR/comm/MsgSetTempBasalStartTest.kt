package info.nightscout.androidaps.plugins.pump.danaR.comm

import info.nightscout.androidaps.danar.comm.MsgSetTempBasalStart
import org.junit.Assert
import org.junit.jupiter.api.Test

class MsgSetTempBasalStartTest : DanaRTestBase() {

    @Test fun runTest() {
        val packet = MsgSetTempBasalStart(injector, 250, 1)

        // test message decoding
        packet.handleMessage(createArray(34, 7.toByte()))
        Assert.assertEquals(true, packet.failed)
    }
}
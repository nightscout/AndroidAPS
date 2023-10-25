package info.nightscout.pump.danaR.comm

import info.nightscout.androidaps.danar.comm.MsgStatusTempBasal
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class MsgStatusTempBasalTest : DanaRTestBase() {

    @Test fun runTest() {
        val packet = MsgStatusTempBasal(injector)
        // test message decoding
        packet.handleMessage(createArray(34, 1.toByte()))
        Assertions.assertEquals(true, packet.isTempBasalInProgress)
        // passing an bigger number
        packet.handleMessage(createArray(34, 2.toByte()))
        Assertions.assertEquals(false, packet.isTempBasalInProgress)
    }
}
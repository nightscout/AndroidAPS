package app.aaps.pump.danaR.comm

import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.pump.danar.comm.MsgBolusProgress
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class MsgBolusProgressTest : DanaRTestBase() {

    @Test fun runTest() {
        danaPump.bolusingDetailedBolusInfo = DetailedBolusInfo().also { it.insulin = 3.0 }
        val packet = MsgBolusProgress(injector)

        // test message decoding
        val array = ByteArray(100)
        putIntToArray(array, 0, 2 * 100)
        packet.handleMessage(array)
        Assertions.assertEquals(1.0, BolusProgressData.delivered, 0.0)
    }
}
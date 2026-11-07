package app.aaps.pump.danar.comm

import app.aaps.core.interfaces.pump.DetailedBolusInfo
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class MsgBolusStopTest : DanaRTestBase() {

    @Test fun runTest() {
//        whenever(rh.gs(app.aaps.pump.dana.R.string.overview_bolusprogress_delivered)).thenReturn("Delivered")
        danaPump.bolusingDetailedBolusInfo = DetailedBolusInfo()
        val packet = MsgBolusStop(injector)

        // test message decoding
        packet.handleMessage(ByteArray(100))
        Assertions.assertEquals(true, danaPump.bolusStopped)
    }
}
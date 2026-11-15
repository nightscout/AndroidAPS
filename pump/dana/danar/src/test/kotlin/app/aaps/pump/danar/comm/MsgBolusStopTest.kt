package app.aaps.pump.danar.comm

import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.pump.PumpInsulin
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class MsgBolusStopTest : DanaRTestBase() {

    @Test fun runTest() {
        danaPump.bolusingDetailedBolusInfo = DetailedBolusInfo()
        val packet = MsgBolusStop(injector)

        // test message decoding
        packet.handleMessage(ByteArray(100))
        Assertions.assertEquals(true, danaPump.bolusStopped)
    }

    // The Korean DanaR records a stopped bolus from the amount shown. The stop once set it to 0, so the given
    // insulin was not recorded.
    @Test fun aStopKeepsTheAmountGiven() {
        bolusProgressData.start(insulin = 2.0, isSMB = false)
        bolusProgressData.updateProgress(PumpInsulin(0.7))
        danaPump.bolusStopForced = true

        MsgBolusStop(injector).handleMessage(ByteArray(100))

        Assertions.assertEquals(0.7, bolusProgressData.state.value?.delivered?.cU)
    }
}

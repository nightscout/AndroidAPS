package app.aaps.pump.danar.comm

import app.aaps.core.interfaces.pump.DetailedBolusInfo
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class MsgBolusProgressTest : DanaRTestBase() {

    @Test fun runTest() {
        danaPump.bolusingDetailedBolusInfo = DetailedBolusInfo().also { it.insulin = 3.0 }
        bolusProgressData.start(3.0, isSMB = false)
        val packet = MsgBolusProgress(injector)

        // test message decoding
        val array = ByteArray(100)
        putIntToArray(array, 0, 2 * 100)
        packet.handleMessage(array)
        Assertions.assertEquals(1.0, bolusProgressData.state.value?.delivered?.cU ?: 0.0, 0.0)
    }

    /** The pump goes on with a bolus the driver no longer follows, for example after a stop with no answer. */
    @Test fun progressOfABolusTheDriverDoesNotFollowIsOnlyLogged() {
        danaPump.bolusingDetailedBolusInfo = null
        val lastProgress = danaPump.bolusProgressLastTimeStamp
        val array = ByteArray(100)
        putIntToArray(array, 0, 2 * 100)

        MsgBolusProgress(injector).handleMessage(array)

        Assertions.assertEquals(lastProgress, danaPump.bolusProgressLastTimeStamp)
    }
}
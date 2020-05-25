package info.nightscout.androidaps.danars.comm

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
class DanaRS_Packet_Review_Get_Pump_Dec_RatioTest : DanaRSTestBase() {

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is DanaRS_Packet_Review_Get_Pump_Dec_Ratio) {
                it.aapsLogger = aapsLogger
                it.danaPump = danaPump
            }
        }
    }

    @Test fun runTest() {
        val packet = DanaRS_Packet_Review_Get_Pump_Dec_Ratio(packetInjector)

        val array = ByteArray(100)
        putByteToArray(array, 0, 4.toByte())
        packet.handleMessage(array)
        Assert.assertEquals(20, danaPump.decRatio)
        Assert.assertEquals("REVIEW__GET_PUMP_DEC_RATIO", packet.friendlyName)
    }
}
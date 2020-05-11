package info.nightscout.androidaps.danars.comm

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
@PrepareForTest()
class DanaRS_Packet_Basal_Get_Temporary_Basal_StateTest : DanaRSTestBase() {

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is DanaRS_Packet) {
                it.aapsLogger = aapsLogger
                it.dateUtil = dateUtil
            }
            if (it is DanaRS_Packet_Basal_Get_Temporary_Basal_State) {
                it.danaPump = danaPump
            }
        }
    }

    @Test fun runTest() {
        val packet = DanaRS_Packet_Basal_Get_Temporary_Basal_State(packetInjector)
        // test message decoding
        val array = ByteArray(100)
        putByteToArray(array, 0, 1.toByte())
        putByteToArray(array, 1, 1.toByte())
        putByteToArray(array, 2, 230.toByte())
        putByteToArray(array, 3, 150.toByte())
        putIntToArray(array, 4, 1)
        packet.handleMessage(array)
        Assert.assertTrue(packet.failed)
        Assert.assertTrue(danaPump.isTempBasalInProgress)
        Assert.assertEquals(300, danaPump.tempBasalPercent)
        Assert.assertEquals(15 * 60, danaPump.tempBasalTotalSec)
        Assert.assertEquals("BASAL__TEMPORARY_BASAL_STATE", packet.friendlyName)
    }
}
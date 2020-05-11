package info.nightscout.androidaps.danars.comm

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.powermock.modules.junit4.PowerMockRunner
import java.util.*

@RunWith(PowerMockRunner::class)
class DanaRS_Packet_General_Get_More_InformationTest : DanaRSTestBase() {

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is DanaRS_Packet) {
                it.aapsLogger = aapsLogger
                it.dateUtil = dateUtil
            }
            if (it is DanaRS_Packet_General_Get_More_Information) {
                it.danaPump = danaPump
            }
        }
    }

    @Test fun runTest() {
        var packet = DanaRS_Packet_General_Get_More_Information(packetInjector)

        packet.handleMessage(createArray(14, 0.toByte()))
        Assert.assertTrue(packet.failed)

        packet = DanaRS_Packet_General_Get_More_Information(packetInjector)
        val array = createArray(15, 0.toByte()) // 13 + 2
        putIntToArray(array, 0, 600) // iob 6
        putIntToArray(array, 2, 1250) // daily units 12.5
        putByteToArray(array, 4, 1) // is extended in progress
        putIntToArray(array, 5, 150) // extended remaining minutes 150
        putByteToArray(array, 9, 15) // hours 15
        putByteToArray(array, 10, 25) // minutes 25
        putIntToArray(array, 11, 170) // last bolus manout 1.70

        packet.handleMessage(array)
        Assert.assertFalse(packet.failed)
        Assert.assertEquals(6.0, danaPump.iob, 0.01)
        Assert.assertEquals(12.5, danaPump.dailyTotalUnits, 0.01)
        Assert.assertTrue(danaPump.isExtendedInProgress)
        Assert.assertEquals(150, danaPump.extendedBolusRemainingMinutes)
        val lastBolus = Date(danaPump.lastBolusTime)
        Assert.assertEquals(15, lastBolus.hours)
        Assert.assertEquals(25, lastBolus.minutes)
        Assert.assertEquals(1.7, danaPump.lastBolusAmount, 0.01)

        Assert.assertEquals("REVIEW__GET_MORE_INFORMATION", packet.friendlyName)
    }
}
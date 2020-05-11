package info.nightscout.androidaps.danars.comm

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner
import java.util.*

@RunWith(PowerMockRunner::class)
@PrepareForTest()
class DanaRS_Packet_Bolus_Get_Step_Bolus_InformationTest : DanaRSTestBase() {

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is DanaRS_Packet) {
                it.aapsLogger = aapsLogger
                it.dateUtil = dateUtil
            }
            if (it is DanaRS_Packet_Bolus_Get_Step_Bolus_Information) {
                it.danaPump = danaPump
            }
        }
    }

    @Test fun runTest() {
        val packet = DanaRS_Packet_Bolus_Get_Step_Bolus_Information(packetInjector)

        val array = createArray(13, 0.toByte()) // 11 + 2
        putByteToArray(array, 0, 2) // error 2
        putByteToArray(array, 1, 3) // bolus type 3
        putIntToArray(array, 2, 600) // initial bolus amount 6
        putByteToArray(array, 4, 13) // 13h
        putByteToArray(array, 5, 20) // 20min
        putIntToArray(array, 6, 1250) // last bolus amount 12.5
        putIntToArray(array, 8, 2500) // max bolus 25
        putByteToArray(array, 10, 100) // bolus step 1

        packet.handleMessage(array)
        Assert.assertTrue(packet.failed)
        Assert.assertEquals(6.0, danaPump.initialBolusAmount, 0.01)
        val lastBolus = Date(danaPump.lastBolusTime)
        Assert.assertEquals(13, lastBolus.hours)
        Assert.assertEquals(20, lastBolus.minutes)
        Assert.assertEquals(12.5, danaPump.lastBolusAmount, 0.01)
        Assert.assertEquals(25.0, danaPump.maxBolus, 0.01)
        Assert.assertEquals(1.0, danaPump.bolusStep, 0.01)
        Assert.assertEquals("BOLUS__GET_STEP_BOLUS_INFORMATION", packet.friendlyName)
    }
}
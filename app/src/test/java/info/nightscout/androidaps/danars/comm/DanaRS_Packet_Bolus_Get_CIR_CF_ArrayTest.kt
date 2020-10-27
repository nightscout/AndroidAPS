package info.nightscout.androidaps.danars.comm

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.dana.DanaPump
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
class DanaRS_Packet_Bolus_Get_CIR_CF_ArrayTest : DanaRSTestBase() {

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is DanaRS_Packet_Bolus_Get_CIR_CF_Array) {
                it.aapsLogger = aapsLogger
                it.danaPump = danaPump
            }
        }
    }

    @Test fun runTest() {
        val packet = DanaRS_Packet_Bolus_Get_CIR_CF_Array(packetInjector)
        // test params
        Assert.assertEquals(null, packet.requestParams)
        // test message decoding
        packet.handleMessage(createArray(34, 0.toByte()))
        // are pump units MG/DL ???
        Assert.assertEquals(DanaPump.UNITS_MGDL, danaPump.units)
        Assert.assertEquals(false, packet.failed)
        packet.handleMessage(createArray(34, 3.toByte()))
        Assert.assertEquals(true, packet.failed)
        Assert.assertEquals("BOLUS__GET_CIR_CF_ARRAY", packet.friendlyName)
    }
}
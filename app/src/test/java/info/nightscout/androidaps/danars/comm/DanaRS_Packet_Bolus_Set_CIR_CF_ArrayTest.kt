package info.nightscout.androidaps.danars.comm

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
class DanaRS_Packet_Bolus_Set_CIR_CF_ArrayTest : DanaRSTestBase() {

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is DanaRS_Packet_Bolus_Set_CIR_CF_Array) {
                it.aapsLogger = aapsLogger
            }
        }
    }

    @Test fun runTest() {
        val packet = DanaRS_Packet_Bolus_Set_CIR_CF_Array(packetInjector, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
        // test params
        val testparams = packet.requestParams
        Assert.assertEquals(0.toByte(), testparams[0])
        Assert.assertEquals(0.toByte(), testparams[18])
        // test message decoding
        packet.handleMessage(createArray(34, 0.toByte()))
        //        DanaRPump testPump = DanaRPump.getInstance();
        Assert.assertEquals(false, packet.failed)
        packet.handleMessage(createArray(34, 1.toByte()))
        //        int valueRequested = (((byte) 1 & 0x000000FF) << 8) + (((byte) 1) & 0x000000FF);
//        assertEquals(valueRequested /100d, testPump.lastBolusAmount, 0);
        Assert.assertEquals(true, packet.failed)
        Assert.assertEquals("BOLUS__SET_CIR_CF_ARRAY", packet.friendlyName)
    }
}
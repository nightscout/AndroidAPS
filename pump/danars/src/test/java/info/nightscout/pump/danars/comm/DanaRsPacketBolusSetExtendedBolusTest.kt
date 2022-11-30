package info.nightscout.pump.danars.comm

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.pump.danars.DanaRSTestBase
import org.junit.Assert
import org.junit.jupiter.api.Test

class DanaRsPacketBolusSetExtendedBolusTest : DanaRSTestBase() {

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is DanaRSPacketBolusSetExtendedBolus) {
                it.aapsLogger = aapsLogger
            }
        }
    }

    @Test fun runTest() {
        val packet = DanaRSPacketBolusSetExtendedBolus(packetInjector, 1.0, 1)
        // test params
        val testparams = packet.getRequestParams()
        Assert.assertEquals(100.toByte(), testparams[0])
        Assert.assertEquals(1.toByte(), testparams[2])
        // test message decoding
        packet.handleMessage(createArray(34, 0.toByte()))
        //        DanaRPump testPump = DanaRPump.getInstance();
        Assert.assertEquals(false, packet.failed)
        packet.handleMessage(createArray(34, 1.toByte()))
        //        int valueRequested = (((byte) 1 & 0x000000FF) << 8) + (((byte) 1) & 0x000000FF);
//        assertEquals(valueRequested /100d, testPump.lastBolusAmount, 0);
        Assert.assertEquals(true, packet.failed)
        Assert.assertEquals("BOLUS__SET_EXTENDED_BOLUS", packet.friendlyName)
    }
}
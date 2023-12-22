package app.aaps.pump.danars.comm

import app.aaps.pump.danars.DanaRSTestBase
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class DanaRsPacketBolusSetBolusOptionTest : DanaRSTestBase() {

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is DanaRSPacketBolusSetBolusOption) {
                it.aapsLogger = aapsLogger
            }
        }
    }

    @Test fun runTest() {
        val packet = DanaRSPacketBolusSetBolusOption(packetInjector, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
        // test params
        val testParams = packet.getRequestParams()
        Assertions.assertEquals(0.toByte(), testParams[0])
        Assertions.assertEquals(0.toByte(), testParams[18])
        // test message decoding
        packet.handleMessage(createArray(34, 0.toByte()))
        //        DanaRPump testPump = DanaRPump.getInstance();
        Assertions.assertEquals(false, packet.failed)
        packet.handleMessage(createArray(34, 1.toByte()))
        //        int valueRequested = (((byte) 1 & 0x000000FF) << 8) + (((byte) 1) & 0x000000FF);
//        assertEquals(valueRequested /100d, testPump.lastBolusAmount, 0);
        Assertions.assertEquals(true, packet.failed)
        Assertions.assertEquals("BOLUS__SET_BOLUS_OPTION", packet.friendlyName)
    }
}
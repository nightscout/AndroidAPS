package app.aaps.pump.danars.comm

import app.aaps.pump.danars.DanaRSTestBase
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class DanaRsPacketBolusSetExtendedBolusCancelTest : DanaRSTestBase() {

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is DanaRSPacketBolusSetExtendedBolusCancel) {
                it.aapsLogger = aapsLogger
            }
        }
    }

    @Test fun runTest() {
        val packet = DanaRSPacketBolusSetExtendedBolusCancel(packetInjector)
        Assertions.assertEquals(0, packet.getRequestParams().size)
        // test message decoding
        packet.handleMessage(createArray(34, 0.toByte()))
        Assertions.assertEquals(false, packet.failed)
        packet.handleMessage(createArray(34, 1.toByte()))
        Assertions.assertEquals(true, packet.failed)
        Assertions.assertEquals("BOLUS__SET_EXTENDED_BOLUS_CANCEL", packet.friendlyName)
    }
}
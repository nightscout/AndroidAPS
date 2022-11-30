package info.nightscout.pump.danars.comm

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.pump.danars.DanaRSTestBase
import org.junit.Assert
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
        Assert.assertEquals(0, packet.getRequestParams().size)
        // test message decoding
        packet.handleMessage(createArray(34, 0.toByte()))
        Assert.assertEquals(false, packet.failed)
        packet.handleMessage(createArray(34, 1.toByte()))
        Assert.assertEquals(true, packet.failed)
        Assert.assertEquals("BOLUS__SET_EXTENDED_BOLUS_CANCEL", packet.friendlyName)
    }
}
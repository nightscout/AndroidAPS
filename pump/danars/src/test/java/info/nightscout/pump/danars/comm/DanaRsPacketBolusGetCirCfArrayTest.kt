package info.nightscout.pump.danars.comm

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.pump.dana.DanaPump
import info.nightscout.pump.danars.DanaRSTestBase
import org.junit.Assert
import org.junit.jupiter.api.Test

class DanaRsPacketBolusGetCirCfArrayTest : DanaRSTestBase() {

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is DanaRSPacketBolusGetCIRCFArray) {
                it.aapsLogger = aapsLogger
                it.danaPump = danaPump
            }
        }
    }

    @Test fun runTest() {
        val packet = DanaRSPacketBolusGetCIRCFArray(packetInjector)
        // test params
        Assert.assertEquals(0, packet.getRequestParams().size)
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
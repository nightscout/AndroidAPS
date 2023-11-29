package app.aaps.pump.danars.comm

import app.aaps.pump.dana.DanaPump
import app.aaps.pump.danars.DanaRSTestBase
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import org.junit.jupiter.api.Assertions
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
        Assertions.assertEquals(0, packet.getRequestParams().size)
        // test message decoding
        packet.handleMessage(createArray(34, 0.toByte()))
        // are pump units MG/DL ???
        Assertions.assertEquals(DanaPump.UNITS_MGDL, danaPump.units)
        Assertions.assertEquals(false, packet.failed)
        packet.handleMessage(createArray(34, 3.toByte()))
        Assertions.assertEquals(true, packet.failed)
        Assertions.assertEquals("BOLUS__GET_CIR_CF_ARRAY", packet.friendlyName)
    }
}
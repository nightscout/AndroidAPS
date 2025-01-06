package app.aaps.pump.danars.comm

import app.aaps.pump.danars.DanaRSTestBase
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class DanaRsPacketOptionGetUserOptionTest : DanaRSTestBase() {

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is DanaRSPacketOptionGetUserOption) {
                it.aapsLogger = aapsLogger
                it.danaPump = danaPump
            }
        }
    }

    @Test fun runTest() {
        val packet = DanaRSPacketOptionGetUserOption(packetInjector)
        // test params
        Assertions.assertEquals(0, packet.getRequestParams().size)
        // test message decoding
        packet.handleMessage(createArray(20, 0.toByte()))
        Assertions.assertEquals(true, packet.failed)
        // everything ok :)
        packet.handleMessage(createArray(20, 5.toByte()))
        Assertions.assertEquals(5, danaPump.lcdOnTimeSec)
        Assertions.assertEquals(false, packet.failed)
        Assertions.assertEquals("OPTION__GET_USER_OPTION", packet.friendlyName)
    }
}
package info.nightscout.androidaps.danars.comm

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.danars.DanaRSTestBase
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
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
        Assert.assertEquals(0, packet.getRequestParams().size)
        // test message decoding
        packet.handleMessage(createArray(20, 0.toByte()))
        Assert.assertEquals(true, packet.failed)
        // everything ok :)
        packet.handleMessage(createArray(20, 5.toByte()))
        Assert.assertEquals(5, danaPump.lcdOnTimeSec)
        Assert.assertEquals(false, packet.failed)
        Assert.assertEquals("OPTION__GET_USER_OPTION", packet.friendlyName)
    }
}
package info.nightscout.androidaps.danars.comm

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
class DanaRS_Packet_General_Get_PasswordTest : DanaRSTestBase() {

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is DanaRS_Packet_General_Get_Password) {
                it.aapsLogger = aapsLogger
                it.danaPump = danaPump
            }
        }
    }

    @Test fun runTest() {
        var packet = DanaRS_Packet_General_Get_Password(packetInjector)
        Assert.assertEquals(null, packet.requestParams)
        // test message decoding
        // test for the length message
        packet.handleMessage(createArray(1, 0.toByte()))
        Assert.assertEquals(true, packet.failed)
        packet = DanaRS_Packet_General_Get_Password(packetInjector)
        packet.handleMessage(createArray(15, 0.toByte()))
        Assert.assertEquals(false, packet.failed)
        Assert.assertEquals("REVIEW__GET_PASSWORD", packet.friendlyName)
    }
}
package info.nightscout.androidaps.danars.comm

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
class DanaRS_Packet_General_Set_History_Upload_ModeTest : DanaRSTestBase() {

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is DanaRS_Packet_General_Set_History_Upload_Mode) {
                it.aapsLogger = aapsLogger
            }
        }
    }

    @Test fun runTest() {
        val packet = DanaRS_Packet_General_Set_History_Upload_Mode(packetInjector, 1)
        // test params
        Assert.assertEquals(1.toByte(), packet.requestParams[0])
        // test message decoding
        packet.handleMessage(createArray(3, 0.toByte()))
        Assert.assertEquals(false, packet.failed)
        // everything ok :)
        packet.handleMessage(createArray(17, 1.toByte()))
        Assert.assertEquals(true, packet.failed)
        Assert.assertEquals("REVIEW__SET_HISTORY_UPLOAD_MODE", packet.friendlyName)
    }
}
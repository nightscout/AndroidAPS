package info.nightscout.androidaps.danars.comm

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
class DanaRS_Packet_General_Get_User_Time_Change_FlagTest : DanaRSTestBase() {

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is DanaRS_Packet_General_Get_User_Time_Change_Flag) {
                it.aapsLogger = aapsLogger
            }
        }
    }

    @Test fun runTest() {
        var packet = DanaRS_Packet_General_Get_User_Time_Change_Flag(packetInjector)
        Assert.assertEquals(null, packet.requestParams)
        // test message decoding
        // test for the length message
        packet.handleMessage(createArray(1, 0.toByte()))
        Assert.assertEquals(true, packet.failed)
        packet = DanaRS_Packet_General_Get_User_Time_Change_Flag(packetInjector)
        packet.handleMessage(createArray(18, 0.toByte()))
        Assert.assertEquals(false, packet.failed)
        Assert.assertEquals("REVIEW__GET_USER_TIME_CHANGE_FLAG", packet.friendlyName)
    }
}
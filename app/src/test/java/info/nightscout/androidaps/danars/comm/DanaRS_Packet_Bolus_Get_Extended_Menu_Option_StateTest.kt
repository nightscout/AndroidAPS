package info.nightscout.androidaps.danars.comm

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
class DanaRS_Packet_Bolus_Get_Extended_Menu_Option_StateTest : DanaRSTestBase() {

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is DanaRS_Packet_Bolus_Get_Extended_Menu_Option_State) {
                it.aapsLogger = aapsLogger
                it.danaPump = danaPump
            }
        }
    }

    @Test fun runTest() {
        val packet = DanaRS_Packet_Bolus_Get_Extended_Menu_Option_State(packetInjector)
        Assert.assertEquals(null, packet.requestParams)
        // test message decoding
        packet.handleMessage(createArray(34, 0.toByte()))
        // isExtendedinprogres should be false
        Assert.assertEquals(false, danaPump.isExtendedInProgress)
        //        assertEquals(false, packet.failed);
        packet.handleMessage(createArray(34, 1.toByte()))
        Assert.assertEquals(true, danaPump.isExtendedInProgress)
        Assert.assertEquals("BOLUS__GET_EXTENDED_MENU_OPTION_STATE", packet.friendlyName)
    }
}
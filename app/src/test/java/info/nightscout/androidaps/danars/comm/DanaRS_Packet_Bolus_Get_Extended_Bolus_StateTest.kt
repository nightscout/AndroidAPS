package info.nightscout.androidaps.danars.comm

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
@PrepareForTest()
class DanaRS_Packet_Bolus_Get_Extended_Bolus_StateTest : DanaRSTestBase() {

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is DanaRS_Packet_Bolus_Get_Extended_Bolus_State) {
                it.aapsLogger = aapsLogger
                it.danaPump = danaPump
            }
        }
    }

    @Test fun runTest() {
        val packet = DanaRS_Packet_Bolus_Get_Extended_Bolus_State(packetInjector)
        // test params
        Assert.assertEquals(null, packet.requestParams)
        // test message decoding
        var testValue = 0.0
        packet.handleMessage(createArray(11, testValue.toByte()))
        Assert.assertEquals(testValue != 0.0, packet.failed)
        testValue = 1.0
        packet.handleMessage(createArray(11, testValue.toByte()))
        // is extended bolus in progress
        Assert.assertEquals(testValue == 1.0, danaPump.isExtendedInProgress)
        Assert.assertEquals(testValue != 0.0, packet.failed)
        Assert.assertEquals("BOLUS__GET_EXTENDED_BOLUS_STATE", packet.friendlyName)
    }
}
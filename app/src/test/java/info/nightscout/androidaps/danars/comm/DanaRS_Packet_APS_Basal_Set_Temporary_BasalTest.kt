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
class DanaRS_Packet_APS_Basal_Set_Temporary_BasalTest : DanaRSTestBase() {

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is DanaRS_Packet_APS_Basal_Set_Temporary_Basal) {
                it.aapsLogger = aapsLogger
            }
        }
    }

    @Test fun runTest() {

        // under 100% should last 30 min
        var packet = DanaRS_Packet_APS_Basal_Set_Temporary_Basal(packetInjector, 0)
        Assert.assertEquals(0, packet.temporaryBasalRatio)
        Assert.assertEquals(DanaRS_Packet_APS_Basal_Set_Temporary_Basal.PARAM30MIN, packet.temporaryBasalDuration)
        //constructor with param
        packet = DanaRS_Packet_APS_Basal_Set_Temporary_Basal(packetInjector, 10)
        Assert.assertEquals(10, packet.temporaryBasalRatio)
        Assert.assertEquals(DanaRS_Packet_APS_Basal_Set_Temporary_Basal.PARAM30MIN, packet.temporaryBasalDuration)
        // over 100% should last 15 min
        packet = DanaRS_Packet_APS_Basal_Set_Temporary_Basal(packetInjector, 150)
        Assert.assertEquals(150, packet.temporaryBasalRatio)
        Assert.assertEquals(DanaRS_Packet_APS_Basal_Set_Temporary_Basal.PARAM15MIN, packet.temporaryBasalDuration)
        // test low hard limit
        packet = DanaRS_Packet_APS_Basal_Set_Temporary_Basal(packetInjector, -1)
        Assert.assertEquals(0, packet.temporaryBasalRatio)
        Assert.assertEquals(DanaRS_Packet_APS_Basal_Set_Temporary_Basal.PARAM30MIN, packet.temporaryBasalDuration)
        // test high hard limit
        packet = DanaRS_Packet_APS_Basal_Set_Temporary_Basal(packetInjector, 550)
        Assert.assertEquals(500, packet.temporaryBasalRatio)
        Assert.assertEquals(DanaRS_Packet_APS_Basal_Set_Temporary_Basal.PARAM15MIN, packet.temporaryBasalDuration)
        // test message generation
        packet = DanaRS_Packet_APS_Basal_Set_Temporary_Basal(packetInjector, 260)
        val generatedCode = packet.requestParams
        Assert.assertEquals(3, generatedCode.size.toLong())
        Assert.assertEquals(4.toByte(), generatedCode[0])
        Assert.assertEquals(1.toByte(), generatedCode[1])
        Assert.assertEquals(DanaRS_Packet_APS_Basal_Set_Temporary_Basal.PARAM15MIN.toUByte(), generatedCode[2].toUByte())
        // test message decoding
        packet.handleMessage(byteArrayOf(0.toByte(), 0.toByte(), 0.toByte()))
        Assert.assertEquals(false, packet.failed)
        packet.handleMessage(byteArrayOf(0.toByte(), 0.toByte(), 1.toByte()))
        Assert.assertEquals(true, packet.failed)
        Assert.assertEquals("BASAL__APS_SET_TEMPORARY_BASAL", packet.friendlyName)
    }
}
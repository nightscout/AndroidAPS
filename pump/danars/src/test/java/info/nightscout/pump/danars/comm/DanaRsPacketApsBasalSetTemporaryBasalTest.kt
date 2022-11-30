package info.nightscout.pump.danars.comm

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.pump.danars.DanaRSTestBase
import org.junit.Assert
import org.junit.jupiter.api.Test

class DanaRsPacketApsBasalSetTemporaryBasalTest : DanaRSTestBase() {

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is DanaRSPacketAPSBasalSetTemporaryBasal) {
                it.aapsLogger = aapsLogger
            }
        }
    }

    @ExperimentalUnsignedTypes
    @Test fun runTest() {

        // under 100% should last 30 min
        var packet = DanaRSPacketAPSBasalSetTemporaryBasal(packetInjector, 0)
        Assert.assertEquals(0, packet.temporaryBasalRatio)
        Assert.assertEquals(DanaRSPacketAPSBasalSetTemporaryBasal.PARAM30MIN, packet.temporaryBasalDuration)
        //constructor with param
        packet = DanaRSPacketAPSBasalSetTemporaryBasal(packetInjector, 10)
        Assert.assertEquals(10, packet.temporaryBasalRatio)
        Assert.assertEquals(DanaRSPacketAPSBasalSetTemporaryBasal.PARAM30MIN, packet.temporaryBasalDuration)
        // over 100% should last 15 min
        packet = DanaRSPacketAPSBasalSetTemporaryBasal(packetInjector, 150)
        Assert.assertEquals(150, packet.temporaryBasalRatio)
        Assert.assertEquals(DanaRSPacketAPSBasalSetTemporaryBasal.PARAM15MIN, packet.temporaryBasalDuration)
        // test low hard limit
        packet = DanaRSPacketAPSBasalSetTemporaryBasal(packetInjector, -1)
        Assert.assertEquals(0, packet.temporaryBasalRatio)
        Assert.assertEquals(DanaRSPacketAPSBasalSetTemporaryBasal.PARAM30MIN, packet.temporaryBasalDuration)
        // test high hard limit
        packet = DanaRSPacketAPSBasalSetTemporaryBasal(packetInjector, 550)
        Assert.assertEquals(500, packet.temporaryBasalRatio)
        Assert.assertEquals(DanaRSPacketAPSBasalSetTemporaryBasal.PARAM15MIN, packet.temporaryBasalDuration)
        // test message generation
        packet = DanaRSPacketAPSBasalSetTemporaryBasal(packetInjector, 260)
        val generatedCode = packet.getRequestParams()
        Assert.assertEquals(3, generatedCode.size.toLong())
        Assert.assertEquals(4.toByte(), generatedCode[0])
        Assert.assertEquals(1.toByte(), generatedCode[1])
        Assert.assertEquals(DanaRSPacketAPSBasalSetTemporaryBasal.PARAM15MIN.toUByte(), generatedCode[2].toUByte())
        // test message decoding
        packet.handleMessage(byteArrayOf(0.toByte(), 0.toByte(), 0.toByte()))
        Assert.assertEquals(false, packet.failed)
        packet.handleMessage(byteArrayOf(0.toByte(), 0.toByte(), 1.toByte()))
        Assert.assertEquals(true, packet.failed)
        Assert.assertEquals("BASAL__APS_SET_TEMPORARY_BASAL", packet.friendlyName)
    }
}
package app.aaps.pump.danars.comm

import app.aaps.pump.danars.DanaRSTestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class DanaRsPacketBolusSetStepBolusStartTest : DanaRSTestBase() {

    @Test fun runTest() {
        val packet = DanaRSPacketBolusSetStepBolusStart(aapsLogger, danaPump)
        // test params
        val testParams = packet.getRequestParams()
        Assertions.assertEquals(0.toByte(), testParams[0])
        Assertions.assertEquals(0.toByte(), testParams[2])
        // test message decoding
        packet.handleMessage(byteArrayOf(0.toByte(), 0.toByte(), 0.toByte()))
        Assertions.assertEquals(false, packet.failed)
        packet.handleMessage(byteArrayOf(1.toByte(), 1.toByte(), 1.toByte(), 1.toByte(), 1.toByte(), 1.toByte(), 1.toByte(), 1.toByte()))
        Assertions.assertEquals(true, packet.failed)
        Assertions.assertEquals("BOLUS__SET_STEP_BOLUS_START", packet.friendlyName)
    }

    @Test fun amountIsRoundedNotCutOff() {
        // 2.55 * 100 is 254.99999 in floating point, 0.29 * 100 is 28.999999
        val big = DanaRSPacketBolusSetStepBolusStart(aapsLogger, danaPump).with(2.55, 0, algorithm = false).getRequestParams()
        val small = DanaRSPacketBolusSetStepBolusStart(aapsLogger, danaPump).with(0.29, 0, algorithm = false).getRequestParams()

        Assertions.assertArrayEquals(byteArrayOf(0xFF.toByte(), 0x00, 0x00), big)
        Assertions.assertArrayEquals(byteArrayOf(29, 0x00, 0x00), small)
    }

    @Test fun olderPumpGetsThreeBytes() {
        danaPump.hwModel = 0x09 // Dana-i BLE5
        val params = DanaRSPacketBolusSetStepBolusStart(aapsLogger, danaPump).with(0.5, 0, algorithm = true).getRequestParams()

        assertThat(params).isEqualTo(byteArrayOf(0x32, 0x00, 0x00))
    }

    @Test fun danaI2GetsTheCommandType() {
        danaPump.hwModel = 0x0B
        val normal = DanaRSPacketBolusSetStepBolusStart(aapsLogger, danaPump).with(0.5, 1, algorithm = false).getRequestParams()
        val smb = DanaRSPacketBolusSetStepBolusStart(aapsLogger, danaPump).with(2.55, 2, algorithm = true).getRequestParams()

        assertThat(normal).isEqualTo(byteArrayOf(0x32, 0x00, 0x01, 0x00))
        assertThat(smb).isEqualTo(byteArrayOf(0xFF.toByte(), 0x00, 0x02, 0x01))
    }

    @Test fun danaI2ReadsTwoByteStatus() {
        danaPump.hwModel = 0x0B
        val packet = DanaRSPacketBolusSetStepBolusStart(aapsLogger, danaPump)

        packet.handleMessage(byteArrayOf(0xB2.toByte(), 0x4A, 0x00, 0x00))
        assertThat(packet.failed).isFalse()

        // Bolus Max is 0x0100: the low byte is 0, so a 1 byte read would take it as OK
        packet.handleMessage(byteArrayOf(0xB2.toByte(), 0x4A, 0x00, 0x01))
        assertThat(packet.failed).isTrue()
        assertThat(danaPump.bolusStartErrorCode).isEqualTo(0x0100)
    }
}

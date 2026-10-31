package app.aaps.pump.danars.comm

import app.aaps.pump.danars.DanaRSTestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class DanaRsPacketOptionGetUserOptionTest : DanaRSTestBase() {

    @Test
    fun runTest() {
        val packet = DanaRSPacketOptionGetUserOption(aapsLogger, danaPump)
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

    /** Real reply of a Dana-i2 (CFL00001TJ), AutoLock is the last byte */
    private fun danaI2Reply(autoLock: Int) = byteArrayOf(
        0xB2.toByte(), 0x72, 0x01, 0x01, 0x05, 0x3C, 0x0A, 0x02, 0x00, 0x00, 0x14, 0x28, 0x00, 0xF0.toByte(), 0x00,
        0x02, 0x02, 0x02, 0x02, 0x02, 0x64, 0x00, autoLock.toByte()
    )

    @Test
    fun danaI2ReplyReadsAutoLock() {
        val packet = DanaRSPacketOptionGetUserOption(aapsLogger, danaPump)

        packet.handleMessage(danaI2Reply(autoLock = 0))
        assertThat(danaPump.autoLock).isFalse()
        assertThat(danaPump.target).isEqualTo(100)
        assertThat(packet.failed).isFalse()

        packet.handleMessage(danaI2Reply(autoLock = 1))
        assertThat(danaPump.autoLock).isTrue()
    }

    @Test
    fun danaIReplyWithoutAutoLockKeepsItOff() {
        // Dana-i reply ends with the target (22 bytes)
        DanaRSPacketOptionGetUserOption(aapsLogger, danaPump).handleMessage(danaI2Reply(autoLock = 1).copyOf(22))

        assertThat(danaPump.autoLock).isFalse()
        assertThat(danaPump.target).isEqualTo(100)
    }
}
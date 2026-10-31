package app.aaps.pump.danars.comm

import app.aaps.pump.danars.DanaRSTestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class DanaRSPacketOptionSetUserOptionTest : DanaRSTestBase() {

    @Test
    fun runTest() {
        val packet = DanaRSPacketOptionSetUserOption(aapsLogger, danaPump)
        // test params
        val params = packet.getRequestParams()
        Assertions.assertEquals((danaPump.lcdOnTimeSec and 0xff).toByte(), params[3])
        // test message decoding
        packet.handleMessage(createArray(3, 0.toByte()))
        Assertions.assertEquals(false, packet.failed)
        // everything ok :)
        packet.handleMessage(createArray(17, 1.toByte()))
        Assertions.assertEquals(true, packet.failed)
        Assertions.assertEquals("OPTION__SET_USER_OPTION", packet.friendlyName)
    }

    @Test
    fun requestSizeFollowsTheModel() {
        danaPump.hwModel = 0x05 // Dana RS
        assertThat(DanaRSPacketOptionSetUserOption(aapsLogger, danaPump).getRequestParams()).hasLength(13)
        danaPump.hwModel = 0x09 // Dana-i, + target
        assertThat(DanaRSPacketOptionSetUserOption(aapsLogger, danaPump).getRequestParams()).hasLength(15)
        danaPump.hwModel = 0x0B // Dana-i2, + AutoLock
        assertThat(DanaRSPacketOptionSetUserOption(aapsLogger, danaPump).getRequestParams()).hasLength(16)
    }

    @Test
    fun danaI2SendsAutoLockAsLastByte() {
        danaPump.hwModel = 0x0B
        danaPump.target = 100

        danaPump.autoLock = true
        val on = DanaRSPacketOptionSetUserOption(aapsLogger, danaPump).getRequestParams()
        danaPump.autoLock = false
        val off = DanaRSPacketOptionSetUserOption(aapsLogger, danaPump).getRequestParams()

        assertThat(on[13]).isEqualTo(100.toByte()) // target is still at its place
        assertThat(on[15]).isEqualTo(1.toByte())
        assertThat(off[15]).isEqualTo(0.toByte())
    }
}
package app.aaps.pump.danars.comm

import app.aaps.pump.dana.DanaPump
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

    /**
     * The options the screen saved go out, not those a settings read stored since: the read put the pump's old values
     * where the screen had put the new ones, and the old values were written and shown as saved.
     */
    @Test
    fun theSavedOptionsAreSentNotThoseOfALaterRead() {
        danaPump.hwModel = 0x0B
        val saved = DanaPump.UserOptions(
            timeDisplayType24 = true, buttonScrollOnOff = true, beepAndAlarm = 6, lcdOnTimeSec = 30, backlightOnTimeSec = 20,
            units = 1, shutdownHour = 5, lowReservoirRate = 30, autoLock = true
        )
        danaPump.userOptionsToWrite = saved
        // A settings read after the save, with the pump's old values.
        danaPump.beepAndAlarm = 1
        danaPump.lcdOnTimeSec = 5
        danaPump.lowReservoirRate = 10
        danaPump.autoLock = false

        val packet = DanaRSPacketOptionSetUserOption(aapsLogger, danaPump)
        val params = packet.getRequestParams()

        assertThat(params[2]).isEqualTo(6.toByte())
        assertThat(params[3]).isEqualTo(30.toByte())
        assertThat(params[8]).isEqualTo(30.toByte())
        assertThat(params[15]).isEqualTo(1.toByte())
        assertThat(packet.written).isEqualTo(saved)

        // Taken by the pump: the saved options are the pump's now, and nothing is left to write.
        danaPump.userOptionsWritten(saved)
        assertThat(danaPump.beepAndAlarm).isEqualTo(6)
        assertThat(danaPump.userOptionsToWrite).isNull()
    }
}
package app.aaps.pump.diaconn.packet

import app.aaps.pump.diaconn.DiaconnG8Pump
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DisplayTimeInquireResponsePacketTest : TestBaseWithProfile() {

    private lateinit var diaconnG8Pump: DiaconnG8Pump

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is DisplayTimeInquireResponsePacket) {
                it.aapsLogger = aapsLogger
                it.dateUtil = dateUtil
                it.diaconnG8Pump = diaconnG8Pump
            }
        }
    }

    @BeforeEach
    fun setup() {
        diaconnG8Pump = DiaconnG8Pump(aapsLogger, dateUtil, decimalFormatter)
    }

    @Test
    fun handleMessageShouldParseDisplayTime() {
        // Given - 30 second display timeout
        val packet = DisplayTimeInquireResponsePacket(packetInjector)
        val data = createValidPacket(lcdOnTimeSec = 30)

        // When
        packet.handleMessage(data)

        // Then
        assertThat(packet.failed).isFalse()
        assertThat(diaconnG8Pump.lcdOnTimeSec).isEqualTo(30)
    }

    @Test
    fun handleMessageShouldHandleMinimumDisplayTime() {
        // Given - 10 second display timeout (minimum)
        val packet = DisplayTimeInquireResponsePacket(packetInjector)
        val data = createValidPacket(lcdOnTimeSec = 10)

        // When
        packet.handleMessage(data)

        // Then
        assertThat(diaconnG8Pump.lcdOnTimeSec).isEqualTo(10)
    }

    @Test
    fun handleMessageShouldHandleMaximumDisplayTime() {
        // Given - 120 second display timeout (maximum)
        val packet = DisplayTimeInquireResponsePacket(packetInjector)
        val data = createValidPacket(lcdOnTimeSec = 120)

        // When
        packet.handleMessage(data)

        // Then
        assertThat(diaconnG8Pump.lcdOnTimeSec).isEqualTo(120)
    }

    @Test
    fun handleMessageShouldHandleDifferentDisplayTimes() {
        // Given - Test different display times
        val packet = DisplayTimeInquireResponsePacket(packetInjector)

        for (seconds in listOf(10, 15, 30, 60, 90, 120)) {
            val data = createValidPacket(lcdOnTimeSec = seconds)
            packet.handleMessage(data)
            assertThat(diaconnG8Pump.lcdOnTimeSec).isEqualTo(seconds)
        }
    }

    @Test
    fun handleMessageShouldFailOnInvalidResult() {
        // Given
        val packet = DisplayTimeInquireResponsePacket(packetInjector)
        val data = createPacketWithResult(17) // CRC error

        // When
        packet.handleMessage(data)

        // Then
        assertThat(packet.failed).isTrue()
    }

    @Test
    fun handleMessageShouldFailOnDefectivePacket() {
        // Given
        val packet = DisplayTimeInquireResponsePacket(packetInjector)
        val data = ByteArray(20)
        data[0] = 0x00 // Wrong SOP

        // When
        packet.handleMessage(data)

        // Then
        assertThat(packet.failed).isTrue()
    }

    @Test
    fun msgTypeShouldBeCorrect() {
        // Given
        val packet = DisplayTimeInquireResponsePacket(packetInjector)

        // Then
        assertThat(packet.msgType).isEqualTo(0x8E.toByte())
    }

    @Test
    fun friendlyNameShouldBeCorrect() {
        // Given
        val packet = DisplayTimeInquireResponsePacket(packetInjector)

        // Then
        assertThat(packet.friendlyName).isEqualTo("PUMP_DISPLAY_TIME_INQUIRE_RESPONSE")
    }

    private fun createValidPacket(lcdOnTimeSec: Int): ByteArray {
        val data = ByteArray(20)
        data[0] = 0xef.toByte() // SOP
        data[1] = 0x8E.toByte() // msgType
        data[2] = 0x01.toByte() // seq
        data[3] = 0x00.toByte() // con_end
        data[4] = 16.toByte()   // result (success)
        data[5] = lcdOnTimeSec.toByte()

        for (i in 6 until 19) {
            data[i] = 0xff.toByte()
        }

        data[19] = DiaconnG8Packet.getCRC(data, 19)
        return data
    }

    private fun createPacketWithResult(result: Int): ByteArray {
        val data = ByteArray(20)
        data[0] = 0xef.toByte()
        data[1] = 0x8E.toByte()
        data[2] = 0x01.toByte()
        data[3] = 0x00.toByte()
        data[4] = result.toByte()

        for (i in 5 until 19) {
            data[i] = 0xff.toByte()
        }

        data[19] = DiaconnG8Packet.getCRC(data, 19)
        return data
    }
}

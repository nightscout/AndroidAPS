package app.aaps.pump.diaconn.packet

import app.aaps.pump.diaconn.DiaconnG8Pump
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TimeInquireResponsePacketTest : TestBaseWithProfile() {

    private lateinit var diaconnG8Pump: DiaconnG8Pump

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is TimeInquireResponsePacket) {
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
    fun handleMessageShouldParsePumpTime() {
        // Given
        val packet = TimeInquireResponsePacket(packetInjector)
        val data = createValidPacket(
            year = 24,    // 2024
            month = 3,
            day = 15,
            hour = 14,
            minute = 30,
            second = 45
        )

        // When
        packet.handleMessage(data)

        // Then
        assertThat(packet.failed).isFalse()
        assertThat(diaconnG8Pump.year).isEqualTo(2024)
        assertThat(diaconnG8Pump.month).isEqualTo(3)
        assertThat(diaconnG8Pump.day).isEqualTo(15)
        assertThat(diaconnG8Pump.hour).isEqualTo(14)
        assertThat(diaconnG8Pump.minute).isEqualTo(30)
        assertThat(diaconnG8Pump.second).isEqualTo(45)
    }

    @Test
    fun handleMessageShouldHandleEdgeTimeValues() {
        // Given
        val packet = TimeInquireResponsePacket(packetInjector)
        val data = createValidPacket(
            year = 99,    // 2099
            month = 12,
            day = 31,
            hour = 23,
            minute = 59,
            second = 59
        )

        // When
        packet.handleMessage(data)

        // Then
        assertThat(diaconnG8Pump.year).isEqualTo(2099)
        assertThat(diaconnG8Pump.month).isEqualTo(12)
        assertThat(diaconnG8Pump.day).isEqualTo(31)
        assertThat(diaconnG8Pump.hour).isEqualTo(23)
        assertThat(diaconnG8Pump.minute).isEqualTo(59)
        assertThat(diaconnG8Pump.second).isEqualTo(59)
    }

    @Test
    fun handleMessageShouldHandleMidnight() {
        // Given
        val packet = TimeInquireResponsePacket(packetInjector)
        val data = createValidPacket(
            year = 23,
            month = 1,
            day = 1,
            hour = 0,
            minute = 0,
            second = 0
        )

        // When
        packet.handleMessage(data)

        // Then
        assertThat(diaconnG8Pump.year).isEqualTo(2023)
        assertThat(diaconnG8Pump.hour).isEqualTo(0)
        assertThat(diaconnG8Pump.minute).isEqualTo(0)
        assertThat(diaconnG8Pump.second).isEqualTo(0)
    }

    @Test
    fun handleMessageShouldFailOnInvalidResult() {
        // Given
        val packet = TimeInquireResponsePacket(packetInjector)
        val data = createPacketWithResult(17) // CRC error

        // When
        packet.handleMessage(data)

        // Then
        assertThat(packet.failed).isTrue()
    }

    @Test
    fun msgTypeShouldBeCorrect() {
        // Given
        val packet = TimeInquireResponsePacket(packetInjector)

        // Then
        assertThat(packet.msgType).isEqualTo(0x8F.toByte())
    }

    @Test
    fun friendlyNameShouldBeCorrect() {
        // Given
        val packet = TimeInquireResponsePacket(packetInjector)

        // Then
        assertThat(packet.friendlyName).isEqualTo("PUMP_TIME_INQUIRE_RESPONSE")
    }

    private fun createValidPacket(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
        second: Int
    ): ByteArray {
        val data = ByteArray(20)
        data[0] = 0xef.toByte() // SOP
        data[1] = 0x8F.toByte() // msgType
        data[2] = 0x01.toByte() // seq
        data[3] = 0x00.toByte() // con_end
        data[4] = 16.toByte()   // result (success)
        data[5] = year.toByte()
        data[6] = month.toByte()
        data[7] = day.toByte()
        data[8] = hour.toByte()
        data[9] = minute.toByte()
        data[10] = second.toByte()

        // Fill rest with padding
        for (i in 11 until 19) {
            data[i] = 0xff.toByte()
        }

        data[19] = DiaconnG8Packet.getCRC(data, 19)
        return data
    }

    private fun createPacketWithResult(result: Int): ByteArray {
        val data = ByteArray(20)
        data[0] = 0xef.toByte()
        data[1] = 0x8F.toByte()
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

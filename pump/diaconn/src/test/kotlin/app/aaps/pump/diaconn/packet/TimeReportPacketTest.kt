package app.aaps.pump.diaconn.packet

import app.aaps.pump.diaconn.DiaconnG8Pump
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TimeReportPacketTest : TestBaseWithProfile() {

    private lateinit var diaconnG8Pump: DiaconnG8Pump

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is TimeReportPacket) {
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
    fun handleMessageShouldParseTimeFields() {
        // Given
        val packet = TimeReportPacket(packetInjector)
        val data = createValidPacket(
            year = 25,
            month = 6,
            day = 15,
            hour = 14,
            minute = 30,
            second = 45
        )

        // When
        packet.handleMessage(data)

        // Then
        assertThat(packet.failed).isFalse()
        assertThat(diaconnG8Pump.year).isEqualTo(25)
        assertThat(diaconnG8Pump.month).isEqualTo(6)
        assertThat(diaconnG8Pump.day).isEqualTo(15)
        assertThat(diaconnG8Pump.hour).isEqualTo(14)
        assertThat(diaconnG8Pump.minute).isEqualTo(30)
        assertThat(diaconnG8Pump.second).isEqualTo(45)
    }

    @Test
    fun handleMessageShouldHandleMinimumValues() {
        // Given
        val packet = TimeReportPacket(packetInjector)
        val data = createValidPacket(
            year = 0,
            month = 1,
            day = 1,
            hour = 0,
            minute = 0,
            second = 0
        )

        // When
        packet.handleMessage(data)

        // Then
        assertThat(diaconnG8Pump.year).isEqualTo(0)
        assertThat(diaconnG8Pump.month).isEqualTo(1)
        assertThat(diaconnG8Pump.day).isEqualTo(1)
        assertThat(diaconnG8Pump.hour).isEqualTo(0)
        assertThat(diaconnG8Pump.minute).isEqualTo(0)
        assertThat(diaconnG8Pump.second).isEqualTo(0)
    }

    @Test
    fun handleMessageShouldHandleMaximumValues() {
        // Given
        val packet = TimeReportPacket(packetInjector)
        val data = createValidPacket(
            year = 99,
            month = 12,
            day = 31,
            hour = 23,
            minute = 59,
            second = 59
        )

        // When
        packet.handleMessage(data)

        // Then
        assertThat(diaconnG8Pump.year).isEqualTo(99)
        assertThat(diaconnG8Pump.month).isEqualTo(12)
        assertThat(diaconnG8Pump.day).isEqualTo(31)
        assertThat(diaconnG8Pump.hour).isEqualTo(23)
        assertThat(diaconnG8Pump.minute).isEqualTo(59)
        assertThat(diaconnG8Pump.second).isEqualTo(59)
    }

    @Test
    fun handleMessageShouldFailOnDefectivePacket() {
        // Given
        val packet = TimeReportPacket(packetInjector)
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
        val packet = TimeReportPacket(packetInjector)

        // Then
        assertThat(packet.msgType).isEqualTo(0xCF.toByte())
    }

    @Test
    fun friendlyNameShouldBeCorrect() {
        // Given
        val packet = TimeReportPacket(packetInjector)

        // Then
        assertThat(packet.friendlyName).isEqualTo("PUMP_TIME_REPORT")
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
        data[1] = 0xCF.toByte() // msgType
        data[2] = 0x01.toByte() // seq
        data[3] = 0x00.toByte() // con_end
        data[4] = year.toByte()
        data[5] = month.toByte()
        data[6] = day.toByte()
        data[7] = hour.toByte()
        data[8] = minute.toByte()
        data[9] = second.toByte()

        for (i in 10 until 19) {
            data[i] = 0xff.toByte()
        }

        data[19] = DiaconnG8Packet.getCRC(data, 19)
        return data
    }
}

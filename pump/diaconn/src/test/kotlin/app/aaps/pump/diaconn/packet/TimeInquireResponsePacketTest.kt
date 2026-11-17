package app.aaps.pump.diaconn.packet

import app.aaps.core.interfaces.di.MetroMemberInjector
import app.aaps.pump.diaconn.DiaconnG8Pump
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import org.joda.time.DateTime
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever

class TimeInquireResponsePacketTest : TestBaseWithProfile() {

    private lateinit var diaconnG8Pump: DiaconnG8Pump

    private val packetInjector = MetroMemberInjector {
        if (it is TimeInquireResponsePacket) {
                it.aapsLogger = aapsLogger
                it.dateUtil = dateUtil
                it.diaconnG8Pump = diaconnG8Pump
        }
        true
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

    // A real G8 (fw 2.50) after its clock was set on the pump: 2026-11-11 18:20:38. It answered 47 s before the phone.
    @Test
    fun theClockOfARealPumpIsReadWithItsOffset() {
        whenever(dateUtil.now()).thenReturn(DateTime(2026, 11, 11, 18, 21, 25).millis)

        TimeInquireResponsePacket(packetInjector).handleMessage(hex("ef 8f 27 00 10 1a 0b 0b 12 14 26 ff ff ff ff ff ff ff ff 92"))

        assertThat(diaconnG8Pump.pumpClockImpossible).isFalse()
        assertThat(diaconnG8Pump.getPumpTime()).isEqualTo(DateTime(2026, 11, 11, 18, 20, 38).millis)
        assertThat(diaconnG8Pump.pumpClockOffsetSeconds()).isEqualTo(-47L)
    }

    // The same pump before, its clock never set: 2025-03-19 00:65:41. Its own screen showed 00:70 a little later.
    @Test
    fun aClockThatIsNoRealTimeIsKnownAsSuch() {
        val packet = TimeInquireResponsePacket(packetInjector)

        packet.handleMessage(hex("ef 8f 17 00 10 19 03 13 00 41 29 ff ff ff ff ff ff ff ff ed"))

        assertThat(packet.failed).isFalse()
        assertThat(diaconnG8Pump.pumpClockImpossible).isTrue()
        assertThat(diaconnG8Pump.getPumpTime()).isEqualTo(0L)
        assertThat(diaconnG8Pump.pumpClockOffsetSeconds()).isNull()
    }

    private fun hex(bytes: String): ByteArray = bytes.split(" ").map { it.toInt(16).toByte() }.toByteArray()

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

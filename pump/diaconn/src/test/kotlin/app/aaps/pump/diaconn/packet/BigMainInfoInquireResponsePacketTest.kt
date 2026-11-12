package app.aaps.pump.diaconn.packet

import app.aaps.core.interfaces.di.MetroMemberInjector
import app.aaps.pump.diaconn.DiaconnG8Pump
import app.aaps.pump.diaconn.keys.DiaconnStringNonKey
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import org.joda.time.DateTime
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`

class BigMainInfoInquireResponsePacketTest : TestBaseWithProfile() {

    private lateinit var diaconnG8Pump: DiaconnG8Pump

    private val packetInjector = MetroMemberInjector {
        if (it is BigMainInfoInquireResponsePacket) {
                it.aapsLogger = aapsLogger
                it.dateUtil = dateUtil
                it.diaconnG8Pump = diaconnG8Pump
                it.preferences = preferences
                it.rh = rh
        }
        true
    }

    @BeforeEach
    fun setup() {
        diaconnG8Pump = DiaconnG8Pump(aapsLogger, dateUtil, decimalFormatter)
        // Mock preferences to return a default version string (will be updated by the packet handler)
        `when`(preferences.get(DiaconnStringNonKey.PumpVersion)).thenReturn("1.0")
    }

    @Test
    fun handleMessageShouldParseBigMainInfo() {
        // Given - This is a BIG packet (182 bytes)
        val packet = BigMainInfoInquireResponsePacket(packetInjector)
        val data = createValidBigPacket()

        // When
        packet.handleMessage(data)

        // Then
        assertThat(packet.failed).isFalse()

        // Verify system info
        assertThat(diaconnG8Pump.systemRemainInsulin).isAtLeast(0.0)
        assertThat(diaconnG8Pump.systemRemainBattery).isIn(0..100)

        // Verify pump time
        assertThat(diaconnG8Pump.year).isAtLeast(2000)
        assertThat(diaconnG8Pump.month).isIn(1..12)
        assertThat(diaconnG8Pump.day).isIn(1..31)
        assertThat(diaconnG8Pump.hour).isIn(0..23)
        assertThat(diaconnG8Pump.minute).isIn(0..59)
        assertThat(diaconnG8Pump.second).isIn(0..59)

        // Verify serial number info
        assertThat(diaconnG8Pump.majorVersion).isAtLeast(0)
        assertThat(diaconnG8Pump.minorVersion).isAtLeast(0)
    }

    @Test
    fun handleMessageShouldParseSpecificValues() {
        // Given
        val packet = BigMainInfoInquireResponsePacket(packetInjector)
        val data = createBigPacketWithSpecificValues(
            remainInsulin = 15000,  // 150.0 U
            remainBattery = 75,
            year = 24,  // 2024
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
        assertThat(diaconnG8Pump.systemRemainInsulin).isWithin(1.0).of(150.0)
        assertThat(diaconnG8Pump.systemRemainBattery).isEqualTo(75)
        assertThat(diaconnG8Pump.year).isEqualTo(2024)
        assertThat(diaconnG8Pump.month).isEqualTo(3)
        assertThat(diaconnG8Pump.day).isEqualTo(15)
        assertThat(diaconnG8Pump.hour).isEqualTo(14)
        assertThat(diaconnG8Pump.minute).isEqualTo(30)
        assertThat(diaconnG8Pump.second).isEqualTo(45)
    }

    @Test
    fun handleMessageShouldFailOnInvalidResult() {
        // Given
        val packet = BigMainInfoInquireResponsePacket(packetInjector)
        val data = createBigPacketWithResult(17) // CRC error

        // When
        packet.handleMessage(data)

        // Then
        assertThat(packet.failed).isTrue()
    }

    @Test
    fun handleMessageShouldFailOnDefectivePacket() {
        // Given
        val packet = BigMainInfoInquireResponsePacket(packetInjector)
        val data = ByteArray(182)
        data[0] = 0x00 // Wrong SOP

        // When
        packet.handleMessage(data)

        // Then
        assertThat(packet.failed).isTrue()
    }

    @Test
    fun msgTypeShouldBeCorrect() {
        // Given
        val packet = BigMainInfoInquireResponsePacket(packetInjector)

        // Then
        assertThat(packet.msgType).isEqualTo(0xb3.toByte())
    }

    /**
     * The answer of a real G8 (DIACONN-64004, fw 2.50) whose clock was never set: 2025-03-19 00:65:21. Building a time from
     * minute 65 threw, on the Bluetooth thread, so the reservoir, the battery and the basal after it were lost too, and
     * with no pump time the driver never became ready.
     */
    @Test
    fun anAnswerWithAClockThatIsNoRealTimeIsReadInFull() {
        val packet = BigMainInfoInquireResponsePacket(packetInjector)

        packet.handleMessage(hex(REAL_ANSWER_CLOCK_NEVER_SET))

        assertThat(packet.failed).isFalse()
        assertThat(diaconnG8Pump.pumpClockImpossible).isTrue()
        assertThat(diaconnG8Pump.getPumpTime()).isEqualTo(0L)
        assertThat(diaconnG8Pump.systemRemainInsulin).isEqualTo(159.0)
        assertThat(diaconnG8Pump.systemRemainBattery).isEqualTo(87)
        assertThat(diaconnG8Pump.serialNo).isEqualTo(64004)
        assertThat(diaconnG8Pump.pumpProfiles!![diaconnG8Pump.activeProfile].take(24)).containsExactlyElementsIn(List(24) { 0.4 })
    }

    /** The same pump once its clock was set on the pump: 2026-11-11 18:22:50. */
    @Test
    fun anAnswerOfARealPumpGivesItsClock() {
        val packet = BigMainInfoInquireResponsePacket(packetInjector)

        packet.handleMessage(hex(REAL_ANSWER_CLOCK_SET))

        assertThat(packet.failed).isFalse()
        assertThat(diaconnG8Pump.pumpClockImpossible).isFalse()
        assertThat(diaconnG8Pump.getPumpTime()).isEqualTo(DateTime(2026, 11, 11, 18, 22, 50).millis)
    }

    private fun hex(bytes: String): ByteArray = bytes.trim().split(" ").map { it.toInt(16).toByte() }.toByteArray()

    private fun createValidBigPacket(): ByteArray {
        return createBigPacketWithSpecificValues(
            remainInsulin = 10000,
            remainBattery = 80,
            year = 24,
            month = 1,
            day = 1,
            hour = 12,
            minute = 0,
            second = 0
        )
    }

    private fun createBigPacketWithResult(result: Int): ByteArray {
        val data = ByteArray(182)
        data[0] = 0xed.toByte() // SOP_BIG
        data[1] = 0xb3.toByte() // msgType
        data[2] = 0x01.toByte() // seq
        data[3] = 0x00.toByte() // con_end
        data[4] = result.toByte()

        // Fill rest with safe defaults
        for (i in 5 until 181) {
            data[i] = 0x00.toByte()
        }

        data[181] = DiaconnG8Packet.getCRC(data, 181)
        return data
    }

    private fun createBigPacketWithSpecificValues(
        remainInsulin: Int,
        remainBattery: Int,
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
        second: Int
    ): ByteArray {
        val data = ByteArray(182)
        var pos = 0

        data[pos++] = 0xed.toByte() // SOP_BIG
        data[pos++] = 0xb3.toByte() // msgType
        data[pos++] = 0x01.toByte() // seq
        data[pos++] = 0x00.toByte() // con_end
        data[pos++] = 16.toByte() // result (success)

        // System info
        data[pos++] = (remainInsulin and 0xFF).toByte()
        data[pos++] = ((remainInsulin shr 8) and 0xFF).toByte()
        data[pos++] = remainBattery.toByte()
        data[pos++] = 1.toByte() // basePattern
        data[pos++] = 2.toByte() // tbStatus
        data[pos++] = 2.toByte() // mealStatus
        data[pos++] = 2.toByte() // snackStatus
        data[pos++] = 2.toByte() // squareStatus
        data[pos++] = 2.toByte() // dualStatus

        // Basal pause
        data[pos++] = 2.toByte() // basePauseStatus

        // Pump time
        data[pos++] = year.toByte()
        data[pos++] = month.toByte()
        data[pos++] = day.toByte()
        data[pos++] = hour.toByte()
        data[pos++] = minute.toByte()
        data[pos++] = second.toByte()

        // Pump system info (serial number, etc.)
        data[pos++] = 48.toByte() // country '0' (ASCII digit)
        data[pos++] = 49.toByte() // productType '1' (ASCII digit)
        data[pos++] = 22.toByte() // makeYear
        data[pos++] = 6.toByte()  // makeMonth
        data[pos++] = 15.toByte() // makeDay
        data[pos++] = 123.toByte() // lotNo
        data[pos++] = 0x39.toByte() // serialNo low
        data[pos++] = 0x30.toByte() // serialNo high
        data[pos++] = 3.toByte()  // majorVersion
        data[pos++] = 42.toByte() // minorVersion

        // Fill rest with padding
        while (pos < 181) {
            data[pos++] = 0x00.toByte()
        }

        data[181] = DiaconnG8Packet.getCRC(data, 181)
        return data
    }

    private companion object {

        // Both from a real G8 (DIACONN-64004, fw 2.50) on 2026-11-11.
        const val REAL_ANSWER_CLOCK_NEVER_SET =
            "ed b3 13 00 10 66 3e 57 01 02 02 02 02 02 02 19 03 13 00 41 15 31 31 14 06 09 01 04 fa 02 32 b1 0f 00 05 02 08 b4 c3 ee 00 02 00 28 " +
                "00 00 00 00 00 00 00 00 02 00 00 00 00 05 02 00 00 00 00 05 02 00 00 01 00 2c 01 00 00 02 00 00 00 00 00 00 01 00 00 00 00 00 02 4a " +
                "df cb 69 14 00 02 cd d8 cb 69 64 00 00 00 00 00 00 00 00 00 00 0a 00 00 0f 00 00 01 00 00 00 00 00 28 00 28 00 28 00 28 00 28 00 28 " +
                "00 28 00 28 00 28 00 28 00 28 00 28 00 28 00 28 00 28 00 28 00 28 00 28 00 28 00 28 00 28 00 28 00 28 00 28 00 ff ff ff ff ff ff ff " +
                "ff ff ff ff ff 38"
        const val REAL_ANSWER_CLOCK_SET =
            "ed b3 38 00 10 66 3e 56 01 02 02 02 02 02 02 1a 0b 0b 12 16 32 31 31 14 06 09 01 04 fa 02 32 cc 0f 00 05 02 08 b4 c3 80 03 02 12 78 " +
                "00 00 00 00 00 00 00 00 02 00 00 00 00 05 02 00 00 00 00 05 02 00 00 01 00 2c 01 00 00 02 00 00 00 00 00 00 01 00 00 00 00 00 02 4a " +
                "df cb 69 14 00 02 cd d8 cb 69 64 00 00 00 00 00 00 00 00 00 00 0a 00 00 0f 00 00 01 12 00 00 00 00 3c 00 3c 00 3c 00 3c 00 3c 00 3c " +
                "00 78 00 78 00 78 00 78 00 78 00 78 00 78 00 78 00 78 00 78 00 78 00 78 00 78 00 78 00 5a 00 5a 00 5a 00 5a 00 ff ff ff ff ff ff ff " +
                "ff ff ff ff ff a8"
    }
}

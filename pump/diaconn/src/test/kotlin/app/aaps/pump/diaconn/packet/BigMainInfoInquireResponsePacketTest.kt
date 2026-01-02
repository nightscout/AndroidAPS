package app.aaps.pump.diaconn.packet

import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.diaconn.DiaconnG8Pump
import app.aaps.pump.diaconn.keys.DiaconnStringNonKey
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`

class BigMainInfoInquireResponsePacketTest : TestBaseWithProfile() {

    private lateinit var diaconnG8Pump: DiaconnG8Pump

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is BigMainInfoInquireResponsePacket) {
                it.aapsLogger = aapsLogger
                it.dateUtil = dateUtil
                it.diaconnG8Pump = diaconnG8Pump
                it.preferences = preferences
                it.rh = rh
            }
        }
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
}

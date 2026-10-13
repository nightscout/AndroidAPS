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
import org.mockito.Mockito.verify

class SerialNumInquireResponsePacketTest : TestBaseWithProfile() {

    private lateinit var diaconnG8Pump: DiaconnG8Pump

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is SerialNumInquireResponsePacket) {
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
    }

    @Test
    fun handleMessageShouldParseSerialNumberCorrectly() {
        // Given
        val packet = SerialNumInquireResponsePacket(packetInjector)
        // Packet: result(16) + country(K=75) + productType(G=71) + makeYear(22) + makeMonth(6) + makeDay(15) + lotNo(123) + serialNo(12345 as short) + majorVer(3) + minorVer(42)
        val data = createValidPacket(
            result = 16,
            country = 48,  // '0' (ASCII digit to avoid NumberFormatException)
            productType = 49,  // '1' (ASCII digit to avoid NumberFormatException)
            makeYear = 22,
            makeMonth = 6,
            makeDay = 15,
            lotNo = 123,
            serialNo = 12345,
            majorVersion = 3,
            minorVersion = 42
        )

        // When
        packet.handleMessage(data)

        // Then
        assertThat(packet.failed).isFalse()
        assertThat(diaconnG8Pump.country).isEqualTo(0) // '0' parsed as integer
        assertThat(diaconnG8Pump.productType).isEqualTo(1) // '1' parsed as integer
        assertThat(diaconnG8Pump.makeYear).isEqualTo(22)
        assertThat(diaconnG8Pump.makeMonth).isEqualTo(6)
        assertThat(diaconnG8Pump.makeDay).isEqualTo(15)
        assertThat(diaconnG8Pump.lotNo).isEqualTo(123)
        assertThat(diaconnG8Pump.serialNo).isEqualTo(12345)
        assertThat(diaconnG8Pump.majorVersion).isEqualTo(3)
        assertThat(diaconnG8Pump.minorVersion).isEqualTo(42)
        verify(preferences).put(DiaconnStringNonKey.PumpVersion, "3.42")
    }

    @Test
    fun handleMessageShouldFailOnInvalidResult() {
        // Given
        val packet = SerialNumInquireResponsePacket(packetInjector)
        val data = createValidPacket(
            result = 17, // CRC error
            country = 48,
            productType = 49,
            makeYear = 22,
            makeMonth = 6,
            makeDay = 15,
            lotNo = 123,
            serialNo = 12345,
            majorVersion = 3,
            minorVersion = 42
        )

        // When
        packet.handleMessage(data)

        // Then
        assertThat(packet.failed).isTrue()
    }

    @Test
    fun handleMessageShouldFailOnDefectivePacket() {
        // Given
        val packet = SerialNumInquireResponsePacket(packetInjector)
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
        val packet = SerialNumInquireResponsePacket(packetInjector)

        // Then
        assertThat(packet.msgType).isEqualTo(0xAE.toByte())
    }

    @Test
    fun friendlyNameShouldBeCorrect() {
        // Given
        val packet = SerialNumInquireResponsePacket(packetInjector)

        // Then
        assertThat(packet.friendlyName).isEqualTo("PUMP_SERIAL_NUM_INQUIRE_RESPONSE")
    }

    private fun createValidPacket(
        result: Int,
        country: Int,
        productType: Int,
        makeYear: Int,
        makeMonth: Int,
        makeDay: Int,
        lotNo: Int,
        serialNo: Int,
        majorVersion: Int,
        minorVersion: Int
    ): ByteArray {
        val data = ByteArray(20)
        data[0] = 0xef.toByte() // SOP
        data[1] = 0xAE.toByte() // msgType
        data[2] = 0x01.toByte() // seq
        data[3] = 0x00.toByte() // con_end
        data[4] = result.toByte()
        data[5] = country.toByte()
        data[6] = productType.toByte()
        data[7] = makeYear.toByte()
        data[8] = makeMonth.toByte()
        data[9] = makeDay.toByte()
        data[10] = lotNo.toByte()
        data[11] = (serialNo and 0xFF).toByte() // serialNo low byte
        data[12] = ((serialNo shr 8) and 0xFF).toByte() // serialNo high byte
        data[13] = majorVersion.toByte()
        data[14] = minorVersion.toByte()

        // Fill rest with padding
        for (i in 15 until 19) {
            data[i] = 0xff.toByte()
        }

        // Calculate and set CRC
        data[19] = DiaconnG8Packet.getCRC(data, 19)

        return data
    }
}

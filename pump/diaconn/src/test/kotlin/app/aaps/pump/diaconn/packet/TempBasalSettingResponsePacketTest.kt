package app.aaps.pump.diaconn.packet

import app.aaps.pump.diaconn.DiaconnG8Pump
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TempBasalSettingResponsePacketTest : TestBaseWithProfile() {

    private lateinit var diaconnG8Pump: DiaconnG8Pump

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is TempBasalSettingResponsePacket) {
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
    fun handleMessageShouldParseSuccessResponse() {
        // Given
        val packet = TempBasalSettingResponsePacket(packetInjector)
        val otpNumber = 12345678
        val data = createValidPacket(result = 0, otpNumber = otpNumber)

        // When
        packet.handleMessage(data)

        // Then
        assertThat(packet.failed).isFalse()
        assertThat(packet.result).isEqualTo(0)
        assertThat(diaconnG8Pump.otpNumber).isEqualTo(otpNumber)
    }

    @Test
    fun handleMessageShouldFailOnError() {
        // Given
        val packet = TempBasalSettingResponsePacket(packetInjector)
        val data = createValidPacket(result = 7, otpNumber = 0) // Error: limited app setup

        // When
        packet.handleMessage(data)

        // Then
        assertThat(packet.failed).isTrue()
        assertThat(diaconnG8Pump.resultErrorCode).isEqualTo(7)
    }

    @Test
    fun handleMessageShouldFailOnTempBasalAlreadyRunning() {
        // Given
        val packet = TempBasalSettingResponsePacket(packetInjector)
        val data = createValidPacket(result = 35, otpNumber = 0) // Tempbasal start rejected when running

        // When
        packet.handleMessage(data)

        // Then
        assertThat(packet.failed).isTrue()
        assertThat(diaconnG8Pump.resultErrorCode).isEqualTo(35)
    }

    @Test
    fun handleMessageShouldFailOnDefectivePacket() {
        // Given
        val packet = TempBasalSettingResponsePacket(packetInjector)
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
        val packet = TempBasalSettingResponsePacket(packetInjector)

        // Then
        assertThat(packet.msgType).isEqualTo(0x8A.toByte())
    }

    @Test
    fun friendlyNameShouldBeCorrect() {
        // Given
        val packet = TempBasalSettingResponsePacket(packetInjector)

        // Then
        assertThat(packet.friendlyName).isEqualTo("PUMP_TEMP_BASAL_SETTING_RESPONSE")
    }

    private fun createValidPacket(result: Int, otpNumber: Int): ByteArray {
        val data = ByteArray(20)
        data[0] = 0xef.toByte() // SOP
        data[1] = 0x8A.toByte() // msgType
        data[2] = 0x01.toByte() // seq
        data[3] = 0x00.toByte() // con_end
        data[4] = result.toByte()

        // OTP number as int (4 bytes, little endian)
        data[5] = (otpNumber and 0xFF).toByte()
        data[6] = ((otpNumber shr 8) and 0xFF).toByte()
        data[7] = ((otpNumber shr 16) and 0xFF).toByte()
        data[8] = ((otpNumber shr 24) and 0xFF).toByte()

        // Fill rest with padding
        for (i in 9 until 19) {
            data[i] = 0xff.toByte()
        }

        // Calculate and set CRC
        data[19] = DiaconnG8Packet.getCRC(data, 19)

        return data
    }
}

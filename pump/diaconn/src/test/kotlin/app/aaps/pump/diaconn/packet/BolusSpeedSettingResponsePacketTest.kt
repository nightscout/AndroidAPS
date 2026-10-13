package app.aaps.pump.diaconn.packet

import app.aaps.pump.diaconn.DiaconnG8Pump
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class BolusSpeedSettingResponsePacketTest : TestBaseWithProfile() {

    private lateinit var diaconnG8Pump: DiaconnG8Pump

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is BolusSpeedSettingResponsePacket) {
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
    fun handleMessageShouldParseOtpNumber() {
        // Given
        val packet = BolusSpeedSettingResponsePacket(packetInjector)
        val data = createValidPacket(otpNumber = 123456)

        // When
        packet.handleMessage(data)

        // Then
        assertThat(packet.failed).isFalse()
        assertThat(packet.result).isEqualTo(0)
        assertThat(diaconnG8Pump.otpNumber).isEqualTo(123456)
    }

    @Test
    fun handleMessageShouldHandleZeroOtpNumber() {
        // Given
        val packet = BolusSpeedSettingResponsePacket(packetInjector)
        val data = createValidPacket(otpNumber = 0)

        // When
        packet.handleMessage(data)

        // Then
        assertThat(diaconnG8Pump.otpNumber).isEqualTo(0)
    }

    @Test
    fun handleMessageShouldHandleLargeOtpNumber() {
        // Given
        val packet = BolusSpeedSettingResponsePacket(packetInjector)
        val data = createValidPacket(otpNumber = 999999)

        // When
        packet.handleMessage(data)

        // Then
        assertThat(diaconnG8Pump.otpNumber).isEqualTo(999999)
    }

    @Test
    fun handleMessageShouldFailOnInvalidResult() {
        // Given
        val packet = BolusSpeedSettingResponsePacket(packetInjector)
        val data = createPacketWithResult(1)

        // When
        packet.handleMessage(data)

        // Then
        assertThat(packet.failed).isTrue()
        assertThat(diaconnG8Pump.resultErrorCode).isEqualTo(1)
    }

    @Test
    fun handleMessageShouldFailOnDefectivePacket() {
        // Given
        val packet = BolusSpeedSettingResponsePacket(packetInjector)
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
        val packet = BolusSpeedSettingResponsePacket(packetInjector)

        // Then
        assertThat(packet.msgType).isEqualTo(0x85.toByte())
    }

    @Test
    fun friendlyNameShouldBeCorrect() {
        // Given
        val packet = BolusSpeedSettingResponsePacket(packetInjector)

        // Then
        assertThat(packet.friendlyName).isEqualTo("PUMP_BOLUS_SPEED_SETTING_RESPONSE")
    }

    private fun createValidPacket(otpNumber: Int): ByteArray {
        val data = ByteArray(20)
        data[0] = 0xef.toByte() // SOP
        data[1] = 0x85.toByte() // msgType
        data[2] = 0x01.toByte() // seq
        data[3] = 0x00.toByte() // con_end
        data[4] = 0.toByte()    // result (0 = success for setting response)
        data[5] = (otpNumber and 0xFF).toByte()
        data[6] = ((otpNumber shr 8) and 0xFF).toByte()
        data[7] = ((otpNumber shr 16) and 0xFF).toByte()
        data[8] = ((otpNumber shr 24) and 0xFF).toByte()

        for (i in 9 until 19) {
            data[i] = 0xff.toByte()
        }

        data[19] = DiaconnG8Packet.getCRC(data, 19)
        return data
    }

    private fun createPacketWithResult(result: Int): ByteArray {
        val data = ByteArray(20)
        data[0] = 0xef.toByte()
        data[1] = 0x85.toByte()
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

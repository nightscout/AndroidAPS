package app.aaps.pump.diaconn.packet

import app.aaps.pump.diaconn.DiaconnG8Pump
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AppConfirmSettingPacketTest : TestBaseWithProfile() {

    private lateinit var diaconnG8Pump: DiaconnG8Pump

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is AppConfirmSettingPacket) {
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
    fun encodeShouldGenerateValidPacketWithOTP() {
        // Given - Confirm temp basal setting with OTP
        val reqMsgType = 0x0A.toByte() // Temp basal setting
        val otp = 123456 // 6-digit OTP
        val packet = AppConfirmSettingPacket(packetInjector, reqMsgType, otp)

        // When
        val encoded = packet.encode(10)

        // Then
        assertThat(encoded.size).isEqualTo(20)
        assertThat(encoded[0]).isEqualTo(0xef.toByte()) // SOP
        assertThat(encoded[1]).isEqualTo(0x37.toByte()) // msgType
        assertThat(encoded[2]).isEqualTo(10.toByte()) // seq
        assertThat(encoded[3]).isEqualTo(0x00.toByte()) // MSG_CON_END
        assertThat(encoded[4]).isEqualTo(reqMsgType)
        assertThat(DiaconnG8Packet.defect(encoded)).isEqualTo(0)
    }

    @Test
    fun encodeShouldHandleDifferentRequestTypes() {
        // Test confirming different request types
        val requestTypes = listOf(
            0x0A.toByte(), // Temp basal
            0x06.toByte(), // Meal bolus
            0x07.toByte(), // Snack bolus
            0x08.toByte()  // Extended bolus
        )

        requestTypes.forEach { reqType ->
            val packet = AppConfirmSettingPacket(packetInjector, reqType, 999999)
            val encoded = packet.encode(1)

            assertThat(encoded[4]).isEqualTo(reqType)
            assertThat(DiaconnG8Packet.defect(encoded)).isEqualTo(0)
        }
    }

    @Test
    fun encodeShouldHandleVariousOTPValues() {
        // Given - Different OTP values
        val otpValues = listOf(100000, 123456, 999999)

        otpValues.forEach { otp ->
            val packet = AppConfirmSettingPacket(packetInjector, 0x0A.toByte(), otp)
            val encoded = packet.encode(1)

            assertThat(DiaconnG8Packet.defect(encoded)).isEqualTo(0)
        }
    }

    @Test
    fun msgTypeShouldBeCorrect() {
        // Given
        val packet = AppConfirmSettingPacket(packetInjector, 0x0A.toByte(), 123456)

        // Then
        assertThat(packet.msgType).isEqualTo(0x37.toByte())
    }

    @Test
    fun friendlyNameShouldBeCorrect() {
        // Given
        val packet = AppConfirmSettingPacket(packetInjector, 0x0A.toByte(), 123456)

        // Then
        assertThat(packet.friendlyName).isEqualTo("PUMP_APP_CONFRIM_SETTING")
    }
}

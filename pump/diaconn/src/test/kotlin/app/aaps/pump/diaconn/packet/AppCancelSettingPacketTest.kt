package app.aaps.pump.diaconn.packet

import app.aaps.pump.diaconn.DiaconnG8Pump
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AppCancelSettingPacketTest : TestBaseWithProfile() {

    private lateinit var diaconnG8Pump: DiaconnG8Pump

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is AppCancelSettingPacket) {
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
    fun encodeShouldGenerateValidPacket() {
        // Given - Cancel temp basal command
        val reqMsgType = 0x0A.toByte() // Temp basal
        val packet = AppCancelSettingPacket(packetInjector, reqMsgType)

        // When
        val encoded = packet.encode(10)

        // Then
        assertThat(encoded.size).isEqualTo(20)
        assertThat(encoded[0]).isEqualTo(0xef.toByte())
        assertThat(encoded[1]).isEqualTo(0x29.toByte()) // msgType
        assertThat(encoded[2]).isEqualTo(10.toByte())
        assertThat(encoded[3]).isEqualTo(0x00.toByte())
        assertThat(encoded[4]).isEqualTo(reqMsgType)
        assertThat(DiaconnG8Packet.defect(encoded)).isEqualTo(0)
    }

    @Test
    fun encodeShouldHandleDifferentRequestTypes() {
        // Test canceling different command types
        val requestTypes = listOf(
            0x0A.toByte(), // Temp basal
            0x06.toByte(), // Meal bolus
            0x07.toByte(), // Snack bolus
            0x0B.toByte()  // Basal setting
        )

        requestTypes.forEach { reqType ->
            val packet = AppCancelSettingPacket(packetInjector, reqType)
            val encoded = packet.encode(1)

            assertThat(encoded[4]).isEqualTo(reqType)
            assertThat(DiaconnG8Packet.defect(encoded)).isEqualTo(0)
        }
    }

    @Test
    fun msgTypeShouldBeCorrect() {
        // Given
        val packet = AppCancelSettingPacket(packetInjector, 0x0A.toByte())

        // Then
        assertThat(packet.msgType).isEqualTo(0x29.toByte())
    }

    @Test
    fun friendlyNameShouldBeCorrect() {
        // Given
        val packet = AppCancelSettingPacket(packetInjector, 0x0A.toByte())

        // Then
        assertThat(packet.friendlyName).isEqualTo("PUMP_APP_CANCEL_SETTING")
    }
}

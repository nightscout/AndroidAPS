package app.aaps.pump.diaconn.packet

import app.aaps.pump.diaconn.DiaconnG8Pump
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class InjectionSnackSettingPacketTest : TestBaseWithProfile() {

    private lateinit var diaconnG8Pump: DiaconnG8Pump

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is InjectionSnackSettingPacket) {
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
    fun encodeShouldGenerateValidPacketForSnackBolus() {
        // Given - 3.5U snack bolus
        val amount = 350 // 3.5U * 100
        val packet = InjectionSnackSettingPacket(packetInjector, amount)

        // When
        val encoded = packet.encode(10)

        // Then
        assertThat(encoded.size).isEqualTo(20)
        assertThat(encoded[0]).isEqualTo(0xef.toByte()) // SOP
        assertThat(encoded[1]).isEqualTo(0x07.toByte()) // msgType
        assertThat(encoded[2]).isEqualTo(10.toByte()) // seq
        assertThat(encoded[3]).isEqualTo(0x00.toByte()) // MSG_CON_END
        assertThat(DiaconnG8Packet.defect(encoded)).isEqualTo(0)
    }

    @Test
    fun encodeShouldHandleSmallSnackBolus() {
        // Given - 0.5U snack bolus
        val amount = 50
        val packet = InjectionSnackSettingPacket(packetInjector, amount)

        // When
        val encoded = packet.encode(1)

        // Then
        assertThat(encoded.size).isEqualTo(20)
        assertThat(DiaconnG8Packet.defect(encoded)).isEqualTo(0)
    }

    @Test
    fun encodeShouldHandleLargeSnackBolus() {
        // Given - 15.0U snack bolus
        val amount = 1500
        val packet = InjectionSnackSettingPacket(packetInjector, amount)

        // When
        val encoded = packet.encode(1)

        // Then
        assertThat(encoded.size).isEqualTo(20)
        assertThat(DiaconnG8Packet.defect(encoded)).isEqualTo(0)
    }

    @Test
    fun msgTypeShouldBeCorrect() {
        // Given
        val packet = InjectionSnackSettingPacket(packetInjector, 350)

        // Then
        assertThat(packet.msgType).isEqualTo(0x07.toByte())
    }

    @Test
    fun friendlyNameShouldBeCorrect() {
        // Given
        val packet = InjectionSnackSettingPacket(packetInjector, 350)

        // Then
        assertThat(packet.friendlyName).isEqualTo("PUMP_INJECTION_SNACK_SETTING")
    }
}

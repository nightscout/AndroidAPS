package app.aaps.pump.diaconn.packet

import app.aaps.pump.diaconn.DiaconnG8Pump
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class InjectionMealSettingPacketTest : TestBaseWithProfile() {

    private lateinit var diaconnG8Pump: DiaconnG8Pump

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is InjectionMealSettingPacket) {
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
    fun encodeShouldGenerateValidPacketForMealBolus() {
        // Given - 5.0U meal bolus
        val amount = 500 // 5.0U * 100
        val bcDttm = 946652400L // Fixed time reference

        val packet = InjectionMealSettingPacket(packetInjector, amount, bcDttm)

        // When
        val encoded = packet.encode(10)

        // Then
        assertThat(encoded.size).isEqualTo(20)
        assertThat(encoded[0]).isEqualTo(0xef.toByte()) // SOP
        assertThat(encoded[1]).isEqualTo(0x06.toByte()) // msgType
        assertThat(encoded[2]).isEqualTo(10.toByte()) // seq
        assertThat(encoded[3]).isEqualTo(0x00.toByte()) // MSG_CON_END
        assertThat(DiaconnG8Packet.defect(encoded)).isEqualTo(0)
    }

    @Test
    fun encodeShouldHandleSmallBolus() {
        // Given - 0.5U meal bolus
        val amount = 50 // 0.5U * 100
        val bcDttm = 946652400L

        val packet = InjectionMealSettingPacket(packetInjector, amount, bcDttm)

        // When
        val encoded = packet.encode(1)

        // Then
        assertThat(encoded.size).isEqualTo(20)
        assertThat(DiaconnG8Packet.defect(encoded)).isEqualTo(0)
    }

    @Test
    fun encodeShouldHandleLargeBolus() {
        // Given - 20.0U meal bolus
        val amount = 2000 // 20.0U * 100
        val bcDttm = 946652400L

        val packet = InjectionMealSettingPacket(packetInjector, amount, bcDttm)

        // When
        val encoded = packet.encode(1)

        // Then
        assertThat(encoded.size).isEqualTo(20)
        assertThat(DiaconnG8Packet.defect(encoded)).isEqualTo(0)
    }

    @Test
    fun msgTypeShouldBeCorrect() {
        // Given
        val packet = InjectionMealSettingPacket(packetInjector, 500, 946652400L)

        // Then
        assertThat(packet.msgType).isEqualTo(0x06.toByte())
    }

    @Test
    fun friendlyNameShouldBeCorrect() {
        // Given
        val packet = InjectionMealSettingPacket(packetInjector, 500, 946652400L)

        // Then
        assertThat(packet.friendlyName).isEqualTo("PUMP_INJECTION_MEAL_SETTING")
    }
}

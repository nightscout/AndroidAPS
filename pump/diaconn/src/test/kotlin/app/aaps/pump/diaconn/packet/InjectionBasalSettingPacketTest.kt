package app.aaps.pump.diaconn.packet

import app.aaps.pump.diaconn.DiaconnG8Pump
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class InjectionBasalSettingPacketTest : TestBaseWithProfile() {

    private lateinit var diaconnG8Pump: DiaconnG8Pump

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is InjectionBasalSettingPacket) {
                it.aapsLogger = aapsLogger
                it.diaconnG8Pump = diaconnG8Pump
            }
        }
    }

    @BeforeEach
    fun setup() {
        diaconnG8Pump = DiaconnG8Pump(aapsLogger, dateUtil, decimalFormatter)
    }

    @Test
    fun encodeShouldGenerateValidPacketWithPattern() {
        // Given - Pattern 2 (Life1)
        val pattern = 2
        val packet = InjectionBasalSettingPacket(packetInjector, pattern)

        // When
        val encoded = packet.encode(6)

        // Then
        assertThat(encoded.size).isEqualTo(20)
        assertThat(encoded[0]).isEqualTo(0xef.toByte()) // SOP
        assertThat(encoded[1]).isEqualTo(0x0C.toByte()) // msgType
        assertThat(encoded[2]).isEqualTo(6.toByte()) // seq
        assertThat(encoded[3]).isEqualTo(0x00.toByte()) // MSG_CON_END
        assertThat(encoded[4]).isEqualTo(pattern.toByte())
        assertThat(DiaconnG8Packet.defect(encoded)).isEqualTo(0)
    }

    @Test
    fun encodeShouldHandleBasicPattern() {
        // Given - Pattern 1 (Basic)
        val pattern = 1
        val packet = InjectionBasalSettingPacket(packetInjector, pattern)

        // When
        val encoded = packet.encode(3)

        // Then
        assertThat(encoded[4]).isEqualTo(pattern.toByte())
        assertThat(DiaconnG8Packet.defect(encoded)).isEqualTo(0)
    }

    @Test
    fun encodeShouldHandleLife2Pattern() {
        // Given - Pattern 3 (Life2)
        val pattern = 3
        val packet = InjectionBasalSettingPacket(packetInjector, pattern)

        // When
        val encoded = packet.encode(8)

        // Then
        assertThat(encoded[4]).isEqualTo(pattern.toByte())
        assertThat(DiaconnG8Packet.defect(encoded)).isEqualTo(0)
    }

    @Test
    fun encodeShouldHandleLife3Pattern() {
        // Given - Pattern 4 (Life3)
        val pattern = 4
        val packet = InjectionBasalSettingPacket(packetInjector, pattern)

        // When
        val encoded = packet.encode(2)

        // Then
        assertThat(encoded[4]).isEqualTo(pattern.toByte())
        assertThat(DiaconnG8Packet.defect(encoded)).isEqualTo(0)
    }

    @Test
    fun encodeShouldHandleDoctor1Pattern() {
        // Given - Pattern 5 (Doctor1)
        val pattern = 5
        val packet = InjectionBasalSettingPacket(packetInjector, pattern)

        // When
        val encoded = packet.encode(11)

        // Then
        assertThat(encoded[4]).isEqualTo(pattern.toByte())
        assertThat(DiaconnG8Packet.defect(encoded)).isEqualTo(0)
    }

    @Test
    fun encodeShouldHandleDoctor2Pattern() {
        // Given - Pattern 6 (Doctor2)
        val pattern = 6
        val packet = InjectionBasalSettingPacket(packetInjector, pattern)

        // When
        val encoded = packet.encode(15)

        // Then
        assertThat(encoded[4]).isEqualTo(pattern.toByte())
        assertThat(DiaconnG8Packet.defect(encoded)).isEqualTo(0)
    }

    @Test
    fun msgTypeShouldBeCorrect() {
        // Given
        val packet = InjectionBasalSettingPacket(packetInjector, 2)

        // Then
        assertThat(packet.msgType).isEqualTo(0x0C.toByte())
    }

    @Test
    fun friendlyNameShouldBeCorrect() {
        // Given
        val packet = InjectionBasalSettingPacket(packetInjector, 2)

        // Then
        assertThat(packet.friendlyName).isEqualTo("PUMP_INJECTION_BASAL_SETTING")
    }
}

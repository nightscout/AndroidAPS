package app.aaps.pump.diaconn.packet

import app.aaps.pump.diaconn.DiaconnG8Pump
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SoundSettingPacketTest : TestBaseWithProfile() {

    private lateinit var diaconnG8Pump: DiaconnG8Pump

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is SoundSettingPacket) {
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
    fun encodeShouldGenerateValidPacketWithTypeAndIntensity() {
        // Given - Type 2 with intensity 3
        val type = 2
        val intensity = 3
        val packet = SoundSettingPacket(packetInjector, type, intensity)

        // When
        val encoded = packet.encode(11)

        // Then
        assertThat(encoded.size).isEqualTo(20)
        assertThat(encoded[0]).isEqualTo(0xef.toByte()) // SOP
        assertThat(encoded[1]).isEqualTo(0x0D.toByte()) // msgType
        assertThat(encoded[2]).isEqualTo(11.toByte()) // seq
        assertThat(encoded[3]).isEqualTo(0x00.toByte()) // MSG_CON_END
        assertThat(encoded[4]).isEqualTo(type.toByte())
        assertThat(encoded[5]).isEqualTo(intensity.toByte())
        assertThat(DiaconnG8Packet.defect(encoded)).isEqualTo(0)
    }

    @Test
    fun encodeShouldHandleType1WithIntensity1() {
        // Given - Type 1, Intensity 1 (min)
        val type = 1
        val intensity = 1
        val packet = SoundSettingPacket(packetInjector, type, intensity)

        // When
        val encoded = packet.encode(5)

        // Then
        assertThat(encoded[4]).isEqualTo(type.toByte())
        assertThat(encoded[5]).isEqualTo(intensity.toByte())
        assertThat(DiaconnG8Packet.defect(encoded)).isEqualTo(0)
    }

    @Test
    fun encodeShouldHandleType3WithIntensity5() {
        // Given - Type 3, Intensity 5
        val type = 3
        val intensity = 5
        val packet = SoundSettingPacket(packetInjector, type, intensity)

        // When
        val encoded = packet.encode(8)

        // Then
        assertThat(encoded[4]).isEqualTo(type.toByte())
        assertThat(encoded[5]).isEqualTo(intensity.toByte())
        assertThat(DiaconnG8Packet.defect(encoded)).isEqualTo(0)
    }

    @Test
    fun encodeShouldHandleAllTypeAndIntensityCombinations() {
        // Test various combinations of type and intensity
        val testCases = listOf(
            Pair(1, 1),
            Pair(1, 3),
            Pair(2, 2),
            Pair(3, 4),
            Pair(4, 5)
        )

        testCases.forEach { (type, intensity) ->
            val packet = SoundSettingPacket(packetInjector, type, intensity)
            val encoded = packet.encode(1)

            assertThat(encoded[4]).isEqualTo(type.toByte())
            assertThat(encoded[5]).isEqualTo(intensity.toByte())
            assertThat(DiaconnG8Packet.defect(encoded)).isEqualTo(0)
        }
    }

    @Test
    fun msgTypeShouldBeCorrect() {
        // Given
        val packet = SoundSettingPacket(packetInjector, 2, 3)

        // Then
        assertThat(packet.msgType).isEqualTo(0x0D.toByte())
    }

    @Test
    fun friendlyNameShouldBeCorrect() {
        // Given
        val packet = SoundSettingPacket(packetInjector, 2, 3)

        // Then
        assertThat(packet.friendlyName).isEqualTo("PUMP_SOUND_SETTING")
    }
}

package app.aaps.pump.diaconn.packet

import app.aaps.pump.diaconn.DiaconnG8Pump
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DisplayTimeoutSettingPacketTest : TestBaseWithProfile() {

    private lateinit var diaconnG8Pump: DiaconnG8Pump

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is DisplayTimeoutSettingPacket) {
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
    fun encodeShouldGenerateValidPacketWithType() {
        // Given - Timeout type 2 (40 sec)
        val type = 2
        val packet = DisplayTimeoutSettingPacket(packetInjector, type)

        // When
        val encoded = packet.encode(7)

        // Then
        assertThat(encoded.size).isEqualTo(20)
        assertThat(encoded[0]).isEqualTo(0xef.toByte()) // SOP
        assertThat(encoded[1]).isEqualTo(0x0E.toByte()) // msgType
        assertThat(encoded[2]).isEqualTo(7.toByte()) // seq
        assertThat(encoded[3]).isEqualTo(0x00.toByte()) // MSG_CON_END
        assertThat(encoded[4]).isEqualTo(type.toByte())
        assertThat(DiaconnG8Packet.defect(encoded)).isEqualTo(0)
    }

    @Test
    fun encodeShouldHandle30SecTimeout() {
        // Given - Type 1 (30 sec)
        val type = 1
        val packet = DisplayTimeoutSettingPacket(packetInjector, type)

        // When
        val encoded = packet.encode(4)

        // Then
        assertThat(encoded[4]).isEqualTo(type.toByte())
        assertThat(DiaconnG8Packet.defect(encoded)).isEqualTo(0)
    }

    @Test
    fun encodeShouldHandle50SecTimeout() {
        // Given - Type 3 (50 sec)
        val type = 3
        val packet = DisplayTimeoutSettingPacket(packetInjector, type)

        // When
        val encoded = packet.encode(9)

        // Then
        assertThat(encoded[4]).isEqualTo(type.toByte())
        assertThat(DiaconnG8Packet.defect(encoded)).isEqualTo(0)
    }

    @Test
    fun msgTypeShouldBeCorrect() {
        // Given
        val packet = DisplayTimeoutSettingPacket(packetInjector, 2)

        // Then
        assertThat(packet.msgType).isEqualTo(0x0E.toByte())
    }

    @Test
    fun friendlyNameShouldBeCorrect() {
        // Given
        val packet = DisplayTimeoutSettingPacket(packetInjector, 2)

        // Then
        assertThat(packet.friendlyName).isEqualTo("PUMP_DISPLAY_TIMEOUT_SETTING_PACKET")
    }
}

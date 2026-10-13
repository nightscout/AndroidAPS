package app.aaps.pump.diaconn.packet

import app.aaps.pump.diaconn.DiaconnG8Pump
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class BasalSettingPacketTest : TestBaseWithProfile() {

    private lateinit var diaconnG8Pump: DiaconnG8Pump

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is BasalSettingPacket) {
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
    fun encodeShouldGenerateValidPacketForFirstGroup() {
        // Given - First group (00-05) with specific basal rates
        // Rates: 0.5, 0.5, 0.6, 0.6, 0.7, 0.7 U/h -> multiply by 100: 50, 50, 60, 60, 70, 70
        val pattern = 1 // Basic pattern
        val group = 1   // 00-05 hours
        val packet = BasalSettingPacket(
            packetInjector,
            pattern, group,
            50, 50, 60, 60, 70, 70
        )

        // When
        val encoded = packet.encode(1)

        // Then
        assertThat(encoded.size).isEqualTo(20)
        assertThat(encoded[0]).isEqualTo(0xef.toByte()) // SOP
        assertThat(encoded[1]).isEqualTo(0x0B.toByte()) // msgType
        assertThat(encoded[3]).isEqualTo(0x01.toByte()) // MSG_CON_CONTINUE (not last group)
        assertThat(encoded[4]).isEqualTo(pattern.toByte())
        assertThat(encoded[5]).isEqualTo(group.toByte())
        assertThat(DiaconnG8Packet.defect(encoded)).isEqualTo(0)
    }

    @Test
    fun encodeShouldGenerateValidPacketForLastGroup() {
        // Given - Last group (18-23) should use MSG_CON_END
        val pattern = 1
        val group = 4 // 18-23 hours (last group)
        val packet = BasalSettingPacket(
            packetInjector,
            pattern, group,
            100, 100, 100, 100, 100, 100
        )

        // When
        val encoded = packet.encode(4)

        // Then
        assertThat(encoded[3]).isEqualTo(0x00.toByte()) // MSG_CON_END (last group)
        assertThat(encoded[4]).isEqualTo(pattern.toByte())
        assertThat(encoded[5]).isEqualTo(group.toByte())
        assertThat(DiaconnG8Packet.defect(encoded)).isEqualTo(0)
    }

    @Test
    fun encodeShouldHandleVariableBasalRates() {
        // Given - Different rates for different hours
        val packet = BasalSettingPacket(
            packetInjector,
            2, // Life1 pattern
            2, // 06-11 hours
            80, 85, 90, 95, 100, 105 // Gradually increasing
        )

        // When
        val encoded = packet.encode(2)

        // Then
        assertThat(encoded.size).isEqualTo(20)
        assertThat(encoded[4]).isEqualTo(2.toByte()) // pattern
        assertThat(encoded[5]).isEqualTo(2.toByte()) // group
        assertThat(DiaconnG8Packet.defect(encoded)).isEqualTo(0)
    }

    @Test
    fun encodeShouldHandleAllPatterns() {
        // Test all pattern types (1-6)
        for (pattern in 1..6) {
            val packet = BasalSettingPacket(
                packetInjector,
                pattern,
                1,
                50, 50, 50, 50, 50, 50
            )

            val encoded = packet.encode(1)
            assertThat(encoded[4]).isEqualTo(pattern.toByte())
            assertThat(DiaconnG8Packet.defect(encoded)).isEqualTo(0)
        }
    }

    @Test
    fun encodeShouldHandleAllGroups() {
        // Test all hour groups (1-4)
        for (group in 1..4) {
            val packet = BasalSettingPacket(
                packetInjector,
                1,
                group,
                50, 50, 50, 50, 50, 50
            )

            val encoded = packet.encode(group)
            assertThat(encoded[5]).isEqualTo(group.toByte())

            // Verify continuation flag
            if (group == 4) {
                assertThat(encoded[3]).isEqualTo(0x00.toByte()) // MSG_CON_END
            } else {
                assertThat(encoded[3]).isEqualTo(0x01.toByte()) // MSG_CON_CONTINUE
            }
        }
    }

    @Test
    fun msgTypeShouldBeCorrect() {
        // Given
        val packet = BasalSettingPacket(
            packetInjector,
            1, 1,
            50, 50, 50, 50, 50, 50
        )

        // Then
        assertThat(packet.msgType).isEqualTo(0x0B.toByte())
    }

    @Test
    fun friendlyNameShouldBeCorrect() {
        // Given
        val packet = BasalSettingPacket(
            packetInjector,
            1, 1,
            50, 50, 50, 50, 50, 50
        )

        // Then
        assertThat(packet.friendlyName).isEqualTo("PUMP_BASAL_SETTING")
    }
}

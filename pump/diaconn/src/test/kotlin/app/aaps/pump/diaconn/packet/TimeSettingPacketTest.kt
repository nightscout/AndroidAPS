package app.aaps.pump.diaconn.packet

import app.aaps.pump.diaconn.DiaconnG8Pump
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TimeSettingPacketTest : TestBaseWithProfile() {

    private lateinit var diaconnG8Pump: DiaconnG8Pump

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is TimeSettingPacket) {
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
    fun encodeShouldGenerateValidPacketForTimeSync() {
        // Given - March 15, 2024, 14:30:45 UTC
        val time = 1710511845000L // 2024-03-15 14:30:45 UTC
        val offset = 0
        val packet = TimeSettingPacket(packetInjector, time, offset)

        // When
        val encoded = packet.encode(10)

        // Then
        assertThat(encoded.size).isEqualTo(20)
        assertThat(encoded[0]).isEqualTo(0xef.toByte()) // SOP
        assertThat(encoded[1]).isEqualTo(0x0F.toByte()) // msgType
        assertThat(encoded[2]).isEqualTo(10.toByte()) // seq
        assertThat(encoded[3]).isEqualTo(0x00.toByte()) // MSG_CON_END
        assertThat(DiaconnG8Packet.defect(encoded)).isEqualTo(0)
    }

    @Test
    fun encodeShouldHandleTimeZoneOffset() {
        // Given - Time with +9 hour offset (e.g., Korea)
        val time = 1710511845000L
        val offset = 9
        val packet = TimeSettingPacket(packetInjector, time, offset)

        // When
        val encoded = packet.encode(1)

        // Then
        assertThat(encoded.size).isEqualTo(20)
        assertThat(DiaconnG8Packet.defect(encoded)).isEqualTo(0)
    }

    @Test
    fun encodeShouldHandleNegativeTimeZoneOffset() {
        // Given - Time with -5 hour offset (e.g., EST)
        val time = 1710511845000L
        val offset = -5
        val packet = TimeSettingPacket(packetInjector, time, offset)

        // When
        val encoded = packet.encode(1)

        // Then
        assertThat(encoded.size).isEqualTo(20)
        assertThat(DiaconnG8Packet.defect(encoded)).isEqualTo(0)
    }

    @Test
    fun encodeShouldHandleDifferentDatesAndTimes() {
        // Test a few different timestamps
        val timestamps = listOf(
            1609459200000L, // 2021-01-01 00:00:00
            1640995200000L, // 2022-01-01 00:00:00
            1735689600000L  // 2025-01-01 00:00:00
        )

        timestamps.forEach { time ->
            val packet = TimeSettingPacket(packetInjector, time, 0)
            val encoded = packet.encode(1)
            assertThat(DiaconnG8Packet.defect(encoded)).isEqualTo(0)
        }
    }

    @Test
    fun msgTypeShouldBeCorrect() {
        // Given
        val packet = TimeSettingPacket(packetInjector, System.currentTimeMillis(), 0)

        // Then
        assertThat(packet.msgType).isEqualTo(0x0F.toByte())
    }

    @Test
    fun friendlyNameShouldBeCorrect() {
        // Given
        val packet = TimeSettingPacket(packetInjector, System.currentTimeMillis(), 0)

        // Then
        assertThat(packet.friendlyName).isEqualTo("PUMP_TIME_SETTING_REQUEST")
    }
}

package app.aaps.pump.diaconn.packet

import app.aaps.core.interfaces.di.MetroMemberInjector
import app.aaps.pump.diaconn.DiaconnG8Pump
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TimeSettingPacketTest : TestBaseWithProfile() {

    private lateinit var diaconnG8Pump: DiaconnG8Pump

    private val packetInjector = MetroMemberInjector {
        if (it is TimeSettingPacket) {
                it.aapsLogger = aapsLogger
                it.dateUtil = dateUtil
                it.diaconnG8Pump = diaconnG8Pump
        }
        true
    }

    @BeforeEach
    fun setup() {
        diaconnG8Pump = DiaconnG8Pump(aapsLogger, dateUtil, decimalFormatter)
    }

    @Test
    fun encodeShouldGenerateValidPacketForTimeSync() {
        // Given - March 15, 2024, 14:30:45 UTC
        val time = 1710511845000L // 2024-03-15 14:30:45 UTC
        val packet = TimeSettingPacket(packetInjector, time)

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

    // 00:30 in Prague is 23:30 UTC the day before. The UTC hour plus one gave hour 24 of the day before.
    @Test
    fun aTimeJustAfterMidnightIsSentAsTheLocalDateAndHour() {
        val local = encodedIn("Europe/Prague", DateTime(2026, 11, 11, 0, 30, 15, DateTimeZone.forID("Europe/Prague")).millis)

        assertThat(local).containsExactly(26, 11, 11, 0, 30, 15).inOrder()
    }

    // Half an hour off UTC: the whole hours of the zone were added, so the pump was set 30 minutes off.
    @Test
    fun aZoneWithHalfHoursIsSentAsTheLocalTime() {
        val local = encodedIn("Asia/Kolkata", DateTime(2026, 11, 11, 18, 45, 0, DateTimeZone.forID("Asia/Kolkata")).millis)

        assertThat(local).containsExactly(26, 11, 11, 18, 45, 0).inOrder()
    }

    // West of UTC early in the UTC day the hour was negative.
    @Test
    fun aZoneWestOfUtcIsSentAsTheLocalTime() {
        val local = encodedIn("America/New_York", DateTime(2026, 11, 10, 21, 5, 0, DateTimeZone.forID("America/New_York")).millis)

        assertThat(local).containsExactly(26, 11, 10, 21, 5, 0).inOrder()
    }

    /** Year (from 2000), month, day, hour, minute and second the packet sends for [time], with the phone in [zone]. */
    private fun encodedIn(zone: String, time: Long): List<Int> {
        val phoneZone = DateTimeZone.getDefault()
        DateTimeZone.setDefault(DateTimeZone.forID(zone))
        try {
            val encoded = TimeSettingPacket(packetInjector, time).encode(1)
            return (4..9).map { encoded[it].toInt() and 0xff }
        } finally {
            DateTimeZone.setDefault(phoneZone)
        }
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
            val packet = TimeSettingPacket(packetInjector, time)
            val encoded = packet.encode(1)
            assertThat(DiaconnG8Packet.defect(encoded)).isEqualTo(0)
        }
    }

    @Test
    fun msgTypeShouldBeCorrect() {
        // Given
        val packet = TimeSettingPacket(packetInjector, System.currentTimeMillis())

        // Then
        assertThat(packet.msgType).isEqualTo(0x0F.toByte())
    }

    @Test
    fun friendlyNameShouldBeCorrect() {
        // Given
        val packet = TimeSettingPacket(packetInjector, System.currentTimeMillis())

        // Then
        assertThat(packet.friendlyName).isEqualTo("PUMP_TIME_SETTING_REQUEST")
    }
}

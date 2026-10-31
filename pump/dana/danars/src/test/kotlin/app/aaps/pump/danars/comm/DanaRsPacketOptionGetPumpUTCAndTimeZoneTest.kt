package app.aaps.pump.danars.comm

import app.aaps.pump.danars.DanaRSTestBase
import com.google.common.truth.Truth.assertThat
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.junit.jupiter.api.Test

/** A pump that keeps UTC (Dana-i): its clock is UTC, whatever the time zone of the phone is */
class DanaRsPacketOptionGetPumpUTCAndTimeZoneTest : DanaRSTestBase() {

    private fun packet() = DanaRSPacketOptionGetPumpUTCAndTimeZone(aapsLogger, dateUtil).also { it.danaPump = danaPump }

    /** UTC Y M D h m s and the zone offset in hours, as the pump sends them */
    private fun reply(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int, zoneOffset: Int): ByteArray {
        val array = createArray(9, 0.toByte()) // 7 + 2
        listOf(year, month, day, hour, minute, second, zoneOffset).forEachIndexed { index, value -> putByteToArray(array, index, value.toByte()) }
        return array
    }

    private fun withDefaultZone(id: String, block: () -> Unit) {
        val defaultZone = DateTimeZone.getDefault()
        try {
            DateTimeZone.setDefault(DateTimeZone.forID(id))
            block()
        } finally {
            DateTimeZone.setDefault(defaultZone)
        }
    }

    @Test
    fun pumpClockIsReadAsUtc() {
        // Kolkata is 5:30 ahead: the old code moved the time by whole hours only and was 30 minutes off
        for (zone in listOf("UTC", "Europe/Prague", "America/New_York", "Asia/Kolkata")) withDefaultZone(zone) {
            packet().handleMessage(reply(26, 10, 15, 8, 30, 15, 2))

            assertThat(danaPump.pumpTime).isEqualTo(DateTime(2026, 10, 15, 8, 30, 15, DateTimeZone.UTC).millis)
            assertThat(danaPump.zoneOffset).isEqualTo(2)
        }
    }

    /**
     * #449: 28 March 2027 02:30 UTC is 04:30 in Prague. Read as Prague time, 02:30 does not exist that night:
     * it threw, the reply counted as not received, and the pump was reset.
     */
    @Test
    fun utcTimeInTheLocalDaylightSavingGapIsRead() = withDefaultZone("Europe/Prague") {
        packet().handleMessage(reply(27, 3, 28, 2, 30, 0, 2))

        assertThat(danaPump.pumpTime).isEqualTo(DateTime(2027, 3, 28, 2, 30, 0, DateTimeZone.UTC).millis)
    }

    /** In the hour after the change the old code moved the time by the new offset, so it was an hour off */
    @Test
    fun utcTimeRightAfterTheChangeIsRead() = withDefaultZone("Europe/Prague") {
        packet().handleMessage(reply(27, 3, 28, 1, 30, 0, 2))

        assertThat(danaPump.pumpTime).isEqualTo(DateTime(2027, 3, 28, 1, 30, 0, DateTimeZone.UTC).millis)
    }

    @Test
    fun friendlyName() {
        assertThat(packet().friendlyName).isEqualTo("OPTION__GET_PUMP_UTC_AND_TIMEZONE")
    }
}

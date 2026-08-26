package app.aaps.database.entities

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.util.TimeZone

/**
 * [defaultUtcOffset] replaced `java.util.TimeZone.getDefault().getOffset(timestamp)` in the default
 * of every entity's `utcOffset` column, so that the entities can compile for every target. The two
 * have to agree exactly: the value is written to the database and sent to Nightscout, and a wrong
 * offset moves a treatment by hours.
 *
 * The oracle is the java call that was there before. It runs in the default zone of the machine, so
 * the test says nothing on a UTC-only CI unless a zone is set - which is why the zone is set here
 * and restored afterwards.
 */
class UtcOffsetsTest {

    @Test
    fun `agrees with java TimeZone in every zone tried, on both sides of a DST switch`() {
        val original = TimeZone.getDefault()
        try {
            for (zone in listOf("Europe/Prague", "America/New_York", "Australia/Lord_Howe", "Asia/Kathmandu", "UTC")) {
                TimeZone.setDefault(TimeZone.getTimeZone(zone))
                for (timestamp in TIMESTAMPS) {
                    assertThat(defaultUtcOffset(timestamp))
                        .isEqualTo(TimeZone.getDefault().getOffset(timestamp).toLong())
                }
            }
        } finally {
            TimeZone.setDefault(original)
        }
    }

    private companion object {

        /** Epoch, a winter date, a summer date, and the hours around the European DST switches. */
        val TIMESTAMPS = listOf(
            0L,
            1_700_000_000_000L,  // 2023-11-14, winter
            1_690_000_000_000L,  // 2023-07-22, summer
            1_679_792_400_000L,  // 2023-03-26 01:00 UTC, the spring switch
            1_698_541_200_000L   // 2023-10-29 01:00 UTC, the autumn switch
        )
    }
}

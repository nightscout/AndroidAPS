package app.aaps.core.data.datetime

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.joda.time.DateTime
import org.joda.time.format.ISODateTimeFormat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.TimeZone

/**
 * Pins [parseIsoToEpochMillisOrNull] against joda's `ISODateTimeFormat.dateTimeParser()`.
 *
 * These strings come from **Nightscout device status written by other uploaders**, not only from
 * AAPS itself, so the shapes are not hypothetical. A strict parser that rejected one of them would
 * not fail loudly - the value would land in 1970 or the whole document would be dropped, and the
 * only symptom would be a missing reading.
 *
 * The zone is pinned to a non-UTC one with daylight saving because the offset-less shapes are
 * resolved in the **local** zone: on a UTC machine those cases would agree no matter what the
 * implementation did, and the test would prove nothing.
 */
class IsoDateParserParityTest {

    private lateinit var original: TimeZone

    @BeforeEach fun pinZone() {
        original = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Prague"))
    }

    @AfterEach fun restoreZone() {
        TimeZone.setDefault(original)
    }

    private fun joda(text: String): Long =
        DateTime.parse(text, ISODateTimeFormat.dateTimeParser()).toDate().time

    /** Shapes joda accepts, which therefore have to keep working. */
    private val accepted = listOf(
        "2026-08-06T04:56:19.555Z",
        "2026-08-06T04:56:19Z",
        "2026-08-06T04:56:19.555+02:00",
        "2026-08-06T04:56:19.555+0200",       // no colon - kotlinx alone rejects this
        "2026-08-06T04:56:19.555-04:00",
        "2026-08-06T04:56:19.555-0430",
        "2017-11-19T22:50:34.417+0200",       // from the historical DateUtil test
        "2017-12-03T16:09:25.000Z",
        "2017-12-22T00:32:30Z",
        "2026-08-06T04:56:19.555",            // no offset at all - local
        "2026-08-06T04:56:19",                // no offset, no fraction - local
        "2026-08-06T04:56",                   // minutes only - local
        "2026-01-15T04:56:19.555",            // winter, local: offset differs from summer
        "2026-08-06",                         // date only - local midnight
        "2026-01-15"
    )

    @Test fun `every shape joda accepts parses to the same instant`() {
        accepted.forEach { text ->
            assertWithMessage("input %s", text)
                .that(parseIsoToEpochMillisOrNull(text)).isEqualTo(joda(text))
        }
    }

    @Test fun `lower case t and z are accepted like joda`() {
        listOf("2026-08-06t04:56:19.555z", "2026-08-06t04:56:19.555Z").forEach { text ->
            assertWithMessage("input %s", text)
                .that(parseIsoToEpochMillisOrNull(text)).isEqualTo(joda(text))
        }
    }

    @Test fun `surrounding whitespace is tolerated`() {
        assertThat(parseIsoToEpochMillisOrNull("  2026-08-06T04:56:19.555Z  "))
            .isEqualTo(joda("2026-08-06T04:56:19.555Z"))
    }

    /**
     * Proves the offset-less cases above are not vacuous: in Europe/Prague the same wall clock time
     * maps to two different instants depending on the time of year, so a parser that ignored the
     * local zone would fail one of them.
     */
    @Test fun `offset-less values really are resolved in the local zone`() {
        val summer = parseIsoToEpochMillisOrNull("2026-08-06T04:56:19.555")!!
        val winter = parseIsoToEpochMillisOrNull("2026-01-15T04:56:19.555")!!
        val summerUtc = parseIsoToEpochMillisOrNull("2026-08-06T04:56:19.555Z")!!
        val winterUtc = parseIsoToEpochMillisOrNull("2026-01-15T04:56:19.555Z")!!

        assertThat(summerUtc - summer).isEqualTo(2 * 3_600_000L)   // CEST
        assertThat(winterUtc - winter).isEqualTo(1 * 3_600_000L)   // CET
    }

    @Test fun `garbage returns null rather than a wrong instant`() {
        listOf("", "   ", "not a date", "2026-13-45T99:99:99Z", "Z", "+02:00").forEach { text ->
            assertWithMessage("input %s", text)
                .that(parseIsoToEpochMillisOrNull(text)).isNull()
        }
    }
}

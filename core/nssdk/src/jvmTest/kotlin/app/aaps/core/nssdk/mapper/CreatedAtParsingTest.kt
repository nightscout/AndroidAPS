package app.aaps.core.nssdk.mapper

import app.aaps.core.nssdk.nsSdkJson
import app.aaps.core.nssdk.remotemodel.RemoteTreatment
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.util.TimeZone

/**
 * Pins every `created_at` shape the parser has to cope with.
 *
 * This exists so joda can be removed from `:core:nssdk` without guessing. `ISODateTimeFormat
 * .dateTimeParser()` is deliberately **lenient** - it accepts far more than RFC 3339 - while
 * `kotlinx.datetime.Instant.parse` is strict. A straight swap would silently start returning `0L`
 * for shapes that parse today, and `0L` means the treatment lands in 1970 with no error anywhere.
 *
 * `created_at` is only consulted when `date`, `mills` and `timestamp` are all absent, which AAPS
 * never produces - but older API v1 documents and other uploaders do, and those are exactly the
 * records with unusual date formats.
 *
 * Two of these shapes carry **no zone at all** and are therefore read as **local** time. Their
 * expected value is computed from the machine's own offset rather than hard coded, so the test says
 * what it means ("this is local time") and passes anywhere. Note that the JVM default zone cannot be
 * swapped inside the test to check this: joda caches its own `DateTimeZone` default at class init
 * and ignores a later `TimeZone.setDefault`.
 */
class CreatedAtParsingTest {

    private fun timestampOf(createdAt: String): Long =
        nsSdkJson.decodeFromString(
            RemoteTreatment.serializer(),
            """{"eventType":"Correction Bolus","created_at":"$createdAt"}"""
        ).timestamp()

    // 2026-08-06T04:56:19.555Z == 1785992179555
    private val utcMillis = 1785992179555L
    private val midnightUtc = 1785974400000L

    /** Local offset at that moment, so the zone-less expectations hold in any time zone. */
    private fun offsetAt(instant: Long) = TimeZone.getDefault().getOffset(instant).toLong()

    @Test
    fun `the shapes that must keep working`() {
        val results = linkedMapOf(
            "full UTC" to timestampOf("2026-08-06T04:56:19.555Z"),
            "no millis" to timestampOf("2026-08-06T04:56:19Z"),
            "offset with colon" to timestampOf("2026-08-06T06:56:19.555+02:00"),
            "offset without colon" to timestampOf("2026-08-06T06:56:19.555+0200"),
            "negative offset" to timestampOf("2026-08-06T00:56:19.555-04:00"),
            "no zone at all" to timestampOf("2026-08-06T04:56:19.555"),
            "no seconds" to timestampOf("2026-08-06T04:56Z"),
            "date only" to timestampOf("2026-08-06"),
            "one decimal" to timestampOf("2026-08-06T04:56:19.5Z"),
            "lowercase t and z" to timestampOf("2026-08-06t04:56:19.555z")
        )

        // Asserted as one map so a single run shows the whole picture rather than the first failure.
        assertThat(results).isEqualTo(
            linkedMapOf(
                "full UTC" to utcMillis,
                "no millis" to 1785992179000L,
                "offset with colon" to utcMillis,
                "offset without colon" to utcMillis,
                "negative offset" to utcMillis,
                // no zone -> local wall clock, so the instant is earlier by the local offset
                "no zone at all" to utcMillis - offsetAt(utcMillis),
                "no seconds" to 1785992160000L,
                "date only" to midnightUtc - offsetAt(midnightUtc),   // local midnight
                "one decimal" to 1785992179500L,
                "lowercase t and z" to utcMillis
            )
        )
    }

    /**
     * The zone-less case stated on its own, because it is the one a stricter parser would get wrong
     * in a way nothing else would notice: `Instant.parse` rejects it outright, and treating it as UTC
     * would silently shift the treatment by the local offset.
     */
    @Test
    fun `a created_at without a zone is read as local time, not UTC`() {
        val parsed = timestampOf("2026-08-06T04:56:19.555")

        assertThat(parsed).isEqualTo(utcMillis - offsetAt(utcMillis))
        // and it really is offset-dependent unless the machine happens to run on UTC
        if (offsetAt(utcMillis) != 0L) assertThat(parsed).isNotEqualTo(utcMillis)
    }

    /** Anything unparseable gives 0 rather than throwing - the record survives, dated 1970. */
    @Test
    fun `unparseable text gives zero and does not throw`() {
        assertThat(timestampOf("whenever")).isEqualTo(0L)
        assertThat(timestampOf("")).isEqualTo(0L)
        assertThat(timestampOf("not-a-date-at-all")).isEqualTo(0L)
    }

    /** The epoch forms, which are handled before the ISO parser is reached. */
    @Test
    fun `epoch forms are handled before ISO parsing`() {
        assertThat(timestampOf("1785992179555")).isEqualTo(1785992179555L)
        assertThat(timestampOf(" 1785992179555 ")).isEqualTo(1785992179555L)
    }
}

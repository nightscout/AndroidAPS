package app.aaps.core.interfaces.aps

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Pins [RT.TimestampToIsoSerializer.toISOString] against the `SimpleDateFormat` implementation it is
 * being converted away from.
 *
 * This string is not cosmetic: it is what the APS result timestamp is serialized to, so it travels
 * into stored results and to Nightscout. The format has to stay `yyyy-MM-dd'T'HH:mm:ss.SSS'Z'` in
 * UTC, with exactly three fractional digits, including when the fraction is zero - which rules out
 * `Instant.toString()`, since that drops trailing zeros and omits the fraction entirely on a whole
 * second.
 */
class RtIsoStringParityTest {

    private lateinit var originalLocale: Locale

    @BeforeEach fun save() {
        originalLocale = Locale.getDefault()
    }

    @AfterEach fun restore() {
        Locale.setDefault(originalLocale)
    }

    /** A literal copy of the original body, so the comparison is against real old behaviour. */
    private fun reference(date: Long, locale: Locale): String {
        val f: DateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", locale)
        f.timeZone = TimeZone.getTimeZone("UTC")
        return f.format(date)
    }

    private val samples = listOf(
        0L,                     // epoch
        1_785_992_181_588L,     // arbitrary, non zero millis
        1_785_992_181_000L,     // whole second - the case Instant.toString() would render differently
        1_785_992_181_007L,     // sub-10 millis, needs zero padding
        1_767_225_600_000L,     // 2026-01-01T00:00:00Z
        253_402_300_799_000L    // year 9999
    )

    @Test fun `matches SimpleDateFormat in a neutral locale`() {
        Locale.setDefault(Locale.US)
        samples.forEach { t ->
            assertWithMessage("t=%s", t)
                .that(RT.TimestampToIsoSerializer.toISOString(t))
                .isEqualTo(reference(t, Locale.US))
        }
    }

    @Test fun `always has exactly three fractional digits`() {
        Locale.setDefault(Locale.US)
        samples.forEach { t ->
            assertWithMessage("t=%s", t)
                .that(RT.TimestampToIsoSerializer.toISOString(t))
                .matches("""\d{4,}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z""")
        }
    }

    @Test fun `round trips through the parser`() {
        Locale.setDefault(Locale.US)
        samples.forEach { t ->
            val text = RT.TimestampToIsoSerializer.toISOString(t)
            assertWithMessage("t=%s via %s", t, text)
                .that(RT.TimestampToIsoSerializer.fromISODateString(text)).isEqualTo(t)
        }
    }

    /**
     * The reason this conversion is worth doing beyond portability.
     *
     * `SimpleDateFormat` resolves its calendar from the locale, and a Thai locale selects the
     * Buddhist calendar - so the old code rendered the year as 2569 rather than 2026 for a user
     * whose phone is set to Thai. That is a wire format bug, not a display preference: the receiver
     * gets a timestamp 543 years in the future.
     *
     * This asserts the defect exists in the OLD implementation, so the claim is measured rather than
     * assumed, and so the test fails loudly if a JDK change ever makes it untrue.
     */
    @Test fun `the SimpleDateFormat version was locale sensitive`() {
        val thai = Locale.forLanguageTag("th-TH-u-ca-buddhist")
        val inThai = reference(1_785_992_181_588L, thai)
        val inUs = reference(1_785_992_181_588L, Locale.US)

        assertWithMessage("old implementation in Thai locale: %s", inThai)
            .that(inThai).isNotEqualTo(inUs)
        assertThat(inUs).startsWith("2026-")
        assertThat(inThai).startsWith("2569-")
    }

    /** ...and the replacement must not be, whatever the phone's locale is set to. */
    @Test fun `the new version is locale independent`() {
        val expected = RT.TimestampToIsoSerializer.toISOString(1_785_992_181_588L)
        listOf(
            Locale.forLanguageTag("th-TH-u-ca-buddhist"),
            Locale.forLanguageTag("ar-EG-u-nu-arab"),
            Locale.forLanguageTag("hi-IN"),
            Locale.forLanguageTag("ja-JP-u-ca-japanese")
        ).forEach { locale ->
            Locale.setDefault(locale)
            assertWithMessage("locale %s", locale)
                .that(RT.TimestampToIsoSerializer.toISOString(1_785_992_181_588L)).isEqualTo(expected)
        }
    }
}

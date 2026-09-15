package app.aaps.core.interfaces.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSDateFormatterNoStyle
import platform.Foundation.NSDateFormatterShortStyle
import platform.Foundation.NSLocale

/**
 * The same reader, against the patterns a real `NSDateFormatter` produces.
 *
 * The parser's own cases live in `commonTest`, because the parser is shared code. These two cannot:
 * they exist to check the reader against what Apple actually hands back, so they need the Apple
 * formatter and only run on iOS. Keeping them is what makes the whole thing more than a test of my
 * own assumptions about pattern strings - `zh_TW` was found this way, by running the formatter over
 * every locale rather than by reading the standard.
 */
class ClockPatternIosLocaleTest {

    private fun shortTimePattern(locale: String): String {
        val formatter = NSDateFormatter().apply {
            this.locale = NSLocale(localeIdentifier = locale)
            dateStyle = NSDateFormatterNoStyle
            timeStyle = NSDateFormatterShortStyle
        }
        return formatter.dateFormat.orEmpty()
    }

    /**
     * Only that an answer comes back for every locale - the answers themselves belong to the system.
     * This keeps holding if Apple changes a pattern.
     */
    @Test
    fun `every locale the app ships in names an hour field`() {
        val locales = listOf(
            "en_US", "en_GB", "cs_CZ", "de_DE", "fr_FR", "es_ES", "it_IT", "pt_BR", "nl_NL", "pl_PL",
            "ru_RU", "tr_TR", "sv_SE", "da_DK", "fi_FI", "nb_NO", "ja_JP", "ko_KR", "zh_CN", "zh_TW",
            "ar_EG", "he_IL", "hi_IN", "el_GR", "hu_HU", "ro_RO", "sk_SK", "bg_BG", "hr_HR", "lt_LT",
            "sl_SI", "ca_ES", "vi_VN", "uk_UA"
        )
        for (name in locales) {
            val pattern = shortTimePattern(name)
            if (usesTwelveHourClock(pattern) == null) throw AssertionError("No hour field found for $name in '$pattern'")
        }
    }

    /** Traditional Chinese specifically - the locale the AM/PM reading got wrong. */
    @Test
    fun `traditional chinese reads as twelve hour through the real formatter`() {
        assertEquals(true, usesTwelveHourClock(shortTimePattern("zh_TW")))
    }

    /**
     * The other call that asks the same question.
     *
     * `IosDateFormatPlatform` does not use the short style above - it asks for the `j` template, the
     * locale's preferred hour field. That is a different formatter call and so needs its own case,
     * which is how the two iOS answers were allowed to disagree in the first place: the theme parsed
     * the hour field of the short pattern while `DateUtil` looked for an `a` in the template.
     */
    @Test
    fun `the preferred hour template reads as twelve hour for traditional chinese`() {
        val template = NSDateFormatter.dateFormatFromTemplate("j", 0uL, NSLocale(localeIdentifier = "zh_TW"))

        assertEquals(true, usesTwelveHourClock(template.orEmpty()))
    }
}

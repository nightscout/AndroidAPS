package app.aaps.core.ui.compose

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSDateFormatterNoStyle
import platform.Foundation.NSDateFormatterShortStyle
import platform.Foundation.NSLocale

/**
 * Which clock a time pattern asks for.
 *
 * The case that matters is Traditional Chinese. Its short time pattern is `Bh:mm`, where `B` is the
 * flexible day period and `h` is a 12 hour hour. Reading the AM/PM letter finds no `a` there and
 * answers "24 hour", which is the wrong picker for that user. Every test below exists to keep that
 * from coming back.
 */
class ClockPatternTest {

    @Test
    fun `the plain twelve hour pattern reads as twelve hour`() {
        assertEquals(true, usesTwelveHourClock("h:mm a"))
    }

    @Test
    fun `the plain twenty four hour pattern reads as twenty four hour`() {
        assertEquals(false, usesTwelveHourClock("HH:mm"))
    }

    /** The regression. No `a` in it anywhere, and still a 12 hour clock. */
    @Test
    fun `a day period written as B is still a twelve hour clock`() {
        assertEquals(true, usesTwelveHourClock("Bh:mm"))
    }

    @Test
    fun `both spellings of each hour field are understood`() {
        assertEquals(true, usesTwelveHourClock("K:mm a"))
        assertEquals(false, usesTwelveHourClock("k:mm"))
    }

    @Test
    fun `an hour letter inside a quoted literal is text and not a field`() {
        assertEquals(false, usesTwelveHourClock("HH'h'mm"))
        assertEquals(true, usesTwelveHourClock("h 'o''clock'"))
    }

    @Test
    fun `a pattern naming no hour has no answer to give`() {
        assertNull(usesTwelveHourClock("mm:ss"))
        assertNull(usesTwelveHourClock(""))
    }

    /**
     * The same check against the real formatter, so this keeps holding if Apple changes a pattern.
     * Only that an answer comes back for every locale - the answers themselves belong to the system.
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
            val formatter = NSDateFormatter().apply {
                locale = NSLocale(localeIdentifier = name)
                dateStyle = NSDateFormatterNoStyle
                timeStyle = NSDateFormatterShortStyle
            }
            val pattern = formatter.dateFormat.orEmpty()
            assertNotNullMessage(name, pattern, usesTwelveHourClock(pattern))
        }
    }

    /** Traditional Chinese specifically - the locale the AM/PM reading got wrong. */
    @Test
    fun `traditional chinese reads as twelve hour through the real formatter`() {
        val formatter = NSDateFormatter().apply {
            locale = NSLocale(localeIdentifier = "zh_TW")
            dateStyle = NSDateFormatterNoStyle
            timeStyle = NSDateFormatterShortStyle
        }
        assertEquals(true, usesTwelveHourClock(formatter.dateFormat.orEmpty()))
    }

    private fun assertNotNullMessage(locale: String, pattern: String, answer: Boolean?) {
        if (answer == null) throw AssertionError("No hour field found for $locale in '$pattern'")
    }
}

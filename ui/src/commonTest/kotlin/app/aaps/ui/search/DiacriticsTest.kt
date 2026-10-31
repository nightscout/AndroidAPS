package app.aaps.ui.search

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins [removeDiacritics] on every target.
 *
 * There are two unrelated implementations behind it - NFD-and-strip on Android, Foundation folding on
 * Apple - and the search index relies on them agreeing. Nothing else compares them, so this test is
 * what makes "the same search matches on both platforms" true rather than assumed.
 */
class DiacriticsTest {

    @Test
    fun `strips the accents a translated app actually uses`() {
        // Czech, which is what the plugin's own KDoc names as the case that must keep working.
        assertEquals("prumer", "průměr".removeDiacritics())
        assertEquals("Kratkodoby", "Krátkodobý".removeDiacritics())
        // German, French, Spanish, Portuguese, Scandinavian.
        assertEquals("uber", "über".removeDiacritics())
        assertEquals("eleve", "élevé".removeDiacritics())
        assertEquals("anos", "años".removeDiacritics())
        assertEquals("acucar", "açúcar".removeDiacritics())
        assertEquals("malning", "målning".removeDiacritics())
    }

    @Test
    fun `leaves text that has no accents exactly as it is`() {
        assertEquals("insulin", "insulin".removeDiacritics())
        assertEquals("IOB", "IOB".removeDiacritics())
        assertEquals("", "".removeDiacritics())
        assertEquals("3.5 U/h", "3.5 U/h".removeDiacritics())
    }

    @Test
    fun `keeps the capital I so a Turkish phone still matches IOB`() {
        // Diacritic folding must not be locale sensitive. With a Turkish locale "I" folds to a dotless
        // "ı", and a search for IOB would then stop matching for Turkish users only.
        assertEquals("IOB", "IOB".removeDiacritics())
        assertEquals("Insulin", "Insulin".removeDiacritics())
    }

    @Test
    fun `is idempotent so an already stripped query is unchanged`() {
        val once = "průměr".removeDiacritics()
        assertEquals(once, once.removeDiacritics())
    }
}

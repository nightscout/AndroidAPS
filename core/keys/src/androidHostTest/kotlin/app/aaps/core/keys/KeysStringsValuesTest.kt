package app.aaps.core.keys

import app.aaps.core.keys.interfaces.TextRef
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * The generated text that iOS and the desktop read, as they have no Android resources.
 *
 * Hebrew is in `values-iw-rIL`, with the old language code. The setting and iOS ask for `he`, so a
 * wrong tag here shows English with no error at all.
 */
class KeysStringsValuesTest {

    /** A language name must stay readable when the app is in a language the user cannot read. */
    @Test
    fun `the language setting is the same in every language`() {
        assertThat(StringKey.GeneralLanguage.title).isEqualTo(TextRef.Literal("Language"))
        assertThat(StringKey.GeneralLanguage.entries.filterValues { it !is TextRef.Literal }.keys).containsExactly("default")
    }

    @Test
    fun `the language list starts with the phone language and English`() {
        assertThat(StringKey.GeneralLanguage.entries.keys.take(2)).containsExactly("default", "en").inOrder()
    }

    @Test
    fun `Hebrew is found by its current language code`() {
        assertThat(KeysStringsValues.textOf("lang_default", "he")).isEqualTo("ברירת המחדל של המערכת")
        assertThat(KeysStringsValues.textOf("lang_default", "he-IL")).isEqualTo("ברירת המחדל של המערכת")
    }
}

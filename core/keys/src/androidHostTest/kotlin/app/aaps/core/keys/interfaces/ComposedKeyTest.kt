package app.aaps.core.keys.interfaces

import app.aaps.core.keys.BooleanComposedKey
import app.aaps.core.keys.IntComposedKey
import app.aaps.core.keys.LongComposedKey
import app.aaps.core.keys.ProfileComposedBooleanKey
import app.aaps.core.keys.ProfileComposedStringKey
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.Locale

/**
 * [ComposedKey.composeKey] builds SharedPreferences key names. If the text ever changes, users lose
 * the setting stored under the old name, so these tests pin it hard.
 *
 * The old code was `String.format(Locale.ENGLISH, key + format, *arguments)`. That call is the
 * oracle here: the new hand written version has to give exactly the same text.
 */
class ComposedKeyTest {

    private val originalLocale: Locale = Locale.getDefault()

    @AfterEach fun restoreLocale() {
        Locale.setDefault(originalLocale)
    }

    /** Every real composed key in the app, with an argument that suits its format. */
    private fun allKeys(): List<Pair<ComposedKey, Any>> =
        BooleanComposedKey.entries.map { it to argumentFor(it) } +
            IntComposedKey.entries.map { it to argumentFor(it) } +
            LongComposedKey.entries.map { it to argumentFor(it) } +
            ProfileComposedBooleanKey.entries.map { it to argumentFor(it) } +
            ProfileComposedStringKey.entries.map { it to argumentFor(it) }

    private fun argumentFor(key: ComposedKey): Any = if (key.format.contains("%d")) 7 else "abc"

    /** The old implementation, kept here only to compare against. */
    private fun oldComposeKey(key: ComposedKey, argument: Any): String =
        String.format(Locale.ENGLISH, key.key + key.format, argument)

    @Test
    fun `gives the same text as the old String format for every key`() {
        for ((key, argument) in allKeys())
        // Truth reads the message as a template, and key.format itself holds %s or %d,
        // so it has to go in as a placeholder value, not inside the text.
            assertWithMessage("key '%s' format '%s'", key.key, key.format)
                .that(key.composeKey(argument))
                .isEqualTo(oldComposeKey(key, argument))
    }

    /**
     * The reason this was rewritten. A locale with its own digits would make String.format write
     * "%d" in that script unless a locale is passed every time. Building the text by hand cannot
     * go wrong that way.
     */
    @Test
    fun `key text does not depend on the locale`() {
        val expected = allKeys().map { (key, argument) -> key.composeKey(argument) }
        for (tag in listOf("en", "de-DE", "ar-SA", "ne-NP", "bn-IN", "my-MM", "fa-IR", "th-TH-u-nu-thai")) {
            Locale.setDefault(Locale.forLanguageTag(tag))
            val actual = allKeys().map { (key, argument) -> key.composeKey(argument) }
            assertWithMessage("locale $tag").that(actual).isEqualTo(expected)
        }
    }

    private fun key(k: String, f: String) = object : ComposedKey {
        override val key: String = k
        override val format: String = f
    }

    @Test
    fun `substitutes d and s`() {
        assertThat(key("a_", "%d").composeKey(5)).isEqualTo("a_5")
        assertThat(key("a_", "%d").composeKey(-5)).isEqualTo("a_-5")
        assertThat(key("a_", "%d").composeKey(5L)).isEqualTo("a_5")
        assertThat(key("a_", "%s").composeKey("x")).isEqualTo("a_x")
        assertThat(key("a_", "%s").composeKey(true)).isEqualTo("a_true")
        assertThat(key("a_", "%d_%s").composeKey(1, "b")).isEqualTo("a_1_b")
        assertThat(key("a_", "").composeKey()).isEqualTo("a_")
    }

    @Test
    fun `a doubled percent stays a single percent`() {
        assertThat(key("a_", "%%").composeKey()).isEqualTo("a_%")
        assertThat(key("a_", "%d%%").composeKey(5)).isEqualTo("a_5%")
    }

    @Test
    fun `an unsupported format is refused`() {
        for (bad in listOf("%f", "%x", "%b", "%02d", "%.2f", "%1\$s")) {
            assertThrows<IllegalArgumentException>("format $bad should be refused") {
                key("a_", bad).composeKey(1)
            }
        }
    }

    @Test
    fun `a lonely percent at the end is refused`() {
        assertThrows<IllegalArgumentException> { key("a_", "%").composeKey() }
    }

    @Test
    fun `a value that is not a whole number is refused for d`() {
        assertThrows<IllegalArgumentException> { key("a_", "%d").composeKey(1.5) }
        assertThrows<IllegalArgumentException> { key("a_", "%d").composeKey("1") }
    }

    @Test
    fun `a wrong number of arguments is refused`() {
        assertThrows<IllegalArgumentException> { key("a_", "%d").composeKey() }
        assertThrows<IllegalArgumentException> { key("a_", "%d").composeKey(1, 2) }
        assertThrows<IllegalArgumentException> { key("a_", "").composeKey(1) }
    }
}

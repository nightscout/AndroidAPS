package app.aaps.core.interfaces.resources

import app.aaps.core.keys.interfaces.TextRef
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Answering in the language the app is set to, without any caller asking for one.
 *
 * The locale is state here, exactly as it is in `Resources` on Android - that is what lets `gs(ref)`
 * keep its signature and every call site stay untouched. These tests pin that arrangement, and the
 * fallback that goes with it.
 *
 * Before this, only English existed on the platforms without a resource table, so the app ignored
 * both the phone's language and the language setting.
 */
class TextRefValueRegistryTest {

    private val english = mapOf("hello" to "Hello", "only_english" to "Only English")
    private val czech = mapOf("hello" to "Ahoj")

    @BeforeTest
    fun setUp() {
        TextRefValueRegistry.clear()
        TextRefValueRegistry.register("m") { name, locale ->
            when (locale?.substringBefore('-')) {
                "cs" -> czech[name] ?: english[name]
                else -> english[name]
            }
        }
    }

    @AfterTest
    fun tearDown() = TextRefValueRegistry.clear()

    private fun text(name: String) = TextRefValueRegistry.textOf(TextRef.Named("m", name))

    @Test
    fun `with no locale set the answer is english`() {
        assertEquals("Hello", text("hello"))
    }

    @Test
    fun `setting a locale changes the answer without the caller asking`() {
        TextRefValueRegistry.locale = "cs-CZ"

        assertEquals("Ahoj", text("hello"))
    }

    /**
     * The reason the fallback is per string rather than per locale: no translation is complete, so a
     * language that has most of the app must still answer in English for the rest instead of showing
     * a name or nothing.
     */
    @Test
    fun `an untranslated string falls back to english in a translated locale`() {
        TextRefValueRegistry.locale = "cs-CZ"

        assertEquals("Ahoj", text("hello"))
        assertEquals("Only English", text("only_english"))
    }

    /** The language setting stores `cs`; the translations are keyed `cs-CZ`. Both must resolve. */
    @Test
    fun `a bare language resolves as well as a full tag`() {
        TextRefValueRegistry.locale = "cs"

        assertEquals("Ahoj", text("hello"))
    }

    /** A language nothing was translated into is English, not empty. */
    @Test
    fun `an unknown language falls back to english`() {
        TextRefValueRegistry.locale = "xx-XX"

        assertEquals("Hello", text("hello"))
    }

    @Test
    fun `switching back to no locale returns to english`() {
        TextRefValueRegistry.locale = "cs-CZ"
        TextRefValueRegistry.locale = null

        assertEquals("Hello", text("hello"))
    }

    @Test
    fun `a name no module owns has no text`() {
        assertNull(text("not_a_string"))
        assertNull(TextRefValueRegistry.textOf(TextRef.Named("other", "hello")))
    }

    /** Clearing must drop the locale too, or one test leaks its language into the next. */
    @Test
    fun `clearing resets the locale`() {
        TextRefValueRegistry.locale = "cs-CZ"

        TextRefValueRegistry.clear()

        assertNull(TextRefValueRegistry.locale)
    }
}

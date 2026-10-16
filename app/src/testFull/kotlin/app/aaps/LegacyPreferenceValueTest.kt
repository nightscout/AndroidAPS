package app.aaps

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Pins the two rules that make the start-up migrations safe, because both are easy to "simplify"
 * away and neither failure would be visible:
 *
 * 1. **A value that does not fit is null, never a substitute.** Null makes the caller skip the key
 *    and leave it alone. Before this, the migrations cast, and a failed cast in `MainApp` is caught
 *    as "Fatal initialization error" - the app will not start until its data is cleared.
 * 2. **Nothing guesses.** `toBoolean()` in place of `toBooleanStrictOrNull()` would read "yes",
 *    "1" or "" as a real `false` and quietly turn a plugin off; `toString()` in place of a String
 *    check would hand the profile migration a number where it expects an ISF array.
 */
class LegacyPreferenceValueTest {

    @Test fun `a long stays a long`() {
        assertEquals(42L, LegacyPreferenceValue.asLong(42L))
    }

    @Test fun `an int is widened, because that is what the store returns for one`() {
        assertEquals(42L, LegacyPreferenceValue.asLong(42))
    }

    @Test fun `a number written as text is read`() {
        assertEquals(42L, LegacyPreferenceValue.asLong("42"))
    }

    @Test fun `text that is not a number is skipped, not turned into zero`() {
        assertNull(LegacyPreferenceValue.asLong("not a number"))
        assertNull(LegacyPreferenceValue.asLong(""))
    }

    @Test fun `a decimal is not silently truncated to a long`() {
        assertNull(LegacyPreferenceValue.asLong(1.5))
        assertNull(LegacyPreferenceValue.asLong("1.5"))
    }

    @Test fun `nothing is not a long`() {
        assertNull(LegacyPreferenceValue.asLong(null))
        assertNull(LegacyPreferenceValue.asLong(true))
    }

    @Test fun `a boolean stays a boolean`() {
        assertEquals(true, LegacyPreferenceValue.asBoolean(true))
        assertEquals(false, LegacyPreferenceValue.asBoolean(false))
    }

    @Test fun `true and false written as text are read`() {
        assertEquals(true, LegacyPreferenceValue.asBoolean("true"))
        assertEquals(false, LegacyPreferenceValue.asBoolean("false"))
    }

    @Test fun `anything else is skipped rather than read as false`() {
        // This is the whole reason for toBooleanStrictOrNull. Each of these is `false` to
        // toBoolean(), so a broken value would migrate as a real setting and turn something off.
        assertNull(LegacyPreferenceValue.asBoolean("yes"))
        assertNull(LegacyPreferenceValue.asBoolean("1"))
        assertNull(LegacyPreferenceValue.asBoolean("TRUE"))
        assertNull(LegacyPreferenceValue.asBoolean(""))
        assertNull(LegacyPreferenceValue.asBoolean(1))
        assertNull(LegacyPreferenceValue.asBoolean(null))
    }

    @Test fun `text stays text`() {
        assertEquals("[1.0, 2.0]", LegacyPreferenceValue.asString("[1.0, 2.0]"))
        assertEquals("", LegacyPreferenceValue.asString(""))
    }

    @Test fun `a non-text value is skipped, not rendered with toString`() {
        // A profile's ISF, IC, basal and target are text. "3.5" arriving here as a Double means the
        // key holds something we do not understand, and rendering it would be accepted downstream
        // as a profile the user never set.
        assertNull(LegacyPreferenceValue.asString(3.5))
        assertNull(LegacyPreferenceValue.asString(42L))
        assertNull(LegacyPreferenceValue.asString(true))
        assertNull(LegacyPreferenceValue.asString(null))
    }
}

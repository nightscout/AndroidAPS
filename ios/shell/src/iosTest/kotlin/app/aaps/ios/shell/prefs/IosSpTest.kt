package app.aaps.ios.shell.prefs

import platform.Foundation.NSUserDefaults
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What the preference store has to get right on iOS.
 *
 * These run against their own `NSUserDefaults` suite rather than the standard one, so a test can
 * write and clear freely without touching anything the app or the device owns.
 */
class IosSpTest {

    private val domain = "app.aaps.ios.shell.test"
    private val sp = IosSp(domain, NSUserDefaults(suiteName = domain))

    @BeforeTest fun clean() = sp.clear()

    @AfterTest fun cleanUp() = sp.clear()

    // The one that matters. NSUserDefaults answers a missing key with a zero value instead of
    // reporting it missing, so a store written the obvious way returns false and 0.0 and silently
    // replaces every AAPS default. Each type is listed separately: getting one right and another
    // wrong is exactly the shape this bug takes.

    @Test
    fun `a missing boolean gives the callers default not false`() {
        assertTrue(sp.getBoolean("absent", defaultValue = true))
    }

    @Test
    fun `a missing double gives the callers default not zero`() {
        assertEquals(5.5, sp.getDouble("absent", defaultValue = 5.5))
    }

    @Test
    fun `a missing int gives the callers default not zero`() {
        assertEquals(42, sp.getInt("absent", defaultValue = 42))
    }

    @Test
    fun `a missing long gives the callers default not zero`() {
        assertEquals(99L, sp.getLong("absent", defaultValue = 99L))
    }

    @Test
    fun `a missing string gives the callers default`() {
        assertEquals("fallback", sp.getString("absent", defaultValue = "fallback"))
        assertNull(sp.getStringOrNull("absent", defaultValue = null))
    }

    @Test
    fun `a stored false is returned not mistaken for missing`() {
        // The inverse of the trap above: presence has to be judged by the key existing, not by the
        // value being non-zero, or storing false would read back as the default.
        sp.putBoolean("flag", false)

        assertFalse(sp.getBoolean("flag", defaultValue = true))
    }

    @Test
    fun `a stored zero is returned not mistaken for missing`() {
        sp.putDouble("zero", 0.0)
        sp.putInt("zeroInt", 0)

        assertEquals(0.0, sp.getDouble("zero", defaultValue = 7.0))
        assertEquals(0, sp.getInt("zeroInt", defaultValue = 7))
    }

    @Test
    fun `every type survives a round trip`() {
        sp.putBoolean("b", true)
        sp.putDouble("d", 7.25)
        sp.putInt("i", -13)
        sp.putLong("l", 9_000_000_000L)
        sp.putString("s", "text")

        assertTrue(sp.getBoolean("b", defaultValue = false))
        assertEquals(7.25, sp.getDouble("d", defaultValue = 0.0))
        assertEquals(-13, sp.getInt("i", defaultValue = 0))
        // Larger than Int.MAX_VALUE on purpose: a Long stored through a 32 bit path would come back
        // truncated rather than fail.
        assertEquals(9_000_000_000L, sp.getLong("l", defaultValue = 0L))
        assertEquals("text", sp.getString("s", defaultValue = ""))
    }

    @Test
    fun `contains and remove agree with each other`() {
        assertFalse(sp.contains("k"))

        sp.putInt("k", 1)
        assertTrue(sp.contains("k"))

        sp.remove("k")
        assertFalse(sp.contains("k"))
    }

    @Test
    fun `increment starts from the default when the key is absent`() {
        sp.incInt("counter")
        sp.incLong("longCounter")

        assertEquals(1, sp.getInt("counter", defaultValue = 0))
        assertEquals(1L, sp.getLong("longCounter", defaultValue = 0L))
    }

    @Test
    fun `increment adds to a stored value`() {
        sp.putInt("counter", 41)
        sp.incInt("counter")

        assertEquals(42, sp.getInt("counter", defaultValue = 0))
    }

    @Test
    fun `an edit block writes every value in it`() {
        sp.edit {
            putString("a", "one")
            putInt("b", 2)
            putBoolean("c", true)
        }

        assertEquals("one", sp.getString("a", defaultValue = ""))
        assertEquals(2, sp.getInt("b", defaultValue = 0))
        assertTrue(sp.getBoolean("c", defaultValue = false))
    }

    @Test
    fun `clear removes everything that was written`() {
        sp.putString("a", "one")
        sp.putInt("b", 2)

        sp.clear()

        assertFalse(sp.contains("a"))
        assertFalse(sp.contains("b"))
        assertTrue(sp.getAll().isEmpty())
    }

    @Test
    fun `keys do not leak into each other`() {
        sp.putInt("first", 1)
        sp.putInt("second", 2)

        assertEquals(1, sp.getInt("first", defaultValue = 0))
        assertEquals(2, sp.getInt("second", defaultValue = 0))
    }
}

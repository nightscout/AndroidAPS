package app.aaps.ios.shell

import app.aaps.core.data.format.NumberFormat
import app.aaps.core.data.format.NumberFormatPlatform
import app.aaps.core.data.time.systemUtcOffsetAt
import app.aaps.core.interfaces.concurrent.AapsLock
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The `actual` implementations, which are the only AAPS code that differs on iOS.
 *
 * Everything else in the shared modules is one source compiled by one frontend, so running it again
 * here would mostly repeat what the JVM tests already cover. These cannot be covered anywhere else:
 * a wrong `actual` is invisible to every test that runs on the JVM.
 *
 * The assertions are invariants rather than fixed values, because the answers depend on the device's
 * locale and time zone. A test that demanded a dot would pass in London and fail in Prague, which
 * would say something about the simulator rather than about AAPS.
 */
class PlatformActualTest {

    @Test
    fun `the utc offset is a real time zone offset`() {
        val offset = systemUtcOffsetAt(timestamp = 1_700_000_000_000L)

        // Real world offsets run from -12:00 to +14:00. A stub returning zero would still pass this,
        // so the point is to catch nonsense such as milliseconds mistaken for minutes.
        assertTrue(offset in -12 * 3_600_000L..14 * 3_600_000L, "implausible utc offset: $offset")
    }

    @Test
    fun `the locale separator is one that a number can actually use`() {
        assertContains(listOf('.', ','), NumberFormatPlatform.localeSeparator)
    }

    @Test
    fun `formatting uses the separator the platform reports`() {
        val formatted = NumberFormatPlatform.format(NumberFormat.DECIMAL_1, 12.3)

        // This is the pairing that matters. A formatter that emitted a dot while the platform
        // reported a comma would put dots in a Czech user's insulin doses, and no JVM test would
        // notice.
        assertContains(formatted, NumberFormatPlatform.localeSeparator)
    }

    @Test
    fun `formatting honours an explicit separator`() {
        assertContains(NumberFormatPlatform.format(NumberFormat.DECIMAL_1, 12.3, separator = '.'), '.')
    }

    @Test
    fun `the dot separator constant is a dot`() {
        assertEquals('.', NumberFormatPlatform.SEPARATOR_DOT)
    }

    @Test
    fun `the lock can be taken and released more than once`() {
        val lock = AapsLock()

        // Once would only show it does not crash. Twice shows unlock really releases, which is the
        // part a wrong `actual` gets wrong: the second lock would block forever.
        repeat(2) {
            lock.lock()
            lock.unlock()
        }
    }
}

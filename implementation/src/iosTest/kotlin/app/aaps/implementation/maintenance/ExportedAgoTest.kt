package app.aaps.implementation.maintenance

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * The decisions behind the export date, without any formatting.
 *
 * The wording itself belongs to the system formatter and changes with the device language, so what
 * is worth pinning is which form gets picked and that a damaged timestamp does not take the screen
 * down.
 */
class ExportedAgoTest {

    private val exported = Instant.parse("2026-09-01T12:00:00.000Z")

    @Test
    fun `a timestamp written by the exporter is understood`() {
        assertEquals(exported, ExportedAgo.parse("2026-09-01T12:00:00.000Z"))
    }

    @Test
    fun `text that is not a timestamp reads as null instead of throwing`() {
        assertNull(ExportedAgo.parse("not a date"))
        assertNull(ExportedAgo.parse(""))
        assertNull(ExportedAgo.parse("2026-09-01"))
    }

    @Test
    fun `an export from minutes ago reads as relative`() {
        assertTrue(ExportedAgo.isRelative(exported, exported + 5.minutes))
    }

    @Test
    fun `an export from this moment reads as relative`() {
        assertTrue(ExportedAgo.isRelative(exported, exported))
    }

    @Test
    fun `an export from hours and from days ago both read as relative`() {
        assertTrue(ExportedAgo.isRelative(exported, exported + 3.hours))
        assertTrue(ExportedAgo.isRelative(exported, exported + 30.days))
    }

    @Test
    fun `the window ends at sixty days - the same age Android switches over at`() {
        assertEquals(60.days, ExportedAgo.relativeWindow)
        assertTrue(ExportedAgo.isRelative(exported, exported + 60.days - 1.minutes))
        assertFalse(ExportedAgo.isRelative(exported, exported + 60.days))
        assertFalse(ExportedAgo.isRelative(exported, exported + 365.days))
    }

    @Test
    fun `an export dated in the future reads as a date so no one is told it happens later`() {
        assertFalse(ExportedAgo.isRelative(exported, exported - 1.minutes))
        assertFalse(ExportedAgo.isRelative(exported, exported - 10.days))
    }

    @Test
    fun `the date part is the day the export was written`() {
        assertEquals("2026-09-01", ExportedAgo.datePart("2026-09-01T12:00:00.000Z"))
    }

    @Test
    fun `a truncated timestamp is shown whole rather than cut further`() {
        assertEquals("2026-09", ExportedAgo.datePart("2026-09"))
        assertEquals("", ExportedAgo.datePart(""))
    }
}

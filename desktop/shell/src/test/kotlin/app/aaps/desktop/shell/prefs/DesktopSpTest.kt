package app.aaps.desktop.shell.prefs

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * The part worth pinning is that a write never leaves the file damaged.
 *
 * `persist()` used to open the real file, which truncates it, and only then write into it. A crash,
 * a kill or a full disk in between left an empty or half-written file, and there is no second copy -
 * the next start reads it, finds nothing, and every setting on that machine is back to its default.
 * The write now goes to a sibling file and is moved over the real one, so the old content survives
 * until the new content is complete.
 */
class DesktopSpTest {

    @Test fun `a value survives a reload`(@TempDir dir: File) {
        val file = File(dir, "preferences.properties")
        DesktopSp(file).putString("units", "mmol")

        assertEquals("mmol", DesktopSp(file).getString("units", "mgdl"))
    }

    @Test fun `a write that fails leaves the previous content untouched`(@TempDir dir: File) {
        val file = File(dir, "preferences.properties")
        val sp = DesktopSp(file)
        sp.putString("units", "mmol")
        val afterGoodWrite = file.readBytes()

        // Block the sibling the write goes to, by putting a directory in its place: a directory
        // cannot be opened as a file, so persist() fails BEFORE it touches the real file. This is
        // deliberately white box - it names the ".new" sibling - because the mechanism is the point.
        // Under the old code this test fails: that version opened the real file directly, which
        // truncates it, so "mgdl" would be on disk and the bytes would differ.
        File(dir, "preferences.properties.new").mkdirs()
        runCatching { sp.putString("units", "mgdl") }

        assertArrayEqualsMessage(afterGoodWrite, file.readBytes())
        assertEquals("mmol", DesktopSp(file).getString("units", "x"))
    }

    @Test fun `no temporary file is left behind after a successful write`(@TempDir dir: File) {
        val file = File(dir, "preferences.properties")
        DesktopSp(file).putString("units", "mmol")

        assertTrue(file.isFile)
        assertFalse(File(dir, "preferences.properties.new").exists())
    }

    @Test fun `edit writes once and keeps every key`(@TempDir dir: File) {
        val file = File(dir, "preferences.properties")
        DesktopSp(file).edit {
            putString("units", "mmol")
            putInt("dia", 5)
            putBoolean("closed", true)
        }

        val reloaded = DesktopSp(file)
        assertEquals("mmol", reloaded.getString("units", ""))
        assertEquals(5, reloaded.getInt("dia", 0))
        assertEquals(true, reloaded.getBoolean("closed", false))
    }

    @Test fun `a value that is not the type asked for falls back to the default, it does not become zero`(@TempDir dir: File) {
        val file = File(dir, "preferences.properties")
        DesktopSp(file).putString("dia", "not a number")

        assertEquals(5, DesktopSp(file).getInt("dia", 5))
    }

    private fun assertArrayEqualsMessage(expected: ByteArray, actual: ByteArray) {
        assertEquals(
            expected.toString(Charsets.ISO_8859_1),
            actual.toString(Charsets.ISO_8859_1),
            "the preferences file changed even though the write that followed it failed"
        )
    }
}

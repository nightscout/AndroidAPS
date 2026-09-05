package app.aaps.implementation.maintenance

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The real file access behind an export, against real files.
 *
 * Everything else in this feature tests against a fake store, which is right for the format and the
 * rules but leaves this class - the one that actually touches the disk - proven only by someone
 * running the app. A bug here breaks export on a real phone while every other test stays green, so
 * these use a real directory and real bytes.
 *
 * A directory of its own per run, removed afterwards, so nothing is read from or left in the user's
 * own export folder.
 */
@OptIn(ExperimentalForeignApi::class)
class IosPrefsFileAccessTest {

    private val manager = NSFileManager.defaultManager
    private val directory = NSTemporaryDirectory() + "aaps-export-test-" + NSUUID().UUIDString()

    private val sut = IosPrefsFileAccess(directory)

    init {
        manager.createDirectoryAtPath(directory, withIntermediateDirectories = true, attributes = null, error = null)
    }

    @AfterTest
    fun removeDirectory() {
        manager.removeItemAtPath(directory, null)
    }

    @Test
    fun `what is written can be read back`() {
        sut.write("export.json", """{"format":"aaps_encrypted"}""")

        assertEquals(listOf("export.json" to """{"format":"aaps_encrypted"}"""), sut.list())
    }

    /** An export is a few kilobytes of base64, not a short string. */
    @Test
    fun `a whole export survives the round trip`() {
        val contents = """{"content":"${"A".repeat(8000)}"}"""

        sut.write("big.json", contents)

        assertEquals(contents, sut.list().single().second)
    }

    /** Settings can carry any language, so the file has to be written as UTF-8 and read back as UTF-8. */
    @Test
    fun `non ascii content survives the round trip`() {
        val contents = """{"note":"hešlo→🙂 Ω"}"""

        sut.write("unicode.json", contents)

        assertEquals(contents, sut.list().single().second)
    }

    @Test
    fun `several exports are all listed`() {
        sut.write("one.json", "{}")
        sut.write("two.json", "{}")

        assertEquals(setOf("one.json", "two.json"), sut.list().map { it.first }.toSet())
    }

    /** The directory is the user's, and it holds other things. Only json is ours to offer. */
    @Test
    fun `files that are not json are ignored`() {
        sut.write("export.json", "{}")
        manager.createFileAtPath("$directory/aaps.log", contents = null, attributes = null)
        manager.createFileAtPath("$directory/notes.txt", contents = null, attributes = null)

        assertEquals(listOf("export.json"), sut.list().map { it.first })
    }

    @Test
    fun `an empty directory lists nothing`() {
        assertTrue(sut.list().isEmpty())
    }

    /** A directory that is not there is an empty list, never a crash on a screen the user opened. */
    @Test
    fun `a directory that does not exist lists nothing`() {
        val missing = IosPrefsFileAccess(NSTemporaryDirectory() + "not-there-" + NSUUID().UUIDString())

        assertTrue(missing.list().isEmpty())
    }

    @Test
    fun `no directory at all lists nothing`() {
        assertTrue(IosPrefsFileAccess(null).list().isEmpty())
    }

    /** Exporting twice must replace, not append or fail. */
    @Test
    fun `writing the same name again replaces it`() {
        sut.write("export.json", "first")
        sut.write("export.json", "second")

        assertEquals("second", sut.list().single().second)
    }

    /**
     * The shape Android writes, because the two are sorted and compared together. Pinned as a
     * pattern rather than a fixed string, since the time is the time.
     */
    @Test
    fun `the export name has the shape Android uses`() {
        val name = sut.newExportName("full")

        assertTrue(Regex("""\d{4}-\d{2}-\d{2}_\d{6}_full\.json""").matches(name), "unexpected name: $name")
    }

    /**
     * Western digits and a Gregorian year, whatever the phone is set to. An `NSDateFormatter` follows
     * the user's calendar unless told otherwise, so on a Thai or Arabic phone this would otherwise be
     * a name no other AAPS build can sort next to its own.
     */
    @Test
    fun `the export name is not affected by the phone's calendar`() {
        val year = sut.newExportName("full").take(4).toIntOrNull()

        assertTrue(year != null && year in 2020..2100, "not a Gregorian year: ${sut.newExportName("full")}")
    }

    @Test
    fun `the flavour is part of the name`() {
        assertTrue(sut.newExportName("aapsclient").endsWith("_aapsclient.json"))
    }
}

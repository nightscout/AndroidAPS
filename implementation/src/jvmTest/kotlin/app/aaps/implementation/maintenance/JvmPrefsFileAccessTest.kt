package app.aaps.implementation.maintenance

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The desktop half of an export: a folder, three operations.
 *
 * Everything above this - the format, the crypto, the metadata - is shared and already covered by
 * `PrefsFormatCodecTest` and `PrefsTransferTest`, which run on this same target. What is left to
 * check is only that a file written here comes back, that the listing ignores what is not ours, and
 * that a name is in the shape the other platforms write.
 *
 * A temporary directory of its own, so the test neither reads the developer's real exports nor
 * leaves files behind for the next run to list.
 */
class JvmPrefsFileAccessTest {

    private val directory: File = Files.createTempDirectory("aaps-export-test").toFile()
    private val sut = JvmPrefsFileAccess(directory)

    @AfterTest
    fun cleanUp() {
        directory.deleteRecursively()
    }

    @Test
    fun `what was written comes back`() {
        sut.write("backup.json", """{"format":"aaps_encrypted"}""")

        assertEquals(listOf("backup.json" to """{"format":"aaps_encrypted"}"""), sut.list())
    }

    /** The folder is the user's, so anything else in it must not be offered as a backup. */
    @Test
    fun `only json files are listed`() {
        sut.write("backup.json", "{}")
        File(directory, "notes.txt").writeText("shopping list")
        File(directory, "subfolder").mkdirs()

        assertEquals(listOf("backup.json"), sut.list().map { it.first })
    }

    /** A folder nobody has exported into yet is empty, not an error. */
    @Test
    fun `a missing directory lists nothing`() {
        val absent = JvmPrefsFileAccess(File(directory, "not-created-yet"))

        assertEquals(emptyList(), absent.list())
    }

    /** Writing creates the folder, so a fresh install does not have to be told to make it first. */
    @Test
    fun `writing creates the directory`() {
        val fresh = File(directory, "made-on-demand")
        val access = JvmPrefsFileAccess(fresh)

        access.write("backup.json", "{}")

        assertTrue(fresh.isDirectory)
        assertEquals(listOf("backup.json"), access.list().map { it.first })
    }

    /**
     * The same shape iOS and Android write: `2026-09-05_143022_full.json`.
     *
     * Sorted and compared against files written by a phone, so the timestamp is fixed-width and the
     * flavour is on the end.
     */
    @Test
    fun `an export name carries the timestamp and the flavour`() {
        val name = sut.newExportName("full")

        assertTrue(name.endsWith("_full.json"), name)
        assertTrue(Regex("""\d{4}-\d{2}-\d{2}_\d{6}_full\.json""").matches(name), name)
    }
}

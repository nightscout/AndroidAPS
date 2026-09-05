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

    /** Given explicitly, or a CSV in a test would be written into the real `~/AAPS/exports`. */
    private val csvDirectory: File = Files.createTempDirectory("aaps-csv-test").toFile()
    private val sut = JvmPrefsFileAccess(directory, csvDirectory)

    @AfterTest
    fun cleanUp() {
        directory.deleteRecursively()
        csvDirectory.deleteRecursively()
    }

    /**
     * Settings and the user-entry CSV go to different folders, because a phone puts them in
     * different folders: `newPreferenceFile` creates in `AAPS/preferences`, `newExportCsvFile` in
     * `AAPS/exports`. A desktop that pooled them would not be a mirror of a phone, and the listing
     * below would start offering a CSV as a backup to restore from.
     */
    @Test
    fun `the csv goes to the exports folder and the settings do not`() {
        sut.write("2026-09-05_143022_full.json", "{}")
        sut.write("2026-09-05_143022_UserEntry.csv", "date,action")

        assertEquals(listOf("2026-09-05_143022_full.json"), directory.list()?.toList())
        assertEquals(listOf("2026-09-05_143022_UserEntry.csv"), csvDirectory.list()?.toList())
    }

    /** The two names the writer generates have to land on the right side of that split. */
    @Test
    fun `the generated names route the way they are meant to`() {
        sut.write(sut.newExportName("full"), "{}")
        sut.write(sut.newCsvName(), "date,action")

        assertEquals(1, directory.list()?.size)
        assertEquals(1, csvDirectory.list()?.size)
        assertTrue(directory.list()!!.single().endsWith("_full.json"))
        assertTrue(csvDirectory.list()!!.single().endsWith("_UserEntry.csv"))
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

    /** `2026-09-05_143022_UserEntry.csv`, the name Android's `newExportCsvFile` writes. */
    @Test
    fun `a csv name matches the one Android writes`() {
        val name = sut.newCsvName()

        assertTrue(Regex("""\d{4}-\d{2}-\d{2}_\d{6}_UserEntry\.csv""").matches(name), name)
    }

    /**
     * A CSV is written, and is never offered as a backup.
     *
     * `list()` feeds the import screen, so a user-entry dump appearing there would be offered as a
     * backup and then refused when they picked it. It lands in the exports folder rather than beside
     * the settings, which is the same split a phone makes.
     */
    @Test
    fun `a csv is written but never offered as a backup`() {
        sut.write(sut.newCsvName(), "date,action\n2026-09-05,BOLUS")
        sut.write("backup.json", "{}")

        assertEquals(listOf("backup.json"), sut.list().map { it.first })
        assertTrue(csvDirectory.listFiles()!!.any { it.name.endsWith("_UserEntry.csv") })
    }
}

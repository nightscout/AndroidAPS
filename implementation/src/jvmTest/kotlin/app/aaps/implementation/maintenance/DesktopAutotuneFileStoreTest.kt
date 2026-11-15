package app.aaps.implementation.maintenance

import app.aaps.core.interfaces.autotune.AutotuneFolder
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipFile
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The desktop's Autotune folders, and through them the `java.io` store Android uses too.
 *
 * A temporary folder that does not exist yet, as `~/AAPS/autotune` does not on a desktop that never
 * ran Autotune, so the test neither writes into the developer's own folder nor leaves files behind.
 */
class DesktopAutotuneFileStoreTest {

    private val root: File = Files.createTempDirectory("aaps-autotune-test").toFile()
    private val sut = DesktopAutotuneFileStore(File(root, "AAPS/autotune"))

    @AfterTest
    fun cleanUp() {
        root.deleteRecursively()
    }

    @Test
    fun `a folder is made once`() {
        assertTrue(sut.createFolder(AutotuneFolder.RUN))
        assertFalse(sut.createFolder(AutotuneFolder.RUN))
        assertTrue(File(root, "AAPS/autotune/autotune").isDirectory)
    }

    @Test
    fun `a file holds the text and a line break`() {
        sut.createFolder(AutotuneFolder.SETTINGS)
        assertTrue(sut.write(AutotuneFolder.SETTINGS, "settings.json", "{\"a\":1}"))
        assertEquals("{\"a\":1}" + System.lineSeparator(), File(root, "AAPS/autotune/settings/settings.json").readText())
    }

    @Test
    fun `writing into a folder that was never made fails rather than throws`() {
        assertFalse(sut.write(AutotuneFolder.RUN, "x.json", "{}"))
    }

    @Test
    fun `deleting leaves folders inside`() {
        sut.createFolder(AutotuneFolder.RUN)
        sut.write(AutotuneFolder.RUN, "old.json", "{}")
        File(root, "AAPS/autotune/autotune/inner").mkdir()
        sut.deleteFiles(AutotuneFolder.RUN)
        assertEquals(listOf("inner"), File(root, "AAPS/autotune/autotune").list()?.toList())
    }

    @Test
    fun `the zip holds both folders under their names`() {
        AutotuneFolder.entries.forEach { sut.createFolder(it) }
        sut.write(AutotuneFolder.RUN, "aaps-entries.2026-11-15.json", "[]")
        sut.write(AutotuneFolder.SETTINGS, "settings.json", "{}")

        assertTrue(sut.zip("autotune_2026-11-15_10-30-00.zip"))

        ZipFile(File(root, "AAPS/autotune/autotune_2026-11-15_10-30-00.zip")).use { zip ->
            val entries = zip.entries().toList().associate { it.name to zip.getInputStream(it).readBytes().decodeToString() }
            assertEquals(setOf("autotune/aaps-entries.2026-11-15.json", "settings/settings.json"), entries.keys)
            assertEquals("[]" + System.lineSeparator(), entries["autotune/aaps-entries.2026-11-15.json"])
        }
    }

    @Test
    fun `the folder is under the visible root`() {
        assertEquals(File(System.getProperty("user.home"), "AAPS/autotune").path, DesktopFolders.autotune.path)
    }
}

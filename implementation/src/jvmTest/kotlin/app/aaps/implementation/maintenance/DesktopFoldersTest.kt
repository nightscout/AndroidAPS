package app.aaps.implementation.maintenance

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * That a desktop lays its AAPS folder out the way a phone does.
 *
 * These names are matched across a module boundary and by hand. Android builds them in
 * `FileListProviderImpl` - `preferences`, `exports`, `extra` under `Documents/AAPS` - and a desktop
 * has to arrive at the same relative shape or a backup carried between the two lands somewhere the
 * other does not look.
 *
 * It has already gone wrong once in each direction. Settings were written to `~/AAPS/exports` while
 * the import screen listed `~/.aaps/exports`, a folder that never existed, so every export was
 * invisible to the app that wrote it; and option markers were read from `~/.aaps` while Android
 * reads them from `AAPS/extra`, so `engineering_mode` could be created exactly as documented and do
 * nothing. Neither showed a symptom that pointed at a path.
 */
class DesktopFoldersTest {

    private val home = File(System.getProperty("user.home") ?: ".")

    @Test
    fun `the root is the visible AAPS folder, not the data directory`() {
        assertEquals(File(home, "AAPS"), DesktopFolders.root)
    }

    /** The three names Android's FileListProviderImpl creates under its own AAPS folder. */
    @Test
    fun `the subfolders are named the way Android names them`() {
        assertEquals("preferences", DesktopFolders.preferences.name)
        assertEquals("exports", DesktopFolders.exports.name)
        assertEquals("extra", DesktopFolders.extra.name)
    }

    @Test
    fun `every subfolder sits under the root`() {
        listOf(DesktopFolders.preferences, DesktopFolders.exports, DesktopFolders.extra).forEach {
            assertEquals(DesktopFolders.root, it.parentFile, "${it.name} should be inside the AAPS folder")
        }
    }

    /**
     * The data directory is a different place and must stay one.
     *
     * `~/.aaps` holds the database, the keys and the log. Nothing the user is asked to create by
     * hand, and nothing they are asked to find, belongs in it.
     */
    @Test
    fun `the AAPS folder is not the hidden data directory`() {
        assertEquals("AAPS", DesktopFolders.root.name)
        assertTrue(!DesktopFolders.root.name.startsWith("."), "the visible folder must not be the .aaps data directory")
    }
}

package app.aaps.implementation.maintenance

import java.io.File
import kotlin.test.AfterTest
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

    @AfterTest
    fun resetClient() {
        DesktopFolders.client = 1
    }

    /**
     * Each client gets its own data directory, the way each Android client is a separate app with
     * its own private storage. Sharing one would mean two clients on one database - which is the
     * thing the single-instance lock inside this folder exists to stop, and that lock is only
     * correct if a *different* client is still allowed to run.
     */
    @Test
    fun `each client keeps its own data directory`() {
        DesktopFolders.client = 1
        val first = DesktopFolders.data
        DesktopFolders.client = 2
        val second = DesktopFolders.data
        DesktopFolders.client = 3
        val third = DesktopFolders.data

        assertEquals(3, setOf(first, second, third).size, "three clients must not share a directory")
    }

    /** Client 1 keeps the plain name, so an install that already exists is not moved. */
    @Test
    fun `client one is the unsuffixed directory`() {
        DesktopFolders.client = 1

        assertEquals(File(home, ".aaps"), DesktopFolders.data)
    }

    @Test
    fun `the other clients are numbered`() {
        DesktopFolders.client = 2
        assertEquals(File(home, ".aaps2"), DesktopFolders.data)
        DesktopFolders.client = 3
        assertEquals(File(home, ".aaps3"), DesktopFolders.data)
    }

    /**
     * The shared half stays shared, whichever client is running.
     *
     * Exports and option markers are the user's, not the app's, and a phone shares them between its
     * clients too. Making these per client would hide one client's backup from another.
     */
    @Test
    fun `the AAPS folder is shared by every client`() {
        DesktopFolders.client = 1
        val one = listOf(DesktopFolders.root, DesktopFolders.preferences, DesktopFolders.exports, DesktopFolders.extra)
        DesktopFolders.client = 3
        val three = listOf(DesktopFolders.root, DesktopFolders.preferences, DesktopFolders.exports, DesktopFolders.extra)

        assertEquals(one, three, "the user-visible folder must not depend on which client is running")
    }

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

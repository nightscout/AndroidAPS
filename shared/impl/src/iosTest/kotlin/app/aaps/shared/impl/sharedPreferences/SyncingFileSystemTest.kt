package app.aaps.shared.impl.sharedPreferences

import okio.FileSystem
import okio.IOException
import okio.Path
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs

class SyncingFileSystemTest {

    private val fs = FileSystem.SYSTEM
    private val dir: Path = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "aaps-sync-test-${Random.nextLong().toULong()}"
    private val target: Path = dir / "preferences.store"

    @AfterTest fun cleanUp() {
        fs.deleteRecursively(dir)
    }

    @Test fun `the store uses the syncing file system on iOS`() {
        assertIs<SyncingFileSystem>(storeFileSystem)
    }

    @Test fun `a moved file keeps its content`() {
        fs.createDirectories(dir)
        val source = dir / "preferences.store.tmp"
        fs.write(source) { writeUtf8("new") }
        fs.write(target) { writeUtf8("old") }

        SyncingFileSystem(fs).atomicMove(source, target)

        assertEquals("new", fs.read(target) { readUtf8() })
        assertFalse(fs.exists(source))
    }

    @Test fun `a file that cannot be synced is not moved`() {
        fs.createDirectories(dir)
        fs.write(target) { writeUtf8("old") }

        assertFailsWith<IOException> { SyncingFileSystem(fs).atomicMove(dir / "missing.tmp", target) }

        assertEquals("old", fs.read(target) { readUtf8() })
    }
}

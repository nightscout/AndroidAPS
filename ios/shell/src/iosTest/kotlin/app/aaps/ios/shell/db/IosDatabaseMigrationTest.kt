package app.aaps.ios.shell.db

import app.aaps.database.di.IosAppDatabaseBuilder
import app.aaps.database.entities.GlucoseValue
import app.aaps.database.transactions.CgmSourceTransaction
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.test.runTest
import platform.Foundation.NSFileManager
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Moving the database out of Documents, once, without ever losing it.
 *
 * The move itself is not the risk. The risk is the half of it that fails: `Room.databaseBuilder`
 * makes no existence check, so a build that decides the database is at a new path when it is not
 * creates an empty one and shows the user a fresh install with their history gone and no error. So
 * these do not only check that the file arrives - they check what happens when it does not, which is
 * the case that has to keep working on the old path instead.
 *
 * `moveFile` is injected rather than the filesystem being made to refuse, because a test that has to
 * chmod a directory to fail leaves the simulator broken for the next one when it fails half way.
 */
@OptIn(ExperimentalForeignApi::class)
class IosDatabaseMigrationTest {

    private val manager = NSFileManager.defaultManager
    private val names = mutableListOf<String>()

    private fun builder(
        moveFile: (String, String) -> Boolean = { from, to -> manager.moveItemAtPath(from, toPath = to, error = null) }
    ) = IosAppDatabaseBuilder(log = { _, _ -> }, moveFile = moveFile)

    private fun name(name: String): String = name.also { names += it }

    @AfterTest
    fun removeDatabases() {
        names.forEach { IosAppDatabaseBuilder().deleteDatabase(it) }
    }

    private fun exists(path: String) = manager.fileExistsAtPath(path)

    /** Puts an empty file where a build from before the move would have left one. */
    private fun writeLegacy(fileName: String, vararg suffixes: String) {
        val legacy = IosAppDatabaseBuilder().documentsPath(fileName)
        suffixes.forEach { suffix -> manager.createFileAtPath(legacy + suffix, contents = null, attributes = null) }
    }

    private fun reading(timestamp: Long, value: Double) = GlucoseValue(
        timestamp = timestamp,
        raw = null,
        value = value,
        trendArrow = GlucoseValue.TrendArrow.FLAT,
        noise = null,
        sourceSensor = GlucoseValue.SourceSensor.UNKNOWN
    )

    @Test
    fun `a database left in Documents is moved to Application Support`() {
        val file = name("test-move-basic.db")
        writeLegacy(file, "")
        val sut = builder()

        val resolved = sut.resolveDatabasePath(file)

        assertEquals(sut.applicationSupportPath(file), resolved)
        assertTrue(exists(sut.applicationSupportPath(file)))
        assertFalse(exists(sut.documentsPath(file)))
    }

    /** A `.db` without its `-wal` is not the database, so the journal files travel with it. */
    @Test
    fun `the journal files move with the database`() {
        val file = name("test-move-journal.db")
        writeLegacy(file, "", "-wal", "-shm")
        val sut = builder()

        sut.resolveDatabasePath(file)

        listOf("", "-wal", "-shm").forEach { suffix ->
            assertTrue(exists(sut.applicationSupportPath(file) + suffix), "$suffix should have moved")
            assertFalse(exists(sut.documentsPath(file) + suffix), "$suffix should be gone from Documents")
        }
    }

    /** A cleanly closed database has no `-wal` beside it. That is normal and not a failed move. */
    @Test
    fun `a database with no journal files still moves`() {
        val file = name("test-move-nojournal.db")
        writeLegacy(file, "")
        val sut = builder()

        assertEquals(sut.applicationSupportPath(file), sut.resolveDatabasePath(file))
    }

    @Test
    fun `a fresh install opens in Application Support`() {
        val file = name("test-move-fresh.db")
        val sut = builder()

        assertEquals(sut.applicationSupportPath(file), sut.resolveDatabasePath(file))
    }

    /**
     * The failure this whole design exists for. When the move does not happen the old file is still
     * the only copy of the user's history, so the app has to go on using it.
     */
    @Test
    fun `a move that fails keeps the database in Documents`() {
        val file = name("test-move-refused.db")
        writeLegacy(file, "")
        val sut = builder(moveFile = { _, _ -> false })

        val resolved = sut.resolveDatabasePath(file)

        assertEquals(sut.documentsPath(file), resolved)
        assertTrue(exists(sut.documentsPath(file)), "the database must still be where it was")
        assertFalse(exists(sut.applicationSupportPath(file)))
    }

    /**
     * A move that gets the `.db` across and then fails on the `-wal` must not leave the set split
     * over two directories, because the half in Documents is the half that would then be opened.
     */
    @Test
    fun `a move that fails part way puts back what it moved`() {
        val file = name("test-move-partial.db")
        writeLegacy(file, "", "-wal")
        // Refuse only the -wal going out. The rollback that follows moves the .db the other way and
        // is allowed to work, which is what happens when the destination is what refused the file.
        val sut = builder(moveFile = { from, to ->
            if (to.endsWith("-wal")) false else manager.moveItemAtPath(from, toPath = to, error = null)
        })

        val resolved = sut.resolveDatabasePath(file)

        assertEquals(sut.documentsPath(file), resolved)
        assertTrue(exists(sut.documentsPath(file)), "the database must be put back")
        assertTrue(exists(sut.documentsPath(file) + "-wal"), "the journal never left")
        assertFalse(exists(sut.applicationSupportPath(file)), "nothing may be left at the new path")
    }

    /** Both present means the move already ran. The new file is the real one and is not written over. */
    @Test
    fun `a database already moved is not moved again`() {
        val file = name("test-move-twice.db")
        writeLegacy(file, "")
        val sut = builder()
        sut.resolveDatabasePath(file)
        // A leftover appears in Documents again - a stale backup, say.
        writeLegacy(file, "")

        val resolved = sut.resolveDatabasePath(file)

        assertEquals(sut.applicationSupportPath(file), resolved)
        assertTrue(exists(sut.applicationSupportPath(file)))
    }


    /**
     * The worst case: the move fails and so does putting it back, so the `.db` is stranded at the
     * new path. The old path is now an empty directory, and answering with it would be the silent
     * empty database this whole design exists to prevent - so the answer follows the file.
     */
    @Test
    fun `a database stranded by a failed rollback is still found`() {
        val file = name("test-move-stranded.db")
        writeLegacy(file, "", "-wal")
        var moves = 0
        val sut = builder(moveFile = { from, to ->
            moves++
            // First the .db goes across, then the -wal is refused, then the rollback is refused too.
            if (moves == 1) manager.moveItemAtPath(from, toPath = to, error = null) else false
        })

        val resolved = sut.resolveDatabasePath(file)

        assertEquals(sut.applicationSupportPath(file), resolved, "the answer must follow the database")
        assertTrue(exists(sut.applicationSupportPath(file)))
        assertFalse(exists(sut.documentsPath(file)), "the .db really is gone from the old path")
    }


    /**
     * The lock file travels too. It holds no data, but Documents is about to become visible in the
     * Files app for the settings export, and a stray `aaps-ios.db.lck` there is just confusing.
     */
    @Test
    fun `the lock file is taken along`() {
        val file = name("test-move-lock.db")
        writeLegacy(file, "", ".lck")
        val sut = builder()

        sut.resolveDatabasePath(file)

        assertTrue(exists(sut.applicationSupportPath(file) + ".lck"))
        assertFalse(exists(sut.documentsPath(file) + ".lck"))
    }

    /** A lock file that will not move is not a reason to leave the database where it was. */
    @Test
    fun `a lock file that cannot move does not fail the migration`() {
        val file = name("test-move-lockstuck.db")
        writeLegacy(file, "", ".lck")
        val sut = builder(moveFile = { from, to ->
            if (to.endsWith(".lck")) false else manager.moveItemAtPath(from, toPath = to, error = null)
        })

        val resolved = sut.resolveDatabasePath(file)

        assertEquals(sut.applicationSupportPath(file), resolved)
        assertTrue(exists(sut.applicationSupportPath(file)), "the database still moved")
    }

    /**
     * The one that matters: a real database with real rows, written where the shipped build put it,
     * still answers queries after the move. Everything above moves empty files around and would pass
     * just as well if the file that arrived were unusable.
     */
    @Test
    fun `real data survives the move out of Documents`() = runTest {
        val file = name("test-move-realdata.db")
        val sut = builder()

        // Write a database, then put it where a build from before the move kept it.
        IosAppDatabaseBuilder().provideAppRepository(file).use { repo ->
            repo.runTransactionSuspend(CgmSourceTransaction(listOf(reading(7_000L, 88.0)), emptyList(), null))
        }
        listOf("", "-wal", "-shm").forEach { suffix ->
            val from = sut.applicationSupportPath(file) + suffix
            if (exists(from)) manager.moveItemAtPath(from, toPath = sut.documentsPath(file) + suffix, error = null)
        }
        assertTrue(exists(sut.documentsPath(file)), "the test needs the database in the old place to begin")

        // A launch of the new build finds it there and moves it.
        IosAppDatabaseBuilder().provideAppRepository(file).use { reopened ->
            assertEquals(88.0, reopened.getLastGlucoseValue()?.value)
        }

        assertTrue(exists(sut.applicationSupportPath(file)), "it should have been moved")
        assertFalse(exists(sut.documentsPath(file)), "and not left behind")
    }
}

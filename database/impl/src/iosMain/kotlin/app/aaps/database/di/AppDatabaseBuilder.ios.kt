package app.aaps.database.di

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import app.aaps.database.AppDatabase
import app.aaps.database.AppRepository
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask

/**
 * Opens the AAPS database on iOS.
 *
 * The counterpart of the Android builder, and the only part of the database that genuinely has to
 * differ: Android names a database and lets the framework place it, while iOS has to be told a path.
 * Everything past that point - the driver, the indexes, the entities and the DAOs - is the same
 * code on both.
 *
 * ## Why the file is not in Documents
 *
 * It used to be, and that was never a decision - it was settled while fixing a CI failure where the
 * directory did not exist on a clean runner. Documents is the wrong home, because it is one build
 * setting away from being the user's to edit: `UIFileSharingEnabled` and
 * `LSSupportsOpeningDocumentsInPlace` expose it in the Files app, and the settings export feature
 * needs exactly those. The moment they are set, a user tidying up their files can delete the
 * database.
 *
 * That would be quiet rather than loud. `Room.databaseBuilder` performs no existence check: pointed
 * at a path with no file it creates a new, empty, schema-correct one. The user would see a fresh
 * install with their whole treatment history gone and no error to explain it.
 *
 * `NSApplicationSupportDirectory` is app-private and still included in the device and iCloud backup,
 * which is how an iOS user carries their history to a new phone. Nothing here excludes it from that
 * backup on purpose - the backup is a feature, not the hazard. `NSCachesDirectory` would be wrong
 * for the opposite reason: iOS evicts it under storage pressure, so the app would come back empty on
 * its own.
 *
 * ## No migrations here, on purpose
 *
 * The Android builder passes fifteen `Migration` objects. They are absent here because there is no
 * older database on iOS to come from: nothing imports an Android database, so the first file this
 * creates is created at the current schema version.
 *
 * That covers arriving on iOS. It does not cover staying: once an iOS build reaches a user, their
 * database sits at whatever version shipped, and the next schema change needs a migration path for
 * them like any other. At that point the migration list has to move to commonMain and be passed
 * here too, rather than be copied, because two histories drift and a schema that differs by
 * platform corrupts data instead of failing loudly.
 *
 * @param log where the file move reports itself. The first argument says whether it failed, so a
 *   caller with a real logger can raise the failure and leave the ordinary case at debug. The
 *   default prints, because this module cannot see `AAPSLogger`.
 * @param moveFile moves one file and says whether it arrived. Injectable so a test can make the move
 *   fail without having to make the filesystem refuse it.
 */
class IosAppDatabaseBuilder(
    private val log: (failed: Boolean, message: String) -> Unit = { _, message -> println(message) },
    private val moveFile: (from: String, to: String) -> Boolean = ::moveWithFileManager
) {

    /** Builds a repository over the database file, moving it out of Documents first if it is there. */
    fun provideAppRepository(fileName: String): AppRepository =
        AppRepository { provideAppDatabase(fileName) }

    internal fun provideAppDatabase(fileName: String): AppDatabase =
        Room
            .databaseBuilder<AppDatabase>(name = resolveDatabasePath(fileName))
            // Same driver as Android: SQLite compiled from source, so the engine matches on both
            // platforms rather than following whatever the OS happens to ship.
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .addCallback(object : RoomDatabase.Callback() {
                override fun onOpen(connection: SQLiteConnection) {
                    super.onOpen(connection)
                    createCustomIndexes(connection)
                }
            })
            .fallbackToDestructiveMigration(false)
            .build()

    /**
     * The path the database will actually be opened at, moving it out of Documents if it is there.
     *
     * The order of the checks is the whole safety of this. A database is only read from the new
     * place once it is known to be there; if the move does not happen, the old path is returned and
     * the app keeps running on the file it already had. Failing the other way - returning the new
     * path after a failed move - is what would hand Room an empty directory and silently start the
     * user again from nothing.
     */
    fun resolveDatabasePath(fileName: String): String {
        val manager = NSFileManager.defaultManager
        val target = applicationSupportPath(fileName)
        val legacy = documentsPath(fileName)

        // Nothing in Documents: either a fresh install or a move that already happened.
        if (!manager.fileExistsAtPath(legacy)) return target
        // Both exist. The move already ran and left something behind; the new file is the real one,
        // so do not touch it and do not move over it.
        if (manager.fileExistsAtPath(target)) return target

        moveDatabase(legacy, target)

        // Decided on what is on disk, not on what the move reported. A rollback can fail too - the
        // filesystem that refused the move is the one being asked to undo it - and then the database
        // is at the new path even though the move "failed". Trusting the return value there would
        // send Room to the old path, find nothing, and quietly create an empty database.
        return if (manager.fileExistsAtPath(legacy)) legacy else target
    }

    /**
     * Moves the database and the journal files that belong to it, or moves nothing.
     *
     * A `.db` separated from its `-wal` is not the database, so a failure part way through puts back
     * what it already moved rather than leaving the set split across two directories. It moves
     * rather than copies for the same reason: a copy could run twice and leave two histories to
     * drift apart.
     *
     * The caller does not take the answer on trust - see [resolveDatabasePath]. This reports what it
     * meant to do, which is what belongs in the log; where the file actually ended up is a separate
     * question and is asked of the filesystem.
     */
    private fun moveDatabase(legacy: String, target: String): Boolean {
        val manager = NSFileManager.defaultManager
        val moved = mutableListOf<Pair<String, String>>()

        SUFFIXES.forEach { suffix ->
            val from = legacy + suffix
            // A cleanly closed database has no -wal or -shm beside it. That is normal, not a failure.
            if (!manager.fileExistsAtPath(from)) return@forEach

            val to = target + suffix
            if (moveFile(from, to)) {
                moved += to to from
            } else {
                moved.forEach { (movedTo, movedFrom) -> moveFile(movedTo, movedFrom) }
                log(true, "$TAG could not move $from to $to. Keeping the database in Documents for this launch.")
                return false
            }
        }

        // The lock file is not part of the data and is remade on demand, so it moves last and its
        // failure is not the migration's failure. It moves at all only so that nothing is left
        // behind in a directory the settings export is about to make visible in the Files app.
        INCIDENTAL_SUFFIXES.forEach { suffix ->
            val from = legacy + suffix
            if (manager.fileExistsAtPath(from)) moveFile(from, target + suffix)
        }

        log(false, "$TAG moved the database out of Documents into Application Support")
        return true
    }

    /** Where the database lives now. */
    fun applicationSupportPath(fileName: String): String = pathIn(NSApplicationSupportDirectory, fileName)

    /** Where the database used to live, and where a build from before the move still has it. */
    fun documentsPath(fileName: String): String = pathIn(NSDocumentDirectory, fileName)

    /**
     * Removes a database file and the journal files that sit beside it, in both directories.
     *
     * Only meant for tests, which need each case to start from nothing. It clears the old directory
     * as well as the new one, or a test that put a file in Documents would leave it there to be
     * migrated into the next test.
     */
    @OptIn(ExperimentalForeignApi::class)
    fun deleteDatabase(fileName: String) {
        val manager = NSFileManager.defaultManager
        listOf(applicationSupportPath(fileName), documentsPath(fileName)).forEach { base ->
            (SUFFIXES + INCIDENTAL_SUFFIXES).forEach { suffix -> manager.removeItemAtPath(base + suffix, null) }
        }
    }

    /**
     * The same computed indexes the Android builder creates.
     *
     * Room cannot declare an index over an expression, so both platforms add these by hand. They
     * belong in commonMain next to the migrations for the same reason.
     */
    private fun createCustomIndexes(connection: SQLiteConnection) {
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_temporaryBasals_end` ON `temporaryBasals` (`timestamp` + `duration`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_extendedBoluses_end` ON `extendedBoluses` (`timestamp` + `duration`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_temporaryTargets_end` ON `temporaryTargets` (`timestamp` + `duration`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_carbs_end` ON `carbs` (`timestamp` + `duration`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_runningModes_end` ON `runningModes` (`timestamp` + `duration`)")
    }

    /**
     * A path inside one of the app's own directories, creating the directory if it is not there yet.
     *
     * `URLsForDirectory` answers where the directory *would* be; it does not make it. Documents
     * always exists in a real app because iOS creates the container, but Application Support does
     * not - iOS leaves that one to the app - and a bare test binary has neither. SQLite cannot
     * create a file in a directory that does not exist, which is what used to fail the database
     * tests on a clean CI runner with an `IllegalStateException` from Room that named nothing useful.
     */
    @OptIn(ExperimentalForeignApi::class)
    private fun pathIn(directory: NSSearchPathDirectory, fileName: String): String {
        val manager = NSFileManager.defaultManager
        val base = manager.URLsForDirectory(directory = directory, inDomains = NSUserDomainMask).first() as NSURL
        base.path?.let { path ->
            if (!manager.fileExistsAtPath(path)) {
                manager.createDirectoryAtPath(path, withIntermediateDirectories = true, attributes = null, error = null)
            }
        }
        return requireNotNull(base.URLByAppendingPathComponent(fileName)?.path) {
            "could not place the database inside $base"
        }
    }

    private companion object {

        private const val TAG = "IosAppDatabaseBuilder:"

        /** The database and the two files SQLite keeps beside it. The order is the order they move in. */
        private val SUFFIXES = listOf("", "-wal", "-shm")

        /** Files that sit beside the database but carry none of its data. */
        private val INCIDENTAL_SUFFIXES = listOf(".lck")
    }
}

/** The real move. Separate so the class can take a different one in a test. */
@OptIn(ExperimentalForeignApi::class)
private fun moveWithFileManager(from: String, to: String): Boolean =
    NSFileManager.defaultManager.moveItemAtPath(from, toPath = to, error = null)

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
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
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
 */
class IosAppDatabaseBuilder {

    /** Builds a repository over a database file inside the app's Documents directory. */
    @OptIn(ExperimentalForeignApi::class)
    fun provideAppRepository(fileName: String): AppRepository =
        AppRepository { provideAppDatabase(fileName) }

    @OptIn(ExperimentalForeignApi::class)
    internal fun provideAppDatabase(fileName: String): AppDatabase =
        Room
            .databaseBuilder<AppDatabase>(name = documentsPath(fileName))
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

    @OptIn(ExperimentalForeignApi::class)
    private fun documentsPath(fileName: String): String {
        val documents = NSFileManager.defaultManager.URLsForDirectory(
            directory = NSDocumentDirectory,
            inDomains = NSUserDomainMask
        ).first() as NSURL
        return requireNotNull(documents.URLByAppendingPathComponent(fileName)?.path) {
            "could not place the database inside $documents"
        }
    }
}

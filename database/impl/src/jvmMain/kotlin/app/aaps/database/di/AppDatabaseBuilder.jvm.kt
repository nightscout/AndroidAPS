package app.aaps.database.di

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import app.aaps.database.AppDatabase
import app.aaps.database.AppRepository
import kotlinx.coroutines.Dispatchers
import java.io.File

/**
 * Opens the AAPS database on desktop.
 *
 * The counterpart of the Android and Apple builders, and the only part of the database that has to
 * differ: Android names a database and lets the framework place it, while desktop has to be told a
 * path. Everything past that point - the driver, the indexes, the entities and the DAOs - is the
 * same code on all three.
 *
 * ## Where the file goes
 *
 * Wherever the caller says. The desktop shell passes a path inside its own data directory, which is
 * one per client - so two clients never open one database. This module used to build that path
 * itself, which meant the database was the single piece of per-client state that could not follow
 * the client.
 *
 * The shell currently picks a dot folder under the user's home, the plainest thing that works the
 * same on Windows, macOS and Linux. A per-OS location - `%APPDATA%`, `~/Library/Application Support`
 * - would be more idiomatic, and is worth changing to before the desktop build reaches anyone,
 * because moving a database after people have data in it is the expensive kind of change. That
 * decision now lives with the shell, where it belongs.
 *
 * ## No migrations here, on purpose
 *
 * The Android builder passes fifteen `Migration` objects. They are absent here because there is no
 * older database on desktop to come from: nothing imports an Android database, so the first file
 * this creates is created at the current schema version.
 *
 * That covers arriving on desktop. It does not cover staying: once a desktop build reaches a user,
 * their database sits at whatever version shipped, and the next schema change needs a migration path
 * for them like any other. At that point the migration list has to move to commonMain and be passed
 * here too, rather than be copied, because two histories drift and a schema that differs by platform
 * corrupts data instead of failing loudly.
 */
class JvmAppDatabaseBuilder {

    /** Builds a repository over the database file at [fileName], which is a full path. */
    fun provideAppRepository(fileName: String): AppRepository =
        AppRepository { provideAppDatabase(fileName) }

    internal fun provideAppDatabase(fileName: String): AppDatabase =
        Room
            .databaseBuilder<AppDatabase>(name = databasePath(fileName))
            // Same driver as Android and Apple: SQLite compiled from source, so the engine matches on
            // every platform rather than following whatever the OS happens to ship.
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
     * Removes a database file and the journal files that sit beside it.
     *
     * Only meant for tests, which need each case to start from nothing. Room writes `-wal` and
     * `-shm` next to the database, and leaving those behind would carry state into the next test.
     */
    fun deleteDatabase(fileName: String) {
        listOf("", "-wal", "-shm").forEach { suffix -> File(databasePath(fileName) + suffix).delete() }
    }

    /**
     * The same computed indexes the other builders create.
     *
     * Room cannot declare an index over an expression, so every platform adds these by hand. They
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
     * Takes the path it is given, and only makes sure the folder exists.
     *
     * This used to build `~/.aaps/<name>` itself, which put the desktop's folder layout in a module
     * that knows nothing about it - and made the database the one piece of per-client state that
     * could not follow the client. The shell decides where its data lives; a database module should
     * not have an opinion about a user's home directory.
     */
    private fun databasePath(path: String): String {
        val file = File(path)
        file.absoluteFile.parentFile?.mkdirs()
        return file.absolutePath
    }
}

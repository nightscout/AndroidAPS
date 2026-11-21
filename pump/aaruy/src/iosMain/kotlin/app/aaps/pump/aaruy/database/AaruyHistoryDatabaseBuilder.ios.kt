package app.aaps.pump.aaruy.database

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask

/**
 * Opens the pump log on iOS, in Application Support: app-private and in the backup, as the AAPS database (see
 * `IosAppDatabaseBuilder` in `:database:impl` for why not Documents). The same driver and migrations as on Android.
 */
internal fun buildAaruyHistoryDatabase(): AaruyHistoryDatabase =
    Room.databaseBuilder<AaruyHistoryDatabase>(name = applicationSupportPath(AARUY_DATABASE_FILE))
        .setDriver(BundledSQLiteDriver())
        .addMigrations(*AaruyHistoryDatabase.migrations)
        .fallbackToDestructiveMigration(dropAllTables = true)
        .build()

@OptIn(ExperimentalForeignApi::class)
private fun applicationSupportPath(fileName: String): String {
    val manager = NSFileManager.defaultManager
    val base = manager.URLsForDirectory(directory = NSApplicationSupportDirectory, inDomains = NSUserDomainMask).first() as NSURL
    base.path?.let { path ->
        if (!manager.fileExistsAtPath(path)) manager.createDirectoryAtPath(path, withIntermediateDirectories = true, attributes = null, error = null)
    }
    return requireNotNull(base.URLByAppendingPathComponent(fileName)?.path) { "could not place the pump log inside $base" }
}

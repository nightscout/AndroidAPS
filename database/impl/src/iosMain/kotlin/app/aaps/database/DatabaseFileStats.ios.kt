package app.aaps.database

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSFileSystemFreeSize

@kotlinx.cinterop.ExperimentalForeignApi
internal actual fun databaseFileStats(path: String): DatabaseFileStats {
    val manager = NSFileManager.defaultManager

    fun sizeOf(of: String): Long =
        (manager.attributesOfItemAtPath(of, null)?.get(NSFileSize) as? Number)?.toLong() ?: 0L

    val free = (manager.attributesOfFileSystemForPath(path, null)?.get(NSFileSystemFreeSize) as? Number)?.toLong() ?: -1L

    return DatabaseFileStats(
        name = path.substringAfterLast('/'),
        sizeBytes = sizeOf(path) + sizeOf("$path-wal") + sizeOf("$path-shm"),
        freeBytes = free
    )
}

// Dispatchers.IO does exist on Native - it is only absent from the common API, which is why this is
// expect/actual at all. Same dispatcher as Android, so behaviour matches.
internal actual val databaseDispatcher: CoroutineDispatcher = Dispatchers.IO

// Room has no clearAllTables outside Android, and no iOS surface asks for it yet. Fail loudly rather
// than silently doing nothing - this empties the user's whole history when it does run.
internal actual suspend fun AppDatabase.clearAllTablesCompat(): Unit =
    throw UnsupportedOperationException("Clearing the database is not implemented on this platform")

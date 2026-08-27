package app.aaps.database

import android.os.StatFs
import java.io.File

internal actual fun databaseFileStats(path: String): DatabaseFileStats {
    val dbFile = File(path)
    return DatabaseFileStats(
        name = dbFile.name,
        // The write ahead log and the shared memory file count towards what the database occupies.
        sizeBytes = dbFile.length() + File("$path-wal").length() + File("$path-shm").length(),
        freeBytes = dbFile.parent?.let { runCatching { StatFs(it).availableBytes }.getOrDefault(-1L) } ?: -1L
    )
}

internal actual val databaseDispatcher: kotlinx.coroutines.CoroutineDispatcher = kotlinx.coroutines.Dispatchers.IO

internal actual suspend fun AppDatabase.clearAllTablesCompat() = clearAllTables()

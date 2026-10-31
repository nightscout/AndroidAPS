package app.aaps.database

import java.io.File

/**
 * Desktop reads free space from the file system directly. Android needs `StatFs` because a plain
 * `File.usableSpace` is unreliable on scoped storage; off Android there is no such restriction.
 */
internal actual fun databaseFileStats(path: String): DatabaseFileStats {
    val dbFile = File(path)
    return DatabaseFileStats(
        name = dbFile.name,
        // The write ahead log and the shared memory file count towards what the database occupies.
        sizeBytes = dbFile.length() + File("$path-wal").length() + File("$path-shm").length(),
        freeBytes = dbFile.parentFile?.let { runCatching { it.usableSpace }.getOrDefault(-1L) } ?: -1L
    )
}

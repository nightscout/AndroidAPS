package app.aaps.database

/**
 * Size of the database on disk and the space left beside it, for the maintenance readout.
 *
 * @param name the file name on its own, without the directory
 * @param sizeBytes the database plus its `-wal` and `-shm` companions, or 0 when they cannot be read
 * @param freeBytes free space on the volume holding it, or -1 when the platform will not say
 */
internal data class DatabaseFileStats(val name: String, val sizeBytes: Long, val freeBytes: Long)

/**
 * Measures the database file at [path].
 *
 * The only part of the repository that has to know about a file system, which is why it is the only
 * part that is `expect`. It feeds a diagnostic string, so a platform that cannot answer returns
 * unknowns rather than failing - the maintenance screen must never be the thing that breaks.
 */
internal expect fun databaseFileStats(path: String): DatabaseFileStats

/**
 * The dispatcher the repository emits its change notifications on.
 *
 * `Dispatchers.IO` does not exist outside the JVM, and the Android behaviour should not change just
 * because the class moved, so each platform names its own.
 */
internal expect val databaseDispatcher: kotlinx.coroutines.CoroutineDispatcher

/**
 * Empties every table.
 *
 * `RoomDatabase.clearAllTables()` is Android and JVM only - it is absent from the iOS klib - so the
 * one caller (the maintenance "reset databases" action) reaches it through here.
 */
internal expect suspend fun AppDatabase.clearAllTablesCompat()

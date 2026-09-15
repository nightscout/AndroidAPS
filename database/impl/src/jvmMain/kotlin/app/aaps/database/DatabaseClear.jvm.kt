package app.aaps.database

// Room only generates clearAllTables for Android, so desktop clears the tables with SQL instead. The
// work is shared with the Apple targets - see clearAllTablesBySql.
internal actual suspend fun AppDatabase.clearAllTablesCompat() = clearAllTablesBySql()

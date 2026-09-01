package app.aaps.database

// Room has no clearAllTables outside Android, and no desktop surface asks for it yet. Fail loudly
// rather than silently doing nothing - this empties the user's whole history when it does run. Same
// answer as the Apple targets.
internal actual suspend fun AppDatabase.clearAllTablesCompat(): Unit =
    throw UnsupportedOperationException("Clearing the database is not implemented on this platform")

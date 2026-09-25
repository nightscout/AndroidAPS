package app.aaps.database

import androidx.room.Transactor.SQLiteTransactionType
import androidx.room.useWriterConnection

/**
 * Empties every user table with plain SQL, for the targets where Room has no `clearAllTables`.
 *
 * Room only generates `clearAllTables()` for Android, so iOS and desktop have to do the same work
 * themselves. This is that work, written once in common code rather than twice, and it follows what
 * Room does on Android step for step so a reset means the same thing on every platform:
 *
 * - Tables come from `sqlite_master`, not from a hand written list. A list would silently stop
 *   clearing a table the day one is added, and the leftover rows would only show up much later as a
 *   history that survived a reset.
 * - `PRAGMA defer_foreign_keys` inside the transaction, so the order the tables come back in cannot
 *   fail a foreign key check half way through. One transaction, so a failure leaves the database
 *   untouched instead of half empty.
 * - `sqlite_sequence` is left alone, which is also what Room does. It holds the next auto generated
 *   id, and keeping it means ids are never handed out twice. Nightscout records point at those ids,
 *   so restarting them at 1 would make a fresh record collide with an old sync entry.
 *
 * The checkpoint at the end returns the freed pages to the main file. Without it the deletions sit
 * in the write ahead log and the database on disk does not shrink at all, which is half of what
 * somebody resetting it is asking for.
 */
internal suspend fun AppDatabase.clearAllTablesBySql() {
    useWriterConnection { connection ->
        val tables = mutableListOf<String>()
        connection.usePrepared(
            "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name NOT LIKE 'room_%' AND name != 'android_metadata'"
        ) { statement -> while (statement.step()) tables.add(statement.getText(0)) }

        connection.withTransaction(SQLiteTransactionType.IMMEDIATE) {
            usePrepared("PRAGMA defer_foreign_keys = TRUE") { it.step() }
            for (table in tables) usePrepared("DELETE FROM `$table`") { it.step() }
        }

        connection.usePrepared("PRAGMA wal_checkpoint(TRUNCATE)") { it.step() }
    }
}

package app.aaps.database.daos.workaround

import androidx.room.Transaction
import app.aaps.database.daos.TraceableDao
import app.aaps.database.daos.insertNewEntryImpl
import app.aaps.database.daos.updateExistingEntryImpl
import app.aaps.database.entities.interfaces.TraceableDBEntry

interface TraceableDaoWorkaround<T : TraceableDBEntry> {

    /**
     * Inserts a new entry
     *
     * @return The ID of the newly generated entry
     */
    @Transaction
    fun insertNewEntry(entry: T): Long =
        (this as TraceableDao<T>).insertNewEntryImpl(entry)

    /**
     * Updates an existing entry
     *
     * @return The ID of the newly generated HISTORIC entry
     */
    @Transaction
    fun updateExistingEntry(entry: T): Long =
        (this as TraceableDao<T>).updateExistingEntryImpl(entry)
}

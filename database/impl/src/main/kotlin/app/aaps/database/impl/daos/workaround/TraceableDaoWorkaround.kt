package app.aaps.database.impl.daos.workaround

import androidx.room.Transaction
import app.aaps.database.entities.interfaces.TraceableDBEntry
import app.aaps.database.impl.daos.TraceableDao
import app.aaps.database.impl.daos.insertNewEntryImpl
import app.aaps.database.impl.daos.updateExistingEntryImpl

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

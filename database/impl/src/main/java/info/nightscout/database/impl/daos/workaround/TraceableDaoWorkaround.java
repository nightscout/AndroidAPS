package info.nightscout.database.impl.daos.workaround;

import androidx.room.Transaction;

import app.aaps.database.entities.interfaces.TraceableDBEntry;
import info.nightscout.database.impl.daos.TraceableDao;
import info.nightscout.database.impl.daos.TraceableDaoKt;

// keep in java, it's easier
public interface TraceableDaoWorkaround<T extends TraceableDBEntry> {

    /**
     * Inserts a new entry
     *
     * @return The ID of the newly generated entry
     */
    @Transaction
    default long insertNewEntry(T entry) {
        return TraceableDaoKt.insertNewEntryImpl((TraceableDao<T>) this, entry);
    }

    /**
     * Updates an existing entry
     *
     * @return The ID of the newly generated HISTORIC entry
     */
    @Transaction
    default long updateExistingEntry(T entry) {
        return TraceableDaoKt.updateExistingEntryImpl((TraceableDao<T>) this, entry);
    }

}

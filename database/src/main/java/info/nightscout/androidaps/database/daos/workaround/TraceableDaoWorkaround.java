package info.nightscout.androidaps.database.daos.workaround;

import androidx.room.Transaction;

import info.nightscout.androidaps.database.daos.TraceableDao;
import info.nightscout.androidaps.database.daos.TraceableDaoKt;
import info.nightscout.androidaps.database.interfaces.TraceableDBEntry;

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

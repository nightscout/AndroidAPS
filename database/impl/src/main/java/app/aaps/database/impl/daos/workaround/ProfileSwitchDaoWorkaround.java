package app.aaps.database.impl.daos.workaround;

import androidx.room.Transaction;

import app.aaps.database.entities.ProfileSwitch;
import app.aaps.database.impl.daos.ProfileSwitchDao;
import app.aaps.database.impl.daos.ProfileSwitchDaoKt;
import app.aaps.database.impl.daos.TraceableDao;

public interface ProfileSwitchDaoWorkaround extends TraceableDao<ProfileSwitch> {

    @Override
    @Transaction
    default long insertNewEntry(ProfileSwitch entry) {
        return ProfileSwitchDaoKt.insertNewEntryImpl((ProfileSwitchDao) this, entry);
    }

    @Override
    @Transaction
    default long updateExistingEntry(ProfileSwitch entry) {
        return ProfileSwitchDaoKt.updateExistingEntryImpl((ProfileSwitchDao) this, entry);
    }
}

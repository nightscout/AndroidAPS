package info.nightscout.database.impl.daos.workaround;

import androidx.room.Transaction;

import app.aaps.database.entities.ProfileSwitch;
import info.nightscout.database.impl.daos.ProfileSwitchDao;
import info.nightscout.database.impl.daos.ProfileSwitchDaoKt;
import info.nightscout.database.impl.daos.TraceableDao;

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

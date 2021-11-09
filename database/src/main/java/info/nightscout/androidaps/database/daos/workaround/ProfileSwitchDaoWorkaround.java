package info.nightscout.androidaps.database.daos.workaround;

import androidx.room.Transaction;

import info.nightscout.androidaps.database.daos.ProfileSwitchDao;
import info.nightscout.androidaps.database.daos.ProfileSwitchDaoKt;
import info.nightscout.androidaps.database.daos.TraceableDao;
import info.nightscout.androidaps.database.entities.ProfileSwitch;

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

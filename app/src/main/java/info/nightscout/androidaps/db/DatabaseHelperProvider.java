package info.nightscout.androidaps.db;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.j256.ormlite.dao.CloseableIterator;

import java.sql.SQLException;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.interfaces.DatabaseHelperInterface;

@Deprecated
@Singleton
public class DatabaseHelperProvider implements DatabaseHelperInterface {

    @Inject DatabaseHelperProvider() {
    }

    @Override public void createOrUpdate(@NonNull OmnipodHistoryRecord record) {
        MainApp.Companion.getDbHelper().createOrUpdate(record);
    }

    @Override public boolean createOrUpdate(@NonNull TemporaryBasal tempBasal) {
//        return MainApp.Companion.getDbHelper().createOrUpdate(tempBasal);
        return false;
    }

    @Nullable @Override public TemporaryBasal findTempBasalByPumpId(long id) {
//        return MainApp.Companion.getDbHelper().findTempBasalByPumpId(id);
        return null;
    }

    @Deprecated
    @NonNull @Override public List<TemporaryBasal> getTemporaryBasalsDataFromTime(long mills, boolean ascending) {
//        return MainApp.Companion.getDbHelper().getTemporaryBasalsDataFromTime(mills, ascending);
        return null;
    }

    @NonNull @Override public List<OmnipodHistoryRecord> getAllOmnipodHistoryRecordsFromTimestamp(long timestamp, boolean ascending) {
        return MainApp.Companion.getDbHelper().getAllOmnipodHistoryRecordsFromTimeStamp(timestamp, ascending);
    }

    @Nullable @Override public OmnipodHistoryRecord findOmnipodHistoryRecordByPumpId(long pumpId) {
        return MainApp.Companion.getDbHelper().findOmnipodHistoryRecordByPumpId(pumpId);
    }

    @Override public void delete(@NonNull ExtendedBolus extendedBolus) {
//        MainApp.Companion.getDbHelper().delete(extendedBolus);
    }

    @Nullable @Override public ExtendedBolus getExtendedBolusByPumpId(long pumpId) {
//        return MainApp.Companion.getDbHelper().getExtendedBolusByPumpId(pumpId);
        return null;
    }

    @Override public void resetDatabases() {
        MainApp.Companion.getDbHelper().resetDatabases();
    }

    @Override public void createOrUpdate(@NonNull OHQueueItem record) {
        MainApp.Companion.getDbHelper().createOrUpdate(record);
    }

    @NonNull @Override public List<OHQueueItem> getAllOHQueueItems(long maxEntries) {
        return MainApp.Companion.getDbHelper().getAllOHQueueItems(maxEntries);
    }

    @Override public long getOHQueueSize() {
        return MainApp.Companion.getDbHelper().getOHQueueSize();
    }

    @Override public void clearOpenHumansQueue() {
        MainApp.Companion.getDbHelper().clearOpenHumansQueue();
    }

    @Override public void removeAllOHQueueItemsWithIdSmallerThan(long id) {
        MainApp.Companion.getDbHelper().removeAllOHQueueItemsWithIdSmallerThan(id);
    }
}

package info.nightscout.androidaps.db;

import com.j256.ormlite.dao.CloseableIterator;

import androidx.annotation.NonNull;
import org.jetbrains.annotations.Nullable;

import java.sql.SQLException;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.interfaces.DatabaseHelperInterface;

@Singleton
public class DatabaseHelperProvider implements DatabaseHelperInterface {

    @Inject DatabaseHelperProvider() {
    }

    @Override public void createOrUpdate(@NonNull CareportalEvent careportalEvent) {
        MainApp.getDbHelper().createOrUpdate(careportalEvent);
    }

    @Override public void createOrUpdate(@NonNull DanaRHistoryRecord record) {
        MainApp.getDbHelper().createOrUpdate(record);
    }

    @Override public void createOrUpdate(@NonNull OmnipodHistoryRecord record) {
        MainApp.getDbHelper().createOrUpdate(record);
    }

    @NonNull @Override public List<DanaRHistoryRecord> getDanaRHistoryRecordsByType(byte type) {
        return MainApp.getDbHelper().getDanaRHistoryRecordsByType(type);
    }

    @NonNull @Override public List<TDD> getTDDs() {
        return MainApp.getDbHelper().getTDDs();
    }

    @Override public long size(@NonNull String table) {
        return MainApp.getDbHelper().size(table);
    }

    @Override public void create(@NonNull DbRequest record) {
        try {
            MainApp.getDbHelper().create(record);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override public void deleteAllDbRequests() {
        MainApp.getDbHelper().deleteAllDbRequests();
    }

    @Override public int deleteDbRequest(@NonNull String id) {
        return MainApp.getDbHelper().deleteDbRequest(id);
    }

    @Override public void deleteDbRequestbyMongoId(@NonNull String action, @NonNull String _id) {
        MainApp.getDbHelper().deleteDbRequestbyMongoId(action, _id);
    }

    @NonNull @Override public CloseableIterator<DbRequest> getDbRequestInterator() {
        return MainApp.getDbHelper().getDbRequestInterator();
    }

    @Override public long roundDateToSec(long date) {
        return MainApp.getDbHelper().roundDateToSec(date);
    }

    @Override public void createOrUpdateTDD(@NonNull TDD record) {
        MainApp.getDbHelper().createOrUpdateTDD(record);
    }

    @Override public void createOrUpdate(@NonNull TemporaryBasal tempBasal) {
        MainApp.getDbHelper().createOrUpdate(tempBasal);
    }

    @NonNull @Override public TemporaryBasal findTempBasalByPumpId(long id) {
        return MainApp.getDbHelper().findTempBasalByPumpId(id);
    }

    @NonNull @Override public List<TemporaryBasal> getTemporaryBasalsDataFromTime(long mills, boolean ascending) {
        return MainApp.getDbHelper().getTemporaryBasalsDataFromTime(mills, ascending);
    }

    @Override public CareportalEvent getCareportalEventFromTimestamp(long timestamp) {
        return MainApp.getDbHelper().getCareportalEventFromTimestamp(timestamp);
    }

    @NonNull @Override public List<OmnipodHistoryRecord> getAllOmnipodHistoryRecordsFromTimestamp(long timestamp, boolean ascending) {
        return MainApp.getDbHelper().getAllOmnipodHistoryRecordsFromTimeStamp(timestamp, ascending);
    }

    @Nullable @Override public OmnipodHistoryRecord findOmnipodHistoryRecordByPumpId(long pumpId) {
        return MainApp.getDbHelper().findOmnipodHistoryRecordByPumpId(pumpId);
    }

    @NonNull @Override public List<TDD> getTDDsForLastXDays(int days) {
        return MainApp.getDbHelper().getTDDsForLastXDays(days);
    }

    @NonNull @Override public List<ProfileSwitch> getProfileSwitchData(long from, boolean ascending) {
        return MainApp.getDbHelper().getProfileSwitchData(from, ascending);
    }

}

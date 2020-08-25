package info.nightscout.androidaps.db;

import com.j256.ormlite.dao.CloseableIterator;

import org.jetbrains.annotations.NotNull;

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

    @NotNull @Override public List<BgReading> getAllBgreadingsDataFromTime(long mills, boolean ascending) {
        return MainApp.getDbHelper().getAllBgreadingsDataFromTime(mills, ascending);
    }

    @Override public void createOrUpdate(@NotNull CareportalEvent careportalEvent) {
        MainApp.getDbHelper().createOrUpdate(careportalEvent);
    }

    @Override public void createOrUpdate(@NotNull DanaRHistoryRecord record) {
        MainApp.getDbHelper().createOrUpdate(record);
    }

    @Override public void createOrUpdate(@NotNull OmnipodHistoryRecord record) {
        MainApp.getDbHelper().createOrUpdate(record);
    }

    @NotNull @Override public List<DanaRHistoryRecord> getDanaRHistoryRecordsByType(byte type) {
        return MainApp.getDbHelper().getDanaRHistoryRecordsByType(type);
    }

    @NotNull @Override public List<TDD> getTDDs() {
        return MainApp.getDbHelper().getTDDs();
    }

    @Override public long size(@NotNull String table) {
        return MainApp.getDbHelper().size(table);
    }

    @Override public void create(@NotNull DbRequest record) {
        try {
            MainApp.getDbHelper().create(record);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override public void deleteAllDbRequests() {
        MainApp.getDbHelper().deleteAllDbRequests();
    }

    @Override public int deleteDbRequest(@NotNull String id) {
        return MainApp.getDbHelper().deleteDbRequest(id);
    }

    @Override public void deleteDbRequestbyMongoId(@NotNull String action, @NotNull String _id) {
        MainApp.getDbHelper().deleteDbRequestbyMongoId(action, _id);
    }

    @NotNull @Override public CloseableIterator<DbRequest> getDbRequestInterator() {
        return MainApp.getDbHelper().getDbRequestInterator();
    }

    @Override public long roundDateToSec(long date) {
        return MainApp.getDbHelper().roundDateToSec(date);
    }

    @Override public void createOrUpdateTDD(@NotNull TDD record) {
        MainApp.getDbHelper().createOrUpdateTDD(record);
    }

    @Override public void createOrUpdate(@NotNull TemporaryBasal tempBasal) {
        MainApp.getDbHelper().createOrUpdate(tempBasal);
    }

    @NotNull @Override public TemporaryBasal findTempBasalByPumpId(long id) {
        return MainApp.getDbHelper().findTempBasalByPumpId(id);
    }

    @NotNull @Override public List<TemporaryBasal> getTemporaryBasalsDataFromTime(long mills, boolean ascending) {
        return MainApp.getDbHelper().getTemporaryBasalsDataFromTime(mills, ascending);
    }

    @Override public CareportalEvent getCareportalEventFromTimestamp(long timestamp) {
        return MainApp.getDbHelper().getCareportalEventFromTimestamp(timestamp);
    }

    @NotNull @Override public List<OmnipodHistoryRecord> getAllOmnipodHistoryRecordsFromTimestamp(long timestamp, boolean ascending) {
        return MainApp.getDbHelper().getAllOmnipodHistoryRecordsFromTimeStamp(timestamp, ascending);
    }

    @NotNull @Override public List<TDD> getTDDsForLastXDays(int days) {
        return MainApp.getDbHelper().getTDDsForLastXDays(days);
    }

    @NotNull @Override public List<ProfileSwitch> getProfileSwitchData(long from, boolean ascending) {
        return MainApp.getDbHelper().getProfileSwitchData(from, ascending);
    }

}

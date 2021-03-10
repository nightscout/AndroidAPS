package info.nightscout.androidaps.db;

import androidx.annotation.NonNull;

import com.j256.ormlite.dao.CloseableIterator;

import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;

import java.sql.SQLException;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.interfaces.ActivePluginProvider;
import info.nightscout.androidaps.interfaces.DatabaseHelperInterface;
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload;

@Singleton
public class DatabaseHelperProvider implements DatabaseHelperInterface {

    @Inject DatabaseHelperProvider() {
    }

    @Override public void createOrUpdate(@NonNull DanaRHistoryRecord record) {
        MainApp.Companion.getDbHelper().createOrUpdate(record);
    }

    @Override public void createOrUpdate(@NonNull OmnipodHistoryRecord record) {
        MainApp.Companion.getDbHelper().createOrUpdate(record);
    }

    @NonNull @Override public List<DanaRHistoryRecord> getDanaRHistoryRecordsByType(byte type) {
        return MainApp.Companion.getDbHelper().getDanaRHistoryRecordsByType(type);
    }

    @NonNull @Override public List<TDD> getTDDs() {
        return MainApp.Companion.getDbHelper().getTDDs();
    }

    @Override public long size(@NonNull String table) {
        return MainApp.Companion.getDbHelper().size(table);
    }

    @Override public void create(@NonNull DbRequest record) {
        try {
            MainApp.Companion.getDbHelper().create(record);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override public void deleteAllDbRequests() {
        MainApp.Companion.getDbHelper().deleteAllDbRequests();
    }

    @Override public int deleteDbRequest(@NonNull String id) {
        return MainApp.Companion.getDbHelper().deleteDbRequest(id);
    }

    @Override public void deleteDbRequestbyMongoId(@NonNull String action, @NonNull String _id) {
        MainApp.Companion.getDbHelper().deleteDbRequestbyMongoId(action, _id);
    }

    @NonNull @Override public CloseableIterator<DbRequest> getDbRequestIterator() {
        return MainApp.Companion.getDbHelper().getDbRequestIterator();
    }

    @Override public long roundDateToSec(long date) {
        return MainApp.Companion.getDbHelper().roundDateToSec(date);
    }

    @Override public void createOrUpdateTDD(@NonNull TDD record) {
        MainApp.Companion.getDbHelper().createOrUpdateTDD(record);
    }

    @Override public boolean createOrUpdate(@NonNull TemporaryBasal tempBasal) {
        return MainApp.Companion.getDbHelper().createOrUpdate(tempBasal);
    }

    @NonNull @Override public TemporaryBasal findTempBasalByPumpId(long id) {
        return MainApp.Companion.getDbHelper().findTempBasalByPumpId(id);
    }

    @NonNull @Override public List<TemporaryBasal> getTemporaryBasalsDataFromTime(long mills, boolean ascending) {
        return MainApp.Companion.getDbHelper().getTemporaryBasalsDataFromTime(mills, ascending);
    }

    @NonNull @Override public List<OmnipodHistoryRecord> getAllOmnipodHistoryRecordsFromTimestamp(long timestamp, boolean ascending) {
        return MainApp.Companion.getDbHelper().getAllOmnipodHistoryRecordsFromTimeStamp(timestamp, ascending);
    }

    @Nullable @Override public OmnipodHistoryRecord findOmnipodHistoryRecordByPumpId(long pumpId) {
        return MainApp.Companion.getDbHelper().findOmnipodHistoryRecordByPumpId(pumpId);
    }

    @NonNull @Override public List<TDD> getTDDsForLastXDays(int days) {
        return MainApp.Companion.getDbHelper().getTDDsForLastXDays(days);
    }

    @NonNull @Override public List<ProfileSwitch> getProfileSwitchData(long from, boolean ascending) {
        return MainApp.Companion.getDbHelper().getProfileSwitchData(from, ascending);
    }

    @Override public void createOrUpdate(@NonNull InsightBolusID record) {
        MainApp.Companion.getDbHelper().createOrUpdate(record);
    }

    @Override public void createOrUpdate(@NonNull InsightPumpID record) {
        MainApp.Companion.getDbHelper().createOrUpdate(record);
    }

    @Override public void createOrUpdate(@NonNull InsightHistoryOffset record) {
        MainApp.Companion.getDbHelper().createOrUpdate(record);
    }

    @Override public void delete(@NonNull ExtendedBolus extendedBolus) {
        MainApp.Companion.getDbHelper().delete(extendedBolus);
    }

    @Nullable @Override public ExtendedBolus getExtendedBolusByPumpId(long pumpId) {
        return MainApp.Companion.getDbHelper().getExtendedBolusByPumpId(pumpId);
    }

    @Nullable @Override public InsightBolusID getInsightBolusID(@NonNull String pumpSerial, int bolusID, long timestamp) {
        return MainApp.Companion.getDbHelper().getInsightBolusID(pumpSerial, bolusID, timestamp);
    }

    @Nullable @Override public InsightHistoryOffset getInsightHistoryOffset(@NonNull String pumpSerial) {
        return MainApp.Companion.getDbHelper().getInsightHistoryOffset(pumpSerial);
    }

    @Nullable @Override public InsightPumpID getPumpStoppedEvent(@NonNull String pumpSerial, long before) {
        return MainApp.Companion.getDbHelper().getPumpStoppedEvent(pumpSerial, before);
    }

    @Override public boolean createOrUpdate(@NonNull ExtendedBolus extendedBolus) {
        return MainApp.Companion.getDbHelper().createOrUpdate(extendedBolus);
    }

    @Override public void createOrUpdate(@NonNull ProfileSwitch profileSwitch) {
        MainApp.Companion.getDbHelper().createOrUpdate(profileSwitch);
    }

    @Override public void delete(@NonNull TemporaryBasal tempBasal) {
        MainApp.Companion.getDbHelper().delete(tempBasal);
    }

    @NonNull @Override public List<ExtendedBolus> getExtendedBolusDataFromTime(long mills, boolean ascending) {
        return MainApp.Companion.getDbHelper().getExtendedBolusDataFromTime(mills, ascending);
    }

    @Override public void deleteTempBasalById(@NonNull String _id) {
        MainApp.Companion.getDbHelper().deleteTempBasalById(_id);
    }

    @Override public void deleteExtendedBolusById(@NonNull String _id) {
        MainApp.Companion.getDbHelper().deleteExtendedBolusById(_id);
    }

    @Override public void deleteProfileSwitchById(@NonNull String _id) {
        MainApp.Companion.getDbHelper().deleteProfileSwitchById(_id);
    }

    @Override public void createTempBasalFromJsonIfNotExists(@NonNull JSONObject json) {
        MainApp.Companion.getDbHelper().createTempBasalFromJsonIfNotExists(json);
    }

    @Override public void createExtendedBolusFromJsonIfNotExists(@NonNull JSONObject json) {
        MainApp.Companion.getDbHelper().createExtendedBolusFromJsonIfNotExists(json);
    }

    @Override public void createProfileSwitchFromJsonIfNotExists(@NonNull ActivePluginProvider activePluginProvider, @NonNull NSUpload nsUpload, @NonNull JSONObject trJson) {
        MainApp.Companion.getDbHelper().createProfileSwitchFromJsonIfNotExists(activePluginProvider, nsUpload, trJson);
    }

    @Override public void resetDatabases() {
        MainApp.Companion.getDbHelper().resetDatabases();
    }

    @Override public void createOrUpdate(@NonNull OHQueueItem record) {
        MainApp.Companion.getDbHelper().createOrUpdate(record);
    }

    @Override public void delete(@NonNull ProfileSwitch profileSwitch) {
        MainApp.Companion.getDbHelper().delete(profileSwitch);
    }

    @NonNull @Override public List<ProfileSwitch> getProfileSwitchEventsFromTime(long from, long to, boolean ascending) {
        return MainApp.Companion.getDbHelper().getProfileSwitchEventsFromTime(from, to, ascending);
    }

    @NonNull @Override public List<ProfileSwitch> getProfileSwitchEventsFromTime(long mills, boolean ascending) {
        return MainApp.Companion.getDbHelper().getProfileSwitchEventsFromTime(mills, ascending);
    }

    @NonNull @Override public List<ExtendedBolus> getAllExtendedBoluses() {
        return MainApp.Companion.getDbHelper().getAllExtendedBoluses();
    }

    @NonNull @Override public List<ProfileSwitch> getAllProfileSwitches() {
        return MainApp.Companion.getDbHelper().getAllProfileSwitches();
    }

    @NonNull @Override public List<TDD> getAllTDDs() {
        return MainApp.Companion.getDbHelper().getAllTDDs();
    }

    @NonNull @Override public List<TemporaryBasal> getAllTemporaryBasals() {
        return MainApp.Companion.getDbHelper().getAllTemporaryBasals();
    }

    @NonNull @Override public List<OHQueueItem> getAllOHQueueItems(long maxEntries) {
        return MainApp.Companion.getDbHelper().getAllOHQueueItems(maxEntries);
    }

    @Override public void resetProfileSwitch() {
        MainApp.Companion.getDbHelper().resetProfileSwitch();
    }

    @Override public long getOHQueueSize() {
        return MainApp.Companion.getDbHelper().getOHQueueSize();
    }

    @Override public void clearOpenHumansQueue() {
        MainApp.Companion.getDbHelper().clearOpenHumansQueue();
    }

    @Override public long getCountOfAllRows() {
        return MainApp.Companion.getDbHelper().getCountOfAllRows();
    }

    @Override public void removeAllOHQueueItemsWithIdSmallerThan(long id) {
        MainApp.Companion.getDbHelper().removeAllOHQueueItemsWithIdSmallerThan(id);
    }
}

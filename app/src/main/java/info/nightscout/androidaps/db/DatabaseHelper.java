package info.nightscout.androidaps.db;

import android.content.Context;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.support.annotation.Nullable;

import com.j256.ormlite.android.apptools.OrmLiteSqliteOpenHelper;
import com.j256.ormlite.dao.CloseableIterator;
import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.stmt.PreparedQuery;
import com.j256.ormlite.stmt.QueryBuilder;
import com.j256.ormlite.stmt.Where;
import com.j256.ormlite.support.ConnectionSource;
import com.j256.ormlite.table.TableUtils;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.events.EventExtendedBolusChange;
import info.nightscout.androidaps.events.EventNewBG;
import info.nightscout.androidaps.events.EventTempBasalChange;
import info.nightscout.androidaps.events.EventTreatmentChange;
import info.nightscout.androidaps.plugins.IobCobCalculator.events.EventNewHistoryData;
import info.nightscout.androidaps.plugins.NSClientInternal.data.NSProfile;
import info.nightscout.androidaps.plugins.PumpDanaR.History.DanaRNSHistorySync;
import info.nightscout.androidaps.plugins.TempTargetRange.events.EventTempTargetRangeChange;

public class DatabaseHelper extends OrmLiteSqliteOpenHelper {
    private static Logger log = LoggerFactory.getLogger(DatabaseHelper.class);

    public static final String DATABASE_NAME = "AndroidAPSDb";
    public static final String DATABASE_BGREADINGS = "BgReadings";
    public static final String DATABASE_TEMPORARYBASALS = "TemporaryBasals";
    public static final String DATABASE_EXTENDEDBOLUSES = "ExtendedBoluses";
    public static final String DATABASE_TEMPTARGETS = "TempTargets";
    public static final String DATABASE_TREATMENTS = "Treatments";
    public static final String DATABASE_DANARHISTORY = "DanaRHistory";
    public static final String DATABASE_DBREQUESTS = "DBRequests";

    private static final int DATABASE_VERSION = 7;

    private static Long latestTreatmentChange = null;

    private static final ScheduledExecutorService treatmentsWorker = Executors.newSingleThreadScheduledExecutor();
    private static ScheduledFuture<?> scheduledTratmentPost = null;

    private static final ScheduledExecutorService tempBasalsWorker = Executors.newSingleThreadScheduledExecutor();
    private static ScheduledFuture<?> scheduledTemBasalsPost = null;

    private static final ScheduledExecutorService extendedBolusWorker = Executors.newSingleThreadScheduledExecutor();
    private static ScheduledFuture<?> scheduledExtendedBolusPost = null;

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        onCreate(getWritableDatabase(), getConnectionSource());
    }

    @Override
    public void onCreate(SQLiteDatabase database, ConnectionSource connectionSource) {
        try {
            log.info("onCreate");
            TableUtils.createTableIfNotExists(connectionSource, TempTarget.class);
            TableUtils.createTableIfNotExists(connectionSource, Treatment.class);
            TableUtils.createTableIfNotExists(connectionSource, BgReading.class);
            TableUtils.createTableIfNotExists(connectionSource, DanaRHistoryRecord.class);
            TableUtils.createTableIfNotExists(connectionSource, DbRequest.class);
            TableUtils.createTableIfNotExists(connectionSource, TemporaryBasal.class);
            TableUtils.createTableIfNotExists(connectionSource, ExtendedBolus.class);
        } catch (SQLException e) {
            log.error("Can't create database", e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public void onUpgrade(SQLiteDatabase database, ConnectionSource connectionSource, int oldVersion, int newVersion) {
        try {
            log.info(DatabaseHelper.class.getName(), "onUpgrade");
            TableUtils.dropTable(connectionSource, TempTarget.class, true);
            TableUtils.dropTable(connectionSource, Treatment.class, true);
            TableUtils.dropTable(connectionSource, BgReading.class, true);
            TableUtils.dropTable(connectionSource, DanaRHistoryRecord.class, true);
            TableUtils.dropTable(connectionSource, DbRequest.class, true);
            TableUtils.dropTable(connectionSource, TemporaryBasal.class, true);
            TableUtils.dropTable(connectionSource, ExtendedBolus.class, true);
            onCreate(database, connectionSource);
        } catch (SQLException e) {
            log.error("Can't drop databases", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Close the database connections and clear any cached DAOs.
     */
    @Override
    public void close() {
        super.close();
    }

    public void cleanUpDatabases() {
        // TODO: call it somewhere
        log.debug("Before BgReadings size: " + DatabaseUtils.queryNumEntries(getReadableDatabase(), DATABASE_BGREADINGS));
        getWritableDatabase().delete(DATABASE_BGREADINGS, "date" + " < '" + (new Date().getTime() - Constants.hoursToKeepInDatabase * 60 * 60 * 1000L) + "'", null);
        log.debug("After BgReadings size: " + DatabaseUtils.queryNumEntries(getReadableDatabase(), DATABASE_BGREADINGS));

        log.debug("Before TempTargets size: " + DatabaseUtils.queryNumEntries(getReadableDatabase(), DATABASE_TEMPTARGETS));
        getWritableDatabase().delete(DATABASE_TEMPTARGETS, "date" + " < '" + (new Date().getTime() - Constants.hoursToKeepInDatabase * 60 * 60 * 1000L) + "'", null);
        log.debug("After TempTargets size: " + DatabaseUtils.queryNumEntries(getReadableDatabase(), DATABASE_TEMPTARGETS));

        log.debug("Before Treatments size: " + DatabaseUtils.queryNumEntries(getReadableDatabase(), DATABASE_TREATMENTS));
        getWritableDatabase().delete(DATABASE_TREATMENTS, "date" + " < '" + (new Date().getTime() - Constants.hoursToKeepInDatabase * 60 * 60 * 1000L) + "'", null);
        log.debug("After Treatments size: " + DatabaseUtils.queryNumEntries(getReadableDatabase(), DATABASE_TREATMENTS));

        log.debug("Before History size: " + DatabaseUtils.queryNumEntries(getReadableDatabase(), DATABASE_DANARHISTORY));
        getWritableDatabase().delete(DATABASE_DANARHISTORY, "recordDate" + " < '" + (new Date().getTime() - Constants.daysToKeepHistoryInDatabase * 24 * 60 * 60 * 1000L) + "'", null);
        log.debug("After History size: " + DatabaseUtils.queryNumEntries(getReadableDatabase(), DATABASE_DANARHISTORY));

        log.debug("Before TemporaryBasals size: " + DatabaseUtils.queryNumEntries(getReadableDatabase(), DATABASE_TEMPORARYBASALS));
        getWritableDatabase().delete(DATABASE_TEMPORARYBASALS, "recordDate" + " < '" + (new Date().getTime() - Constants.daysToKeepHistoryInDatabase * 24 * 60 * 60 * 1000L) + "'", null);
        log.debug("After TemporaryBasals size: " + DatabaseUtils.queryNumEntries(getReadableDatabase(), DATABASE_TEMPORARYBASALS));

        log.debug("Before ExtendedBoluses size: " + DatabaseUtils.queryNumEntries(getReadableDatabase(), DATABASE_EXTENDEDBOLUSES));
        getWritableDatabase().delete(DATABASE_EXTENDEDBOLUSES, "recordDate" + " < '" + (new Date().getTime() - Constants.daysToKeepHistoryInDatabase * 24 * 60 * 60 * 1000L) + "'", null);
        log.debug("After ExtendedBoluses size: " + DatabaseUtils.queryNumEntries(getReadableDatabase(), DATABASE_EXTENDEDBOLUSES));
    }

    public long size(String database) {
        return DatabaseUtils.queryNumEntries(getReadableDatabase(), database);
    }

    // --------------------- DB resets ---------------------

    public void resetDatabases() {
        try {
            TableUtils.dropTable(connectionSource, TempTarget.class, true);
            TableUtils.dropTable(connectionSource, Treatment.class, true);
            TableUtils.dropTable(connectionSource, BgReading.class, true);
            TableUtils.dropTable(connectionSource, DanaRHistoryRecord.class, true);
            TableUtils.dropTable(connectionSource, DbRequest.class, true);
            TableUtils.dropTable(connectionSource, TemporaryBasal.class, true);
            TableUtils.dropTable(connectionSource, ExtendedBolus.class, true);
            //DbRequests can be cleared from NSClient fragment
            TableUtils.createTableIfNotExists(connectionSource, TempTarget.class);
            TableUtils.createTableIfNotExists(connectionSource, Treatment.class);
            TableUtils.createTableIfNotExists(connectionSource, BgReading.class);
            TableUtils.createTableIfNotExists(connectionSource, DanaRHistoryRecord.class);
            TableUtils.createTableIfNotExists(connectionSource, DbRequest.class);
            TableUtils.createTableIfNotExists(connectionSource, TemporaryBasal.class);
            TableUtils.createTableIfNotExists(connectionSource, ExtendedBolus.class);
            latestTreatmentChange = 0L;
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void resetTreatments() {
        try {
            TableUtils.dropTable(connectionSource, Treatment.class, true);
            TableUtils.createTableIfNotExists(connectionSource, Treatment.class);
            latestTreatmentChange = 0L;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        scheduleTreatmentChange();
    }

    public void resetTempTargets() {
        try {
            TableUtils.dropTable(connectionSource, TempTarget.class, true);
            TableUtils.createTableIfNotExists(connectionSource, TempTarget.class);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void resetTemporaryBasals() {
        try {
            TableUtils.dropTable(connectionSource, TemporaryBasal.class, true);
            TableUtils.createTableIfNotExists(connectionSource, TemporaryBasal.class);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        scheduleTemporaryBasalChange();
    }

    public void resetExtededBoluses() {
        try {
            TableUtils.dropTable(connectionSource, ExtendedBolus.class, true);
            TableUtils.createTableIfNotExists(connectionSource, ExtendedBolus.class);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        scheduleExtendedBolusChange();
    }

    // ------------------ getDao -------------------------------------------

    private Dao<TempTarget, Long> getDaoTempTargets() throws SQLException {
        return getDao(TempTarget.class);
    }

    private Dao<Treatment, Long> getDaoTreatments() throws SQLException {
        return getDao(Treatment.class);
    }

    private Dao<BgReading, Long> getDaoBgReadings() throws SQLException {
        return getDao(BgReading.class);
    }

    private Dao<DanaRHistoryRecord, String> getDaoDanaRHistory() throws SQLException {
        return getDao(DanaRHistoryRecord.class);
    }

    private Dao<DbRequest, String> getDaoDbRequest() throws SQLException {
        return getDao(DbRequest.class);
    }

    private Dao<TemporaryBasal, Long> getDaoTemporaryBasal() throws SQLException {
        return getDao(TemporaryBasal.class);
    }

    private Dao<ExtendedBolus, Long> getDaoExtendedBolus() throws SQLException {
        return getDao(ExtendedBolus.class);
    }

    // -------------------  BgReading handling -----------------------

    public void createIfNotExists(BgReading bgReading) {
        try {
            getDaoBgReadings().createIfNotExists(bgReading);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        MainApp.bus().post(new EventNewBG());
    }

    /*
         * Return last BgReading from database or null if db is empty
         */
    @Nullable
    public static BgReading lastBg() {
        List<BgReading> bgList = null;

        try {
            Dao<BgReading, Long> daoBgReadings = MainApp.getDbHelper().getDaoBgReadings();
            QueryBuilder<BgReading, Long> queryBuilder = daoBgReadings.queryBuilder();
            queryBuilder.orderBy("date", false);
            queryBuilder.limit(1L);
            queryBuilder.where().gt("value", 38);
            PreparedQuery<BgReading> preparedQuery = queryBuilder.prepare();
            bgList = daoBgReadings.query(preparedQuery);

        } catch (SQLException e) {
            log.debug(e.getMessage(), e);
        }
        if (bgList != null && bgList.size() > 0)
            return bgList.get(0);
        else
            return null;
    }

    /*
         * Return bg reading if not old ( <9 min )
         * or null if older
         */
    @Nullable
    public static BgReading actualBg() {
        BgReading lastBg = lastBg();

        if (lastBg == null)
            return null;

        if (lastBg.date > new Date().getTime() - 9 * 60 * 1000)
            return lastBg;

        return null;
    }


    public List<BgReading> getBgreadingsDataFromTime(long mills, boolean ascending) {
        try {
            Dao<BgReading, Long> daoBgreadings = getDaoBgReadings();
            List<BgReading> bgReadings;
            QueryBuilder<BgReading, Long> queryBuilder = daoBgreadings.queryBuilder();
            queryBuilder.orderBy("date", ascending);
            Where where = queryBuilder.where();
            where.ge("date", mills).and().gt("value", 38);
            PreparedQuery<BgReading> preparedQuery = queryBuilder.prepare();
            bgReadings = daoBgreadings.query(preparedQuery);
            return bgReadings;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return new ArrayList<BgReading>();
    }

    // ------------- DbRequests handling -------------------

    public void create(DbRequest dbr) {
        try {
            getDaoDbRequest().create(dbr);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public int delete(DbRequest dbr) {
        try {
            return getDaoDbRequest().delete(dbr);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    public int deleteDbRequest(String nsClientId) {
        try {
            return getDaoDbRequest().deleteById(nsClientId);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    public int deleteDbRequestbyMongoId(String action, String id) {
        try {
            QueryBuilder<DbRequest, String> queryBuilder = getDaoDbRequest().queryBuilder();
            Where where = queryBuilder.where();
            where.eq("_id", id).and().eq("action", action);
            queryBuilder.limit(10L);
            PreparedQuery<DbRequest> preparedQuery = queryBuilder.prepare();
            List<DbRequest> dbList = getDaoDbRequest().query(preparedQuery);
            if (dbList.size() != 1) {
                log.error("deleteDbRequestbyMongoId query size: " + dbList.size());
            } else {
                //log.debug("Treatment findTreatmentById found: " + trList.get(0).log());
                return delete(dbList.get(0));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    public void deleteAllDbRequests() {
        try {
            TableUtils.clearTable(connectionSource, DbRequest.class);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public CloseableIterator getDbRequestInterator() {
        try {
            return getDaoDbRequest().closeableIterator();
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    //  -------------------- TREATMENT HANDLING -------------------

    public boolean affectingIobCob(Treatment t) {
        Treatment existing = findTreatmentByTimeIndex(t.date);
        if (existing == null)
            return true;
        if (existing.insulin == t.insulin && existing.carbs == t.carbs)
            return false;
        return true;
    }

    public int update(Treatment treatment) {
        int updated = 0;
        try {
            boolean historyChange = affectingIobCob(treatment);
            updated = getDaoTreatments().update(treatment);
            if (historyChange)
                latestTreatmentChange = treatment.date;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        scheduleTreatmentChange();
        return updated;
    }

    public Dao.CreateOrUpdateStatus createOrUpdate(Treatment treatment) {
        Dao.CreateOrUpdateStatus status = null;
        try {
            boolean historyChange = affectingIobCob(treatment);
            status = getDaoTreatments().createOrUpdate(treatment);
            if (historyChange)
                latestTreatmentChange = treatment.date;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        scheduleTreatmentChange();
        return status;
    }

    public void create(Treatment treatment) {
        try {
            getDaoTreatments().create(treatment);
            latestTreatmentChange = treatment.date;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        scheduleTreatmentChange();
    }

    public void delete(Treatment treatment) {
        try {
            getDaoTreatments().delete(treatment);
            latestTreatmentChange = treatment.date;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        scheduleTreatmentChange();
    }

    public int deleteTreatmentById(String _id) {
        Treatment stored = findTreatmentById(_id);
        int removed = 0;
        if (stored != null) {
            log.debug("REMOVE: Existing treatment (removing): " + _id);
            try {
                removed = getDaoTreatments().delete(stored);
            } catch (SQLException e) {
                e.printStackTrace();
            }
            if (Config.logIncommingData)
                log.debug("Records removed: " + removed);
            latestTreatmentChange = stored.date;
            scheduleTreatmentChange();
        } else {
            log.debug("REMOVE: Not stored treatment (ignoring): " + _id);
        }
        return removed;
    }

    @Nullable
    public Treatment findTreatmentById(String _id) {
        try {
            Dao<Treatment, Long> daoTreatments = getDaoTreatments();
            QueryBuilder<Treatment, Long> queryBuilder = daoTreatments.queryBuilder();
            Where where = queryBuilder.where();
            where.eq("_id", _id);
            queryBuilder.limit(10L);
            PreparedQuery<Treatment> preparedQuery = queryBuilder.prepare();
            List<Treatment> trList = daoTreatments.query(preparedQuery);
            if (trList.size() != 1) {
                //log.debug("Treatment findTreatmentById query size: " + trList.size());
                return null;
            } else {
                //log.debug("Treatment findTreatmentById found: " + trList.get(0).log());
                return trList.get(0);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Nullable
    public Treatment findTreatmentByTimeIndex(Long timeIndex) {
        try {
            QueryBuilder<Treatment, String> qb = null;
            Dao<Treatment, Long> daoTreatments = getDaoTreatments();
            QueryBuilder<Treatment, Long> queryBuilder = daoTreatments.queryBuilder();
            Where where = queryBuilder.where();
            where.eq("date", timeIndex);
            queryBuilder.limit(10L);
            PreparedQuery<Treatment> preparedQuery = queryBuilder.prepare();
            List<Treatment> trList = daoTreatments.query(preparedQuery);
            if (trList.size() != 1) {
                log.debug("Treatment findTreatmentByTimeIndex query size: " + trList.size());
                return null;
            } else {
                log.debug("Treatment findTreatmentByTimeIndex found: " + trList.get(0).log());
                return trList.get(0);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    static public void scheduleTreatmentChange() {
        class PostRunnable implements Runnable {
            public void run() {
                MainApp.bus().post(new EventTreatmentChange());
                if (latestTreatmentChange != null)
                    MainApp.bus().post(new EventNewHistoryData(latestTreatmentChange));
                latestTreatmentChange = null;
                scheduledTratmentPost = null;
            }
        }
        // prepare task for execution in 5 sec
        // cancel waiting task to prevent sending multiple posts
        if (scheduledTratmentPost != null)
            scheduledTratmentPost.cancel(false);
        Runnable task = new PostRunnable();
        final int sec = 5;
        scheduledTratmentPost = treatmentsWorker.schedule(task, sec, TimeUnit.SECONDS);

    }

    public List<Treatment> getTreatmentDataFromTime(long mills, boolean ascending) {
        try {
            Dao<Treatment, Long> daoTreatments = getDaoTreatments();
            List<Treatment> treatments;
            QueryBuilder<Treatment, Long> queryBuilder = daoTreatments.queryBuilder();
            queryBuilder.orderBy("date", ascending);
            Where where = queryBuilder.where();
            where.ge("date", mills);
            PreparedQuery<Treatment> preparedQuery = queryBuilder.prepare();
            treatments = daoTreatments.query(preparedQuery);
            return treatments;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return new ArrayList<Treatment>();
    }

    // ---------------- TempTargets handling ---------------

    public List<TempTarget> getTemptargetsDataFromTime(long mills, boolean ascending) {
        try {
            Dao<TempTarget, Long> daoTempTargets = getDaoTempTargets();
            List<TempTarget> tempTargets;
            QueryBuilder<TempTarget, Long> queryBuilder = daoTempTargets.queryBuilder();
            queryBuilder.orderBy("date", ascending);
            Where where = queryBuilder.where();
            where.ge("date", mills);
            PreparedQuery<TempTarget> preparedQuery = queryBuilder.prepare();
            tempTargets = daoTempTargets.query(preparedQuery);
            return tempTargets;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return new ArrayList<TempTarget>();
    }

    public void createIfNotExists(TempTarget tempTarget) {
        try {
            getDaoTempTargets().createIfNotExists(tempTarget);
            MainApp.bus().post(new EventTempTargetRangeChange());
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void delete(TempTarget tempTarget) {
        try {
            getDaoTempTargets().delete(tempTarget);
            MainApp.bus().post(new EventTempTargetRangeChange());
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void createFromJsonIfNotExists(JSONObject trJson) {
        try {
            QueryBuilder<TempTarget, Long> queryBuilder = null;
            queryBuilder = getDaoTempTargets().queryBuilder();
            Where where = queryBuilder.where();
            where.eq("_id", trJson.getString("_id")).or().eq("date", trJson.getLong("mills"));
            PreparedQuery<TempTarget> preparedQuery = queryBuilder.prepare();
            List<TempTarget> list = getDaoTempTargets().query(preparedQuery);
            NSProfile profile = MainApp.getConfigBuilder().getActiveProfile().getProfile();
            if (profile == null) return; // no profile data, better ignore than do something wrong
            String units = profile.getUnits();
            TempTarget tempTarget;
            if (list.size() == 0) {
                tempTarget = new TempTarget();
                if (Config.logIncommingData)
                    log.debug("Adding TempTarget record to database: " + trJson.toString());
                // Record does not exists. add
            } else if (list.size() == 1) {
                tempTarget = list.get(0);
                if (Config.logIncommingData)
                    log.debug("Updating TempTarget record in database: " + trJson.toString());
            } else {
                log.error("Somthing went wrong");
                return;
            }
            tempTarget.date = trJson.getLong("mills");
            tempTarget.durationInMinutes = trJson.getInt("duration");
            tempTarget.low = NSProfile.toMgdl(trJson.getDouble("targetBottom"), units);
            tempTarget.high = NSProfile.toMgdl(trJson.getDouble("targetTop"), units);
            tempTarget.reason = trJson.getString("reason");
            tempTarget._id = trJson.getString("_id");
            createIfNotExists(tempTarget);
            MainApp.bus().post(new EventTempTargetRangeChange());
        } catch (SQLException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void deleteTempTargetById(String _id) {
        try {
            QueryBuilder<TempTarget, Long> queryBuilder = null;
            queryBuilder = getDaoTempTargets().queryBuilder();
            Where where = queryBuilder.where();
            where.eq("_id", _id);
            PreparedQuery<TempTarget> preparedQuery = queryBuilder.prepare();
            List<TempTarget> list = getDaoTempTargets().query(preparedQuery);

            if (list.size() == 1) {
                TempTarget record = list.get(0);
                if (Config.logIncommingData)
                    log.debug("Removing TempTarget record from database: " + record.log());
                getDaoTempTargets().delete(record);
                MainApp.bus().post(new EventTempTargetRangeChange());
            } else {
                if (Config.logIncommingData)
                    log.debug("TempTarget not found database: " + _id);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // ----------------- DanaRHistory handling --------------------

    public void createIfNotExists(DanaRHistoryRecord record) {
        try {
            getDaoDanaRHistory().createIfNotExists(record);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public List<DanaRHistoryRecord> getDanaRHistoryRecordsByType(byte type) {
        List<DanaRHistoryRecord> historyList;
        try {
            QueryBuilder<DanaRHistoryRecord, String> queryBuilder = getDaoDanaRHistory().queryBuilder();
            queryBuilder.orderBy("recordDate", false);
            Where where = queryBuilder.where();
            where.eq("recordCode", type);
            queryBuilder.limit(200L);
            PreparedQuery<DanaRHistoryRecord> preparedQuery = queryBuilder.prepare();
            historyList = getDaoDanaRHistory().query(preparedQuery);
        } catch (SQLException e) {
            e.printStackTrace();
            historyList = new ArrayList<>();
        }
        return historyList;
    }

    public void updateDanaRHistoryRecordId(JSONObject trJson) {
        try {
            QueryBuilder<DanaRHistoryRecord, String> queryBuilder = getDaoDanaRHistory().queryBuilder();
            Where where = queryBuilder.where();
            where.ge("bytes", trJson.get(DanaRNSHistorySync.DANARSIGNATURE));
            PreparedQuery<DanaRHistoryRecord> preparedQuery = queryBuilder.prepare();
            List<DanaRHistoryRecord> list = getDaoDanaRHistory().query(preparedQuery);
            if (list.size() == 0) {
                // Record does not exists. Ignore
            } else if (list.size() == 1) {
                DanaRHistoryRecord record = list.get(0);
                if (record._id == null || !record._id.equals(trJson.getString("_id"))) {
                    if (Config.logIncommingData)
                        log.debug("Updating _id in DanaR history database: " + trJson.getString("_id"));
                    record._id = trJson.getString("_id");
                    getDaoDanaRHistory().update(record);
                } else {
                    // already set
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    // ------------ TemporaryBasal handling ---------------

    public int update(TemporaryBasal tempBasal) {
        int updated = 0;
        try {
            updated = getDaoTemporaryBasal().update(tempBasal);
            latestTreatmentChange = tempBasal.date;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        scheduleTemporaryBasalChange();
        return updated;
    }

    public void create(TemporaryBasal tempBasal) {
        try {
            getDaoTemporaryBasal().create(tempBasal);
            latestTreatmentChange = tempBasal.date;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        scheduleTemporaryBasalChange();
    }

    public void delete(TemporaryBasal tempBasal) {
        try {
            getDaoTemporaryBasal().delete(tempBasal);
            latestTreatmentChange = tempBasal.date;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        scheduleTemporaryBasalChange();
    }

    public List<TemporaryBasal> getTemporaryBasalsDataFromTime(long mills, boolean ascending) {
        try {
            List<TemporaryBasal> tempbasals;
            QueryBuilder<TemporaryBasal, Long> queryBuilder = getDaoTemporaryBasal().queryBuilder();
            queryBuilder.orderBy("date", ascending);
            Where where = queryBuilder.where();
            where.ge("date", mills);
            PreparedQuery<TemporaryBasal> preparedQuery = queryBuilder.prepare();
            tempbasals = getDaoTemporaryBasal().query(preparedQuery);
            return tempbasals;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return new ArrayList<TemporaryBasal>();
    }

    static public void scheduleTemporaryBasalChange() {
        class PostRunnable implements Runnable {
            public void run() {
                MainApp.bus().post(new EventTempBasalChange());
                scheduledTemBasalsPost = null;
            }
        }
        // prepare task for execution in 5 sec
        // cancel waiting task to prevent sending multiple posts
        if (scheduledTemBasalsPost != null)
            scheduledTemBasalsPost.cancel(false);
        Runnable task = new PostRunnable();
        final int sec = 5;
        scheduledTemBasalsPost = tempBasalsWorker.schedule(task, sec, TimeUnit.SECONDS);

    }

    // ------------ ExtendedBolus handling ---------------

    public int update(ExtendedBolus extendedBolus) {
        int updated = 0;
        try {
            updated = getDaoExtendedBolus().update(extendedBolus);
            latestTreatmentChange = extendedBolus.date;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        scheduleExtendedBolusChange();
        return updated;
    }

    public void create(ExtendedBolus extendedBolus) {
        try {
            getDaoExtendedBolus().create(extendedBolus);
            latestTreatmentChange = extendedBolus.date;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        scheduleExtendedBolusChange();
    }

    public void delete(ExtendedBolus extendedBolus) {
        try {
            getDaoExtendedBolus().delete(extendedBolus);
            latestTreatmentChange = extendedBolus.date;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        scheduleExtendedBolusChange();
    }

    public List<ExtendedBolus> getExtendedBolusDataFromTime(long mills, boolean ascending) {
        try {
            List<ExtendedBolus> extendedBoluses;
            QueryBuilder<ExtendedBolus, Long> queryBuilder = getDaoExtendedBolus().queryBuilder();
            queryBuilder.orderBy("date", ascending);
            Where where = queryBuilder.where();
            where.ge("date", mills);
            PreparedQuery<ExtendedBolus> preparedQuery = queryBuilder.prepare();
            extendedBoluses = getDaoExtendedBolus().query(preparedQuery);
            return extendedBoluses;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return new ArrayList<ExtendedBolus>();
    }

    static public void scheduleExtendedBolusChange() {
        class PostRunnable implements Runnable {
            public void run() {
                MainApp.bus().post(new EventExtendedBolusChange());
                scheduledExtendedBolusPost = null;
            }
        }
        // prepare task for execution in 5 sec
        // cancel waiting task to prevent sending multiple posts
        if (scheduledExtendedBolusPost != null)
            scheduledExtendedBolusPost.cancel(false);
        Runnable task = new PostRunnable();
        final int sec = 5;
        scheduledExtendedBolusPost = extendedBolusWorker.schedule(task, sec, TimeUnit.SECONDS);

    }


}

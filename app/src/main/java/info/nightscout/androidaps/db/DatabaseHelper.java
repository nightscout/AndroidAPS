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
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.data.ProfileStore;
import info.nightscout.androidaps.events.EventCareportalEventChange;
import info.nightscout.androidaps.events.EventExtendedBolusChange;
import info.nightscout.androidaps.events.EventFoodDatabaseChanged;
import info.nightscout.androidaps.events.EventNewBG;
import info.nightscout.androidaps.events.EventProfileSwitchChange;
import info.nightscout.androidaps.events.EventRefreshOverview;
import info.nightscout.androidaps.events.EventReloadProfileSwitchData;
import info.nightscout.androidaps.events.EventReloadTempBasalData;
import info.nightscout.androidaps.events.EventReloadTreatmentData;
import info.nightscout.androidaps.events.EventTempBasalChange;
import info.nightscout.androidaps.events.EventTempTargetChange;
import info.nightscout.androidaps.events.EventTreatmentChange;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.IobCobCalculator.events.EventNewHistoryData;
import info.nightscout.androidaps.plugins.Overview.events.EventNewNotification;
import info.nightscout.androidaps.plugins.Overview.notifications.Notification;
import info.nightscout.androidaps.plugins.PumpDanaR.activities.DanaRNSHistorySync;
import info.nightscout.androidaps.plugins.PumpVirtual.VirtualPumpPlugin;
import info.nightscout.utils.DateUtil;
import info.nightscout.utils.NSUpload;
import info.nightscout.utils.PercentageSplitter;

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
    public static final String DATABASE_CAREPORTALEVENTS = "CareportalEvents";
    public static final String DATABASE_PROFILESWITCHES = "ProfileSwitches";
    public static final String DATABASE_FOODS = "Foods";

    private static final int DATABASE_VERSION = 8;

    private static Long earliestDataChange = null;

    private static final ScheduledExecutorService bgWorker = Executors.newSingleThreadScheduledExecutor();
    private static ScheduledFuture<?> scheduledBgPost = null;

    private static final ScheduledExecutorService treatmentsWorker = Executors.newSingleThreadScheduledExecutor();
    private static ScheduledFuture<?> scheduledTratmentPost = null;

    private static final ScheduledExecutorService tempBasalsWorker = Executors.newSingleThreadScheduledExecutor();
    private static ScheduledFuture<?> scheduledTemBasalsPost = null;

    private static final ScheduledExecutorService tempTargetWorker = Executors.newSingleThreadScheduledExecutor();
    private static ScheduledFuture<?> scheduledTemTargetPost = null;

    private static final ScheduledExecutorService extendedBolusWorker = Executors.newSingleThreadScheduledExecutor();
    private static ScheduledFuture<?> scheduledExtendedBolusPost = null;

    private static final ScheduledExecutorService careportalEventWorker = Executors.newSingleThreadScheduledExecutor();
    private static ScheduledFuture<?> scheduledCareportalEventPost = null;

    private static final ScheduledExecutorService profileSwitchEventWorker = Executors.newSingleThreadScheduledExecutor();
    private static ScheduledFuture<?> scheduledProfileSwitchEventPost = null;

    public FoodHelper foodHelper = new FoodHelper(this);

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        onCreate(getWritableDatabase(), getConnectionSource());
        //onUpgrade(getWritableDatabase(), getConnectionSource(), 1,1);
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
            TableUtils.createTableIfNotExists(connectionSource, CareportalEvent.class);
            TableUtils.createTableIfNotExists(connectionSource, ProfileSwitch.class);
            TableUtils.createTableIfNotExists(connectionSource, Food.class);
        } catch (SQLException e) {
            log.error("Can't create database", e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public void onUpgrade(SQLiteDatabase database, ConnectionSource connectionSource, int oldVersion, int newVersion) {
        try {
            if (oldVersion == 7 && newVersion == 8) {
                log.debug("Upgrading database from v7 to v8");
                TableUtils.dropTable(connectionSource, Treatment.class, true);
                TableUtils.createTableIfNotExists(connectionSource, Treatment.class);
            } else {
                log.info(DatabaseHelper.class.getName(), "onUpgrade");
                TableUtils.dropTable(connectionSource, TempTarget.class, true);
                TableUtils.dropTable(connectionSource, Treatment.class, true);
                TableUtils.dropTable(connectionSource, BgReading.class, true);
                TableUtils.dropTable(connectionSource, DanaRHistoryRecord.class, true);
                TableUtils.dropTable(connectionSource, DbRequest.class, true);
                TableUtils.dropTable(connectionSource, TemporaryBasal.class, true);
                TableUtils.dropTable(connectionSource, ExtendedBolus.class, true);
                TableUtils.dropTable(connectionSource, CareportalEvent.class, true);
                TableUtils.dropTable(connectionSource, ProfileSwitch.class, true);
                TableUtils.dropTable(connectionSource, Food.class, true);
                onCreate(database, connectionSource);
            }
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
        getWritableDatabase().delete(DATABASE_BGREADINGS, "date" + " < '" + (System.currentTimeMillis() - Constants.hoursToKeepInDatabase * 60 * 60 * 1000L) + "'", null);
        log.debug("After BgReadings size: " + DatabaseUtils.queryNumEntries(getReadableDatabase(), DATABASE_BGREADINGS));

        log.debug("Before TempTargets size: " + DatabaseUtils.queryNumEntries(getReadableDatabase(), DATABASE_TEMPTARGETS));
        getWritableDatabase().delete(DATABASE_TEMPTARGETS, "date" + " < '" + (System.currentTimeMillis() - Constants.hoursToKeepInDatabase * 60 * 60 * 1000L) + "'", null);
        log.debug("After TempTargets size: " + DatabaseUtils.queryNumEntries(getReadableDatabase(), DATABASE_TEMPTARGETS));

        log.debug("Before Treatments size: " + DatabaseUtils.queryNumEntries(getReadableDatabase(), DATABASE_TREATMENTS));
        getWritableDatabase().delete(DATABASE_TREATMENTS, "date" + " < '" + (System.currentTimeMillis() - Constants.hoursToKeepInDatabase * 60 * 60 * 1000L) + "'", null);
        log.debug("After Treatments size: " + DatabaseUtils.queryNumEntries(getReadableDatabase(), DATABASE_TREATMENTS));

        log.debug("Before History size: " + DatabaseUtils.queryNumEntries(getReadableDatabase(), DATABASE_DANARHISTORY));
        getWritableDatabase().delete(DATABASE_DANARHISTORY, "recordDate" + " < '" + (System.currentTimeMillis() - Constants.daysToKeepHistoryInDatabase * 24 * 60 * 60 * 1000L) + "'", null);
        log.debug("After History size: " + DatabaseUtils.queryNumEntries(getReadableDatabase(), DATABASE_DANARHISTORY));

        log.debug("Before TemporaryBasals size: " + DatabaseUtils.queryNumEntries(getReadableDatabase(), DATABASE_TEMPORARYBASALS));
        getWritableDatabase().delete(DATABASE_TEMPORARYBASALS, "recordDate" + " < '" + (System.currentTimeMillis() - Constants.daysToKeepHistoryInDatabase * 24 * 60 * 60 * 1000L) + "'", null);
        log.debug("After TemporaryBasals size: " + DatabaseUtils.queryNumEntries(getReadableDatabase(), DATABASE_TEMPORARYBASALS));

        log.debug("Before ExtendedBoluses size: " + DatabaseUtils.queryNumEntries(getReadableDatabase(), DATABASE_EXTENDEDBOLUSES));
        getWritableDatabase().delete(DATABASE_EXTENDEDBOLUSES, "recordDate" + " < '" + (System.currentTimeMillis() - Constants.daysToKeepHistoryInDatabase * 24 * 60 * 60 * 1000L) + "'", null);
        log.debug("After ExtendedBoluses size: " + DatabaseUtils.queryNumEntries(getReadableDatabase(), DATABASE_EXTENDEDBOLUSES));

        log.debug("Before CareportalEvent size: " + DatabaseUtils.queryNumEntries(getReadableDatabase(), DATABASE_CAREPORTALEVENTS));
        getWritableDatabase().delete(DATABASE_CAREPORTALEVENTS, "recordDate" + " < '" + (System.currentTimeMillis() - Constants.daysToKeepHistoryInDatabase * 24 * 60 * 60 * 1000L) + "'", null);
        log.debug("After CareportalEvent size: " + DatabaseUtils.queryNumEntries(getReadableDatabase(), DATABASE_CAREPORTALEVENTS));

        log.debug("Before ProfileSwitch size: " + DatabaseUtils.queryNumEntries(getReadableDatabase(), DATABASE_PROFILESWITCHES));
        getWritableDatabase().delete(DATABASE_PROFILESWITCHES, "recordDate" + " < '" + (System.currentTimeMillis() - Constants.daysToKeepHistoryInDatabase * 24 * 60 * 60 * 1000L) + "'", null);
        log.debug("After ProfileSwitch size: " + DatabaseUtils.queryNumEntries(getReadableDatabase(), DATABASE_PROFILESWITCHES));
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
            TableUtils.dropTable(connectionSource, CareportalEvent.class, true);
            TableUtils.dropTable(connectionSource, ProfileSwitch.class, true);
            TableUtils.createTableIfNotExists(connectionSource, TempTarget.class);
            TableUtils.createTableIfNotExists(connectionSource, Treatment.class);
            TableUtils.createTableIfNotExists(connectionSource, BgReading.class);
            TableUtils.createTableIfNotExists(connectionSource, DanaRHistoryRecord.class);
            TableUtils.createTableIfNotExists(connectionSource, DbRequest.class);
            TableUtils.createTableIfNotExists(connectionSource, TemporaryBasal.class);
            TableUtils.createTableIfNotExists(connectionSource, ExtendedBolus.class);
            TableUtils.createTableIfNotExists(connectionSource, CareportalEvent.class);
            TableUtils.createTableIfNotExists(connectionSource, ProfileSwitch.class);
            foodHelper.resetFood();
            updateEarliestDataChange(0);
        } catch (SQLException e) {
            log.error("Unhandled exception", e);
        }
        VirtualPumpPlugin.setFakingStatus(true);
        scheduleBgChange(); // trigger refresh
        scheduleTemporaryBasalChange();
        scheduleTreatmentChange();
        scheduleExtendedBolusChange();
        scheduleTemporaryTargetChange();
        scheduleCareportalEventChange();
        scheduleProfileSwitchChange();
        foodHelper.scheduleFoodChange();
        new java.util.Timer().schedule(
                new java.util.TimerTask() {
                    @Override
                    public void run() {
                        MainApp.bus().post(new EventRefreshOverview("resetDatabases"));
                    }
                },
                3000
        );
    }

    public void resetTreatments() {
        try {
            TableUtils.dropTable(connectionSource, Treatment.class, true);
            TableUtils.createTableIfNotExists(connectionSource, Treatment.class);
            updateEarliestDataChange(0);
        } catch (SQLException e) {
            log.error("Unhandled exception", e);
        }
        scheduleTreatmentChange();
    }

    public void resetTempTargets() {
        try {
            TableUtils.dropTable(connectionSource, TempTarget.class, true);
            TableUtils.createTableIfNotExists(connectionSource, TempTarget.class);
        } catch (SQLException e) {
            log.error("Unhandled exception", e);
        }
        scheduleTemporaryTargetChange();
    }

    public void resetTemporaryBasals() {
        try {
            TableUtils.dropTable(connectionSource, TemporaryBasal.class, true);
            TableUtils.createTableIfNotExists(connectionSource, TemporaryBasal.class);
            updateEarliestDataChange(0);
        } catch (SQLException e) {
            log.error("Unhandled exception", e);
        }
        VirtualPumpPlugin.setFakingStatus(false);
        scheduleTemporaryBasalChange();
    }

    public void resetExtededBoluses() {
        try {
            TableUtils.dropTable(connectionSource, ExtendedBolus.class, true);
            TableUtils.createTableIfNotExists(connectionSource, ExtendedBolus.class);
            updateEarliestDataChange(0);
        } catch (SQLException e) {
            log.error("Unhandled exception", e);
        }
        scheduleExtendedBolusChange();
    }

    public void resetCareportalEvents() {
        try {
            TableUtils.dropTable(connectionSource, CareportalEvent.class, true);
            TableUtils.createTableIfNotExists(connectionSource, CareportalEvent.class);
        } catch (SQLException e) {
            log.error("Unhandled exception", e);
        }
        scheduleCareportalEventChange();
    }

    public void resetProfileSwitch() {
        try {
            TableUtils.dropTable(connectionSource, ProfileSwitch.class, true);
            TableUtils.createTableIfNotExists(connectionSource, ProfileSwitch.class);
        } catch (SQLException e) {
            log.error("Unhandled exception", e);
        }
        scheduleProfileSwitchChange();
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

    private Dao<CareportalEvent, Long> getDaoCareportalEvents() throws SQLException {
        return getDao(CareportalEvent.class);
    }

    private Dao<ProfileSwitch, Long> getDaoProfileSwitch() throws SQLException {
        return getDao(ProfileSwitch.class);
    }

    public long roundDateToSec(long date) {
        return date - date % 1000;
    }
    // -------------------  BgReading handling -----------------------

    public boolean createIfNotExists(BgReading bgReading, String from) {
        try {
            bgReading.date = roundDateToSec(bgReading.date);
            BgReading old = getDaoBgReadings().queryForId(bgReading.date);
            if (old == null) {
                getDaoBgReadings().create(bgReading);
                log.debug("BG: New record from: " + from + " " + bgReading.toString());
                scheduleBgChange();
                return true;
            }
            if (!old.isEqual(bgReading)) {
                log.debug("BG: Similiar found: " + old.toString());
                old.copyFrom(bgReading);
                getDaoBgReadings().update(old);
                log.debug("BG: Updating record from: " + from + " New data: " + old.toString());
                scheduleBgChange();
                return false;
            }
        } catch (SQLException e) {
            log.error("Unhandled exception", e);
        }
        return false;
    }

    public void update(BgReading bgReading) {
        bgReading.date = roundDateToSec(bgReading.date);
        try {
            getDaoBgReadings().update(bgReading);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private static void scheduleBgChange() {
        class PostRunnable implements Runnable {
            public void run() {
                log.debug("Firing EventNewBg");
                MainApp.bus().post(new EventNewBG());
                scheduledBgPost = null;
            }
        }
        // prepare task for execution in 1 sec
        // cancel waiting task to prevent sending multiple posts
        if (scheduledBgPost != null)
            scheduledBgPost.cancel(false);
        Runnable task = new PostRunnable();
        final int sec = 1;
        scheduledBgPost = bgWorker.schedule(task, sec, TimeUnit.SECONDS);

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
            queryBuilder.where().gt("value", 38).and().eq("isValid", true);
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

        if (lastBg.date > System.currentTimeMillis() - 9 * 60 * 1000)
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
            where.ge("date", mills).and().gt("value", 38).and().eq("isValid", true);
            PreparedQuery<BgReading> preparedQuery = queryBuilder.prepare();
            bgReadings = daoBgreadings.query(preparedQuery);
            return bgReadings;
        } catch (SQLException e) {
            log.error("Unhandled exception", e);
        }
        return new ArrayList<BgReading>();
    }

    public List<BgReading> getAllBgreadingsDataFromTime(long mills, boolean ascending) {
        try {
            Dao<BgReading, Long> daoBgreadings = getDaoBgReadings();
            List<BgReading> bgReadings;
            QueryBuilder<BgReading, Long> queryBuilder = daoBgreadings.queryBuilder();
            queryBuilder.orderBy("date", ascending);
            Where where = queryBuilder.where();
            where.ge("date", mills);
            PreparedQuery<BgReading> preparedQuery = queryBuilder.prepare();
            bgReadings = daoBgreadings.query(preparedQuery);
            return bgReadings;
        } catch (SQLException e) {
            log.error("Unhandled exception", e);
        }
        return new ArrayList<BgReading>();
    }

    // ------------- DbRequests handling -------------------

    public void create(DbRequest dbr) {
        try {
            getDaoDbRequest().create(dbr);
        } catch (SQLException e) {
            log.error("Unhandled exception", e);
        }
    }

    public int delete(DbRequest dbr) {
        try {
            return getDaoDbRequest().delete(dbr);
        } catch (SQLException e) {
            log.error("Unhandled exception", e);
        }
        return 0;
    }

    public int deleteDbRequest(String nsClientId) {
        try {
            return getDaoDbRequest().deleteById(nsClientId);
        } catch (SQLException e) {
            log.error("Unhandled exception", e);
        }
        return 0;
    }

    public void deleteDbRequestbyMongoId(String action, String id) {
        try {
            QueryBuilder<DbRequest, String> queryBuilder = getDaoDbRequest().queryBuilder();
            Where where = queryBuilder.where();
            where.eq("_id", id).and().eq("action", action);
            queryBuilder.limit(10L);
            PreparedQuery<DbRequest> preparedQuery = queryBuilder.prepare();
            List<DbRequest> dbList = getDaoDbRequest().query(preparedQuery);
            log.error("deleteDbRequestbyMongoId query size: " + dbList.size());
            for (DbRequest r : dbList) {
                delete(r);
            }
        } catch (SQLException e) {
            log.error("Unhandled exception", e);
        }
    }

    public void deleteAllDbRequests() {
        try {
            TableUtils.clearTable(connectionSource, DbRequest.class);
        } catch (SQLException e) {
            log.error("Unhandled exception", e);
        }
    }

    public CloseableIterator getDbRequestInterator() {
        try {
            return getDaoDbRequest().closeableIterator();
        } catch (SQLException e) {
            log.error("Unhandled exception", e);
            return null;
        }
    }

    //  -------------------- TREATMENT HANDLING -------------------

    // return true if new record is created
    public boolean createOrUpdate(Treatment treatment) {
        try {
            Treatment old;
            treatment.date = roundDateToSec(treatment.date);

            if (treatment.source == Source.PUMP) {
                // check for changed from pump change in NS
                QueryBuilder<Treatment, Long> queryBuilder = getDaoTreatments().queryBuilder();
                Where where = queryBuilder.where();
                where.eq("pumpId", treatment.pumpId);
                PreparedQuery<Treatment> preparedQuery = queryBuilder.prepare();
                List<Treatment> trList = getDaoTreatments().query(preparedQuery);
                if (trList.size() > 0) {
                    // do nothing, pump history record cannot be changed
                    return false;
                }
                getDaoTreatments().create(treatment);
                log.debug("TREATMENT: New record from: " + Source.getString(treatment.source) + " " + treatment.toString());
                updateEarliestDataChange(treatment.date);
                scheduleTreatmentChange();
                return true;
            }
            if (treatment.source == Source.NIGHTSCOUT) {
                old = getDaoTreatments().queryForId(treatment.date);
                if (old != null) {
                    if (!old.isEqual(treatment)) {
                        boolean historyChange = old.isDataChanging(treatment);
                        long oldDate = old.date;
                        getDaoTreatments().delete(old); // need to delete/create because date may change too
                        old.copyFrom(treatment);
                        getDaoTreatments().create(old);
                        log.debug("TREATMENT: Updating record by date from: " + Source.getString(treatment.source) + " " + old.toString());
                        if (historyChange) {
                            updateEarliestDataChange(oldDate);
                            updateEarliestDataChange(old.date);
                        }
                        scheduleTreatmentChange();
                        return true;
                    }
                    return false;
                }
                // find by NS _id
                if (treatment._id != null) {
                    QueryBuilder<Treatment, Long> queryBuilder = getDaoTreatments().queryBuilder();
                    Where where = queryBuilder.where();
                    where.eq("_id", treatment._id);
                    PreparedQuery<Treatment> preparedQuery = queryBuilder.prepare();
                    List<Treatment> trList = getDaoTreatments().query(preparedQuery);
                    if (trList.size() > 0) {
                        old = trList.get(0);
                        if (!old.isEqual(treatment)) {
                            boolean historyChange = old.isDataChanging(treatment);
                            long oldDate = old.date;
                            getDaoTreatments().delete(old); // need to delete/create because date may change too
                            old.copyFrom(treatment);
                            getDaoTreatments().create(old);
                            log.debug("TREATMENT: Updating record by _id from: " + Source.getString(treatment.source) + " " + old.toString());
                            if (historyChange) {
                                updateEarliestDataChange(oldDate);
                                updateEarliestDataChange(old.date);
                            }
                            scheduleTreatmentChange();
                            return true;
                        }
                    }
                }
                getDaoTreatments().create(treatment);
                log.debug("TREATMENT: New record from: " + Source.getString(treatment.source) + " " + treatment.toString());
                updateEarliestDataChange(treatment.date);
                scheduleTreatmentChange();
                return true;
            }
            if (treatment.source == Source.USER) {
                getDaoTreatments().create(treatment);
                log.debug("TREATMENT: New record from: " + Source.getString(treatment.source) + " " + treatment.toString());
                updateEarliestDataChange(treatment.date);
                scheduleTreatmentChange();
                return true;
            }
        } catch (SQLException e) {
            log.error("Unhandled exception", e);
        }
        return false;
    }

    public void delete(Treatment treatment) {
        try {
            getDaoTreatments().delete(treatment);
            updateEarliestDataChange(treatment.date);
        } catch (SQLException e) {
            log.error("Unhandled exception", e);
        }
        scheduleTreatmentChange();
    }

    public void update(Treatment treatment) {
        try {
            getDaoTreatments().update(treatment);
            updateEarliestDataChange(treatment.date);
        } catch (SQLException e) {
            log.error("Unhandled exception", e);
        }
        scheduleTreatmentChange();
    }

    public void deleteTreatmentById(String _id) {
        Treatment stored = findTreatmentById(_id);
        if (stored != null) {
            log.debug("TREATMENT: Removing Treatment record from database: " + stored.toString());
            delete(stored);
            updateEarliestDataChange(stored.date);
            scheduleTreatmentChange();
        }
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
            log.error("Unhandled exception", e);
        }
        return null;
    }

    private void updateEarliestDataChange(long newDate) {
        if (earliestDataChange == null) {
            earliestDataChange = newDate;
            return;
        }
        if (newDate < earliestDataChange) {
            earliestDataChange = newDate;
        }
    }

    private static void scheduleTreatmentChange() {
        class PostRunnable implements Runnable {
            public void run() {
                log.debug("Firing EventTreatmentChange");
                MainApp.bus().post(new EventReloadTreatmentData(new EventTreatmentChange()));
                if (earliestDataChange != null)
                    MainApp.bus().post(new EventNewHistoryData(earliestDataChange));
                earliestDataChange = null;
                scheduledTratmentPost = null;
            }
        }
        // prepare task for execution in 1 sec
        // cancel waiting task to prevent sending multiple posts
        if (scheduledTratmentPost != null)
            scheduledTratmentPost.cancel(false);
        Runnable task = new PostRunnable();
        final int sec = 1;
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
            log.error("Unhandled exception", e);
        }
        return new ArrayList<Treatment>();
    }

    public void createTreatmentFromJsonIfNotExists(JSONObject trJson) {
        try {
            Treatment treatment = new Treatment();
            treatment.source = Source.NIGHTSCOUT;
            treatment.date = roundDateToSec(trJson.getLong("mills"));
            treatment.carbs = trJson.has("carbs") ? trJson.getDouble("carbs") : 0;
            treatment.insulin = trJson.has("insulin") ? trJson.getDouble("insulin") : 0d;
            treatment.pumpId = trJson.has("pumpId") ? trJson.getLong("pumpId") : 0;
            treatment._id = trJson.getString("_id");
            if (trJson.has("isSMB"))
                treatment.isSMB = trJson.getBoolean("isSMB");
            if (trJson.has("eventType")) {
                treatment.mealBolus = !trJson.get("eventType").equals("Correction Bolus");
                double carbs = treatment.carbs;
                if (trJson.has("boluscalc")) {
                    JSONObject boluscalc = trJson.getJSONObject("boluscalc");
                    if (boluscalc.has("carbs")) {
                        carbs = Math.max(boluscalc.getDouble("carbs"), carbs);
                    }
                }
                if (carbs <= 0)
                    treatment.mealBolus = false;
            }
            createOrUpdate(treatment);
        } catch (JSONException e) {
            log.error("Unhandled exception", e);
        }
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
            log.error("Unhandled exception", e);
        }
        return new ArrayList<TempTarget>();
    }

    public boolean createOrUpdate(TempTarget tempTarget) {
        try {
            TempTarget old;
            tempTarget.date = roundDateToSec(tempTarget.date);

            if (tempTarget.source == Source.NIGHTSCOUT) {
                old = getDaoTempTargets().queryForId(tempTarget.date);
                if (old != null) {
                    if (!old.isEqual(tempTarget)) {
                        getDaoTempTargets().delete(old); // need to delete/create because date may change too
                        old.copyFrom(tempTarget);
                        getDaoTempTargets().create(old);
                        log.debug("TEMPTARGET: Updating record by date from: " + Source.getString(tempTarget.source) + " " + old.toString());
                        scheduleTemporaryTargetChange();
                        return true;
                    }
                    return false;
                }
                // find by NS _id
                if (tempTarget._id != null) {
                    QueryBuilder<TempTarget, Long> queryBuilder = getDaoTempTargets().queryBuilder();
                    Where where = queryBuilder.where();
                    where.eq("_id", tempTarget._id);
                    PreparedQuery<TempTarget> preparedQuery = queryBuilder.prepare();
                    List<TempTarget> trList = getDaoTempTargets().query(preparedQuery);
                    if (trList.size() > 0) {
                        old = trList.get(0);
                        if (!old.isEqual(tempTarget)) {
                            getDaoTempTargets().delete(old); // need to delete/create because date may change too
                            old.copyFrom(tempTarget);
                            getDaoTempTargets().create(old);
                            log.debug("TEMPTARGET: Updating record by _id from: " + Source.getString(tempTarget.source) + " " + old.toString());
                            scheduleTemporaryTargetChange();
                            return true;
                        }
                    }
                }
                getDaoTempTargets().create(tempTarget);
                log.debug("TEMPTARGET: New record from: " + Source.getString(tempTarget.source) + " " + tempTarget.toString());
                scheduleTemporaryTargetChange();
                return true;
            }
            if (tempTarget.source == Source.USER) {
                getDaoTempTargets().create(tempTarget);
                log.debug("TEMPTARGET: New record from: " + Source.getString(tempTarget.source) + " " + tempTarget.toString());
                scheduleTemporaryTargetChange();
                return true;
            }
        } catch (SQLException e) {
            log.error("Unhandled exception", e);
        }
        return false;
    }

    public void delete(TempTarget tempTarget) {
        try {
            getDaoTempTargets().delete(tempTarget);
            scheduleTemporaryTargetChange();
        } catch (SQLException e) {
            log.error("Unhandled exception", e);
        }
    }

    private static void scheduleTemporaryTargetChange() {
        class PostRunnable implements Runnable {
            public void run() {
                log.debug("Firing EventTempTargetChange");
                MainApp.bus().post(new EventTempTargetChange());
                scheduledTemTargetPost = null;
            }
        }
        // prepare task for execution in 1 sec
        // cancel waiting task to prevent sending multiple posts
        if (scheduledTemTargetPost != null)
            scheduledTemTargetPost.cancel(false);
        Runnable task = new PostRunnable();
        final int sec = 1;
        scheduledTemTargetPost = tempTargetWorker.schedule(task, sec, TimeUnit.SECONDS);

    }

 /*
 {
    "_id": "58795998aa86647ba4d68ce7",
    "enteredBy": "",
    "eventType": "Temporary Target",
    "reason": "Eating Soon",
    "targetTop": 80,
    "targetBottom": 80,
    "duration": 120,
    "created_at": "2017-01-13T22:50:00.782Z",
    "carbs": null,
    "insulin": null
}
  */

    public void createTemptargetFromJsonIfNotExists(JSONObject trJson) {
        try {
            String units = MainApp.getConfigBuilder().getProfileUnits();
            TempTarget tempTarget = new TempTarget();
            tempTarget.date = trJson.getLong("mills");
            tempTarget.durationInMinutes = trJson.getInt("duration");
            tempTarget.low = Profile.toMgdl(trJson.getDouble("targetBottom"), units);
            tempTarget.high = Profile.toMgdl(trJson.getDouble("targetTop"), units);
            tempTarget.reason = trJson.getString("reason");
            tempTarget._id = trJson.getString("_id");
            tempTarget.source = Source.NIGHTSCOUT;
            createOrUpdate(tempTarget);
        } catch (JSONException e) {
            log.error("Unhandled exception", e);
        }
    }

    public void deleteTempTargetById(String _id) {
        TempTarget stored = findTempTargetById(_id);
        if (stored != null) {
            log.debug("TEMPTARGET: Removing TempTarget record from database: " + stored.toString());
            delete(stored);
            scheduleTemporaryTargetChange();
        }
    }

    public TempTarget findTempTargetById(String _id) {
        try {
            QueryBuilder<TempTarget, Long> queryBuilder = getDaoTempTargets().queryBuilder();
            Where where = queryBuilder.where();
            where.eq("_id", _id);
            PreparedQuery<TempTarget> preparedQuery = queryBuilder.prepare();
            List<TempTarget> list = getDaoTempTargets().query(preparedQuery);

            if (list.size() == 1) {
                return list.get(0);
            } else {
                return null;
            }
        } catch (SQLException e) {
            log.error("Unhandled exception", e);
        }
        return null;
    }

    // ----------------- DanaRHistory handling --------------------

    public void createOrUpdate(DanaRHistoryRecord record) {
        try {
            getDaoDanaRHistory().createOrUpdate(record);
        } catch (SQLException e) {
            log.error("Unhandled exception", e);
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
            log.error("Unhandled exception", e);
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
        } catch (SQLException | JSONException e) {
            log.error("Unhandled exception", e);
        }
    }

    // ------------ TemporaryBasal handling ---------------

    //return true if new record was created
    public boolean createOrUpdate(TemporaryBasal tempBasal) {
        try {
            TemporaryBasal old;
            tempBasal.date = roundDateToSec(tempBasal.date);

            if (tempBasal.source == Source.PUMP) {
                // check for changed from pump change in NS
                QueryBuilder<TemporaryBasal, Long> queryBuilder = getDaoTemporaryBasal().queryBuilder();
                Where where = queryBuilder.where();
                where.eq("pumpId", tempBasal.pumpId);
                PreparedQuery<TemporaryBasal> preparedQuery = queryBuilder.prepare();
                List<TemporaryBasal> trList = getDaoTemporaryBasal().query(preparedQuery);
                if (trList.size() > 0) {
                    // do nothing, pump history record cannot be changed
                    log.debug("TEMPBASAL: Already exists from: " + Source.getString(tempBasal.source) + " " + tempBasal.toString());
                    return false;
                }
                getDaoTemporaryBasal().create(tempBasal);
                log.debug("TEMPBASAL: New record from: " + Source.getString(tempBasal.source) + " " + tempBasal.toString());
                updateEarliestDataChange(tempBasal.date);
                scheduleTemporaryBasalChange();
                return true;
            }
            if (tempBasal.source == Source.NIGHTSCOUT) {
                old = getDaoTemporaryBasal().queryForId(tempBasal.date);
                if (old != null) {
                    if (!old.isAbsolute && tempBasal.isAbsolute) { // converted to absolute by "ns_sync_use_absolute"
                        // so far ignore, do not convert back because it may not be accurate
                        return false;
                    }
                    if (!old.isEqual(tempBasal)) {
                        long oldDate = old.date;
                        getDaoTemporaryBasal().delete(old); // need to delete/create because date may change too
                        old.copyFrom(tempBasal);
                        getDaoTemporaryBasal().create(old);
                        log.debug("TEMPBASAL: Updating record by date from: " + Source.getString(tempBasal.source) + " " + old.toString());
                        updateEarliestDataChange(oldDate);
                        updateEarliestDataChange(old.date);
                        scheduleTemporaryBasalChange();
                        return true;
                    }
                    return false;
                }
                // find by NS _id
                if (tempBasal._id != null) {
                    QueryBuilder<TemporaryBasal, Long> queryBuilder = getDaoTemporaryBasal().queryBuilder();
                    Where where = queryBuilder.where();
                    where.eq("_id", tempBasal._id);
                    PreparedQuery<TemporaryBasal> preparedQuery = queryBuilder.prepare();
                    List<TemporaryBasal> trList = getDaoTemporaryBasal().query(preparedQuery);
                    if (trList.size() > 0) {
                        old = trList.get(0);
                        if (!old.isEqual(tempBasal)) {
                            long oldDate = old.date;
                            getDaoTemporaryBasal().delete(old); // need to delete/create because date may change too
                            old.copyFrom(tempBasal);
                            getDaoTemporaryBasal().create(old);
                            log.debug("TEMPBASAL: Updating record by _id from: " + Source.getString(tempBasal.source) + " " + old.toString());
                            updateEarliestDataChange(oldDate);
                            updateEarliestDataChange(old.date);
                            scheduleTemporaryBasalChange();
                            return true;
                        }
                    }
                }
                getDaoTemporaryBasal().create(tempBasal);
                log.debug("TEMPBASAL: New record from: " + Source.getString(tempBasal.source) + " " + tempBasal.toString());
                updateEarliestDataChange(tempBasal.date);
                scheduleTemporaryBasalChange();
                return true;
            }
            if (tempBasal.source == Source.USER) {
                getDaoTemporaryBasal().create(tempBasal);
                log.debug("TEMPBASAL: New record from: " + Source.getString(tempBasal.source) + " " + tempBasal.toString());
                updateEarliestDataChange(tempBasal.date);
                scheduleTemporaryBasalChange();
                return true;
            }
        } catch (SQLException e) {
            log.error("Unhandled exception", e);
        }
        return false;
    }

    public void delete(TemporaryBasal tempBasal) {
        try {
            getDaoTemporaryBasal().delete(tempBasal);
            updateEarliestDataChange(tempBasal.date);
        } catch (SQLException e) {
            log.error("Unhandled exception", e);
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
            log.error("Unhandled exception", e);
        }
        return new ArrayList<TemporaryBasal>();
    }

    private static void scheduleTemporaryBasalChange() {
        class PostRunnable implements Runnable {
            public void run() {
                log.debug("Firing EventTempBasalChange");
                MainApp.bus().post(new EventReloadTempBasalData());
                MainApp.bus().post(new EventTempBasalChange());
                if (earliestDataChange != null)
                    MainApp.bus().post(new EventNewHistoryData(earliestDataChange));
                earliestDataChange = null;
                scheduledTemBasalsPost = null;
            }
        }
        // prepare task for execution in 1 sec
        // cancel waiting task to prevent sending multiple posts
        if (scheduledTemBasalsPost != null)
            scheduledTemBasalsPost.cancel(false);
        Runnable task = new PostRunnable();
        final int sec = 1;
        scheduledTemBasalsPost = tempBasalsWorker.schedule(task, sec, TimeUnit.SECONDS);

    }

    /*
    {
        "_id": "59232e1ddd032d04218dab00",
        "eventType": "Temp Basal",
        "duration": 60,
        "percent": -50,
        "created_at": "2017-05-22T18:29:57Z",
        "enteredBy": "AndroidAPS",
        "notes": "Basal Temp Start 50% 60.0 min",
        "NSCLIENT_ID": 1495477797863,
        "mills": 1495477797000,
        "mgdl": 194.5,
        "endmills": 1495481397000
    }
    */

    public void createTempBasalFromJsonIfNotExists(JSONObject trJson) {
        try {
            if (trJson.has("originalExtendedAmount")) { // extended bolus uploaded as temp basal
                ExtendedBolus extendedBolus = new ExtendedBolus();
                extendedBolus.source = Source.NIGHTSCOUT;
                extendedBolus.date = trJson.getLong("mills");
                extendedBolus.pumpId = trJson.has("pumpId") ? trJson.getLong("pumpId") : 0;
                extendedBolus.durationInMinutes = trJson.getInt("duration");
                extendedBolus.insulin = trJson.getDouble("originalExtendedAmount");
                extendedBolus._id = trJson.getString("_id");
                if (!VirtualPumpPlugin.getFakingStatus()) {
                    VirtualPumpPlugin.setFakingStatus(true);
                    updateEarliestDataChange(0);
                    scheduleTemporaryBasalChange();
                }
                createOrUpdate(extendedBolus);
            } else if (trJson.has("isFakedTempBasal")) { // extended bolus end uploaded as temp basal end
                ExtendedBolus extendedBolus = new ExtendedBolus();
                extendedBolus.source = Source.NIGHTSCOUT;
                extendedBolus.date = trJson.getLong("mills");
                extendedBolus.pumpId = trJson.has("pumpId") ? trJson.getLong("pumpId") : 0;
                extendedBolus.durationInMinutes = 0;
                extendedBolus.insulin = 0;
                extendedBolus._id = trJson.getString("_id");
                if (!VirtualPumpPlugin.getFakingStatus()) {
                    VirtualPumpPlugin.setFakingStatus(true);
                    updateEarliestDataChange(0);
                    scheduleTemporaryBasalChange();
                }
                createOrUpdate(extendedBolus);
            } else {
                TemporaryBasal tempBasal = new TemporaryBasal();
                tempBasal.date = trJson.getLong("mills");
                tempBasal.source = Source.NIGHTSCOUT;
                tempBasal.pumpId = trJson.has("pumpId") ? trJson.getLong("pumpId") : 0;
                if (trJson.has("duration")) {
                    tempBasal.durationInMinutes = trJson.getInt("duration");
                }
                if (trJson.has("percent")) {
                    tempBasal.percentRate = trJson.getInt("percent") + 100;
                    tempBasal.isAbsolute = false;
                }
                if (trJson.has("absolute")) {
                    tempBasal.absoluteRate = trJson.getDouble("absolute");
                    tempBasal.isAbsolute = true;
                }
                tempBasal._id = trJson.getString("_id");
                createOrUpdate(tempBasal);
            }
        } catch (JSONException e) {
            log.error("Unhandled exception", e);
        }
    }

    public void deleteTempBasalById(String _id) {
        TemporaryBasal stored = findTempBasalById(_id);
        if (stored != null) {
            log.debug("TEMPBASAL: Removing TempBasal record from database: " + stored.toString());
            delete(stored);
            updateEarliestDataChange(stored.date);
            scheduleTemporaryBasalChange();
        }
    }

    public TemporaryBasal findTempBasalById(String _id) {
        try {
            QueryBuilder<TemporaryBasal, Long> queryBuilder = null;
            queryBuilder = getDaoTemporaryBasal().queryBuilder();
            Where where = queryBuilder.where();
            where.eq("_id", _id);
            PreparedQuery<TemporaryBasal> preparedQuery = queryBuilder.prepare();
            List<TemporaryBasal> list = getDaoTemporaryBasal().query(preparedQuery);

            if (list.size() != 1) {
                return null;
            } else {
                return list.get(0);
            }
        } catch (SQLException e) {
            log.error("Unhandled exception", e);
        }
        return null;
    }

    // ------------ ExtendedBolus handling ---------------

    public boolean createOrUpdate(ExtendedBolus extendedBolus) {
        try {
            ExtendedBolus old;
            extendedBolus.date = roundDateToSec(extendedBolus.date);

            if (extendedBolus.source == Source.PUMP) {
                // check for changed from pump change in NS
                QueryBuilder<ExtendedBolus, Long> queryBuilder = getDaoExtendedBolus().queryBuilder();
                Where where = queryBuilder.where();
                where.eq("pumpId", extendedBolus.pumpId);
                PreparedQuery<ExtendedBolus> preparedQuery = queryBuilder.prepare();
                List<ExtendedBolus> trList = getDaoExtendedBolus().query(preparedQuery);
                if (trList.size() > 0) {
                    // do nothing, pump history record cannot be changed
                    return false;
                }
                getDaoExtendedBolus().create(extendedBolus);
                log.debug("EXTENDEDBOLUS: New record from: " + Source.getString(extendedBolus.source) + " " + extendedBolus.toString());
                updateEarliestDataChange(extendedBolus.date);
                scheduleExtendedBolusChange();
                return true;
            }
            if (extendedBolus.source == Source.NIGHTSCOUT) {
                old = getDaoExtendedBolus().queryForId(extendedBolus.date);
                if (old != null) {
                    if (!old.isEqual(extendedBolus)) {
                        long oldDate = old.date;
                        getDaoExtendedBolus().delete(old); // need to delete/create because date may change too
                        old.copyFrom(extendedBolus);
                        getDaoExtendedBolus().create(old);
                        log.debug("EXTENDEDBOLUS: Updating record by date from: " + Source.getString(extendedBolus.source) + " " + old.toString());
                        updateEarliestDataChange(oldDate);
                        updateEarliestDataChange(old.date);
                        scheduleExtendedBolusChange();
                        return true;
                    }
                    return false;
                }
                // find by NS _id
                if (extendedBolus._id != null) {
                    QueryBuilder<ExtendedBolus, Long> queryBuilder = getDaoExtendedBolus().queryBuilder();
                    Where where = queryBuilder.where();
                    where.eq("_id", extendedBolus._id);
                    PreparedQuery<ExtendedBolus> preparedQuery = queryBuilder.prepare();
                    List<ExtendedBolus> trList = getDaoExtendedBolus().query(preparedQuery);
                    if (trList.size() > 0) {
                        old = trList.get(0);
                        if (!old.isEqual(extendedBolus)) {
                            long oldDate = old.date;
                            getDaoExtendedBolus().delete(old); // need to delete/create because date may change too
                            old.copyFrom(extendedBolus);
                            getDaoExtendedBolus().create(old);
                            log.debug("EXTENDEDBOLUS: Updating record by _id from: " + Source.getString(extendedBolus.source) + " " + old.toString());
                            updateEarliestDataChange(oldDate);
                            updateEarliestDataChange(old.date);
                            scheduleExtendedBolusChange();
                            return true;
                        }
                    }
                }
                getDaoExtendedBolus().create(extendedBolus);
                log.debug("EXTENDEDBOLUS: New record from: " + Source.getString(extendedBolus.source) + " " + extendedBolus.toString());
                updateEarliestDataChange(extendedBolus.date);
                scheduleExtendedBolusChange();
                return true;
            }
            if (extendedBolus.source == Source.USER) {
                getDaoExtendedBolus().create(extendedBolus);
                log.debug("EXTENDEDBOLUS: New record from: " + Source.getString(extendedBolus.source) + " " + extendedBolus.toString());
                updateEarliestDataChange(extendedBolus.date);
                scheduleExtendedBolusChange();
                return true;
            }
        } catch (SQLException e) {
            log.error("Unhandled exception", e);
        }
        return false;
    }

    public void delete(ExtendedBolus extendedBolus) {
        try {
            getDaoExtendedBolus().delete(extendedBolus);
            updateEarliestDataChange(extendedBolus.date);
        } catch (SQLException e) {
            log.error("Unhandled exception", e);
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
            log.error("Unhandled exception", e);
        }
        return new ArrayList<ExtendedBolus>();
    }

    public void deleteExtendedBolusById(String _id) {
        ExtendedBolus stored = findExtendedBolusById(_id);
        if (stored != null) {
            log.debug("EXTENDEDBOLUS: Removing ExtendedBolus record from database: " + stored.toString());
            delete(stored);
            updateEarliestDataChange(stored.date);
            scheduleExtendedBolusChange();
        }
    }

    public ExtendedBolus findExtendedBolusById(String _id) {
        try {
            QueryBuilder<ExtendedBolus, Long> queryBuilder = null;
            queryBuilder = getDaoExtendedBolus().queryBuilder();
            Where where = queryBuilder.where();
            where.eq("_id", _id);
            PreparedQuery<ExtendedBolus> preparedQuery = queryBuilder.prepare();
            List<ExtendedBolus> list = getDaoExtendedBolus().query(preparedQuery);

            if (list.size() == 1) {
                return list.get(0);
            } else {
                return null;
            }
        } catch (SQLException e) {
            log.error("Unhandled exception", e);
        }
        return null;
    }

    /*
{
    "_id": "5924898d577eb0880e355337",
    "eventType": "Combo Bolus",
    "duration": 120,
    "splitNow": 0,
    "splitExt": 100,
    "enteredinsulin": 1,
    "relative": 1,
    "created_at": "2017-05-23T19:12:14Z",
    "enteredBy": "AndroidAPS",
    "NSCLIENT_ID": 1495566734628,
    "mills": 1495566734000,
    "mgdl": 106
}
     */

    public void createExtendedBolusFromJsonIfNotExists(JSONObject trJson) {
        try {
            QueryBuilder<ExtendedBolus, Long> queryBuilder = null;
            queryBuilder = getDaoExtendedBolus().queryBuilder();
            Where where = queryBuilder.where();
            where.eq("_id", trJson.getString("_id")).or().eq("date", trJson.getLong("mills"));
            PreparedQuery<ExtendedBolus> preparedQuery = queryBuilder.prepare();
            List<ExtendedBolus> list = getDaoExtendedBolus().query(preparedQuery);
            ExtendedBolus extendedBolus;
            if (list.size() == 0) {
                extendedBolus = new ExtendedBolus();
                extendedBolus.source = Source.NIGHTSCOUT;
                if (Config.logIncommingData)
                    log.debug("Adding ExtendedBolus record to database: " + trJson.toString());
                // Record does not exists. add
            } else if (list.size() == 1) {
                extendedBolus = list.get(0);
                if (Config.logIncommingData)
                    log.debug("Updating ExtendedBolus record in database: " + trJson.toString());
            } else {
                log.error("Something went wrong");
                return;
            }
            extendedBolus.date = trJson.getLong("mills");
            extendedBolus.durationInMinutes = trJson.has("duration") ? trJson.getInt("duration") : 0;
            extendedBolus.insulin = trJson.getDouble("relative");
            extendedBolus._id = trJson.getString("_id");
            createOrUpdate(extendedBolus);
        } catch (SQLException | JSONException e) {
            log.error("Unhandled exception", e);
        }
    }

    private static void scheduleExtendedBolusChange() {
        class PostRunnable implements Runnable {
            public void run() {
                log.debug("Firing EventExtendedBolusChange");
                MainApp.bus().post(new EventReloadTreatmentData(new EventExtendedBolusChange()));
                if (earliestDataChange != null)
                    MainApp.bus().post(new EventNewHistoryData(earliestDataChange));
                earliestDataChange = null;
                scheduledExtendedBolusPost = null;
            }
        }
        // prepare task for execution in 1 sec
        // cancel waiting task to prevent sending multiple posts
        if (scheduledExtendedBolusPost != null)
            scheduledExtendedBolusPost.cancel(false);
        Runnable task = new PostRunnable();
        final int sec = 1;
        scheduledExtendedBolusPost = extendedBolusWorker.schedule(task, sec, TimeUnit.SECONDS);

    }


    // ------------ CareportalEvent handling ---------------

    public void createOrUpdate(CareportalEvent careportalEvent) {
        careportalEvent.date = careportalEvent.date - careportalEvent.date % 1000;
        try {
            getDaoCareportalEvents().createOrUpdate(careportalEvent);
        } catch (SQLException e) {
            log.error("Unhandled exception", e);
        }
        scheduleCareportalEventChange();
    }

    public void delete(CareportalEvent careportalEvent) {
        try {
            getDaoCareportalEvents().delete(careportalEvent);
        } catch (SQLException e) {
            log.error("Unhandled exception", e);
        }
        scheduleCareportalEventChange();
    }

    @Nullable
    public CareportalEvent getLastCareportalEvent(String event) {
        try {
            List<CareportalEvent> careportalEvents;
            QueryBuilder<CareportalEvent, Long> queryBuilder = getDaoCareportalEvents().queryBuilder();
            queryBuilder.orderBy("date", false);
            Where where = queryBuilder.where();
            where.eq("eventType", event);
            queryBuilder.limit(1L);
            PreparedQuery<CareportalEvent> preparedQuery = queryBuilder.prepare();
            careportalEvents = getDaoCareportalEvents().query(preparedQuery);
            if (careportalEvents.size() == 1)
                return careportalEvents.get(0);
            else
                return null;
        } catch (SQLException e) {
            log.error("Unhandled exception", e);
        }
        return null;
    }

    public List<CareportalEvent> getCareportalEventsFromTime(long mills, boolean ascending) {
        try {
            List<CareportalEvent> careportalEvents;
            QueryBuilder<CareportalEvent, Long> queryBuilder = getDaoCareportalEvents().queryBuilder();
            queryBuilder.orderBy("date", ascending);
            Where where = queryBuilder.where();
            where.ge("date", mills);
            PreparedQuery<CareportalEvent> preparedQuery = queryBuilder.prepare();
            careportalEvents = getDaoCareportalEvents().query(preparedQuery);
            return careportalEvents;
        } catch (SQLException e) {
            log.error("Unhandled exception", e);
        }
        return new ArrayList<>();
    }

    public List<CareportalEvent> getCareportalEventsFromTime(boolean ascending) {
        try {
            List<CareportalEvent> careportalEvents;
            QueryBuilder<CareportalEvent, Long> queryBuilder = getDaoCareportalEvents().queryBuilder();
            queryBuilder.orderBy("date", ascending);
            PreparedQuery<CareportalEvent> preparedQuery = queryBuilder.prepare();
            careportalEvents = getDaoCareportalEvents().query(preparedQuery);
            return careportalEvents;
        } catch (SQLException e) {
            log.error("Unhandled exception", e);
        }
        return new ArrayList<>();
    }

    public void deleteCareportalEventById(String _id) {
        try {
            QueryBuilder<CareportalEvent, Long> queryBuilder = null;
            queryBuilder = getDaoCareportalEvents().queryBuilder();
            Where where = queryBuilder.where();
            where.eq("_id", _id);
            PreparedQuery<CareportalEvent> preparedQuery = queryBuilder.prepare();
            List<CareportalEvent> list = getDaoCareportalEvents().query(preparedQuery);

            if (list.size() == 1) {
                CareportalEvent record = list.get(0);
                if (Config.logIncommingData)
                    log.debug("Removing CareportalEvent record from database: " + record.log());
                delete(record);
            } else {
                if (Config.logIncommingData)
                    log.debug("CareportalEvent not found database: " + _id);
            }
        } catch (SQLException e) {
            log.error("Unhandled exception", e);
        }
    }

    public void createCareportalEventFromJsonIfNotExists(JSONObject trJson) {
        try {
            QueryBuilder<CareportalEvent, Long> queryBuilder = null;
            queryBuilder = getDaoCareportalEvents().queryBuilder();
            Where where = queryBuilder.where();
            where.eq("_id", trJson.getString("_id")).or().eq("date", trJson.getLong("mills"));
            PreparedQuery<CareportalEvent> preparedQuery = queryBuilder.prepare();
            List<CareportalEvent> list = getDaoCareportalEvents().query(preparedQuery);
            CareportalEvent careportalEvent;
            if (list.size() == 0) {
                careportalEvent = new CareportalEvent();
                careportalEvent.source = Source.NIGHTSCOUT;
                if (Config.logIncommingData)
                    log.debug("Adding CareportalEvent record to database: " + trJson.toString());
                // Record does not exists. add
            } else if (list.size() == 1) {
                careportalEvent = list.get(0);
                if (Config.logIncommingData)
                    log.debug("Updating CareportalEvent record in database: " + trJson.toString());
            } else {
                log.error("Something went wrong");
                return;
            }
            careportalEvent.date = trJson.getLong("mills");
            careportalEvent.eventType = trJson.getString("eventType");
            careportalEvent.json = trJson.toString();
            careportalEvent._id = trJson.getString("_id");
            createOrUpdate(careportalEvent);
        } catch (SQLException | JSONException e) {
            log.error("Unhandled exception", e);
        }
    }

    private static void scheduleCareportalEventChange() {
        class PostRunnable implements Runnable {
            public void run() {
                log.debug("Firing scheduleCareportalEventChange");
                MainApp.bus().post(new EventCareportalEventChange());
                scheduledCareportalEventPost = null;
            }
        }
        // prepare task for execution in 1 sec
        // cancel waiting task to prevent sending multiple posts
        if (scheduledCareportalEventPost != null)
            scheduledCareportalEventPost.cancel(false);
        Runnable task = new PostRunnable();
        final int sec = 1;
        scheduledCareportalEventPost = careportalEventWorker.schedule(task, sec, TimeUnit.SECONDS);

    }

    // ---------------- ProfileSwitch handling ---------------

    public List<ProfileSwitch> getProfileSwitchData(boolean ascending) {
        try {
            Dao<ProfileSwitch, Long> daoProfileSwitch = getDaoProfileSwitch();
            List<ProfileSwitch> profileSwitches;
            QueryBuilder<ProfileSwitch, Long> queryBuilder = daoProfileSwitch.queryBuilder();
            queryBuilder.orderBy("date", ascending);
            queryBuilder.limit(20L);
            PreparedQuery<ProfileSwitch> preparedQuery = queryBuilder.prepare();
            profileSwitches = daoProfileSwitch.query(preparedQuery);
            return profileSwitches;
        } catch (SQLException e) {
            log.error("Unhandled exception", e);
        }
        return new ArrayList<ProfileSwitch>();
    }

    public boolean createOrUpdate(ProfileSwitch profileSwitch) {
        try {
            ProfileSwitch old;
            profileSwitch.date = roundDateToSec(profileSwitch.date);

            if (profileSwitch.source == Source.NIGHTSCOUT) {
                old = getDaoProfileSwitch().queryForId(profileSwitch.date);
                if (old != null) {
                    if (!old.isEqual(profileSwitch)) {
                        profileSwitch.source = old.source;
                        profileSwitch.profileName = old.profileName; // preserver profileName to prevent multiple CPP extension
                        getDaoProfileSwitch().delete(old); // need to delete/create because date may change too
                        getDaoProfileSwitch().create(profileSwitch);
                        log.debug("PROFILESWITCH: Updating record by date from: " + Source.getString(profileSwitch.source) + " " + old.toString());
                        scheduleProfileSwitchChange();
                        return true;
                    }
                    return false;
                }
                // find by NS _id
                if (profileSwitch._id != null) {
                    QueryBuilder<ProfileSwitch, Long> queryBuilder = getDaoProfileSwitch().queryBuilder();
                    Where where = queryBuilder.where();
                    where.eq("_id", profileSwitch._id);
                    PreparedQuery<ProfileSwitch> preparedQuery = queryBuilder.prepare();
                    List<ProfileSwitch> trList = getDaoProfileSwitch().query(preparedQuery);
                    if (trList.size() > 0) {
                        old = trList.get(0);
                        if (!old.isEqual(profileSwitch)) {
                            getDaoProfileSwitch().delete(old); // need to delete/create because date may change too
                            old.copyFrom(profileSwitch);
                            getDaoProfileSwitch().create(old);
                            log.debug("PROFILESWITCH: Updating record by _id from: " + Source.getString(profileSwitch.source) + " " + old.toString());
                            scheduleProfileSwitchChange();
                            return true;
                        }
                    }
                }
                // look for already added percentage from NS
                profileSwitch.profileName = PercentageSplitter.pureName(profileSwitch.profileName);
                getDaoProfileSwitch().create(profileSwitch);
                log.debug("PROFILESWITCH: New record from: " + Source.getString(profileSwitch.source) + " " + profileSwitch.toString());
                scheduleProfileSwitchChange();
                return true;
            }
            if (profileSwitch.source == Source.USER) {
                getDaoProfileSwitch().create(profileSwitch);
                log.debug("PROFILESWITCH: New record from: " + Source.getString(profileSwitch.source) + " " + profileSwitch.toString());
                scheduleProfileSwitchChange();
                return true;
            }
        } catch (SQLException e) {
            log.error("Unhandled exception", e);
        }
        return false;
    }

    public void delete(ProfileSwitch profileSwitch) {
        try {
            getDaoProfileSwitch().delete(profileSwitch);
            scheduleProfileSwitchChange();
        } catch (SQLException e) {
            log.error("Unhandled exception", e);
        }
    }

    private static void scheduleProfileSwitchChange() {
        class PostRunnable implements Runnable {
            public void run() {
                log.debug("Firing EventProfileSwitchChange");
                MainApp.bus().post(new EventReloadProfileSwitchData());
                MainApp.bus().post(new EventProfileSwitchChange());
                scheduledProfileSwitchEventPost = null;
            }
        }
        // prepare task for execution in 1 sec
        // cancel waiting task to prevent sending multiple posts
        if (scheduledProfileSwitchEventPost != null)
            scheduledProfileSwitchEventPost.cancel(false);
        Runnable task = new PostRunnable();
        final int sec = 1;
        scheduledProfileSwitchEventPost = profileSwitchEventWorker.schedule(task, sec, TimeUnit.SECONDS);

    }

 /*
{
    "_id":"592fa43ed97496a80da913d2",
    "created_at":"2017-06-01T05:20:06Z",
    "eventType":"Profile Switch",
    "profile":"2016 +30%",
    "units":"mmol",
    "enteredBy":"sony",
    "NSCLIENT_ID":1496294454309,
}
  */

    public void createProfileSwitchFromJsonIfNotExists(JSONObject trJson) {
        try {
            ProfileSwitch profileSwitch = new ProfileSwitch();
            profileSwitch.date = trJson.getLong("mills");
            if (trJson.has("duration"))
                profileSwitch.durationInMinutes = trJson.getInt("duration");
            profileSwitch._id = trJson.getString("_id");
            profileSwitch.profileName = trJson.getString("profile");
            profileSwitch.isCPP = trJson.has("CircadianPercentageProfile");
            profileSwitch.source = Source.NIGHTSCOUT;
            if (trJson.has("timeshift"))
                profileSwitch.timeshift = trJson.getInt("timeshift");
            if (trJson.has("percentage"))
                profileSwitch.percentage = trJson.getInt("percentage");
            if (trJson.has("profileJson"))
                profileSwitch.profileJson = trJson.getString("profileJson");
            else {
                ProfileStore store = ConfigBuilderPlugin.getActiveProfileInterface().getProfile();
                Profile profile = store.getSpecificProfile(profileSwitch.profileName);
                if (profile != null) {
                    profileSwitch.profileJson = profile.getData().toString();
                    log.debug("Profile switch prefilled with JSON from local store");
                    // Update data in NS
                    NSUpload.updateProfileSwitch(profileSwitch);
                } else {
                    log.debug("JSON for profile switch doesn't exist. Ignoring: " + trJson.toString());
                    return;
                }
            }
            if (trJson.has("profilePlugin"))
                profileSwitch.profilePlugin = trJson.getString("profilePlugin");
            createOrUpdate(profileSwitch);
        } catch (JSONException e) {
            log.error("Unhandled exception", e);
        }
    }

    public void deleteProfileSwitchById(String _id) {
        ProfileSwitch stored = findProfileSwitchById(_id);
        if (stored != null) {
            log.debug("PROFILESWITCH: Removing ProfileSwitch record from database: " + stored.toString());
            delete(stored);
            scheduleTemporaryTargetChange();
        }
    }

    public ProfileSwitch findProfileSwitchById(String _id) {
        try {
            QueryBuilder<ProfileSwitch, Long> queryBuilder = getDaoProfileSwitch().queryBuilder();
            Where where = queryBuilder.where();
            where.eq("_id", _id);
            PreparedQuery<ProfileSwitch> preparedQuery = queryBuilder.prepare();
            List<ProfileSwitch> list = getDaoProfileSwitch().query(preparedQuery);

            if (list.size() == 1) {
                return list.get(0);
            } else {
                return null;
            }
        } catch (SQLException e) {
            log.error("Unhandled exception", e);
        }
        return null;
    }

    // ---------------- Food handling ---------------
}
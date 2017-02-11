package info.nightscout.androidaps.db;

import android.content.Context;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.support.annotation.Nullable;

import com.j256.ormlite.android.apptools.OrmLiteSqliteOpenHelper;
import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.stmt.PreparedQuery;
import com.j256.ormlite.stmt.QueryBuilder;
import com.j256.ormlite.stmt.Where;
import com.j256.ormlite.support.ConnectionSource;
import com.j256.ormlite.table.TableUtils;

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
import info.nightscout.androidaps.events.EventTreatmentChange;

public class DatabaseHelper extends OrmLiteSqliteOpenHelper {
    private static Logger log = LoggerFactory.getLogger(DatabaseHelper.class);

    public static final String DATABASE_NAME = "AndroidAPSDb";
    public static final String DATABASE_BGREADINGS = "BgReadings";
    public static final String DATABASE_TEMPBASALS = "TempBasals";
    public static final String DATABASE_TEMPTARGETS = "TempTargets";
    public static final String DATABASE_TREATMENTS = "Treatments";
    public static final String DATABASE_DANARHISTORY = "DanaRHistory";

    private static final int DATABASE_VERSION = 5;

    private long latestTreatmentChange = 0;

    private static final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor();
    private static ScheduledFuture<?> scheduledPost = null;

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        onCreate(getWritableDatabase(), getConnectionSource());
    }


    @Override
    public void onCreate(SQLiteDatabase database, ConnectionSource connectionSource) {
        try {
            log.info("onCreate");
            TableUtils.createTableIfNotExists(connectionSource, TempBasal.class);
            TableUtils.createTableIfNotExists(connectionSource, TempTarget.class);
            TableUtils.createTableIfNotExists(connectionSource, Treatment.class);
            TableUtils.createTableIfNotExists(connectionSource, BgReading.class);
            TableUtils.createTableIfNotExists(connectionSource, DanaRHistoryRecord.class);
        } catch (SQLException e) {
            log.error("Can't create database", e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public void onUpgrade(SQLiteDatabase database, ConnectionSource connectionSource, int oldVersion, int newVersion) {
        try {
            log.info(DatabaseHelper.class.getName(), "onUpgrade");
            TableUtils.dropTable(connectionSource, TempBasal.class, true);
            TableUtils.dropTable(connectionSource, TempTarget.class, true);
            TableUtils.dropTable(connectionSource, Treatment.class, true);
            TableUtils.dropTable(connectionSource, BgReading.class, true);
            TableUtils.dropTable(connectionSource, DanaRHistoryRecord.class, true);
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
        getWritableDatabase().delete(DATABASE_BGREADINGS, "timeIndex" + " < '" + (new Date().getTime() - Constants.hoursToKeepInDatabase * 60 * 60 * 1000L) + "'", null);
        log.debug("After BgReadings size: " + DatabaseUtils.queryNumEntries(getReadableDatabase(), DATABASE_BGREADINGS));

        log.debug("Before TempBasals size: " + DatabaseUtils.queryNumEntries(getReadableDatabase(), DATABASE_TEMPBASALS));
        getWritableDatabase().delete(DATABASE_TEMPBASALS, "timeIndex" + " < '" + (new Date().getTime() - Constants.hoursToKeepInDatabase * 60 * 60 * 1000L) + "'", null);
        log.debug("After TempBasals size: " + DatabaseUtils.queryNumEntries(getReadableDatabase(), DATABASE_TEMPBASALS));

        log.debug("Before TempTargets size: " + DatabaseUtils.queryNumEntries(getReadableDatabase(), DATABASE_TEMPTARGETS));
        getWritableDatabase().delete(DATABASE_TEMPTARGETS, "timeIndex" + " < '" + (new Date().getTime() - Constants.hoursToKeepInDatabase * 60 * 60 * 1000L) + "'", null);
        log.debug("After TempTargets size: " + DatabaseUtils.queryNumEntries(getReadableDatabase(), DATABASE_TEMPTARGETS));

        log.debug("Before Treatments size: " + DatabaseUtils.queryNumEntries(getReadableDatabase(), DATABASE_TREATMENTS));
        getWritableDatabase().delete(DATABASE_TREATMENTS, "timeIndex" + " < '" + (new Date().getTime() - Constants.hoursToKeepInDatabase * 60 * 60 * 1000L) + "'", null);
        log.debug("After Treatments size: " + DatabaseUtils.queryNumEntries(getReadableDatabase(), DATABASE_TREATMENTS));

        log.debug("Before History size: " + DatabaseUtils.queryNumEntries(getReadableDatabase(), DATABASE_DANARHISTORY));
        getWritableDatabase().delete(DATABASE_DANARHISTORY, "recordDate" + " < '" + (new Date().getTime() - Constants.daysToKeepHistoryInDatabase * 24 * 60 * 60 * 1000L) + "'", null);
        log.debug("After History size: " + DatabaseUtils.queryNumEntries(getReadableDatabase(), DATABASE_DANARHISTORY));
    }

    public void resetDatabases() {
        try {
            TableUtils.dropTable(connectionSource, TempBasal.class, true);
            TableUtils.dropTable(connectionSource, TempTarget.class, true);
            TableUtils.dropTable(connectionSource, Treatment.class, true);
            TableUtils.dropTable(connectionSource, BgReading.class, true);
            TableUtils.dropTable(connectionSource, DanaRHistoryRecord.class, true);
            TableUtils.createTableIfNotExists(connectionSource, TempBasal.class);
            TableUtils.createTableIfNotExists(connectionSource, TempTarget.class);
            TableUtils.createTableIfNotExists(connectionSource, Treatment.class);
            TableUtils.createTableIfNotExists(connectionSource, BgReading.class);
            TableUtils.createTableIfNotExists(connectionSource, DanaRHistoryRecord.class);
            latestTreatmentChange = 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void resetTreatments() {
        try {
            TableUtils.dropTable(connectionSource, Treatment.class, true);
            TableUtils.createTableIfNotExists(connectionSource, Treatment.class);
            latestTreatmentChange = 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void resetTempTargets() {
        try {
            TableUtils.dropTable(connectionSource, TempTarget.class, true);
            TableUtils.createTableIfNotExists(connectionSource, TempTarget.class);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public Dao<TempBasal, Long> getDaoTempBasals() throws SQLException {
        return getDao(TempBasal.class);
    }

    public Dao<TempTarget, Long> getDaoTempTargets() throws SQLException {
        return getDao(TempTarget.class);
    }

    private Dao<Treatment, Long> getDaoTreatments() throws SQLException {
        return getDao(Treatment.class);
    }

    public Dao<BgReading, Long> getDaoBgReadings() throws SQLException {
        return getDao(BgReading.class);
    }

    public Dao<DanaRHistoryRecord, String> getDaoDanaRHistory() throws SQLException {
        return getDao(DanaRHistoryRecord.class);
    }

    public List<BgReading> getBgreadingsDataFromTime(long mills, boolean ascending) {
        try {
            Dao<BgReading, Long> daoBgreadings = getDaoBgReadings();
            List<BgReading> bgReadings;
            QueryBuilder<BgReading, Long> queryBuilder = daoBgreadings.queryBuilder();
            queryBuilder.orderBy("timeIndex", ascending);
            Where where = queryBuilder.where();
            where.ge("timeIndex", mills).and().gt("value", 38);
            PreparedQuery<BgReading> preparedQuery = queryBuilder.prepare();
            bgReadings = daoBgreadings.query(preparedQuery);
            return bgReadings;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return new ArrayList<BgReading>();
    }

    // TREATMENT HANDLING

    public boolean isDataUnchanged(long time) {
        if (time >= latestTreatmentChange) return true;
        else return false;
    }

    public int update(Treatment treatment) {
        int updated = 0;
        try {
            updated = getDaoTreatments().update(treatment);
            latestTreatmentChange = treatment.getTimeIndex();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        scheduleTreatmentChange();
        return updated;
    }

    public Dao.CreateOrUpdateStatus createOrUpdate(Treatment treatment) {
        Dao.CreateOrUpdateStatus status = null;
        try {
            status = getDaoTreatments().createOrUpdate(treatment);
            latestTreatmentChange = treatment.getTimeIndex();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        scheduleTreatmentChange();
        return status;
    }

    public void create(Treatment treatment) {
        try {
            getDaoTreatments().create(treatment);
            latestTreatmentChange = treatment.getTimeIndex();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        scheduleTreatmentChange();
    }

    public int delete(String _id) {
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
            latestTreatmentChange = stored.getTimeIndex();
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
            where.eq("timeIndex", timeIndex);
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
                scheduledPost = null;
            }
        }
        // prepare task for execution in 5 sec
        // cancel waiting task to prevent sending multiple posts
        if (scheduledPost != null)
            scheduledPost.cancel(false);
        Runnable task = new PostRunnable();
        final int sec = 5;
        scheduledPost = worker.schedule(task, sec, TimeUnit.SECONDS);

    }

    public List<Treatment> getTreatmentDataFromTime(long mills, boolean ascending) {
        try {
            Dao<Treatment, Long> daoTreatments = getDaoTreatments();
            List<Treatment> treatments;
            QueryBuilder<Treatment, Long> queryBuilder = daoTreatments.queryBuilder();
            queryBuilder.orderBy("timeIndex", ascending);
            Where where = queryBuilder.where();
            where.ge("timeIndex", mills);
            PreparedQuery<Treatment> preparedQuery = queryBuilder.prepare();
            treatments = daoTreatments.query(preparedQuery);
            return treatments;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return new ArrayList<Treatment>();
    }

    public List<TempTarget> getTemptargetsDataFromTime(long mills, boolean ascending) {
        try {
            Dao<TempTarget, Long> daoTempTargets = getDaoTempTargets();
            List<TempTarget> tempTargets;
            QueryBuilder<TempTarget, Long> queryBuilder = daoTempTargets.queryBuilder();
            queryBuilder.orderBy("timeIndex", ascending);
            Where where = queryBuilder.where();
            where.ge("timeIndex", mills);
            PreparedQuery<TempTarget> preparedQuery = queryBuilder.prepare();
            tempTargets = daoTempTargets.query(preparedQuery);
            return tempTargets;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return new ArrayList<TempTarget>();
    }


    public List<TempBasal> getTempbasalsDataFromTime(long mills, boolean ascending, boolean isExtended) {
        try {
            Dao<TempBasal, Long> daoTempbasals = getDaoTempBasals();
            List<TempBasal> tempbasals;
            QueryBuilder<TempBasal, Long> queryBuilder = daoTempbasals.queryBuilder();
            queryBuilder.orderBy("timeIndex", ascending);
            Where where = queryBuilder.where();
            where.ge("timeIndex", mills).and().eq("isExtended", isExtended);
            PreparedQuery<TempBasal> preparedQuery = queryBuilder.prepare();
            tempbasals = daoTempbasals.query(preparedQuery);
            return tempbasals;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return new ArrayList<TempBasal>();
    }

}

package info.nightscout.androidaps.db;

import android.content.Context;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;

import com.j256.ormlite.android.apptools.OrmLiteSqliteOpenHelper;
import com.j256.ormlite.dao.CloseableIterator;
import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.stmt.DeleteBuilder;
import com.j256.ormlite.stmt.PreparedQuery;
import com.j256.ormlite.stmt.QueryBuilder;
import com.j256.ormlite.stmt.Where;
import com.j256.ormlite.support.ConnectionSource;
import com.j256.ormlite.table.TableUtils;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.inject.Inject;

import info.nightscout.androidaps.events.EventRefreshOverview;
import info.nightscout.androidaps.interfaces.ActivePlugin;
import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.bus.RxBusWrapper;
import info.nightscout.androidaps.plugins.general.openhumans.OpenHumansUploader;
import info.nightscout.androidaps.plugins.pump.virtual.VirtualPumpPlugin;
import info.nightscout.androidaps.utils.DateUtil;

/**
 * This Helper contains all resource to provide a central DB management functionality. Only methods handling
 * data-structure (and not the DB content) should be contained in here (meaning DDL and not SQL).
 * <p>
 * This class can safely be called from Services, but should not call Services to avoid circular dependencies.
 * One major issue with this (right now) are the scheduled events, which are put into the service. Therefor all
 * direct calls to the corresponding methods (eg. resetDatabases) should be done by a central service.
 */
public class DatabaseHelper extends OrmLiteSqliteOpenHelper {
    @Inject AAPSLogger aapsLogger;
    @Inject RxBusWrapper rxBus;
    @Inject VirtualPumpPlugin virtualPumpPlugin;
    @Inject OpenHumansUploader openHumansUploader;
    @Inject ActivePlugin activePlugin;
    @Inject DateUtil dateUtil;

    public static final String DATABASE_NAME = "AndroidAPSDb";

    private static final int DATABASE_VERSION = 13;

    public static Long earliestDataChange = null;

    private int oldVersion = 0;
    private int newVersion = 0;

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        StaticInjector.Companion.getInstance().androidInjector().inject(this);
        onCreate(getWritableDatabase(), getConnectionSource());
        //onUpgrade(getWritableDatabase(), getConnectionSource(), 1,1);
    }

    @Override
    public void onCreate(SQLiteDatabase database, ConnectionSource connectionSource) {
        try {
            aapsLogger.info(LTag.DATABASE, "onCreate");
            TableUtils.createTableIfNotExists(connectionSource, OmnipodHistoryRecord.class);
            TableUtils.createTableIfNotExists(connectionSource, OHQueueItem.class);
        } catch (SQLException e) {
            aapsLogger.error("Can't create database", e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public void onUpgrade(SQLiteDatabase database, ConnectionSource connectionSource, int oldVersion, int newVersion) {
        try {
            this.oldVersion = oldVersion;
            this.newVersion = newVersion;

            if (oldVersion < 7) {
                aapsLogger.info(LTag.DATABASE, "onUpgrade");
                onCreate(database, connectionSource);
            }
            TableUtils.createTableIfNotExists(connectionSource, OHQueueItem.class);
        } catch (SQLException e) {
            aapsLogger.error("Can't drop databases", e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        aapsLogger.info(LTag.DATABASE, "Do nothing for downgrading...");
        aapsLogger.info(LTag.DATABASE, "oldVersion: {}, newVersion: {}", oldVersion, newVersion);
    }

    public int getOldVersion() {
        return oldVersion;
    }

    public int getNewVersion() {
        return newVersion;
    }

    public long size(String database) {
        return DatabaseUtils.queryNumEntries(getReadableDatabase(), database);
    }

    // --------------------- DB resets ---------------------

    public void resetDatabases() {
        try {
            TableUtils.dropTable(connectionSource, OmnipodHistoryRecord.class, true);
            TableUtils.createTableIfNotExists(connectionSource, OmnipodHistoryRecord.class);
            updateEarliestDataChange(0);
        } catch (SQLException e) {
            aapsLogger.error("Unhandled exception", e);
        }
        virtualPumpPlugin.setFakingStatus(true);
        new java.util.Timer().schedule(
                new java.util.TimerTask() {
                    @Override
                    public void run() {
                        rxBus.send(new EventRefreshOverview("resetDatabases", false));
                    }
                },
                3000
        );
    }

    // ------------------ getDao -------------------------------------------

    private Dao<OmnipodHistoryRecord, Long> getDaoPodHistory() throws SQLException {
        return getDao(OmnipodHistoryRecord.class);
    }

    private Dao<OHQueueItem, Long> getDaoOpenHumansQueue() throws SQLException {
        return getDao(OHQueueItem.class);
    }

    public static void updateEarliestDataChange(long newDate) {
        if (earliestDataChange == null) {
            earliestDataChange = newDate;
            return;
        }
        if (newDate < earliestDataChange) {
            earliestDataChange = newDate;
        }
    }

    // ---------------- Food handling ---------------

    // ---------------- PodHistory handling ---------------

    public void createOrUpdate(OmnipodHistoryRecord omnipodHistoryRecord) {
        try {
            getDaoPodHistory().createOrUpdate(omnipodHistoryRecord);
        } catch (SQLException e) {
            aapsLogger.error("Unhandled exception", e);
        }
    }

    public List<OmnipodHistoryRecord> getAllOmnipodHistoryRecordsFromTimeStamp(long from, boolean ascending) {
        try {
            Dao<OmnipodHistoryRecord, Long> daoPodHistory = getDaoPodHistory();
            List<OmnipodHistoryRecord> podHistories;
            QueryBuilder<OmnipodHistoryRecord, Long> queryBuilder = daoPodHistory.queryBuilder();
            queryBuilder.orderBy("date", ascending);
            //queryBuilder.limit(100L);
            Where where = queryBuilder.where();
            where.ge("date", from);
            PreparedQuery<OmnipodHistoryRecord> preparedQuery = queryBuilder.prepare();
            podHistories = daoPodHistory.query(preparedQuery);
            return podHistories;
        } catch (SQLException e) {
            aapsLogger.error("Unhandled exception", e);
        }
        return new ArrayList<>();
    }

    public OmnipodHistoryRecord findOmnipodHistoryRecordByPumpId(long pumpId) {
        try {
            Dao<OmnipodHistoryRecord, Long> daoPodHistory = getDaoPodHistory();
            QueryBuilder<OmnipodHistoryRecord, Long> queryBuilder = daoPodHistory.queryBuilder();
            queryBuilder.orderBy("date", false);
            Where<OmnipodHistoryRecord, Long> where = queryBuilder.where();
            where.eq("pumpId", pumpId);
            PreparedQuery<OmnipodHistoryRecord> preparedQuery = queryBuilder.prepare();
            return daoPodHistory.queryForFirst(preparedQuery);
        } catch (SQLException e) {
            aapsLogger.error("Unhandled exception", e);
        }
        return null;
    }

/*
    TODO implement again for database branch    // Copied from xDrip+
    String calculateDirection(BgReading bgReading) {
        // Rework to get bgreaings from internal DB and calculate on that base

        List<BgReading> bgReadingsList = MainApp.getDbHelper().getAllBgreadingsDataFromTime(bgReading.date - T.mins(10).msecs(), false);
        if (bgReadingsList == null || bgReadingsList.size() < 2)
            return "NONE";
        BgReading current = bgReadingsList.get(1);
        BgReading previous = bgReadingsList.get(0);

        if (bgReadingsList.get(1).date < bgReadingsList.get(0).date) {
            current = bgReadingsList.get(0);
            previous = bgReadingsList.get(1);
        }

        double slope;

        // Avoid division by 0
        if (current.date == previous.date)
            slope = 0;
        else
            slope = (previous.value - current.value) / (previous.date - current.date);

//        aapsLogger.error(LTag.GLUCOSE, "Slope is :" + slope + " delta " + (previous.value - current.value) + " date difference " + (current.date - previous.date));

        double slope_by_minute = slope * 60000;
        String arrow = "NONE";

        if (slope_by_minute <= (-3.5)) {
            arrow = "DoubleDown";
        } else if (slope_by_minute <= (-2)) {
            arrow = "SingleDown";
        } else if (slope_by_minute <= (-1)) {
            arrow = "FortyFiveDown";
        } else if (slope_by_minute <= (1)) {
            arrow = "Flat";
        } else if (slope_by_minute <= (2)) {
            arrow = "FortyFiveUp";
        } else if (slope_by_minute <= (3.5)) {
            arrow = "SingleUp";
        } else if (slope_by_minute <= (40)) {
            arrow = "DoubleUp";
        }
//        aapsLogger.error(LTag.GLUCOSE, "Direction set to: " + arrow);
        return arrow;
    }
*/
    // ---------------- Open Humans Queue handling ---------------

    public void clearOpenHumansQueue() {
        try {
            TableUtils.clearTable(connectionSource, OHQueueItem.class);
        } catch (SQLException e) {
            aapsLogger.error("Unhandled exception", e);
        }
    }

    public void createOrUpdate(OHQueueItem item) {
        try {
            getDaoOpenHumansQueue().createOrUpdate(item);
        } catch (SQLException e) {
            aapsLogger.error("Unhandled exception", e);
        }
    }

    public void removeAllOHQueueItemsWithIdSmallerThan(long id) {
        try {
            DeleteBuilder<OHQueueItem, Long> deleteBuilder = getDaoOpenHumansQueue().deleteBuilder();
            deleteBuilder.where().le("id", id);
            deleteBuilder.delete();
        } catch (SQLException e) {
            aapsLogger.error("Unhandled exception", e);
        }
    }

    public List<OHQueueItem> getAllOHQueueItems(Long maxEntries) {
        try {
            return getDaoOpenHumansQueue()
                    .queryBuilder()
                    .orderBy("id", true)
                    .limit(maxEntries)
                    .query();
        } catch (SQLException e) {
            aapsLogger.error("Unhandled exception", e);
        }
        return Collections.emptyList();
    }

    public long getOHQueueSize() {
        try {
            return getDaoOpenHumansQueue().countOf();
        } catch (SQLException e) {
            aapsLogger.error("Unhandled exception", e);
        }
        return 0L;
    }
}

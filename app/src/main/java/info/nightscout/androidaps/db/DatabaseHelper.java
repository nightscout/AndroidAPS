package info.nightscout.androidaps.db;

import java.sql.SQLException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import android.content.Context;
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

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;

public class DatabaseHelper extends OrmLiteSqliteOpenHelper {
    private static Logger log = LoggerFactory.getLogger(DatabaseHelper.class);

    public static final String DATABASE_NAME = "AndroidAPSDb";

    private static final int DATABASE_VERSION = 1;

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }


    @Override
    public void onCreate(SQLiteDatabase database, ConnectionSource connectionSource) {
        try {
            log.info("onCreate");
            TableUtils.createTableIfNotExists(connectionSource, TempBasal.class);
            TableUtils.createTableIfNotExists(connectionSource, Treatment.class);
            TableUtils.createTableIfNotExists(connectionSource, BgReading.class);
            // TODO: add bg support
        } catch (SQLException e) {
            log.error(DatabaseHelper.class.getName(), "Can't create database", e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public void onUpgrade(SQLiteDatabase database, ConnectionSource connectionSource, int oldVersion, int newVersion) {
        try {
            log.info(DatabaseHelper.class.getName(), "onUpgrade");
            TableUtils.dropTable(connectionSource, TempBasal.class, true);
            TableUtils.dropTable(connectionSource, Treatment.class, true);
            TableUtils.dropTable(connectionSource, BgReading.class, true);
            onCreate(database, connectionSource);
        } catch (SQLException e) {
            log.error(DatabaseHelper.class.getName(), "Can't drop databases", e);
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

    public void resetDatabases() {
        try {
            TableUtils.dropTable(connectionSource, TempBasal.class, true);
            TableUtils.dropTable(connectionSource, Treatment.class, true);
            TableUtils.dropTable(connectionSource, BgReading.class, true);
            TableUtils.createTableIfNotExists(connectionSource, TempBasal.class);
            TableUtils.createTableIfNotExists(connectionSource, Treatment.class);
            TableUtils.createTableIfNotExists(connectionSource, BgReading.class);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void resetTreatments() {
        try {

            TableUtils.dropTable(connectionSource, Treatment.class, true);
            TableUtils.createTableIfNotExists(connectionSource, Treatment.class);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public Dao<TempBasal, Long> getDaoTempBasals() throws SQLException {
        return getDao(TempBasal.class);
    }

    public Dao<Treatment, Long> getDaoTreatments() throws SQLException {
        return getDao(Treatment.class);
    }

    public Dao<BgReading, Long> getDaoBgReadings() throws SQLException {
        return getDao(BgReading.class);
    }

    /*
     * Return last BgReading from database or null if db is empty
     */
    @Nullable
    public BgReading lastBg() {
        List<BgReading> bgList = null;

        try {
            Dao<BgReading, Long> daoBgReadings = MainApp.getDbHelper().getDaoBgReadings();
            QueryBuilder<BgReading, Long> queryBuilder = daoBgReadings.queryBuilder();
            queryBuilder.orderBy("timeIndex", false);
            queryBuilder.limit(1l);
            PreparedQuery<BgReading> preparedQuery = queryBuilder.prepare();
            bgList = daoBgReadings.query(preparedQuery);

        } catch (SQLException e) {
            log.debug(e.getMessage(), e);
        }
        if (bgList.size() > 0)
            return bgList.get(0);
        else
            return null;
    }

    /*
     * Return bg reading if not old ( <9 min )
     * or null if older
     */
    @Nullable
    public BgReading actualBg() {
        BgReading lastBg = lastBg();

        if (lastBg == null)
            return null;

        if (lastBg.timestamp > new Date().getTime() - 9 * 60 * 1000)
            return lastBg;

        return null;
    }

    public List<BgReading> getDataFromTime(long mills) {
        try {
            Dao<BgReading, Long> daoBgreadings = getDaoBgReadings();
            List<BgReading> bgReadings;
            QueryBuilder<BgReading, Long> queryBuilder = daoBgreadings.queryBuilder();
            queryBuilder.orderBy("timeIndex", false);
            Where where = queryBuilder.where();
            where.ge("timeIndex", (long) Math.ceil(mills / 60000d));
            PreparedQuery<BgReading> preparedQuery = queryBuilder.prepare();
            bgReadings = daoBgreadings.query(preparedQuery);
            return bgReadings;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return new ArrayList<BgReading>();
    }

    /*
     * Returns glucose_status for openAPS or null if no actual data available
     */
    public class GlucoseStatus {
        public double glucose = 0d;
        public double delta = 0d;
        public double avgdelta = 0d;

        @Override
        public String toString() {
            Context context = MainApp.instance().getApplicationContext();
            DecimalFormat formatNumber0decimalplaces = new DecimalFormat("0");
            DecimalFormat formatNumber2decimalplaces = new DecimalFormat("0.00");

            return context.getString(R.string.glucose) + " " +  formatNumber0decimalplaces.format(glucose) + "\n" +
                    context.getString(R.string.delta) + " " + formatNumber0decimalplaces.format(delta) + "\n" +
                    context.getString(R.string.avgdelta) + " " + formatNumber2decimalplaces.format(avgdelta);
        }
    }

    @Nullable
    public GlucoseStatus getGlucoseStatusData() {
        GlucoseStatus result = new GlucoseStatus();
        try {

            Dao<BgReading, Long> daoBgreadings = null;
            daoBgreadings = getDaoBgReadings();
            List<BgReading> bgReadings;
            QueryBuilder<BgReading, Long> queryBuilder = daoBgreadings.queryBuilder();
            queryBuilder.orderBy("timeIndex", false);
            queryBuilder.limit(4l);
            PreparedQuery<BgReading> preparedQuery = queryBuilder.prepare();
            bgReadings = daoBgreadings.query(preparedQuery);

            int sizeRecords = bgReadings.size();

            if (sizeRecords < 4 || bgReadings.get(sizeRecords - 4).timestamp < new Date().getTime() - 7 * 60 * 1000l)
                return null;

            int minutes = 5;
            double change;
            double avg;

            if (bgReadings.size() > 3) {
                BgReading now = bgReadings.get(sizeRecords - 4);
                BgReading last = bgReadings.get(sizeRecords - 3);
                BgReading last1 = bgReadings.get(sizeRecords - 2);
                BgReading last2 = bgReadings.get(sizeRecords - 1);
                if (last2.value > 30) {
                    minutes = 3 * 5;
                    change = now.value - last2.value;
                } else if (last1.value > 30) {
                    minutes = 2 * 5;
                    change = now.value - last1.value;
                } else if (last.value > 30) {
                    minutes = 5;
                    change = now.value - last.value;
                } else {
                    change = 0;
                }
                //multiply by 5 to get the same unit as delta, i.e. mg/dL/5m
                avg = change / minutes * 5;

                result.glucose = now.value;
                result.delta = now.value - last.value;
                result.avgdelta = avg;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
        return result;
    }
}

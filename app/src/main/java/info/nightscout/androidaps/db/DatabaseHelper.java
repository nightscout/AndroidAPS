package info.nightscout.androidaps.db;

import java.sql.SQLException;
import java.util.List;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import com.j256.ormlite.android.apptools.OrmLiteSqliteOpenHelper;
import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.stmt.QueryBuilder;
import com.j256.ormlite.support.ConnectionSource;
import com.j256.ormlite.table.TableUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DatabaseHelper extends OrmLiteSqliteOpenHelper {
	private static Logger log = LoggerFactory.getLogger(DatabaseHelper.class);

	public static final String DATABASE_NAME = "AndroidAPSDb";

	private static final int DATABASE_VERSION = 2;

	public DatabaseHelper(Context context) {
		super(context, DATABASE_NAME, null, DATABASE_VERSION);
	}


    @Override
    public void onCreate(SQLiteDatabase database, ConnectionSource connectionSource) {
		try {
			log.info("onCreate");
			TableUtils.createTableIfNotExists(connectionSource, TempBasal.class);
			TableUtils.createTableIfNotExists(connectionSource, Treatment.class);
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
			TableUtils.createTableIfNotExists(connectionSource, TempBasal.class);
			TableUtils.createTableIfNotExists(connectionSource, Treatment.class);
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
        return  getDao(TempBasal.class);
    }

	public Dao<Treatment, Long> getDaoTreatments() throws SQLException {
		return  getDao(Treatment.class);
	}

}

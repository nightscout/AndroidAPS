package info.nightscout.androidaps;

import android.app.Application;

import com.j256.ormlite.android.apptools.OpenHelperManager;
import com.squareup.otto.Bus;
import com.squareup.otto.ThreadEnforcer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.db.DatabaseHelper;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderFragment;


public class MainApp  extends Application {
    private static Logger log = LoggerFactory.getLogger(MainApp.class);

    private static Bus sBus;
    private static MainApp sInstance;

    private static DatabaseHelper databaseHelper = null;
    private static ConfigBuilderFragment configBuilder = null;

    @Override
    public void onCreate() {
        super.onCreate();

        sBus = new Bus(ThreadEnforcer.ANY);
        sInstance = this;
    }

    public static Bus bus() {
        return sBus;
    }
    public static MainApp instance() {
        return sInstance;
    }

    public static DatabaseHelper getDbHelper() {
        if (databaseHelper == null) {
            databaseHelper = OpenHelperManager.getHelper(sInstance, DatabaseHelper.class);
        }
        return databaseHelper;
    }

    public static void closeDbHelper() {
        if (databaseHelper != null) {
            databaseHelper.close();
            databaseHelper = null;
        }
    }

    public static void setConfigBuilder(ConfigBuilderFragment cb) {
        configBuilder = cb;
    }

    public static ConfigBuilderFragment getConfigBuilder() {
        return configBuilder;
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        databaseHelper.close();
    }
}
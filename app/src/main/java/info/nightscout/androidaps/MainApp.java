package info.nightscout.androidaps;

import android.app.Application;
import android.content.res.Resources;

import com.crashlytics.android.Crashlytics;
import com.j256.ormlite.android.apptools.OpenHelperManager;
import com.squareup.otto.Bus;
import com.squareup.otto.ThreadEnforcer;

import io.fabric.sdk.android.Fabric;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.db.DatabaseHelper;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderFragment;


public class MainApp  extends Application {
    private static Logger log = LoggerFactory.getLogger(MainApp.class);

    private static Bus sBus;
    private static MainApp sInstance;
    public static Resources resources;

    private static DatabaseHelper databaseHelper = null;
    private static ConfigBuilderFragment configBuilder = null;

    @Override
    public void onCreate() {
        super.onCreate();
        Fabric.with(this, new Crashlytics());

        sBus = new Bus(ThreadEnforcer.ANY);
        sInstance = this;
        resources = getResources();
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
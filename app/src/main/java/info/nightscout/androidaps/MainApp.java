package info.nightscout.androidaps;

import android.app.Application;
import android.content.SharedPreferences;

import com.j256.ormlite.android.apptools.OpenHelperManager;
import com.squareup.otto.Bus;
import com.squareup.otto.ThreadEnforcer;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

import info.nightscout.androidaps.data.Pump;
import info.nightscout.androidaps.plugins.Treatments.TreatmentsFragment;
import info.nightscout.client.data.NSProfile;
import info.nightscout.androidaps.db.DatabaseHelper;


public class MainApp  extends Application {
    private static Logger log = LoggerFactory.getLogger(MainApp.class);

    public static final String PREFS_NAME = "NightscoutProfile";


    private static Bus sBus;
    private static MainApp sInstance;

    private static NSProfile nsProfile = null;
    private static String activeProfile = null;

    private static Pump activePump = null;

    private static DatabaseHelper databaseHelper = null;

    @Override
    public void onCreate() {
        super.onCreate();

        sBus = new Bus(ThreadEnforcer.ANY);
        sInstance = this;

        log.debug("Loading stored profile");
        SharedPreferences store = getSharedPreferences(PREFS_NAME, 0);
        activeProfile = store.getString("activeProfile", null);
        String profileString = store.getString("profile", null);
        if (profileString != null) {
            try {
                log.debug("Loaded profile: " + profileString);
                log.debug("Loaded active profile: " + activeProfile);
                setNSProfile(new NSProfile(new JSONObject(profileString), activeProfile));
            } catch (JSONException e) {
            }
        } else
            log.debug("Stored profile not found");

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

    @Override
    public void onTerminate() {
        super.onTerminate();
        databaseHelper.close();
    }

    public static NSProfile getNSProfile() {
        return nsProfile;
    }
    public static void setNSProfile(NSProfile profile) {
        nsProfile = profile;
    }

    public static String getActiveProfile() {
        return activeProfile;
    }
    public static void setActiveProfile(String activeprofile) {
        activeProfile = activeprofile;
    }

    public static Pump getActivePump() {
        return activePump;
    }
    public static Pump setActivePump(Pump activepump) {
        activePump = activepump;
        return activepump;
    }

}
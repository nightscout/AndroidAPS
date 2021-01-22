package info.nightscout.androidaps;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

/**
 * Created for xDrip+ by Emma Black on 3/21/15.
 * Adapted for AAPS by dlvoy 2019-11-06.
 */

public class Aaps extends Application implements SharedPreferences.OnSharedPreferenceChangeListener {

    @SuppressLint("StaticFieldLeak")
    private static Context context;
    private static Boolean unicodeComplications = true;
    private static String complicationTapAction = "default";

    @Override
    public void onCreate() {
        Aaps.context = getApplicationContext();
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        sharedPrefs.registerOnSharedPreferenceChangeListener(this);
        updatePrefs(sharedPrefs);
        super.onCreate();
    }

    private void updatePrefs(SharedPreferences sharedPrefs) {
        unicodeComplications = sharedPrefs.getBoolean("complication_unicode", true);
        complicationTapAction = sharedPrefs.getString("complication_tap_action", "default");
    }

    public static Context getAppContext() {
        return Aaps.context;
    }

    public static Boolean areComplicationsUnicode() {
        return unicodeComplications;
    }

    public static String getComplicationTapAction() {
        return complicationTapAction;
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        updatePrefs(sharedPrefs);

        // we trigger update on Complications
        Intent messageIntent = new Intent();
        messageIntent.setAction(Intent.ACTION_SEND);
        LocalBroadcastManager.getInstance(this).sendBroadcast(messageIntent);
    }
}

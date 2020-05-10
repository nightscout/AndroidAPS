package info.nightscout.androidaps.utils;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import java.util.Map;

import info.nightscout.androidaps.MainApp;

/**
 * Created by mike on 17.02.2017.
 */

public class SP {
    private static SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(MainApp.instance().getApplicationContext());

    @Deprecated
    static public Map<String, ?> getAll() {
        return sharedPreferences.getAll();
    }

    @Deprecated
    static public String getString(String key, String defaultValue) {
        return sharedPreferences.getString(key, defaultValue);
    }

    @Deprecated
    static public boolean getBoolean(int resourceID, Boolean defaultValue) {
        try {
            return sharedPreferences.getBoolean(MainApp.gs(resourceID), defaultValue);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    @Deprecated
    static public void putString(int resourceID, String value) {
        sharedPreferences.edit().putString(MainApp.gs(resourceID), value).apply();
    }

    @Deprecated
    static public void putString(String key, String value) {
        sharedPreferences.edit().putString(key, value).apply();
    }

    @Deprecated
    static public void remove(String key) {
        sharedPreferences.edit().remove(key).apply();
    }
}

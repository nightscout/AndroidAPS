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

    static public Map<String, ?> getAll() {
        return sharedPreferences.getAll();
    }

    static public void clear() {
        sharedPreferences.edit().clear().apply();
    }

    static public boolean contains(String key) {
        return sharedPreferences.contains(key);
    }

    static public boolean contains(int resourceId) {
        return sharedPreferences.contains(MainApp.gs(resourceId));
    }

    static public String getString(int resourceID, String defaultValue) {
        return sharedPreferences.getString(MainApp.gs(resourceID), defaultValue);
    }

    static public String getString(String key, String defaultValue) {
        return sharedPreferences.getString(key, defaultValue);
    }

    static public boolean getBoolean(int resourceID, Boolean defaultValue) {
        try {
            return sharedPreferences.getBoolean(MainApp.gs(resourceID), defaultValue);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    static public boolean getBoolean(String key, Boolean defaultValue) {
        try {
            return sharedPreferences.getBoolean(key, defaultValue);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    static public Double getDouble(int resourceID, Double defaultValue) {
        return SafeParse.stringToDouble(sharedPreferences.getString(MainApp.gs(resourceID), defaultValue.toString()));
    }

    static public Double getDouble(String key, Double defaultValue) {
        return SafeParse.stringToDouble(sharedPreferences.getString(key, defaultValue.toString()));
    }

    static public int getInt(int resourceID, Integer defaultValue) {
        try {
            return sharedPreferences.getInt(MainApp.gs(resourceID), defaultValue);
        } catch (Exception e) {
            return SafeParse.stringToInt(sharedPreferences.getString(MainApp.gs(resourceID), defaultValue.toString()));
        }
    }

    static public int getInt(String key, Integer defaultValue) {
        try {
            return sharedPreferences.getInt(key, defaultValue);
        } catch (Exception e) {
            return SafeParse.stringToInt(sharedPreferences.getString(key, defaultValue.toString()));
        }
    }

    static public long getLong(int resourceID, Long defaultValue) {
        try {
            return sharedPreferences.getLong(MainApp.gs(resourceID), defaultValue);
        } catch (Exception e) {
            return SafeParse.stringToLong(sharedPreferences.getString(MainApp.gs(resourceID), defaultValue.toString()));
        }
    }

    static public long getLong(String key, Long defaultValue) {
        try {
            return sharedPreferences.getLong(key, defaultValue);
        } catch (Exception e) {
            return SafeParse.stringToLong(sharedPreferences.getString(key, defaultValue.toString()));
        }
    }

    static public void putBoolean(String key, boolean value) {
        sharedPreferences.edit().putBoolean(key, value).apply();
    }

    static public void putBoolean(int resourceID, boolean value) {
        sharedPreferences.edit().putBoolean(MainApp.gs(resourceID), value).apply();
    }

    static public void putDouble(String key, double value) {
        sharedPreferences.edit().putString(key, Double.toString(value)).apply();
    }

    static public void putLong(String key, long value) {
        sharedPreferences.edit().putLong(key, value).apply();
    }

    static public void putLong(int resourceID, long value) {
        sharedPreferences.edit().putLong(MainApp.gs(resourceID), value).apply();
    }

    static public void putInt(String key, int value) {
        sharedPreferences.edit().putInt(key, value).apply();
    }

    static public void putInt(int resourceID, int value) {
        sharedPreferences.edit().putInt(MainApp.gs(resourceID), value).apply();
    }

    static public void incInt(int resourceID) {
        int value = SP.getInt(resourceID, 0) + 1;
        sharedPreferences.edit().putInt(MainApp.gs(resourceID), value).apply();
    }

    static public void putString(int resourceID, String value) {
        sharedPreferences.edit().putString(MainApp.gs(resourceID), value).apply();
    }

    static public void putString(String key, String value) {
        sharedPreferences.edit().putString(key, value).apply();
    }

    static public void remove(int resourceID) {
        sharedPreferences.edit().remove(MainApp.gs(resourceID)).apply();
    }

    static public void remove(String key) {
        sharedPreferences.edit().remove(key).apply();
    }
}

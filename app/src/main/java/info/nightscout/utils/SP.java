package info.nightscout.utils;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import info.nightscout.androidaps.MainApp;

/**
 * Created by mike on 17.02.2017.
 */

public class SP {
    static SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(MainApp.instance().getApplicationContext());

    static public boolean contains(String key) {
        return sharedPreferences.contains(key);
    }

    static public String getString(int resourceID, String defaultValue) {
        return sharedPreferences.getString(MainApp.sResources.getString(resourceID), defaultValue);
    }

    static public String getString(String key, String defaultValue) {
        return sharedPreferences.getString(key, defaultValue);
    }

    static public boolean getBoolean(int resourceID, boolean defaultValue) {
        try {
            return sharedPreferences.getBoolean(MainApp.sResources.getString(resourceID), defaultValue);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    static public boolean getBoolean(String key, boolean defaultValue) {
        try {
            return sharedPreferences.getBoolean(key, defaultValue);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    static public Double getDouble(int resourceID, Double defaultValue) {
        return SafeParse.stringToDouble(sharedPreferences.getString(MainApp.sResources.getString(resourceID), defaultValue.toString()));
    }

    static public Double getDouble(String key, Double defaultValue) {
        return SafeParse.stringToDouble(sharedPreferences.getString(key, defaultValue.toString()));
    }

    static public int getInt(int resourceID, Integer defaultValue) {
        return SafeParse.stringToInt(sharedPreferences.getString(MainApp.sResources.getString(resourceID), defaultValue.toString()));
    }

    static public int getInt(String key, Integer defaultValue) {
        return SafeParse.stringToInt(sharedPreferences.getString(key, defaultValue.toString()));
    }

    static public long getLong(int resourceID, Long defaultValue) {
        return SafeParse.stringToLong(sharedPreferences.getString(MainApp.sResources.getString(resourceID), defaultValue.toString()));
    }

    static public long getLong(String key, Long defaultValue) {
        try {
            return sharedPreferences.getLong(key, defaultValue);
        } catch (Exception e) {
            return SafeParse.stringToLong(sharedPreferences.getString(key, defaultValue.toString()));
        }
    }

    static public void putBoolean(String key, boolean value) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(key, value);
        editor.apply();
    }

    static public void putBoolean(int resourceID, boolean value) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(MainApp.sResources.getString(resourceID), value);
        editor.apply();
    }

    static public void removeBoolean(int resourceID) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.remove(MainApp.sResources.getString(resourceID));
        editor.apply();
    }

    static public void putLong(String key, long value) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putLong(key, value);
        editor.apply();
    }

    static public void putString(int resourceID, String value) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(MainApp.sResources.getString(resourceID), value);
        editor.apply();
    }

    static public void removeString(int resourceID) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.remove(MainApp.sResources.getString(resourceID));
        editor.apply();
    }
}

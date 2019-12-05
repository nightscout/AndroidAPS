package info.nightscout.androidaps.interaction.utils;

import android.content.SharedPreferences;
import android.util.Base64;

import androidx.annotation.Nullable;

import com.google.android.gms.wearable.DataMap;

import java.util.Set;

import info.nightscout.androidaps.aaps;

/**
 * Created by dlvoy on 2019-11-12
 */
public class Persistence {

    final SharedPreferences preferences;
    public static final String COMPLICATION_PROVIDER_PREFERENCES_FILE_KEY =
            "info.nightscout.androidaps.complications.COMPLICATION_PROVIDER_PREFERENCES_FILE_KEY";

    public Persistence() {
        preferences = aaps.getAppContext().getSharedPreferences(COMPLICATION_PROVIDER_PREFERENCES_FILE_KEY, 0);
    }

    @Nullable
    public DataMap getDataMap(String key) {
        if (preferences.contains(key)) {
            final String rawB64Data = preferences.getString(key, null);
            byte[] rawData = Base64.decode(rawB64Data, Base64.DEFAULT);
            try {
                return DataMap.fromByteArray(rawData);
            } catch (IllegalArgumentException ex) {
                // Should never happen, and if it happen - we ignore and fallback to null
            }
        }
        return null;
    }

    public void putDataMap(String key, DataMap dataMap) {
        preferences.edit().putString(key, Base64.encodeToString(dataMap.toByteArray(), Base64.DEFAULT)).apply();
    }

    public String getString(String key, String defaultValue) {
        return preferences.getString(key, defaultValue);
    }

    public void putString(String key, String value) {
        preferences.edit().putString(key, value).apply();
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        return preferences.getBoolean(key, defaultValue);
    }

    public void putBoolean(String key, boolean value) {
        preferences.edit().putBoolean(key, value).apply();
    }

    public long whenDataUpdated() {
        return preferences.getLong("data_updated_at", 0);
    }

    private void markDataUpdated() {
        preferences.edit().putLong("data_updated_at", WearUtil.timestamp()).apply();
    }

    public Set<String> getSetOf(String key) {
        return WearUtil.explodeSet(getString(key, ""), "|");
    }

    public void addToSet(String key, String value) {
        final Set<String> set = WearUtil.explodeSet(getString(key, ""), "|");
        set.add(value);
        putString(key, WearUtil.joinSet(set, "|"));
    }

    public void removeFromSet(String key, String value) {
        final Set<String> set = WearUtil.explodeSet(getString(key, ""), "|");
        set.remove(value);
        putString(key, WearUtil.joinSet(set, "|"));
    }

    public static void storeDataMap(String key, DataMap dataMap) {
        Persistence p = new Persistence();
        p.putDataMap(key, dataMap);
        p.markDataUpdated();
    }

    public static Set<String> setOf(String key) {
        Persistence p = new Persistence();
        return p.getSetOf(key);
    }

}

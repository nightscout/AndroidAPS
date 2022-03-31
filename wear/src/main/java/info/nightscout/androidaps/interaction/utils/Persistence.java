package info.nightscout.androidaps.interaction.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.Nullable;

import com.google.android.gms.wearable.DataMap;

import java.util.HashSet;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Singleton;

import info.nightscout.androidaps.Aaps;
import info.nightscout.androidaps.complications.BaseComplicationProviderService;
import info.nightscout.shared.logging.AAPSLogger;
import info.nightscout.shared.logging.LTag;

/**
 * Created by dlvoy on 2019-11-12
 */
@Singleton
public class Persistence {

    private final Context context;
    private final AAPSLogger aapsLogger;
    private final WearUtil wearUtil;
    private final SharedPreferences preferences;
    private final String COMPLICATION_PROVIDER_PREFERENCES_FILE_KEY =
            "info.nightscout.androidaps.complications.COMPLICATION_PROVIDER_PREFERENCES_FILE_KEY";

    @Inject
    public Persistence(Context context, AAPSLogger aapsLogger, WearUtil wearUtil) {
        this.context = context;
        this.aapsLogger = aapsLogger;
        this.wearUtil = wearUtil;
        preferences = context.getSharedPreferences(COMPLICATION_PROVIDER_PREFERENCES_FILE_KEY, 0);
    }

    // For mocking only
    public byte[] base64decode(String str, int flags) {
        return Base64.decode(str, flags);
    }

    // For mocking only
    public String base64encodeToString(byte[] input, int flags) {
        return Base64.encodeToString(input, flags);
    }

    @Nullable
    public DataMap getDataMap(String key) {
        if (preferences.contains(key)) {
            final String rawB64Data = preferences.getString(key, null);
            byte[] rawData = base64decode(rawB64Data, Base64.DEFAULT);
            try {
                return DataMap.fromByteArray(rawData);
            } catch (IllegalArgumentException ex) {
                // Should never happen, and if it happen - we ignore and fallback to null
            }
        }
        return null;
    }

    public void putDataMap(String key, DataMap dataMap) {
        preferences.edit().putString(key, base64encodeToString(dataMap.toByteArray(), Base64.DEFAULT)).apply();
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
        preferences.edit().putLong("data_updated_at", wearUtil.timestamp()).apply();
    }

    public Set<String> getSetOf(String key) {
        return explodeSet(getString(key, ""), "|");
    }

    public void addToSet(String key, String value) {
        final Set<String> set = explodeSet(getString(key, ""), "|");
        set.add(value);
        putString(key, joinSet(set, "|"));
    }

    public void removeFromSet(String key, String value) {
        final Set<String> set = explodeSet(getString(key, ""), "|");
        set.remove(value);
        putString(key, joinSet(set, "|"));
    }

    public void storeDataMap(String key, DataMap dataMap) {
        putDataMap(key, dataMap);
        markDataUpdated();
    }

    public String joinSet(Set<String> set, String separator) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        for (String item : set) {
            final String itemToAdd = item.trim();
            if (itemToAdd.length() > 0) {
                if (i > 0) {
                    sb.append(separator);
                }
                i++;
                sb.append(itemToAdd);
            }
        }
        return sb.toString();
    }

    public Set<String> explodeSet(String joined, String separator) {
        // special RegEx literal \\Q starts sequence we escape, \\E ends is
        // we use it to escape separator for use in RegEx
        String[] items = joined.split("\\Q"+separator+"\\E");
        Set<String> set = new HashSet<>();
        for (String item : items) {
            final String itemToAdd = item.trim();
            if (itemToAdd.length() > 0) {
                set.add(itemToAdd);
            }
        }
        return set;
    }

    public void turnOff() {
        aapsLogger.debug(LTag.WEAR, "TURNING OFF all active complications");
        putString(BaseComplicationProviderService.KEY_COMPLICATIONS, "");
    }
}

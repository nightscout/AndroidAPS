package info.nightscout.androidaps.data;

import androidx.annotation.Nullable;
import androidx.collection.ArrayMap;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;

import info.nightscout.androidaps.Constants;

/**
 * Created by mike on 01.06.2017.
 */

public class ProfileStore {
    private static Logger log = LoggerFactory.getLogger(ProfileStore.class);
    private JSONObject json = null;
    private String units = Constants.MGDL;

    ArrayMap<String, Profile> cachedObjects = new ArrayMap<>();

    public ProfileStore(JSONObject json) {
        this.json = json;
        getDefaultProfile(); // initialize units
    }

    public JSONObject getData() {
        return json;
    }

    @Nullable
    public Profile getDefaultProfile() {
        Profile profile = null;
        try {
            String defaultProfileName = json.getString("defaultProfile");
            JSONObject store = json.getJSONObject("store");
            if (store.has(defaultProfileName)) {
                profile = cachedObjects.get(defaultProfileName);
                if (profile == null) {
                    if (store.has("units"))
                        units = store.getString("units");
                    profile = new Profile(store.getJSONObject(defaultProfileName), units);
                    units = profile.getUnits();
                    cachedObjects.put(defaultProfileName, profile);
                }
            }
        } catch (JSONException e) {
            log.error("Unhandled exception", e);
        }
        return profile;
    }

    @Nullable
    public String getDefaultProfileName() {
        String defaultProfileName = null;
        try {
            defaultProfileName = json.getString("defaultProfile");
            JSONObject store = json.getJSONObject("store");
            if (store.has(defaultProfileName)) {
                return defaultProfileName;
            }
        } catch (JSONException e) {
            log.error("Unhandled exception", e);
        }
        return defaultProfileName;
    }

    public String getUnits() {
        return units;
    }

    @Nullable
    public Profile getSpecificProfile(String profileName) {
        Profile profile = null;
        try {
            JSONObject store = json.getJSONObject("store");
            if (store.has(profileName)) {
                profile = cachedObjects.get(profileName);
                if (profile == null) {
                    if (store.has("units"))
                        units = store.getString("units");
                    profile = new Profile(store.getJSONObject(profileName), units);
                    units = profile.getUnits();
                    cachedObjects.put(profileName, profile);
                }
            }
        } catch (JSONException e) {
            log.error("Unhandled exception", e);
        }
        return profile;
    }

    public ArrayList<CharSequence> getProfileList() {
        ArrayList<CharSequence> ret = new ArrayList<CharSequence>();

        JSONObject store;
        try {
            store = json.getJSONObject("store");
            Iterator<?> keys = store.keys();

            while (keys.hasNext()) {
                String profileName = (String) keys.next();
                ret.add(profileName);
            }
        } catch (JSONException e) {
            log.error("Unhandled exception", e);
        }

        return ret;
    }


}

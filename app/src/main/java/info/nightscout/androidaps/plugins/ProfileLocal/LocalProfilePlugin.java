package info.nightscout.androidaps.plugins.ProfileLocal;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.ProfileStore;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.ProfileInterface;
import info.nightscout.utils.DecimalFormatter;
import info.nightscout.utils.SP;

/**
 * Created by mike on 05.08.2016.
 */
public class LocalProfilePlugin implements PluginBase, ProfileInterface {
    public static final String LOCAL_PROFILE = "LocalProfile";
    private static Logger log = LoggerFactory.getLogger(LocalProfilePlugin.class);

    private static LocalProfilePlugin localProfilePlugin;

    public static LocalProfilePlugin getPlugin() {
        if (localProfilePlugin == null)
            localProfilePlugin = new LocalProfilePlugin();
        return localProfilePlugin;
    }

    private boolean fragmentEnabled = false;
    private boolean fragmentVisible = false;

    private ProfileStore convertedProfile = null;
    private String convertedProfileName = null;

    public static final String DEFAULTARRAY = "[{\"time\":\"00:00\",\"timeAsSeconds\":0,\"value\":0}]";

    boolean mgdl;
    boolean mmol;
    Double dia;
    JSONArray ic;
    JSONArray isf;
    JSONArray basal;
    JSONArray targetLow;
    JSONArray targetHigh;

    public LocalProfilePlugin() {
        loadSettings();
    }

    @Override
    public String getFragmentClass() {
        return LocalProfileFragment.class.getName();
    }

    @Override
    public int getType() {
        return PluginBase.PROFILE;
    }

    @Override
    public String getName() {
        return MainApp.instance().getString(R.string.localprofile);
    }

    @Override
    public String getNameShort() {
        String name = MainApp.sResources.getString(R.string.localprofile_shortname);
        if (!name.trim().isEmpty()) {
            //only if translation exists
            return name;
        }
        // use long name as fallback
        return getName();
    }

    @Override
    public boolean isEnabled(int type) {
        return type == PROFILE && fragmentEnabled;
    }

    @Override
    public boolean isVisibleInTabs(int type) {
        return type == PROFILE && fragmentVisible;
    }

    @Override
    public boolean canBeHidden(int type) {
        return true;
    }

    @Override
    public boolean hasFragment() {
        return true;
    }

    @Override
    public boolean showInList(int type) {
        return true;
    }

    @Override
    public void setFragmentEnabled(int type, boolean fragmentEnabled) {
        if (type == PROFILE) this.fragmentEnabled = fragmentEnabled;
    }

    @Override
    public void setFragmentVisible(int type, boolean fragmentVisible) {
        if (type == PROFILE) this.fragmentVisible = fragmentVisible;
    }

    @Override
    public int getPreferencesId() {
        return -1;
    }

    public void storeSettings() {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(MainApp.instance().getApplicationContext());
        SharedPreferences.Editor editor = settings.edit();
        editor.putBoolean(LOCAL_PROFILE + "mmol", mmol);
        editor.putBoolean(LOCAL_PROFILE + "mgdl", mgdl);
        editor.putString(LOCAL_PROFILE + "dia", dia.toString());
        editor.putString(LOCAL_PROFILE + "ic", ic.toString());
        editor.putString(LOCAL_PROFILE + "isf", isf.toString());
        editor.putString(LOCAL_PROFILE + "basal", basal.toString());
        editor.putString(LOCAL_PROFILE + "targetlow", targetLow.toString());
        editor.putString(LOCAL_PROFILE + "targethigh", targetHigh.toString());

        editor.apply();
        createConvertedProfile();
        if (Config.logPrefsChange)
            log.debug("Storing settings: " + getRawProfile().getData().toString());
    }

    public void loadSettings() {
        if (Config.logPrefsChange)
            log.debug("Loading stored settings");

        mgdl = SP.getBoolean(LOCAL_PROFILE + "mgdl", false);
        mmol = SP.getBoolean(LOCAL_PROFILE + "mmol", true);
        dia = SP.getDouble(LOCAL_PROFILE + "dia", Constants.defaultDIA);
        try {
            ic = new JSONArray(SP.getString(LOCAL_PROFILE + "ic", DEFAULTARRAY));
        } catch (JSONException e1) {
            try {
                ic = new JSONArray(DEFAULTARRAY);
            } catch (JSONException ignored) {
            }
        }
        try {
            isf = new JSONArray(SP.getString(LOCAL_PROFILE + "isf", DEFAULTARRAY));
        } catch (JSONException e1) {
            try {
                isf = new JSONArray(DEFAULTARRAY);
            } catch (JSONException ignored) {
            }
        }
        try {
            basal = new JSONArray(SP.getString(LOCAL_PROFILE + "basal", DEFAULTARRAY));
        } catch (JSONException e1) {
            try {
                basal = new JSONArray(DEFAULTARRAY);
            } catch (JSONException ignored) {
            }
        }
        try {
            targetLow = new JSONArray(SP.getString(LOCAL_PROFILE + "targetlow", DEFAULTARRAY));
        } catch (JSONException e1) {
            try {
                targetLow = new JSONArray(DEFAULTARRAY);
            } catch (JSONException ignored) {
            }
        }
        try {
            targetHigh = new JSONArray(SP.getString(LOCAL_PROFILE + "targethigh", DEFAULTARRAY));
        } catch (JSONException e1) {
            try {
                targetHigh = new JSONArray(DEFAULTARRAY);
            } catch (JSONException ignored) {
            }
        }
    }

    /*
        {
            "_id": "576264a12771b7500d7ad184",
            "startDate": "2016-06-16T08:35:00.000Z",
            "defaultProfile": "Default",
            "store": {
                "Default": {
                    "dia": "3",
                    "carbratio": [{
                        "time": "00:00",
                        "value": "30"
                    }],
                    "carbs_hr": "20",
                    "delay": "20",
                    "sens": [{
                        "time": "00:00",
                        "value": "100"
                    }],
                    "timezone": "UTC",
                    "basal": [{
                        "time": "00:00",
                        "value": "0.1"
                    }],
                    "target_low": [{
                        "time": "00:00",
                        "value": "0"
                    }],
                    "target_high": [{
                        "time": "00:00",
                        "value": "0"
                    }],
                    "startDate": "1970-01-01T00:00:00.000Z",
                    "units": "mmol"
                }
            },
            "created_at": "2016-06-16T08:34:41.256Z"
        }
        */
    private void createConvertedProfile() {
        JSONObject json = new JSONObject();
        JSONObject store = new JSONObject();
        JSONObject profile = new JSONObject();

        try {
            json.put("defaultProfile", LOCAL_PROFILE);
            json.put("store", store);
            profile.put("dia", dia);
            profile.put("carbratio", ic);
            profile.put("sens", isf);
            profile.put("basal", basal);
            profile.put("target_low", targetLow);
            profile.put("target_high", targetHigh);
            profile.put("units", mgdl ? Constants.MGDL : Constants.MMOL);
            store.put(LOCAL_PROFILE, profile);
        } catch (JSONException e) {
            log.error("Unhandled exception", e);
        }
        convertedProfile = new ProfileStore(json);
        convertedProfileName = LOCAL_PROFILE;
    }

    @Override
    public ProfileStore getProfile() {
        if (convertedProfile == null)
            createConvertedProfile();
        if (!convertedProfile.getDefaultProfile().isValid(MainApp.gs(R.string.localprofile)))
            return null;
        return convertedProfile;
    }

    public ProfileStore getRawProfile() {
        if (convertedProfile == null)
            createConvertedProfile();
        return convertedProfile;
    }

    @Override
    public String getUnits() {
        return mgdl ? Constants.MGDL : Constants.MMOL;
    }

    @Override
    public String getProfileName() {
        if (convertedProfile == null)
            createConvertedProfile();
        return DecimalFormatter.to2Decimal(convertedProfile.getDefaultProfile().percentageBasalSum()) + "U ";
    }

}

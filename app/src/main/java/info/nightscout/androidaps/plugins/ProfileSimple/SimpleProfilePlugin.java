package info.nightscout.androidaps.plugins.ProfileSimple;

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
import info.nightscout.utils.SP;

/**
 * Created by mike on 05.08.2016.
 */
public class SimpleProfilePlugin implements PluginBase, ProfileInterface {
    private static Logger log = LoggerFactory.getLogger(SimpleProfilePlugin.class);

    private static SimpleProfilePlugin simpleProfilePlugin;

    public static SimpleProfilePlugin getPlugin() {
        if (simpleProfilePlugin == null)
            simpleProfilePlugin = new SimpleProfilePlugin();
        return simpleProfilePlugin;
    }

    private boolean fragmentEnabled = false;
    private boolean fragmentVisible = false;

    private static ProfileStore convertedProfile = null;

    boolean mgdl;
    boolean mmol;
    Double dia;
    Double ic;
    Double isf;
    Double basal;
    Double targetLow;
    Double targetHigh;

    private SimpleProfilePlugin() {
        loadSettings();
    }

    @Override
    public String getFragmentClass() {
        return SimpleProfileFragment.class.getName();
    }

    @Override
    public int getType() {
        return PluginBase.PROFILE;
    }

    @Override
    public String getName() {
        return MainApp.instance().getString(R.string.simpleprofile);
    }

    @Override
    public String getNameShort() {
        String name = MainApp.sResources.getString(R.string.simpleprofile_shortname);
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
        if (Config.logPrefsChange)
            log.debug("Storing settings");
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(MainApp.instance().getApplicationContext());
        SharedPreferences.Editor editor = settings.edit();
        editor.putBoolean("SimpleProfile" + "mmol", mmol);
        editor.putBoolean("SimpleProfile" + "mgdl", mgdl);
        editor.putString("SimpleProfile" + "dia", dia.toString());
        editor.putString("SimpleProfile" + "ic", ic.toString());
        editor.putString("SimpleProfile" + "isf", isf.toString());
        editor.putString("SimpleProfile" + "basal", basal.toString());
        editor.putString("SimpleProfile" + "targetlow", targetLow.toString());
        editor.putString("SimpleProfile" + "targethigh", targetHigh.toString());

        editor.apply();
        createConvertedProfile();
        if (Config.logPrefsChange)
            log.debug("Storing settings: " + getRawProfile().getData().toString());
    }

    private void loadSettings() {
        if (Config.logPrefsChange)
            log.debug("Loading stored settings");

        mgdl = SP.getBoolean("SimpleProfile" + "mgdl", true);
        mmol = SP.getBoolean("SimpleProfile" + "mmol", false);
        dia = SP.getDouble("SimpleProfile" + "dia", Constants.defaultDIA);
        ic = SP.getDouble("SimpleProfile" + "ic", 0d);
        isf = SP.getDouble("SimpleProfile" + "isf", 0d);
        basal = SP.getDouble("SimpleProfile" + "basal", 0d);
        targetLow = SP.getDouble("SimpleProfile" + "targetlow", 0d);
        targetHigh = SP.getDouble("SimpleProfile" + "targethigh", 0d);
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
            json.put("defaultProfile", "SimpleProfile");
            json.put("store", store);
            profile.put("dia", dia);
            profile.put("carbratio", new JSONArray().put(new JSONObject().put("time", "00:00").put("timeAsSeconds", 0).put("value", ic)));
            profile.put("sens", new JSONArray().put(new JSONObject().put("time", "00:00").put("timeAsSeconds", 0).put("value", isf)));
            profile.put("basal", new JSONArray().put(new JSONObject().put("time", "00:00").put("timeAsSeconds", 0).put("value", basal)));
            profile.put("target_low", new JSONArray().put(new JSONObject().put("time", "00:00").put("timeAsSeconds", 0).put("value", targetLow)));
            profile.put("target_high", new JSONArray().put(new JSONObject().put("time", "00:00").put("timeAsSeconds", 0).put("value", targetHigh)));
            profile.put("units", mgdl ? Constants.MGDL : Constants.MMOL);
            store.put("SimpleProfile", profile);
        } catch (JSONException e) {
            log.error("Unhandled exception", e);
        }
        convertedProfile = new ProfileStore(json);
    }

    @Override
    public ProfileStore getProfile() {
        if (convertedProfile == null)
            createConvertedProfile();
        if (!convertedProfile.getDefaultProfile().isValid(MainApp.gs(R.string.simpleprofile)))
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
        return "SimpleProfile";
    }

}

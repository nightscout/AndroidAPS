package info.nightscout.androidaps.plugins.SimpleProfile;

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
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.ProfileInterface;
import info.nightscout.client.data.NSProfile;
import info.nightscout.utils.SafeParse;

/**
 * Created by mike on 05.08.2016.
 */
public class SimpleProfilePlugin implements PluginBase, ProfileInterface {
    private static Logger log = LoggerFactory.getLogger(SimpleProfilePlugin.class);

    private static boolean fragmentEnabled = true;
    private static boolean fragmentVisible = true;

    private static NSProfile convertedProfile = null;

    boolean mgdl;
    boolean mmol;
    Double dia;
    Double ic;
    Double isf;
    Double car;
    Double basal;
    Double targetLow;
    Double targetHigh;

    public SimpleProfilePlugin() {
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
    public void setFragmentEnabled(int type, boolean fragmentEnabled) {
        if (type == PROFILE) this.fragmentEnabled = fragmentEnabled;
    }

    @Override
    public void setFragmentVisible(int type, boolean fragmentVisible) {
        if (type == PROFILE) this.fragmentVisible = fragmentVisible;
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
        editor.putString("SimpleProfile" + "car", car.toString());
        editor.putString("SimpleProfile" + "basal", basal.toString());
        editor.putString("SimpleProfile" + "targetlow", targetLow.toString());
        editor.putString("SimpleProfile" + "targethigh", targetHigh.toString());

        editor.commit();
        createConvertedProfile();
    }

    private void loadSettings() {
        if (Config.logPrefsChange)
            log.debug("Loading stored settings");
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(MainApp.instance().getApplicationContext());

        if (settings.contains("SimpleProfile" + "mgdl"))
            try {
                mgdl = settings.getBoolean("SimpleProfile" + "mgdl", true);
            } catch (Exception e) {
                log.debug(e.getMessage());
            }
        else mgdl = true;
        if (settings.contains("SimpleProfile" + "mmol"))
            try {
                mmol = settings.getBoolean("SimpleProfile" + "mmol", false);
            } catch (Exception e) {
                log.debug(e.getMessage());
            }
        else mmol = false;
        if (settings.contains("SimpleProfile" + "dia"))
            try {
                dia = SafeParse.stringToDouble(settings.getString("SimpleProfile" + "dia", "3"));
            } catch (Exception e) {
                log.debug(e.getMessage());
            }
        else dia = 3d;
        if (settings.contains("SimpleProfile" + "ic"))
            try {
                ic = SafeParse.stringToDouble(settings.getString("SimpleProfile" + "ic", "20"));
            } catch (Exception e) {
                log.debug(e.getMessage());
            }
        else ic = 20d;
        if (settings.contains("SimpleProfile" + "isf"))
            try {
                isf = SafeParse.stringToDouble(settings.getString("SimpleProfile" + "isf", "200"));
            } catch (Exception e) {
                log.debug(e.getMessage());
            }
        else isf = 200d;
        if (settings.contains("SimpleProfile" + "car"))
            try {
                car = SafeParse.stringToDouble(settings.getString("SimpleProfile" + "car", "20"));
            } catch (Exception e) {
                log.debug(e.getMessage());
            }
        else car = 20d;
        if (settings.contains("SimpleProfile" + "basal"))
            try {
                basal = SafeParse.stringToDouble(settings.getString("SimpleProfile" + "basal", "1"));
            } catch (Exception e) {
                log.debug(e.getMessage());
            }
        else basal = 1d;
        if (settings.contains("SimpleProfile" + "targetlow"))
            try {
                targetLow = SafeParse.stringToDouble(settings.getString("SimpleProfile" + "targetlow", "80"));
            } catch (Exception e) {
                log.debug(e.getMessage());
            }
        else targetLow = 80d;
        if (settings.contains("SimpleProfile" + "targethigh"))
            try {
                targetHigh = SafeParse.stringToDouble(settings.getString("SimpleProfile" + "targethigh", "120"));
            } catch (Exception e) {
                log.debug(e.getMessage());
            }
        else targetHigh = 120d;
        createConvertedProfile();
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
    void createConvertedProfile() {
        JSONObject json = new JSONObject();
        JSONObject store = new JSONObject();
        JSONObject profile = new JSONObject();

        try {
            json.put("defaultProfile", "SimpleProfile");
            json.put("store", store);
            profile.put("dia", dia);
            profile.put("carbratio", new JSONArray().put(new JSONObject().put("timeAsSeconds", 0).put("value", ic)));
            profile.put("carbs_hr", car);
            profile.put("sens", new JSONArray().put(new JSONObject().put("timeAsSeconds", 0).put("value", isf)));
            profile.put("basal", new JSONArray().put(new JSONObject().put("timeAsSeconds", 0).put("value", basal)));
            profile.put("target_low", new JSONArray().put(new JSONObject().put("timeAsSeconds", 0).put("value", targetLow)));
            profile.put("target_high", new JSONArray().put(new JSONObject().put("timeAsSeconds", 0).put("value", targetHigh)));
            profile.put("units", mgdl ? Constants.MGDL : Constants.MMOL);
            store.put("SimpleProfile", profile);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        convertedProfile = new NSProfile(json, "SimpleProfile");
    }

    @Override
    public NSProfile getProfile() {
        return convertedProfile;
    }

}

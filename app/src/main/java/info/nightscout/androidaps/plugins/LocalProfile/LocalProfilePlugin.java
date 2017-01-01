package info.nightscout.androidaps.plugins.LocalProfile;

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
import info.nightscout.androidaps.plugins.SimpleProfile.SimpleProfileFragment;
import info.nightscout.client.data.NSProfile;
import info.nightscout.utils.SafeParse;

/**
 * Created by mike on 05.08.2016.
 */
public class LocalProfilePlugin implements PluginBase, ProfileInterface {
    private static Logger log = LoggerFactory.getLogger(LocalProfilePlugin.class);

    private static boolean fragmentEnabled = true;
    private static boolean fragmentVisible = true;

    private static NSProfile convertedProfile = null;

    final private String DEFAULTARRAY = "[{\"timeAsSeconds\":0,\"value\":0}]";

    boolean mgdl;
    boolean mmol;
    Double dia;
    JSONArray ic;
    JSONArray isf;
    Double car;
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
        editor.putBoolean("LocalProfile" + "mmol", mmol);
        editor.putBoolean("LocalProfile" + "mgdl", mgdl);
        editor.putString("LocalProfile" + "dia", dia.toString());
        editor.putString("LocalProfile" + "ic", ic.toString());
        editor.putString("LocalProfile" + "isf", isf.toString());
        editor.putString("LocalProfile" + "car", car.toString());
        editor.putString("LocalProfile" + "basal", basal.toString());
        editor.putString("LocalProfile" + "targetlow", targetLow.toString());
        editor.putString("LocalProfile" + "targethigh", targetHigh.toString());

        editor.commit();
        createConvertedProfile();
    }

    private void loadSettings() {
        if (Config.logPrefsChange)
            log.debug("Loading stored settings");
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(MainApp.instance().getApplicationContext());

        if (settings.contains("LocalProfile" + "mgdl"))
            try {
                mgdl = settings.getBoolean("LocalProfile" + "mgdl", false);
            } catch (Exception e) {
                log.debug(e.getMessage());
            }
        else mgdl = false;
        if (settings.contains("LocalProfile" + "mmol"))
            try {
                mmol = settings.getBoolean("LocalProfile" + "mmol", true);
            } catch (Exception e) {
                log.debug(e.getMessage());
            }
        else mmol = true;
        if (settings.contains("LocalProfile" + "dia"))
            try {
                dia = SafeParse.stringToDouble(settings.getString("LocalProfile" + "dia", "3"));
            } catch (Exception e) {
                log.debug(e.getMessage());
            }
        else dia = 3d;
        if (settings.contains("LocalProfile" + "ic"))
            try {
                ic = new JSONArray(settings.getString("LocalProfile" + "ic", DEFAULTARRAY));
            } catch (Exception e) {
                log.debug(e.getMessage());
                try {
                    ic = new JSONArray(DEFAULTARRAY);
                } catch (JSONException e1) {
                    e1.printStackTrace();
                }
            }
        else {
            try {
                ic = new JSONArray(DEFAULTARRAY);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        if (settings.contains("LocalProfile" + "isf"))
            try {
                isf = new JSONArray(settings.getString("LocalProfile" + "isf", DEFAULTARRAY));
            } catch (Exception e) {
                log.debug(e.getMessage());
                try {
                    isf = new JSONArray(DEFAULTARRAY);
                } catch (JSONException e1) {
                    e1.printStackTrace();
                }
            }
        else {
            try {
                isf = new JSONArray(DEFAULTARRAY);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        if (settings.contains("LocalProfile" + "car"))
            try {
                car = SafeParse.stringToDouble(settings.getString("LocalProfile" + "car", "20"));
            } catch (Exception e) {
                log.debug(e.getMessage());
            }
        else car = 20d;
        if (settings.contains("LocalProfile" + "basal"))
            try {
                basal = new JSONArray(settings.getString("LocalProfile" + "basal", DEFAULTARRAY));
            } catch (Exception e) {
                log.debug(e.getMessage());
                try {
                    basal = new JSONArray(DEFAULTARRAY);
                } catch (JSONException e1) {
                    e1.printStackTrace();
                }
            }
        else {
            try {
                basal = new JSONArray(DEFAULTARRAY);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        if (settings.contains("LocalProfile" + "targetlow"))
            try {
                targetLow = new JSONArray(settings.getString("LocalProfile" + "targetlow", DEFAULTARRAY));
            } catch (Exception e) {
                log.debug(e.getMessage());
                try {
                    targetLow = new JSONArray(DEFAULTARRAY);
                } catch (JSONException e1) {
                    e1.printStackTrace();
                }
            }
        else {
            try {
                targetLow = new JSONArray(DEFAULTARRAY);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        if (settings.contains("LocalProfile" + "targethigh"))
            try {
                targetHigh = new JSONArray(settings.getString("LocalProfile" + "targethigh", DEFAULTARRAY));
            } catch (Exception e) {
                log.debug(e.getMessage());
                try {
                    targetHigh = new JSONArray(DEFAULTARRAY);
                } catch (JSONException e1) {
                    e1.printStackTrace();
                }
            }
        else {
            try {
                targetHigh = new JSONArray(DEFAULTARRAY);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
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
            json.put("defaultProfile", "LocalProfile");
            json.put("store", store);
            profile.put("dia", dia);
            profile.put("carbratio", ic);
            profile.put("carbs_hr", car);
            profile.put("sens", isf);
            profile.put("basal", basal);
            profile.put("target_low", targetLow);
            profile.put("target_high", targetHigh);
            profile.put("units", mgdl ? Constants.MGDL : Constants.MMOL);
            store.put("LocalProfile", profile);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        convertedProfile = new NSProfile(json, "LocalProfile");
    }

    @Override
    public NSProfile getProfile() {
        return convertedProfile;
    }

}

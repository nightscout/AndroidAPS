package info.nightscout.androidaps.plugins.CircadianPercentageProfile;

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
import info.nightscout.utils.DecimalFormatter;
import info.nightscout.utils.SafeParse;

/**
 * Created by mike on 05.08.2016.
 */
public class CircadianPercentageProfilePlugin implements PluginBase, ProfileInterface {
    public static final String SETTINGS_PREFIX = "CircadianPercentageProfile";
    private static Logger log = LoggerFactory.getLogger(CircadianPercentageProfilePlugin.class);

    private static boolean fragmentEnabled = true;
    private static boolean fragmentVisible = true;

    private static NSProfile convertedProfile = null;

    boolean mgdl;
    boolean mmol;
    Double dia;
    Double ic;
    Double isf;
    Double car;
    Double targetLow;
    Double targetHigh;
    int percentage;
    int timeshift;
    double[] basebasal = new double[]{1d, 1d, 1d, 1d, 1d, 1d, 1d, 1d, 1d, 1d, 1d, 1d, 1d, 1d, 1d, 1d, 1d, 1d, 1d, 1d, 1d, 1d, 1d, 1d};

    public CircadianPercentageProfilePlugin() {
        loadSettings();
    }

    @Override
    public String getFragmentClass() {
        return CircadianPercentageProfileFragment.class.getName();
    }

    @Override
    public int getType() {
        return PluginBase.PROFILE;
    }

    @Override
    public String getName() {
        // TODO Adrian: stringify! (omitted to prevent merge conflicts)
        return "CPP";
        //return MainApp.instance().getString(R.string.simpleprofile);
    }

    @Override
    public boolean isEnabled(int type) {
        return fragmentEnabled;
    }

    @Override
    public boolean isVisibleInTabs(int type) {
        return fragmentVisible;
    }

    @Override
    public boolean canBeHidden(int type) {
        return true;
    }

    @Override
    public void setFragmentEnabled(int type, boolean fragmentEnabled) {
        this.fragmentEnabled = fragmentEnabled;
    }

    @Override
    public void setFragmentVisible(int type, boolean fragmentVisible) {
        this.fragmentVisible = fragmentVisible;
    }

    public void storeSettings() {
        if (Config.logPrefsChange)
            log.debug("Storing settings");
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(MainApp.instance().getApplicationContext());
        SharedPreferences.Editor editor = settings.edit();
        editor.putBoolean(SETTINGS_PREFIX + "mmol", mmol);
        editor.putBoolean(SETTINGS_PREFIX + "mgdl", mgdl);
        editor.putString(SETTINGS_PREFIX + "dia", dia.toString());
        editor.putString(SETTINGS_PREFIX + "ic", ic.toString());
        editor.putString(SETTINGS_PREFIX + "isf", isf.toString());
        editor.putString(SETTINGS_PREFIX + "car", car.toString());
        editor.putString(SETTINGS_PREFIX + "targetlow", targetLow.toString());
        editor.putString(SETTINGS_PREFIX + "targethigh", targetHigh.toString());
        editor.putString(SETTINGS_PREFIX + "timeshift", timeshift+"");
        editor.putString(SETTINGS_PREFIX + "percentage", percentage+"");


        for (int i = 0; i<24; i++) {
            editor.putString(SETTINGS_PREFIX + "basebasal" + i, DecimalFormatter.to2Decimal(basebasal[i]));
        }
        editor.commit();
        createConvertedProfile();
    }

    private void loadSettings() {
        if (Config.logPrefsChange)
            log.debug("Loading stored settings");
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(MainApp.instance().getApplicationContext());

        if (settings.contains(SETTINGS_PREFIX+ "mgdl"))
            try {
                mgdl = settings.getBoolean(SETTINGS_PREFIX + "mgdl", true);
            } catch (Exception e) {
                log.debug(e.getMessage());
            }
        else mgdl = true;
        if (settings.contains(SETTINGS_PREFIX + "mmol"))
            try {
                mmol = settings.getBoolean(SETTINGS_PREFIX + "mmol", false);
            } catch (Exception e) {
                log.debug(e.getMessage());
            }
        else mmol = false;
        if (settings.contains(SETTINGS_PREFIX + "dia"))
            try {
                dia = SafeParse.stringToDouble(settings.getString(SETTINGS_PREFIX + "dia", "3"));
            } catch (Exception e) {
                log.debug(e.getMessage());
            }
        else dia = 3d;
        if (settings.contains(SETTINGS_PREFIX + "ic"))
            try {
                ic = SafeParse.stringToDouble(settings.getString(SETTINGS_PREFIX + "ic", "20"));
            } catch (Exception e) {
                log.debug(e.getMessage());
            }
        else ic = 20d;
        if (settings.contains(SETTINGS_PREFIX + "isf"))
            try {
                isf = SafeParse.stringToDouble(settings.getString(SETTINGS_PREFIX + "isf", "200"));
            } catch (Exception e) {
                log.debug(e.getMessage());
            }
        else isf = 200d;
        if (settings.contains(SETTINGS_PREFIX + "car"))
            try {
                car = SafeParse.stringToDouble(settings.getString(SETTINGS_PREFIX + "car", "20"));
            } catch (Exception e) {
                log.debug(e.getMessage());
            }
        else car = 20d;
        if (settings.contains(SETTINGS_PREFIX + "targetlow"))
            try {
                targetLow = SafeParse.stringToDouble(settings.getString(SETTINGS_PREFIX + "targetlow", "80"));
            } catch (Exception e) {
                log.debug(e.getMessage());
            }
        else targetLow = 80d;
        if (settings.contains(SETTINGS_PREFIX + "targethigh"))
            try {
                targetHigh = SafeParse.stringToDouble(settings.getString(SETTINGS_PREFIX + "targethigh", "120"));
            } catch (Exception e) {
                log.debug(e.getMessage());
            }
        else targetHigh = 120d;
        if (settings.contains(SETTINGS_PREFIX + "percentage"))
            try {
                percentage = SafeParse.stringToInt(settings.getString(SETTINGS_PREFIX + "percentage", "100"));
            } catch (Exception e) {
                log.debug(e.getMessage());
            }
        else percentage = 100;

        if (settings.contains(SETTINGS_PREFIX + "timeshift"))
            try {
                timeshift = SafeParse.stringToInt(settings.getString(SETTINGS_PREFIX + "timeshift", "0"));
            } catch (Exception e) {
                log.debug(e.getMessage());
            }
        else timeshift = 0;

        for (int i = 0; i<24; i++){
            try {
                basebasal[i] = SafeParse.stringToDouble(settings.getString(SETTINGS_PREFIX + "basebasal" + i, DecimalFormatter.to2Decimal(basebasal[i])));
            } catch (Exception e) {
                log.debug(e.getMessage());
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
            json.put("defaultProfile", "CircadianPercentage");
            json.put("store", store);
            profile.put("dia", dia);
            profile.put("carbratio", new JSONArray().put(new JSONObject().put("timeAsSeconds", 0).put("value", ic)));
            profile.put("carbs_hr", car);
            profile.put("sens", new JSONArray().put(new JSONObject().put("timeAsSeconds", 0).put("value", isf)));

            JSONArray basalArray = new JSONArray();

            for (int i = 0; i<24; i++){
                basalArray.put(new JSONObject().put("timeAsSeconds", ((i+timeshift)%24)*60*60).put("value", basebasal[i]*percentage/100d));
            }

            profile.put("basal", basalArray);


            profile.put("target_low", new JSONArray().put(new JSONObject().put("timeAsSeconds", 0).put("value", targetLow)));
            profile.put("target_high", new JSONArray().put(new JSONObject().put("timeAsSeconds", 0).put("value", targetHigh)));
            profile.put("units", mgdl ? Constants.MGDL : Constants.MMOL);
            store.put("CircadianPercentage", profile);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        convertedProfile = new NSProfile(json, "CircadianPercentage");
    }

    @Override
    public NSProfile getProfile() {
        return convertedProfile;
    }

}

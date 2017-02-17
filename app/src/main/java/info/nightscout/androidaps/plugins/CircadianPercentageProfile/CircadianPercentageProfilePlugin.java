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
import info.nightscout.androidaps.plugins.NSClientInternal.data.NSProfile;
import info.nightscout.utils.DecimalFormatter;
import info.nightscout.utils.SP;
import info.nightscout.utils.SafeParse;
import info.nightscout.utils.ToastUtils;

/**
 * Created by Adrian on 12.11.2016.
 * Based on SimpleProfile created by mike on 05.08.2016.
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
    Double targetLow;
    Double targetHigh;
    public int percentage;
    public int timeshift;
    double[] basebasal = new double[]{1d, 1d, 1d, 1d, 1d, 1d, 1d, 1d, 1d, 1d, 1d, 1d, 1d, 1d, 1d, 1d, 1d, 1d, 1d, 1d, 1d, 1d, 1d, 1d};
    double[] baseisf = new double[]{35d, 35d, 35d, 35d, 35d, 35d, 35d, 35d, 35d, 35d, 35d, 35d, 35d, 35d, 35d, 35d, 35d, 35d, 35d, 35d, 35d, 35d, 35d, 35d};
    double[] baseic = new double[]{4d, 4d, 4d, 4d, 4d, 4d, 4d, 4d, 4d, 4d, 4d, 4d, 4d, 4d, 4d, 4d, 4d, 4d, 4d, 4d, 4d, 4d, 4d, 4d};

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
        return MainApp.instance().getString(R.string.circadian_percentage_profile);
    }

    @Override
    public String getNameShort() {
        String name = MainApp.sResources.getString(R.string.circadian_percentage_profile_shortname);
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
    public void setFragmentEnabled(int type, boolean fragmentEnabled) {
        if (type == PROFILE) this.fragmentEnabled = fragmentEnabled;
    }

    @Override
    public void setFragmentVisible(int type, boolean fragmentVisible) {
        if (type == PROFILE) this.fragmentVisible = fragmentVisible;
    }

    void storeSettings() {
        if (Config.logPrefsChange)
            log.debug("Storing settings");
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(MainApp.instance().getApplicationContext());
        SharedPreferences.Editor editor = settings.edit();
        editor.putBoolean(SETTINGS_PREFIX + "mmol", mmol);
        editor.putBoolean(SETTINGS_PREFIX + "mgdl", mgdl);
        editor.putString(SETTINGS_PREFIX + "dia", dia.toString());
        editor.putString(SETTINGS_PREFIX + "targetlow", targetLow.toString());
        editor.putString(SETTINGS_PREFIX + "targethigh", targetHigh.toString());
        editor.putString(SETTINGS_PREFIX + "timeshift", timeshift + "");
        editor.putString(SETTINGS_PREFIX + "percentage", percentage + "");


        for (int i = 0; i < 24; i++) {
            editor.putString(SETTINGS_PREFIX + "basebasal" + i, DecimalFormatter.to2Decimal(basebasal[i]));
            editor.putString(SETTINGS_PREFIX + "baseisf" + i, DecimalFormatter.to2Decimal(baseisf[i]));
            editor.putString(SETTINGS_PREFIX + "baseic" + i, DecimalFormatter.to2Decimal(baseic[i]));
        }
        editor.commit();
        createConvertedProfile();
    }

    void loadSettings() {
        if (Config.logPrefsChange)
            log.debug("Loading stored settings");

        mgdl = SP.getBoolean(SETTINGS_PREFIX + "mgdl", true);
        mmol = SP.getBoolean(SETTINGS_PREFIX + "mmol", false);
        dia = SP.getDouble(SETTINGS_PREFIX + "dia", 3d);
        targetLow = SP.getDouble(SETTINGS_PREFIX + "targetlow", 80d);
        targetHigh = SP.getDouble(SETTINGS_PREFIX + "targethigh", 120d);
        percentage = SP.getInt(SETTINGS_PREFIX + "percentage", 100);
        timeshift = SP.getInt(SETTINGS_PREFIX + "timeshift", 0);

        for (int i = 0; i < 24; i++) {
            basebasal[i] = SP.getDouble(SETTINGS_PREFIX + "basebasal" + i, basebasal[i]);
            baseic[i] = SP.getDouble(SETTINGS_PREFIX + "baseic" + i, baseic[i]);
            baseisf[i] = SP.getDouble(SETTINGS_PREFIX + "baseisf" + i, baseisf[i]);
        }


        createConvertedProfile();
    }

    private void createConvertedProfile() {
        JSONObject json = new JSONObject();
        JSONObject store = new JSONObject();
        JSONObject profile = new JSONObject();

        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(DecimalFormatter.to2Decimal(sum(basebasal)));
        stringBuilder.append("U@");
        stringBuilder.append(percentage);
        stringBuilder.append("%>");
        stringBuilder.append(timeshift);
        stringBuilder.append("h");
        String profileName = stringBuilder.toString();

        try {
            json.put("defaultProfile", profileName);
            json.put("store", store);
            profile.put("dia", dia);

            int offset = -(timeshift % 24) + 24;

            JSONArray icArray = new JSONArray();
            for (int i = 0; i < 24; i++) {
                icArray.put(new JSONObject().put("timeAsSeconds", i * 60 * 60).put("value", baseic[(offset + i) % 24] * 100d / percentage));
            }
            profile.put("carbratio", icArray);

            JSONArray isfArray = new JSONArray();
            for (int i = 0; i < 24; i++) {
                isfArray.put(new JSONObject().put("timeAsSeconds", i * 60 * 60).put("value", baseisf[(offset + i) % 24] * 100d / percentage));
            }
            profile.put("sens", isfArray);

            JSONArray basalArray = new JSONArray();
            for (int i = 0; i < 24; i++) {
                basalArray.put(new JSONObject().put("timeAsSeconds", i * 60 * 60).put("value", basebasal[(offset + i) % 24] * percentage / 100d));
            }
            profile.put("basal", basalArray);


            profile.put("target_low", new JSONArray().put(new JSONObject().put("timeAsSeconds", 0).put("value", targetLow)));
            profile.put("target_high", new JSONArray().put(new JSONObject().put("timeAsSeconds", 0).put("value", targetHigh)));
            profile.put("units", mgdl ? Constants.MGDL : Constants.MMOL);
            store.put(profileName, profile);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        convertedProfile = new NSProfile(json, profileName);
    }

    @Override
    public NSProfile getProfile() {

        performLimitCheck();

        return convertedProfile;
    }

    private void performLimitCheck() {
        if (percentage < Constants.CPP_MIN_PERCENTAGE || percentage > Constants.CPP_MAX_PERCENTAGE) {
            String msg = String.format(MainApp.sResources.getString(R.string.openapsma_valueoutofrange), "Profile-Percentage");
            log.error(msg);
            MainApp.getConfigBuilder().uploadError(msg);
            ToastUtils.showToastInUiThread(MainApp.instance().getApplicationContext(), msg, R.raw.error);
            percentage = Math.max(percentage, Constants.CPP_MIN_PERCENTAGE);
            percentage = Math.min(percentage, Constants.CPP_MAX_PERCENTAGE);
        }
    }

    String basalString() {
        return profileString(basebasal, timeshift, percentage, true);
    }

    String icString() {
        return profileString(baseic, timeshift, percentage, false);
    }

    String isfString() {
        return profileString(baseisf, timeshift, percentage, false);
    }

    String baseIcString() {
        return profileString(baseic, 0, 100, false);
    }

    String baseIsfString() {
        return profileString(baseisf, 0, 100, false);
    }

    String baseBasalString() {
        return profileString(basebasal, 0, 100, true);
    }

    public double baseBasalSum() {
        return sum(basebasal);
    }

    public double percentageBasalSum() {
        double result = 0;
        for (int i = 0; i < basebasal.length; i++) {
            result += SafeParse.stringToDouble(DecimalFormatter.to2Decimal(basebasal[i] * percentage / 100d));
        }
        return result;
    }


    public static double sum(double values[]) {
        double result = 0;
        for (int i = 0; i < values.length; i++) {
            result += values[i];
        }
        return result;
    }


    private static String profileString(double[] values, int timeshift, int percentage, boolean inc) {
        timeshift = -(timeshift % 24) + 24;
        StringBuilder sb = new StringBuilder();
        sb.append("<b>");
        sb.append(0);
        sb.append("h: ");
        sb.append("</b>");
        sb.append(DecimalFormatter.to2Decimal(values[(timeshift + 0) % 24] * (inc ? percentage / 100d : 100d / percentage)));
        double prevVal = values[(timeshift + 0) % 24];
        for (int i = 1; i < 24; i++) {
            if (prevVal != values[(timeshift + i) % 24]) {
                sb.append(", ");
                sb.append("<b>");
                sb.append(i);
                sb.append("h: ");
                sb.append("</b>");
                sb.append(DecimalFormatter.to2Decimal(values[(timeshift + i) % 24] * (inc ? percentage / 100d : 100d / percentage)));
                prevVal = values[(timeshift + i) % 24];
            }
        }
        return sb.toString();
    }

}

package info.nightscout.androidaps.plugins.NSClientInternal.data;

import android.support.annotation.Nullable;

import com.crashlytics.android.Crashlytics;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.TimeZone;

import info.nightscout.androidaps.Constants;
import info.nightscout.utils.DecimalFormatter;

public class NSProfile {
    private static Logger log = LoggerFactory.getLogger(NSProfile.class);

    private JSONObject json = null;
    private String activeProfile = null;

    public NSProfile(JSONObject json, String activeProfile) {
        this.json = json;
        this.activeProfile = null;
        JSONObject store;
        try {
            store = json.getJSONObject("store");
            if (activeProfile != null && store.has(activeProfile)) {
                this.activeProfile = activeProfile;
            } else {
                log.error("Active profile not found in store");
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public JSONObject getDefaultProfile() {
        String defaultProfileName = null;
        JSONObject store;
        JSONObject profile = null;
        try {
            defaultProfileName = (String) json.get("defaultProfile");
            store = json.getJSONObject("store");
            if (activeProfile != null && store.has(activeProfile)) {
                defaultProfileName = activeProfile;
            }
            profile = store.getJSONObject(defaultProfileName);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return profile;
    }

    public JSONObject getSpecificProfile(String profileName) {
        JSONObject profile = null;
        try {
            JSONObject store = json.getJSONObject("store");
            if (store.has(profileName)) {
                profile = store.getJSONObject(profileName);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return profile;
    }

    public ArrayList<CharSequence> getProfileList() {
        ArrayList<CharSequence> ret = new ArrayList<CharSequence>();

        JSONObject store;
        JSONObject profile = null;
        try {
            store = json.getJSONObject("store");
            Iterator<?> keys = store.keys();

            while (keys.hasNext()) {
                String profileName = (String) keys.next();
                ret.add(profileName);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return ret;
    }

    public String log() {
        String ret = "\n";
        for (Integer hour = 0; hour < 24; hour++) {
            double value = getBasal(hour * 60 * 60);
            ret += "NS basal value for " + hour + ":00 is " + value + "\n";
        }
        ret += "NS units: " + getUnits();
        return ret;
    }

    public JSONObject getData() {
        return json;
    }

    public Double getDia() {
        return getDia(getDefaultProfile());
    }

    public Double getDia(JSONObject profile) {
        Double dia;
        if (profile != null) {
            try {
                dia = profile.getDouble("dia");
                return dia;
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return Constants.defaultDIA;
    }
/*
    public Double getCarbAbsorbtionRate() {
        return getCarbAbsorbtionRate(getDefaultProfile());
    }

    public Double getCarbAbsorbtionRate(JSONObject profile) {
        Double carbAbsorptionRate;
        if (profile != null) {
            try {
                carbAbsorptionRate = profile.getDouble("carbs_hr");
                return carbAbsorptionRate;
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return 0D;
    }
*/
    // mmol or mg/dl
    public String getUnits() {
        return getUnits(getDefaultProfile());
    }

    public String getUnits(JSONObject profile) {
        String units;
        if (profile != null) {
            try {
                units = profile.getString("units");
                return units.toLowerCase();
            } catch (JSONException e) {
                log.error("Profile not found. Failing over to main JSON");
                try {
                    return json.getString("units").toLowerCase();
                } catch (JSONException e1) {
                    e1.printStackTrace();
                    Crashlytics.log("Profile failover failed too");
                }
            }
        }
        return Constants.MGDL;
    }

    public TimeZone getTimeZone() {
        return getTimeZone(getDefaultProfile());
    }

    public TimeZone getTimeZone(JSONObject profile) {
        TimeZone timeZone;
        if (profile != null) {
            try {
                return TimeZone.getTimeZone(profile.getString("timezone"));
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return TimeZone.getDefault();
    }

    @Nullable
    public Double getValueToTime(JSONArray array, Integer timeAsSeconds) {
        Double lastValue = null;

        for (Integer index = 0; index < array.length(); index++) {
            try {
                JSONObject o = array.getJSONObject(index);
                Integer tas = o.getInt("timeAsSeconds");
                Double value = o.getDouble("value");
                if (lastValue == null) lastValue = value;
                if (timeAsSeconds < tas) {
                    break;
                }
                lastValue = value;
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return lastValue;
    }

    public String getValuesList(JSONArray array, JSONArray array2, DecimalFormat format, String units) {
        String retValue = "";

        for (Integer index = 0; index < array.length(); index++) {
            try {
                JSONObject o = array.getJSONObject(index);
                retValue += o.getString("time");
                retValue += "    ";
                retValue += format.format(o.getDouble("value"));
                if (array2 != null) {
                    JSONObject o2 = array2.getJSONObject(index);
                    retValue += " - ";
                    retValue += format.format(o2.getDouble("value"));
                }
                retValue += " " + units;
                retValue += "\n";
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return retValue;
    }

    @Nullable
    public Double getIsf(Integer timeAsSeconds) {
        return getIsf(getDefaultProfile(), timeAsSeconds);
    }

    @Nullable
    public Double getIsf(JSONObject profile, Integer timeAsSeconds) {
        if (profile != null) {
            try {
                return getValueToTime(profile.getJSONArray("sens"), timeAsSeconds);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    public String getIsfList() {
        return getIsfList(getDefaultProfile());
    }

    public String getIsfList(JSONObject profile) {
        if (profile != null) {
            try {
                return getValuesList(profile.getJSONArray("sens"), null, new DecimalFormat("0.0"), getUnits() + "/U");
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return "";
    }

    @Nullable
    public Double getIc(Integer timeAsSeconds) {
        return getIc(getDefaultProfile(), timeAsSeconds);
    }

    @Nullable
    public Double getIc(JSONObject profile, Integer timeAsSeconds) {
        if (profile != null) {
            try {
                return getValueToTime(profile.getJSONArray("carbratio"), timeAsSeconds);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return 0D;
    }

    public String getIcList() {
        return getIcList(getDefaultProfile());
    }

    public String getIcList(JSONObject profile) {
        if (profile != null) {
            try {
                return getValuesList(profile.getJSONArray("carbratio"), null, new DecimalFormat("0.0"), "g");
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return "";
    }

    @Nullable
    public Double getBasal(Integer timeAsSeconds) {
        return getBasal(getDefaultProfile(), timeAsSeconds);
    }

    @Nullable
    public Double getBasal(JSONObject profile, Integer timeAsSeconds) {
        if (profile != null) {
            try {
                return getValueToTime(profile.getJSONArray("basal"), timeAsSeconds);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return 0D;
    }

    public String getBasalList() {
        return getBasalList(getDefaultProfile());
    }

    public class BasalValue {
        public BasalValue(Integer timeAsSeconds, Double value) {
            this.timeAsSeconds = timeAsSeconds;
            this.value = value;
        }

        public Integer timeAsSeconds;
        public Double value;
    }

    public BasalValue[] getBasalValues() {
        try {
            JSONArray array = getDefaultProfile().getJSONArray("basal");
            BasalValue[] ret = new BasalValue[array.length()];

            for (Integer index = 0; index < array.length(); index++) {
                JSONObject o = array.getJSONObject(index);
                Integer tas = o.getInt("timeAsSeconds");
                Double value = o.getDouble("value");
                ret[index] = new BasalValue(tas, value);
            }
            return ret;
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return new BasalValue[0];
    }

    public String getBasalList(JSONObject profile) {
        if (profile != null) {
            try {
                return getValuesList(profile.getJSONArray("basal"), null, new DecimalFormat("0.00"), "U");
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return "";
    }

    @Nullable
    public Double getTargetLow(Integer timeAsSeconds) {
        return getTargetLow(getDefaultProfile(), timeAsSeconds);
    }

    @Nullable
    public Double getTargetLow(JSONObject profile, Integer timeAsSeconds) {
        if (profile != null) {
            try {
                return getValueToTime(profile.getJSONArray("target_low"), timeAsSeconds);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return 0D;
    }

    @Nullable
    public Double getTargetHigh(Integer timeAsSeconds) {
        return getTargetHigh(getDefaultProfile(), timeAsSeconds);
    }

    @Nullable
    public Double getTargetHigh(JSONObject profile, Integer timeAsSeconds) {
        if (profile != null) {
            try {
                return getValueToTime(profile.getJSONArray("target_high"), timeAsSeconds);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return 0D;
    }

    public String getTargetList() {
        return getTargetList(getDefaultProfile());
    }

    public String getTargetList(JSONObject profile) {
        if (profile != null) {
            try {
                return getValuesList(profile.getJSONArray("target_low"), profile.getJSONArray("target_high"), new DecimalFormat("0.0"), getUnits());
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return "";
    }

    public String getActiveProfile() {
        if (activeProfile != null)
            return activeProfile;
        else {
            try {
                JSONObject store = json.getJSONObject("store");
                String defaultProfileName = (String) json.get("defaultProfile");
                if (store.has(defaultProfileName)) {
                    return defaultProfileName;
                }
                log.error("Default profile not found");
                return null;
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    public void setActiveProfile(String newProfile) {
        try {
            JSONObject store = json.getJSONObject("store");
            if (newProfile != null && store.has(newProfile)) {
                activeProfile = newProfile;
            } else {
                log.error("Attempt to set wrong active profile");
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public double getMaxDailyBasal() {
        Double max = 0d;
        for (Integer hour = 0; hour < 24; hour++) {
            double value = getBasal(hour * 60 * 60);
            if (value > max) max = value;
        }
        return max;
    }

    public static int secondsFromMidnight() {
        Calendar c = Calendar.getInstance();
        long now = c.getTimeInMillis();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        long passed = now - c.getTimeInMillis();
        return (int) (passed / 1000);
    }

    public static int secondsFromMidnight(Date date) {
        Calendar c = Calendar.getInstance();
        long now = date.getTime();
        c.setTime(date);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        long passed = now - c.getTimeInMillis();
        return (int) (passed / 1000);
    }

    public static int secondsFromMidnight(long date) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(date);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        long passed = date - c.getTimeInMillis();
        return (int) (passed / 1000);
    }

    public static Double toMgdl(Double value, String units) {
        if (units.equals(Constants.MGDL)) return value;
        else return value * Constants.MMOLL_TO_MGDL;
    }

    public static Double fromMgdlToUnits(Double value, String units) {
        if (units.equals(Constants.MGDL)) return value;
        else return value * Constants.MGDL_TO_MMOLL;
    }

    public static Double toUnits(Double valueInMgdl, Double valueInMmol, String units) {
        if (units.equals(Constants.MGDL)) return valueInMgdl;
        else return valueInMmol;
    }

    public static String toUnitsString(Double valueInMgdl, Double valueInMmol, String units) {
        if (units.equals(Constants.MGDL)) return DecimalFormatter.to0Decimal(valueInMgdl);
        else return DecimalFormatter.to1Decimal(valueInMmol);
    }
}

package info.nightscout.client.data;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.DecimalFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

import info.nightscout.androidaps.Constants;

public class NSProfile {
    private JSONObject json = null;
    private String activeProfile = null;

    public NSProfile(JSONObject json, String activeProfile) {
        this.json = json;
        this.activeProfile = activeProfile;
    }

    JSONObject getDefaultProfile() {
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

    public String log() {
        String ret = "\n";
        for (Integer hour = 0; hour < 24; hour ++) {
            double value = getBasal(hour * 60 * 60);
            ret += "NS basal value for " + hour + ":00 is " + value + "\n";
        }
        ret += "NS units: " + getUnits();
        return ret;
    }

    public JSONObject getData () {
        return json;
    }

    public Double getDia() {
        Double dia;
        JSONObject profile = getDefaultProfile();
        if (profile != null) {
            try {
                dia = profile.getDouble("dia");
                return dia;
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return 3D;
    }

   public Double getCarbAbsorbtionRate() {
        Double carbAbsorptionRate;
        JSONObject profile = getDefaultProfile();
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

    // mmol or mg/dl
   public String getUnits() {
       String units;
        JSONObject profile = getDefaultProfile();
        if (profile != null) {
            try {
                units = profile.getString("units");
                return units;
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
       return "mg/dl";
    }

   public TimeZone getTimeZone() {
       TimeZone timeZone;
        JSONObject profile = getDefaultProfile();
        if (profile != null) {
            try {
                return TimeZone.getTimeZone(profile.getString("timezone"));
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
       return TimeZone.getDefault();
    }

    public Double getValueToTime(JSONArray array, Integer timeAsSeconds) {
        Double lastValue = null;

        for(Integer index = 0; index < array.length(); index++) {
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

        for(Integer index = 0; index < array.length(); index++) {
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

    public Double getIsf(Integer timeAsSeconds) {
        JSONObject profile = getDefaultProfile();
        if (profile != null) {
            try {
                return getValueToTime(profile.getJSONArray("sens"),timeAsSeconds);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return 0D;
    }

    public String getIsfList() {
        JSONObject profile = getDefaultProfile();
        if (profile != null) {
            try {
                return getValuesList(profile.getJSONArray("sens"), null, new DecimalFormat("0"), getUnits() + "/U");
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return "";
    }

    public Double getIc(Integer timeAsSeconds) {
        JSONObject profile = getDefaultProfile();
        if (profile != null) {
            try {
                return getValueToTime(profile.getJSONArray("carbratio"),timeAsSeconds);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return 0D;
    }

    public String getIcList() {
        JSONObject profile = getDefaultProfile();
        if (profile != null) {
            try {
                return getValuesList(profile.getJSONArray("carbratio"), null, new DecimalFormat("0"), "g");
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return "";
    }

    public Double getBasal(Integer timeAsSeconds) {
        JSONObject profile = getDefaultProfile();
        if (profile != null) {
            try {
                return getValueToTime(profile.getJSONArray("basal"),timeAsSeconds);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return 0D;
    }

    public String getBasalList() {
        JSONObject profile = getDefaultProfile();
        if (profile != null) {
            try {
                return getValuesList(profile.getJSONArray("basal"), null, new DecimalFormat("0.00"), "U");
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return "";
    }

    public Double getTargetLow(Integer timeAsSeconds) {
        JSONObject profile = getDefaultProfile();
        if (profile != null) {
            try {
                return getValueToTime(profile.getJSONArray("target_low"),timeAsSeconds);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return 0D;
    }

    public Double getTargetHigh(Integer timeAsSeconds) {
        JSONObject profile = getDefaultProfile();
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
        JSONObject profile = getDefaultProfile();
        if (profile != null) {
            try {
                return getValuesList(profile.getJSONArray("target_low"),profile.getJSONArray("target_high"), new DecimalFormat("0.0"), getUnits());
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return "";
    }

    public String getActiveProfile() {
        return activeProfile;
    }

    public Double getMaxDailyBasal() {
        Double max = 0d;
        for (Integer hour = 0; hour < 24; hour ++) {
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

    public static Double toMgdl(Double value, String units) {
        if (units.equals(Constants.MGDL)) return value;
        else return value * Constants.MMOLL_TO_MGDL;
    }
}

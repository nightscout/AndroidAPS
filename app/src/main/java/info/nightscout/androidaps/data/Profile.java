package info.nightscout.androidaps.data;

import android.support.v4.util.LongSparseArray;

import com.crashlytics.android.Crashlytics;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DecimalFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.plugins.Overview.Notification;
import info.nightscout.androidaps.plugins.Overview.events.EventDismissNotification;
import info.nightscout.androidaps.plugins.Overview.events.EventNewNotification;
import info.nightscout.utils.DecimalFormatter;
import info.nightscout.utils.ToastUtils;

public class Profile {
    private static Logger log = LoggerFactory.getLogger(Profile.class);

    private JSONObject json;
    private String units = null;
    double dia = Constants.defaultDIA;
    TimeZone timeZone = TimeZone.getDefault();
    JSONArray isf;
    private LongSparseArray<Double> isf_v = null; // oldest at index 0
    JSONArray ic;
    private LongSparseArray<Double> ic_v = null; // oldest at index 0
    JSONArray basal;
    private LongSparseArray<Double> basal_v = null; // oldest at index 0
    JSONArray targetLow;
    JSONArray targetHigh;

    public Profile(JSONObject json, String units) {
        this(json);
        if (this.units == null) {
            if (units != null)
                this.units = units;
            else {
                Crashlytics.log("Profile failover failed too");
                this.units = Constants.MGDL;
            }
        }
    }

    public Profile(JSONObject json) {
        this.json = json;
        try {
            if (json.has("units"))
                units = json.getString("units").toLowerCase();
            if (json.has("dia"))
                dia = json.getDouble("dia");
            if (json.has("dia"))
                dia = json.getDouble("dia");
            if (json.has("timezone"))
                timeZone = TimeZone.getTimeZone(json.getString("timezone"));
            isf = json.getJSONArray("sens");
            if (getIsf(0) == null) {
                int defaultISF = units.equals(Constants.MGDL) ? 400 : 20;
                isf = new JSONArray("[{\"time\":\"00:00\",\"value\":\"" + defaultISF + "\",\"timeAsSeconds\":\"0\"}]");
                Notification noisf = new Notification(Notification.ISF_MISSING, MainApp.sResources.getString(R.string.isfmissing), Notification.URGENT);
                MainApp.bus().post(new EventNewNotification(noisf));
            } else {
                MainApp.bus().post(new EventDismissNotification(Notification.ISF_MISSING));
            }
            ic = json.getJSONArray("carbratio");
            if (getIc(0) == null) {
                int defaultIC = 25;
                ic = new JSONArray("[{\"time\":\"00:00\",\"value\":\"" + defaultIC + "\",\"timeAsSeconds\":\"0\"}]");
                Notification noic = new Notification(Notification.IC_MISSING, MainApp.sResources.getString(R.string.icmissing), Notification.URGENT);
                MainApp.bus().post(new EventNewNotification(noic));
            } else {
                MainApp.bus().post(new EventDismissNotification(Notification.IC_MISSING));
            }
            basal = json.getJSONArray("basal");
            if (getBasal(0) == null) {
                double defaultBasal = 0.1d;
                basal = new JSONArray("[{\"time\":\"00:00\",\"value\":\"" + defaultBasal + "\",\"timeAsSeconds\":\"0\"}]");
                Notification nobasal = new Notification(Notification.BASAL_MISSING, MainApp.sResources.getString(R.string.basalmissing), Notification.URGENT);
                MainApp.bus().post(new EventNewNotification(nobasal));
            } else {
                MainApp.bus().post(new EventDismissNotification(Notification.BASAL_MISSING));
            }
            targetLow = json.getJSONArray("target_low");
            if (getTargetLow(0) == null) {
                double defaultLow = units.equals(Constants.MGDL) ? 120 : 6;
                targetLow = new JSONArray("[{\"time\":\"00:00\",\"value\":\"" + defaultLow + "\",\"timeAsSeconds\":\"0\"}]");
                Notification notarget = new Notification(Notification.TARGET_MISSING, MainApp.sResources.getString(R.string.targetmissing), Notification.URGENT);
                MainApp.bus().post(new EventNewNotification(notarget));
            } else {
                MainApp.bus().post(new EventDismissNotification(Notification.TARGET_MISSING));
            }
            targetHigh = json.getJSONArray("target_high");
            if (getTargetHigh(0) == null) {
                double defaultHigh = units.equals(Constants.MGDL) ? 160 : 8;
                targetHigh = new JSONArray("[{\"time\":\"00:00\",\"value\":\"" + defaultHigh + "\",\"timeAsSeconds\":\"0\"}]");
                Notification notarget = new Notification(Notification.TARGET_MISSING, MainApp.sResources.getString(R.string.targetmissing), Notification.URGENT);
                MainApp.bus().post(new EventNewNotification(notarget));
            } else {
                MainApp.bus().post(new EventDismissNotification(Notification.TARGET_MISSING));
            }
        } catch (JSONException e) {
            e.printStackTrace();
            ToastUtils.showToastInUiThread(MainApp.instance().getApplicationContext(), MainApp.sResources.getString(R.string.invalidprofile));
        }
    }

    public String log() {
        String ret = "\n";
        for (Integer hour = 0; hour < 24; hour++) {
            double value = getBasal((Integer) (hour * 60 * 60));
            ret += "NS basal value for " + hour + ":00 is " + value + "\n";
        }
        ret += "NS units: " + getUnits();
        return ret;
    }

    public JSONObject getData() {
        if (!json.has("units"))
            try {
                json.put("units", units);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        return json;
    }

    public double getDia() {
        return dia;
    }

    // mmol or mg/dl
    public String getUnits() {
        return units;
    }

    public TimeZone getTimeZone() {
        return timeZone;
    }

    private LongSparseArray<Double> convertToSparseArray(JSONArray array) {
        LongSparseArray<Double> sparse = new LongSparseArray<>();
        for (Integer index = 0; index < array.length(); index++) {
            try {
                JSONObject o = array.getJSONObject(index);
                long tas = o.getLong("timeAsSeconds");
                Double value = o.getDouble("value");
                sparse.put(tas, value);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return sparse;
    }

    private Double getValueToTime(JSONArray array, Integer timeAsSeconds) {
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

    private Double getValueToTime(LongSparseArray<Double> array, long timeAsSeconds) {
        Double lastValue = null;

        for (Integer index = 0; index < array.size(); index++) {
            long tas = array.keyAt(index);
            double value = array.valueAt(index);
            if (lastValue == null) lastValue = value;
            if (timeAsSeconds < tas) {
                break;
            }
            lastValue = value;
        }
        return lastValue;
    }

    private String getValuesList(JSONArray array, JSONArray array2, DecimalFormat format, String units) {
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
                if (index + 1 < array.length())
                    retValue += "\n";
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return retValue;
    }

    public Double getIsf() {
        return getIsf(secondsFromMidnight(System.currentTimeMillis()));
    }

    public Double getIsf(long time) {
        return getIsf(secondsFromMidnight(time));
    }

    public Double getIsf(Integer timeAsSeconds) {
        if (isf_v == null)
            isf_v = convertToSparseArray(isf);
        return getValueToTime(isf_v, timeAsSeconds);
    }

    public String getIsfList() {
        return getValuesList(isf, null, new DecimalFormat("0.0"), getUnits() + "/U");
    }

    public Double getIc() {
        return getIc(secondsFromMidnight(System.currentTimeMillis()));
    }

    public Double getIc(long time) {
        return getIc(secondsFromMidnight(time));
    }

    public Double getIc(Integer timeAsSeconds) {
        if (ic_v == null)
            ic_v = convertToSparseArray(ic);
        return getValueToTime(ic_v, timeAsSeconds);
    }

    public String getIcList() {
        return getValuesList(ic, null, new DecimalFormat("0.0"), " g/U");
    }

    public Double getBasal() {
        return getBasal(secondsFromMidnight(System.currentTimeMillis()));
    }

    public Double getBasal(long time) {
        return getBasal(secondsFromMidnight(time));
    }

    public Double getBasal(Integer timeAsSeconds) {
        if (basal_v == null)
            basal_v = convertToSparseArray(basal);
        return getValueToTime(basal_v, timeAsSeconds);
    }

    public String getBasalList() {
        return getValuesList(basal, null, new DecimalFormat("0.00"), "U");
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
            BasalValue[] ret = new BasalValue[basal.length()];

            for (Integer index = 0; index < basal.length(); index++) {
                JSONObject o = basal.getJSONObject(index);
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

    public Double getTargetLow() {
        return getTargetLow(secondsFromMidnight(System.currentTimeMillis()));
    }

    public Double getTargetLow(long time) {
        return getTargetLow(secondsFromMidnight(time));
    }

    public Double getTargetLow(Integer timeAsSeconds) {
        return getValueToTime(targetLow, timeAsSeconds);
    }

    public Double getTargetHigh() {
        return getTargetHigh(secondsFromMidnight(System.currentTimeMillis()));
    }

    public Double getTargetHigh(long time) {
        return getTargetHigh(secondsFromMidnight(time));
    }

    public Double getTargetHigh(Integer timeAsSeconds) {
        return getValueToTime(targetHigh, timeAsSeconds);
    }

    public String getTargetList() {
        return getValuesList(targetLow, targetHigh, new DecimalFormat("0.0"), getUnits());
    }

    public double getMaxDailyBasal() {
        Double max = 0d;
        for (Integer hour = 0; hour < 24; hour++) {
            double value = getBasal((Integer) (hour * 60 * 60));
            if (value > max) max = value;
        }
        return max;
    }

    public static Integer secondsFromMidnight() {
        Calendar c = Calendar.getInstance();
        long now = c.getTimeInMillis();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        long passed = now - c.getTimeInMillis();
        return (int) (passed / 1000);
    }

    public static Integer secondsFromMidnight(Date date) {
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

    public static Integer secondsFromMidnight(long date) {
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

    public static Double toMmol(Double value, String units) {
        if (units.equals(Constants.MGDL)) return value * Constants.MGDL_TO_MMOLL;
        else return value;
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

    // targets are stored in mg/dl but profile vary
    public static String toTargetRangeString(double low, double high, String sourceUnits, String units) {
        double lowMgdl = toMgdl(low, sourceUnits);
        double highMgdl = toMgdl(high, sourceUnits);
        double lowMmol = toMmol(low, sourceUnits);
        double highMmol = toMmol(high, sourceUnits);
        if (low == high)
            return toUnitsString(lowMgdl, lowMmol, units);
        else
            return toUnitsString(lowMgdl, lowMmol, units) + " - " + toUnitsString(highMgdl, highMmol, units);

    }
}

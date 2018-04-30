package info.nightscout.androidaps.data;

import android.support.v4.util.LongSparseArray;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DecimalFormat;
import java.util.Calendar;
import java.util.TimeZone;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.interfaces.PumpDescription;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.plugins.Overview.events.EventNewNotification;
import info.nightscout.androidaps.plugins.Overview.notifications.Notification;
import info.nightscout.utils.DateUtil;
import info.nightscout.utils.DecimalFormatter;
import info.nightscout.utils.FabricPrivacy;

public class Profile {
    private static Logger log = LoggerFactory.getLogger(Profile.class);

    private JSONObject json;
    private String units;
    private double dia;
    private TimeZone timeZone;
    private JSONArray isf;
    private LongSparseArray<Double> isf_v; // oldest at index 0
    private JSONArray ic;
    private LongSparseArray<Double> ic_v; // oldest at index 0
    private JSONArray basal;
    private LongSparseArray<Double> basal_v; // oldest at index 0
    private JSONArray targetLow;
    private LongSparseArray<Double> targetLow_v; // oldest at index 0
    private JSONArray targetHigh;
    private LongSparseArray<Double> targetHigh_v; // oldest at index 0

    private int percentage;
    private int timeshift;

    protected boolean isValid;
    protected boolean isValidated;

    // Default constructor for tests
    protected Profile() {
    }

    // Constructor from profileStore JSON
    public Profile(JSONObject json, String units) {
        init(json, 100, 0);
        if (this.units == null) {
            if (units != null)
                this.units = units;
            else {
                FabricPrivacy.log("Profile failover failed too");
                this.units = Constants.MGDL;
            }
        }
    }

    public Profile(JSONObject json, int percentage, int timeshift) {
        init(json, percentage, timeshift);
    }

    protected void init(JSONObject json, int percentage, int timeshift) {
        units = null;
        dia = Constants.defaultDIA;
        timeZone = TimeZone.getDefault();
        isf_v = null;
        ic_v = null;
        basal_v = null;
        targetLow_v = null;
        targetHigh_v = null;

        isValid = true;
        isValidated = false;

        this.percentage = percentage;
        this.timeshift = timeshift;
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
            ic = json.getJSONArray("carbratio");
            basal = json.getJSONArray("basal");
            targetLow = json.getJSONArray("target_low");
            targetHigh = json.getJSONArray("target_high");
        } catch (JSONException e) {
            log.error("Unhandled exception", e);
            isValid = false;
            isValidated = true;
        }
    }

    public String log() {
        String ret = "\n";
        for (Integer hour = 0; hour < 24; hour++) {
            double value = getBasalTimeFromMidnight((Integer) (hour * 60 * 60));
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
                log.error("Unhandled exception", e);
            }
        return json;
    }

    public double getDia() {
        return dia;
    }

    // mmol or mg/dl
    public void setUnits(String units) {
        this.units = units;
    }

    public String getUnits() {
        return units;
    }

    public TimeZone getTimeZone() {
        return timeZone;
    }

    private LongSparseArray<Double> convertToSparseArray(JSONArray array) {
        if (array == null) {
            isValid = false;
            return new LongSparseArray<>();
        }

        double multiplier = getMultiplier(array);

        LongSparseArray<Double> sparse = new LongSparseArray<>();
        for (Integer index = 0; index < array.length(); index++) {
            try {
                final JSONObject o = array.getJSONObject(index);
                long tas = 0;
                try {
                    tas = getShitfTimeSecs((int) o.getLong("timeAsSeconds"));
                } catch (JSONException e) {
                    String time = o.getString("time");
                    tas = getShitfTimeSecs(DateUtil.toSeconds(time));
                    //log.debug(">>>>>>>>>>>> Used recalculated timeAsSecons: " + time + " " + tas);
                }
                double value = o.getDouble("value") * multiplier;
                sparse.put(tas, value);
            } catch (JSONException e) {
                log.error("Unhandled exception", e);
                log.error(json.toString());
            }
        }

        // check if start is at 0 (midnight)
        // and add last value before midnight if not
        if (sparse.keyAt(0) != 0) {
            sparse.put(0, sparse.valueAt(sparse.size() - 1));
        }
        return sparse;
    }

    public synchronized boolean isValid(String from) {
        if (!isValid)
            return false;
        if (!isValidated) {
            if (basal_v == null)
                basal_v = convertToSparseArray(basal);
            validate(basal_v);
            if (isf_v == null)
                isf_v = convertToSparseArray(isf);
            validate(isf_v);
            if (ic_v == null)
                ic_v = convertToSparseArray(ic);
            validate(ic_v);
            if (targetLow_v == null)
                targetLow_v = convertToSparseArray(targetLow);
            validate(targetLow_v);
            if (targetHigh_v == null)
                targetHigh_v = convertToSparseArray(targetHigh);
            validate(targetHigh_v);
            isValidated = true;
        }

        if (isValid) {
            // Check for hours alignment
            PumpInterface pump = MainApp.getConfigBuilder().getActivePump();
            if (pump != null && !pump.getPumpDescription().is30minBasalRatesCapable) {
                for (int index = 0; index < basal_v.size(); index++) {
                    long secondsFromMidnight = basal_v.keyAt(index);
                    if (secondsFromMidnight % 3600 != 0) {
                        Notification notification = new Notification(Notification.BASAL_PROFILE_NOT_ALIGNED_TO_HOURS, String.format(MainApp.gs(R.string.basalprofilenotaligned), from), Notification.NORMAL);
                        MainApp.bus().post(new EventNewNotification(notification));
                    }
                }
            }

            // Check for minimal basal value
            if (pump != null) {
                PumpDescription description = pump.getPumpDescription();
                for (int i = 0; i < basal_v.size(); i++) {
                    if (basal_v.valueAt(i) < description.basalMinimumRate) {
                        basal_v.setValueAt(i, description.basalMinimumRate);
                        sendBelowMinimumNotification(from);
                    }
                }
            } else {
                // if pump not available (at start)
                // do not store converted array
                basal_v = null;
                isValidated = false;
            }

         }
        return isValid;
    }

    protected void sendBelowMinimumNotification(String from) {
        MainApp.bus().post(new EventNewNotification(new Notification(Notification.MINIMAL_BASAL_VALUE_REPLACED,  String.format(MainApp.gs(R.string.minimalbasalvaluereplaced), from), Notification.NORMAL)));
    }

    private void validate(LongSparseArray array) {
        if (array.size() == 0) {
            isValid = false;
            return;
        }
        for (int index = 0; index < array.size(); index++) {
            if (array.valueAt(index).equals(0d)) {
                isValid = false;
                return;
            }
        }
    }

    /*
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
                log.error("Unhandled exception", e);
            }
        }
        return lastValue;
    }
    */

    Integer getShitfTimeSecs(Integer originalTime) {
        Integer shiftedTime = originalTime + timeshift * 60 * 60;
        shiftedTime = (shiftedTime + 24 * 60 * 60) % (24 * 60 * 60);
        if (timeshift != 0 && Config.logProfile)
            log.debug("(Sec) Original time: " + originalTime + " ShiftedTime: " + shiftedTime);
        return shiftedTime;
    }

    private double getMultiplier(LongSparseArray<Double> array) {
        double multiplier = 1d;

        if (array == isf_v)
            multiplier = 100d / percentage;
        else if (array == ic_v)
            multiplier = 100d / percentage;
        else if (array == basal_v)
            multiplier = percentage / 100d;
        else
            log.error("Unknown array type");
        return multiplier;
    }

    private double getMultiplier(JSONArray array) {
        double multiplier = 1d;

        if (array == isf)
            multiplier = 100d / percentage;
        else if (array == ic)
            multiplier = 100d / percentage;
        else if (array == basal)
            multiplier = percentage / 100d;
        else if (array == targetLow)
            multiplier = 1d;
        else if (array == targetHigh)
            multiplier = 1d;
        else
            log.error("Unknown array type");
        return multiplier;
    }

    private double getValueToTime(LongSparseArray<Double> array, Integer timeAsSeconds) {
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

    protected String format_HH_MM(Integer timeAsSeconds) {
        String time;
        int hour = timeAsSeconds / 60 / 60;
        int minutes = (timeAsSeconds - hour * 60 * 60) / 60;
        DecimalFormat df = new DecimalFormat("00");
        time = df.format(hour) + ":" + df.format(minutes);
        return time;
    }

    private String getValuesList(LongSparseArray<Double> array, LongSparseArray<Double> array2, DecimalFormat format, String units) {
        String retValue = "";

        for (Integer index = 0; index < array.size(); index++) {
            retValue += format_HH_MM((int) array.keyAt(index));
            retValue += "    ";
            retValue += format.format(array.valueAt(index));
            if (array2 != null) {
                retValue += " - ";
                retValue += format.format(array2.valueAt(index));
            }
            retValue += " " + units;
            if (index + 1 < array.size())
                retValue += "\n";
        }
        return retValue;
    }

    public double getIsf() {
        return getIsfTimeFromMidnight(secondsFromMidnight(System.currentTimeMillis()));
    }

    public double getIsf(long time) {
        return getIsfTimeFromMidnight(secondsFromMidnight(time));
    }

    double getIsfTimeFromMidnight(int timeAsSeconds) {
        if (isf_v == null)
            isf_v = convertToSparseArray(isf);
        return getValueToTime(isf_v, timeAsSeconds);
    }

    public String getIsfList() {
        if (isf_v == null)
            isf_v = convertToSparseArray(isf);
        return getValuesList(isf_v, null, new DecimalFormat("0.0"), getUnits() + "/U");
    }

    public double getIc() {
        return getIcTimeFromMidnight(secondsFromMidnight(System.currentTimeMillis()));
    }

    public double getIc(long time) {
        return getIcTimeFromMidnight(secondsFromMidnight(time));
    }

    public double getIcTimeFromMidnight(int timeAsSeconds) {
        if (ic_v == null)
            ic_v = convertToSparseArray(ic);
        return getValueToTime(ic_v, timeAsSeconds);
    }

    public String getIcList() {
        if (ic_v == null)
            ic_v = convertToSparseArray(ic);
        return getValuesList(ic_v, null, new DecimalFormat("0.0"), "g/U");
    }

    public double getBasal() {
        return getBasalTimeFromMidnight(secondsFromMidnight(System.currentTimeMillis()));
    }

    public double getBasal(long time) {
        return getBasalTimeFromMidnight(secondsFromMidnight(time));
    }

    public synchronized double getBasalTimeFromMidnight(int timeAsSeconds) {
        if (basal_v == null) {
            basal_v = convertToSparseArray(basal);
        }
        return getValueToTime(basal_v, timeAsSeconds);
    }

    public String getBasalList() {
        if (basal_v == null)
            basal_v = convertToSparseArray(basal);
        return getValuesList(basal_v, null, new DecimalFormat("0.00"), "U/h");
    }

    public class BasalValue {
        public BasalValue(int timeAsSeconds, double value) {
            this.timeAsSeconds = timeAsSeconds;
            this.value = value;
        }

        public int timeAsSeconds;
        public double value;
    }

    public synchronized BasalValue[] getBasalValues() {
        if (basal_v == null)
            basal_v = convertToSparseArray(basal);
        BasalValue[] ret = new BasalValue[basal_v.size()];

        for (Integer index = 0; index < basal_v.size(); index++) {
            Integer tas = (int) basal_v.keyAt(index);
            double value = basal_v.valueAt(index);
            ret[index] = new BasalValue(tas, value);
        }
        return ret;
    }

    public double getTarget(){
        return  getTarget(secondsFromMidnight(System.currentTimeMillis()));
    }

    protected double getTarget(int timeAsSeconds) {
        return (getTargetLowTimeFromMidnight(timeAsSeconds) + getTargetHighTimeFromMidnight(timeAsSeconds))/2;
    }

    public double getTargetLow() {
        return getTargetLowTimeFromMidnight(secondsFromMidnight(System.currentTimeMillis()));
    }

    public double getTargetLow(long time) {
        return getTargetLowTimeFromMidnight(secondsFromMidnight(time));
    }

    public double getTargetLowTimeFromMidnight(int timeAsSeconds) {
        if (targetLow_v == null)
            targetLow_v = convertToSparseArray(targetLow);
        return getValueToTime(targetLow_v, timeAsSeconds);
    }

    public double getTargetHigh() {
        return getTargetHighTimeFromMidnight(secondsFromMidnight(System.currentTimeMillis()));
    }

    public double getTargetHigh(long time) {
        return getTargetHighTimeFromMidnight(secondsFromMidnight(time));
    }

    public double getTargetHighTimeFromMidnight(int timeAsSeconds) {
        if (targetHigh_v == null)
            targetHigh_v = convertToSparseArray(targetHigh);
        return getValueToTime(targetHigh_v, timeAsSeconds);
    }

    public String getTargetList() {
        if (targetLow_v == null)
            targetLow_v = convertToSparseArray(targetLow);
        if (targetHigh_v == null)
            targetHigh_v = convertToSparseArray(targetHigh);
        return getValuesList(targetLow_v, targetHigh_v, new DecimalFormat("0.0"), getUnits());
    }

    public double getMaxDailyBasal() {
        double max = 0d;
        for (int hour = 0; hour < 24; hour++) {
            double value = getBasalTimeFromMidnight((Integer) (hour * 60 * 60));
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

    public static double toMgdl(double value, String units) {
        if (units.equals(Constants.MGDL)) return value;
        else return value * Constants.MMOLL_TO_MGDL;
    }

    public static double toMmol(double value, String units) {
        if (units.equals(Constants.MGDL)) return value * Constants.MGDL_TO_MMOLL;
        else return value;
    }

    public static double fromMgdlToUnits(double value, String units) {
        if (units.equals(Constants.MGDL)) return value;
        else return value * Constants.MGDL_TO_MMOLL;
    }

    public static double toUnits(Double valueInMgdl, Double valueInMmol, String units) {
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

    public double percentageBasalSum() {
        double result = 0d;
        for (int i = 0; i < 24; i++) {
            result += getBasalTimeFromMidnight(i * 60 * 60);
        }
        return result;
    }


    public double baseBasalSum() {
        double result = 0d;
        for (int i = 0; i < 24; i++) {
            result += getBasalTimeFromMidnight(i * 60 * 60) / getMultiplier(basal_v);
        }
        return result;
    }

    public int getPercentage() {
        return percentage;
    }

    public int getTimeshift() {
        return timeshift;
    }
}

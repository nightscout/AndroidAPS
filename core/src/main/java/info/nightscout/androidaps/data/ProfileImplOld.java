package info.nightscout.androidaps.data;

import androidx.annotation.NonNull;
import androidx.collection.LongSparseArray;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.DecimalFormat;
import java.util.TimeZone;

import javax.inject.Inject;

import dagger.android.HasAndroidInjector;
import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.core.R;
import info.nightscout.androidaps.interfaces.ActivePlugin;
import info.nightscout.androidaps.interfaces.Config;
import info.nightscout.androidaps.interfaces.GlucoseUnit;
import info.nightscout.androidaps.interfaces.Profile;
import info.nightscout.androidaps.interfaces.Pump;
import info.nightscout.androidaps.interfaces.PumpDescription;
import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.plugins.bus.RxBusWrapper;
import info.nightscout.androidaps.plugins.general.overview.events.EventNewNotification;
import info.nightscout.androidaps.plugins.general.overview.notifications.Notification;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.FabricPrivacy;
import info.nightscout.androidaps.utils.resources.ResourceHelper;

public class ProfileImplOld implements Profile {
    @Inject public AAPSLogger aapsLogger;
    @Inject public ActivePlugin activePlugin;
    @Inject public ResourceHelper resourceHelper;
    @Inject public RxBusWrapper rxBus;
    @Inject public FabricPrivacy fabricPrivacy;
    @Inject public Config config;
    @Inject public DateUtil dateUtil;

    private final HasAndroidInjector injector;

    private JSONObject json;
    private String jsonUnits;
    private double dia; // TODO change to insulinInterface link
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

    protected ProfileImplOld(HasAndroidInjector injector) {
        injector.androidInjector().inject(this);
        this.injector = injector;
    }

    @NonNull @Override
    public String toString() {
        if (json != null)
            return json.toString();
        else
            return "Profile has no JSON";
    }

    // Constructor from profileStore JSON
    public ProfileImplOld(HasAndroidInjector injector, JSONObject json, GlucoseUnit units) {
        this(injector);
        init(json, 100, 0);
        if (this.jsonUnits == null) {
            if (units != null)
                this.jsonUnits = units.getAsText();
            else {
                fabricPrivacy.logCustom("Profile failover failed too");
                this.jsonUnits = Constants.MGDL;
            }
        }
    }

    // Constructor from profileStore JSON
    public ProfileImplOld(HasAndroidInjector injector, JSONObject json) {
        this(injector);
        init(json, 100, 0);
    }

    public ProfileImplOld(HasAndroidInjector injector, JSONObject json, int percentage, int timeshift) {
        this(injector);
        init(json, percentage, timeshift);
    }

    protected void init(JSONObject json, int percentage, int timeshift) {
        if (json == null) return;
        jsonUnits = null;
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
                jsonUnits = json.getString("units").toLowerCase();
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
            aapsLogger.error("Unhandled exception", e);
            isValid = false;
            isValidated = true;
        }
    }

    public String log() {
        String ret = "\n";
        for (int hour = 0; hour < 24; hour++) {
            double value = getBasalTimeFromMidnight(hour * 60 * 60);
            ret += "NS basal value for " + hour + ":00 is " + value + "\n";
        }
        ret += "NS units: " + getUnits();
        return ret;
    }

    public JSONObject getData() {
        if (!json.has("units"))
            try {
                json.put("units", jsonUnits);
            } catch (JSONException e) {
                aapsLogger.error("Unhandled exception", e);
            }
        return json;
    }

    public double getDia() {
        return dia;
    }

    // mmol or mg/dl
    public void setUnits(String units) {
        this.jsonUnits = units;
    }

    public GlucoseUnit getUnits() {
        if (jsonUnits.equals(Constants.MMOL)) return GlucoseUnit.MMOL;
        else return GlucoseUnit.MGDL;
    }

    TimeZone getTimeZone() {
        return timeZone;
    }

    private LongSparseArray<Double> convertToSparseArray(JSONArray array) {
        if (array == null) {
            isValid = false;
            return new LongSparseArray<>();
        }

        double multiplier = getMultiplier(array);

        LongSparseArray<Double> sparse = new LongSparseArray<>();
        for (int index = 0; index < array.length(); index++) {
            try {
                final JSONObject o = array.getJSONObject(index);
                long tas;
                try {
                    String time = o.getString("time");
                    tas = getShitfTimeSecs(dateUtil.toSeconds(time));
                } catch (JSONException e) {
                    //log.debug(">>>>>>>>>>>> Used recalculated timeAsSecons: " + time + " " + tas);
                    tas = getShitfTimeSecs((int) o.getLong("timeAsSeconds"));
                }
                double value = o.getDouble("value") * multiplier;
                sparse.put(tas, value);
            } catch (Exception e) {
                aapsLogger.error("Unhandled exception", e);
                aapsLogger.error(json.toString());
                fabricPrivacy.logException(e);
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
        return isValid(from, true);
    }

    public synchronized boolean isValid(String from, boolean notify) {
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

            if (targetHigh_v.size() != targetLow_v.size()) isValid = false;
            else for (int i = 0; i < targetHigh_v.size(); i++)
                if (targetHigh_v.valueAt(i) < targetLow_v.valueAt(i))
                    isValid = false;

            isValidated = true;
        }

        if (isValid) {
            // Check for hours alignment
            Pump pump = activePlugin.getActivePump();
            if (!pump.getPumpDescription().is30minBasalRatesCapable()) {
                for (int index = 0; index < basal_v.size(); index++) {
                    long secondsFromMidnight = basal_v.keyAt(index);
                    if (notify && secondsFromMidnight % 3600 != 0) {
                        if (config.getAPS()) {
                            Notification notification = new Notification(Notification.BASAL_PROFILE_NOT_ALIGNED_TO_HOURS, resourceHelper.gs(R.string.basalprofilenotaligned, from), Notification.NORMAL);
                            rxBus.send(new EventNewNotification(notification));
                        }
                    }
                }
            }

            // Check for minimal basal value
            PumpDescription description = pump.getPumpDescription();
            for (int i = 0; i < basal_v.size(); i++) {
                if (basal_v.valueAt(i) < description.getBasalMinimumRate()) {
                    basal_v.setValueAt(i, description.getBasalMinimumRate());
                    if (notify)
                        sendBelowMinimumNotification(from);
                } else if (basal_v.valueAt(i) > description.getBasalMaximumRate()) {
                    basal_v.setValueAt(i, description.getBasalMaximumRate());
                    if (notify)
                        sendAboveMaximumNotification(from);
                }
            }

        }
        return isValid;
    }

    protected void sendBelowMinimumNotification(String from) {
        rxBus.send(new EventNewNotification(new Notification(Notification.MINIMAL_BASAL_VALUE_REPLACED, resourceHelper.gs(R.string.minimalbasalvaluereplaced, from), Notification.NORMAL)));
    }

    protected void sendAboveMaximumNotification(String from) {
        rxBus.send(new EventNewNotification(new Notification(Notification.MAXIMUM_BASAL_VALUE_REPLACED, resourceHelper.gs(R.string.maximumbasalvaluereplaced, from), Notification.NORMAL)));
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
        int shiftedTime = originalTime + timeshift * 60 * 60;
        shiftedTime = (shiftedTime + 24 * 60 * 60) % (24 * 60 * 60);
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
            aapsLogger.error("Unknown array type");
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
            aapsLogger.error("Unknown array type");
        return multiplier;
    }

    private double getValueToTime(LongSparseArray<Double> array, Integer timeAsSeconds) {
        Double lastValue = null;

        for (int index = 0; index < array.size(); index++) {
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

    private String getValuesList(LongSparseArray<Double> array, LongSparseArray<Double> array2, DecimalFormat format, String units) {
        String retValue = "";

        for (Integer index = 0; index < array.size(); index++) {
            retValue += dateUtil.format_HH_MM((int) array.keyAt(index));
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

    public double getIsfMgdl() {
        return info.nightscout.androidaps.interfaces.Profile.Companion.toMgdl(getIsfTimeFromMidnight(info.nightscout.androidaps.interfaces.Profile.Companion.secondsFromMidnight()), getUnits());
    }

    public double getIsfMgdl(long time) {
        return info.nightscout.androidaps.interfaces.Profile.Companion.toMgdl(getIsfTimeFromMidnight(info.nightscout.androidaps.interfaces.Profile.Companion.secondsFromMidnight(time)), getUnits());
    }

    public double getIsfMgdlTimeFromMidnight(int timeAsSeconds) {
        return info.nightscout.androidaps.interfaces.Profile.Companion.toMgdl(getIsfTimeFromMidnight(timeAsSeconds), getUnits());
    }

    public double getIsfTimeFromMidnight(int timeAsSeconds) {
        if (isf_v == null)
            isf_v = convertToSparseArray(isf);
        return getValueToTime(isf_v, timeAsSeconds);
    }

    public String getIsfList() {
        if (isf_v == null)
            isf_v = convertToSparseArray(isf);
        return getValuesList(isf_v, null, new DecimalFormat("0.0"), getUnits() + resourceHelper.gs(R.string.profile_per_unit));
    }

    public ProfileValue[] getIsfsMgdl() {
        if (isf_v == null)
            isf_v = convertToSparseArray(ic);
        ProfileValue[] ret = new ProfileValue[isf_v.size()];

        for (int index = 0; index < isf_v.size(); index++) {
            int tas = (int) isf_v.keyAt(index);
            double value = isf_v.valueAt(index);
            ret[index] = new ProfileValue(tas, info.nightscout.androidaps.interfaces.Profile.Companion.toMgdl(value, getUnits()));
        }
        return ret;
    }

    public double getIc() {
        return getIcTimeFromMidnight(info.nightscout.androidaps.interfaces.Profile.Companion.secondsFromMidnight());
    }

    public double getIc(long time) {
        return getIcTimeFromMidnight(info.nightscout.androidaps.interfaces.Profile.Companion.secondsFromMidnight(time));
    }

    public double getIcTimeFromMidnight(int timeAsSeconds) {
        if (ic_v == null)
            ic_v = convertToSparseArray(ic);
        return getValueToTime(ic_v, timeAsSeconds);
    }

    public String getIcList() {
        if (ic_v == null)
            ic_v = convertToSparseArray(ic);
        return getValuesList(ic_v, null, new DecimalFormat("0.0"), resourceHelper.gs(R.string.profile_carbs_per_unit));
    }

    public ProfileValue[] getIcs() {
        if (ic_v == null)
            ic_v = convertToSparseArray(ic);
        ProfileValue[] ret = new ProfileValue[ic_v.size()];

        for (int index = 0; index < ic_v.size(); index++) {
            int tas = (int) ic_v.keyAt(index);
            double value = ic_v.valueAt(index);
            ret[index] = new ProfileValue(tas, value);
        }
        return ret;
    }

    public double getBasal() {
        return getBasalTimeFromMidnight(info.nightscout.androidaps.interfaces.Profile.Companion.secondsFromMidnight());
    }

    public double getBasal(long time) {
        return getBasalTimeFromMidnight(info.nightscout.androidaps.interfaces.Profile.Companion.secondsFromMidnight(time));
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
        return getValuesList(basal_v, null, new DecimalFormat("0.00"), resourceHelper.gs(R.string.profile_ins_units_per_hour));
    }

    @NonNull @Override public JSONObject toNsJson() {
        return getData();
    }

    public synchronized ProfileValue[] getBasalValues() {
        if (basal_v == null)
            basal_v = convertToSparseArray(basal);
        ProfileValue[] ret = new ProfileValue[basal_v.size()];

        for (int index = 0; index < basal_v.size(); index++) {
            int tas = (int) basal_v.keyAt(index);
            double value = basal_v.valueAt(index);
            ret[index] = new ProfileValue(tas, value);
        }
        return ret;
    }

    public double getTargetMgdl() {
        return getTargetMgdl(info.nightscout.androidaps.interfaces.Profile.Companion.secondsFromMidnight());
    }

    public double getTargetMgdl(int timeAsSeconds) {
        return info.nightscout.androidaps.interfaces.Profile.Companion.toMgdl((getTargetLowTimeFromMidnight(timeAsSeconds) + getTargetHighTimeFromMidnight(timeAsSeconds)) / 2, getUnits());
    }

    public double getTargetLowMgdl() {
        return info.nightscout.androidaps.interfaces.Profile.Companion.toMgdl(getTargetLowTimeFromMidnight(info.nightscout.androidaps.interfaces.Profile.Companion.secondsFromMidnight()), getUnits());
    }

    public double getTargetLowMgdl(long time) {
        return info.nightscout.androidaps.interfaces.Profile.Companion.toMgdl(getTargetLowTimeFromMidnight(info.nightscout.androidaps.interfaces.Profile.Companion.secondsFromMidnight(time)), getUnits());
    }

    double getTargetLowTimeFromMidnight(int timeAsSeconds) {
        if (targetLow_v == null)
            targetLow_v = convertToSparseArray(targetLow);
        return getValueToTime(targetLow_v, timeAsSeconds);
    }

    public double getTargetLowMgdlTimeFromMidnight(int timeAsSeconds) {
        return info.nightscout.androidaps.interfaces.Profile.Companion.toMgdl(getTargetLowTimeFromMidnight(timeAsSeconds), getUnits());
    }

    public double getTargetHighMgdl() {
        return info.nightscout.androidaps.interfaces.Profile.Companion.toMgdl(getTargetHighTimeFromMidnight(info.nightscout.androidaps.interfaces.Profile.Companion.secondsFromMidnight()), getUnits());
    }

    public double getTargetHighMgdl(long time) {
        return info.nightscout.androidaps.interfaces.Profile.Companion.toMgdl(getTargetHighTimeFromMidnight(info.nightscout.androidaps.interfaces.Profile.Companion.secondsFromMidnight(time)), getUnits());
    }

    public double getTargetHighTimeFromMidnight(int timeAsSeconds) {
        if (targetHigh_v == null)
            targetHigh_v = convertToSparseArray(targetHigh);
        return getValueToTime(targetHigh_v, timeAsSeconds);
    }

    public double getTargetHighMgdlTimeFromMidnight(int timeAsSeconds) {
        return info.nightscout.androidaps.interfaces.Profile.Companion.toMgdl(getTargetHighTimeFromMidnight(timeAsSeconds), getUnits());
    }

    public static class TargetValue {
        TargetValue(int timeAsSeconds, double low, double high) {
            this.timeAsSeconds = timeAsSeconds;
            this.low = low;
            this.high = high;
        }

        public int timeAsSeconds;
        public double low;
        public double high;
    }

    public TargetValue[] getTargets() {
        if (targetLow_v == null)
            targetLow_v = convertToSparseArray(targetLow);
        if (targetHigh_v == null)
            targetHigh_v = convertToSparseArray(targetHigh);
        TargetValue[] ret = new TargetValue[targetLow_v.size()];

        for (int index = 0; index < targetLow_v.size(); index++) {
            int tas = (int) targetLow_v.keyAt(index);
            double low = targetLow_v.valueAt(index);
            double high = targetHigh_v.valueAt(index);
            ret[index] = new TargetValue(tas, low, high);
        }
        return ret;
    }

    public ProfileValue[] getSingleTargetsMgdl() {
        if (targetLow_v == null)
            targetLow_v = convertToSparseArray(targetLow);
        if (targetHigh_v == null)
            targetHigh_v = convertToSparseArray(targetHigh);
        ProfileValue[] ret = new ProfileValue[targetLow_v.size()];

        for (int index = 0; index < targetLow_v.size(); index++) {
            int tas = (int) targetLow_v.keyAt(index);
            double target = (targetLow_v.valueAt(index) + targetHigh_v.valueAt(index)) / 2;
            ret[index] = new ProfileValue(tas, info.nightscout.androidaps.interfaces.Profile.Companion.toMgdl(target, getUnits()));
        }
        return ret;
    }

    @NonNull public String getTargetList() {
        if (targetLow_v == null)
            targetLow_v = convertToSparseArray(targetLow);
        if (targetHigh_v == null)
            targetHigh_v = convertToSparseArray(targetHigh);
        return getValuesList(targetLow_v, targetHigh_v, new DecimalFormat("0.0"), getUnits().getAsText());
    }

    public double getMaxDailyBasal() {
        double max = 0d;
        for (int hour = 0; hour < 24; hour++) {
            double value = getBasalTimeFromMidnight(hour * 60 * 60);
            if (value > max) max = value;
        }
        return max;
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

    public Profile convertToNonCustomizedProfile() {
        JSONObject o = new JSONObject();
        try {
            o.put("units", jsonUnits);
            o.put("dia", dia);
            o.put("timezone", timeZone.getID());
            // SENS
            JSONArray sens = new JSONArray();
            double lastValue = -1d;
            for (int i = 0; i < 24; i++) {
                int timeAsSeconds = i * 60 * 60;
                double value = getIsfTimeFromMidnight(timeAsSeconds);
                if (value != lastValue) {
                    JSONObject item = new JSONObject();
                    String time;
                    DecimalFormat df = new DecimalFormat("00");
                    time = df.format(i) + ":00";
                    item.put("time", time);
                    item.put("timeAsSeconds", timeAsSeconds);
                    item.put("value", value);
                    lastValue = value;
                    sens.put(item);
                }
            }
            o.put("sens", sens);
            // CARBRATIO
            JSONArray carbratio = new JSONArray();
            lastValue = -1d;
            for (int i = 0; i < 24; i++) {
                int timeAsSeconds = i * 60 * 60;
                double value = getIcTimeFromMidnight(timeAsSeconds);
                if (value != lastValue) {
                    JSONObject item = new JSONObject();
                    String time;
                    DecimalFormat df = new DecimalFormat("00");
                    time = df.format(i) + ":00";
                    item.put("time", time);
                    item.put("timeAsSeconds", timeAsSeconds);
                    item.put("value", value);
                    lastValue = value;
                    carbratio.put(item);
                }
            }
            o.put("carbratio", carbratio);
            // BASAL
            JSONArray basal = new JSONArray();
            lastValue = -1d;
            for (int i = 0; i < 24; i++) {
                int timeAsSeconds = i * 60 * 60;
                double value = getBasalTimeFromMidnight(timeAsSeconds);
                if (value != lastValue) {
                    JSONObject item = new JSONObject();
                    String time;
                    DecimalFormat df = new DecimalFormat("00");
                    time = df.format(i) + ":00";
                    item.put("time", time);
                    item.put("timeAsSeconds", timeAsSeconds);
                    item.put("value", value);
                    lastValue = value;
                    basal.put(item);
                }
            }
            o.put("basal", basal);
            // TARGET_LOW
            JSONArray target_low = new JSONArray();
            lastValue = -1d;
            for (int i = 0; i < 24; i++) {
                int timeAsSeconds = i * 60 * 60;
                double value = getTargetLowTimeFromMidnight(timeAsSeconds);
                if (value != lastValue) {
                    JSONObject item = new JSONObject();
                    String time;
                    DecimalFormat df = new DecimalFormat("00");
                    time = df.format(i) + ":00";
                    item.put("time", time);
                    item.put("timeAsSeconds", timeAsSeconds);
                    item.put("value", value);
                    lastValue = value;
                    target_low.put(item);
                }
            }
            o.put("target_low", target_low);
            // TARGET_HIGH
            JSONArray target_high = new JSONArray();
            lastValue = -1d;
            for (int i = 0; i < 24; i++) {
                int timeAsSeconds = i * 60 * 60;
                double value = getTargetHighTimeFromMidnight(timeAsSeconds);
                if (value != lastValue) {
                    JSONObject item = new JSONObject();
                    String time;
                    DecimalFormat df = new DecimalFormat("00");
                    time = df.format(i) + ":00";
                    item.put("time", time);
                    item.put("timeAsSeconds", timeAsSeconds);
                    item.put("value", value);
                    lastValue = value;
                    target_high.put(item);
                }
            }
            o.put("target_high", target_high);

        } catch (JSONException e) {
            aapsLogger.error("Unhandled exception" + e);
        }
        return new ProfileImplOld(injector, o);
    }
}

package info.nightscout.androidaps.plugins.general.automation.actions;

import org.json.JSONException;
import org.json.JSONObject;

import info.nightscout.androidaps.data.GlucoseStatus;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.plugins.ConfigBuilder.ProfileFunctions;
import info.nightscout.utils.JsonHelper;

public class TriggerBg extends Trigger {

    double threshold;
    int comparator = ISEQUAL;
    String units = ProfileFunctions.getInstance().getProfileUnits();

    @Override
    synchronized boolean shouldRun() {
        GlucoseStatus glucoseStatus = GlucoseStatus.getGlucoseStatusData();

        if (glucoseStatus == null && comparator == NOTAVAILABLE)
            return true;
        if (glucoseStatus == null)
            return false;

        switch (comparator) {
            case ISLOWER:
                return glucoseStatus.glucose < Profile.toMgdl(threshold, units);
            case ISEQUALORLOWER:
                return glucoseStatus.glucose <= Profile.toMgdl(threshold, units);
            case ISEQUAL:
                return glucoseStatus.glucose == Profile.toMgdl(threshold, units);
            case ISEQUALORGREATER:
                return glucoseStatus.glucose >= Profile.toMgdl(threshold, units);
            case ISGREATER:
                return glucoseStatus.glucose > Profile.toMgdl(threshold, units);
        }
        return false;
    }

    @Override
    synchronized String toJSON() {
        JSONObject o = new JSONObject();
        try {
            o.put("type", TriggerBg.class.getName());
            JSONObject data = new JSONObject();
            data.put("threshold", threshold);
            data.put("comparator", comparator);
            data.put("units", units);
            o.put("data", data.toString());
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return o.toString();
    }

    @Override
    Trigger fromJSON(String data) {
        try {
            JSONObject d = new JSONObject(data);
            threshold = JsonHelper.safeGetDouble(d, "threshold");
            comparator = JsonHelper.safeGetInt(d, "comparator");
            units = JsonHelper.safeGetString(d, "units");
         } catch (JSONException e) {
            e.printStackTrace();
        }
        return this;
    }

    TriggerBg threshold(double threshold) {
        this.threshold = threshold;
        return this;
    }

    TriggerBg comparator(int comparator) {
        this.comparator = comparator;
        return this;
    }

    TriggerBg units(String units) {
        this.units = units;
        return this;
    }
}

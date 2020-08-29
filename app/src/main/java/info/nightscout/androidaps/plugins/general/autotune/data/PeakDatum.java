package info.nightscout.androidaps.plugins.general.autotune.data;

import org.json.JSONException;
import org.json.JSONObject;

public class PeakDatum {
    double peak=0d;
    double meanDeviation=0d;
    double smrDeviation=0d;
    double rmsDeviation=0d;

    public PeakDatum() {}

    public PeakDatum(JSONObject json) {
        try {
            if (json.has("peak")) peak = json.getDouble("peak");
            if (json.has("meanDeviation")) meanDeviation = json.getDouble("meanDeviation");
            if (json.has("SMRDeviation")) smrDeviation = json.getDouble("SMRDeviation");
            if (json.has("RMSDeviation")) rmsDeviation = json.getDouble("RMSDeviation");
        } catch (JSONException e) {}
    }

    public JSONObject toJSON() {
        JSONObject crjson = new JSONObject();
        try {
            crjson.put("peak", peak);
            crjson.put("meanDeviation", (int) meanDeviation);
            crjson.put("SMRDeviation", smrDeviation);
            crjson.put("RMSDeviation", (int) rmsDeviation);
        } catch (JSONException e) {}
        return crjson;
    }

    public Boolean equals(PeakDatum obj) {
        Boolean isEqual = true;
        if (peak != obj.peak) isEqual = false;
        if (meanDeviation != obj.meanDeviation) isEqual = false;
        if (smrDeviation != obj.smrDeviation) isEqual = false;
        if (rmsDeviation != obj.rmsDeviation) isEqual = false;
        return isEqual;
    }
}

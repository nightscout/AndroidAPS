package info.nightscout.androidaps.plugins.general.autotune.data;

import org.json.JSONException;
import org.json.JSONObject;

public class DiaDatum {
    double dia=0d;
    double meanDeviation=0d;
    double smrDeviation=0d;
    double rmsDeviation=0d;

    public DiaDatum() {}

    public DiaDatum(JSONObject json) {
        try {
            if (json.has("dia")) dia = json.getDouble("dia");
            if (json.has("meanDeviation")) meanDeviation = json.getDouble("meanDeviation");
            if (json.has("SMRDeviation")) smrDeviation = json.getDouble("SMRDeviation");
            if (json.has("RMSDeviation")) rmsDeviation = json.getDouble("RMSDeviation");
        } catch (JSONException e) {}
    }

    public JSONObject toJSON() {
        JSONObject crjson = new JSONObject();
        try {
            crjson.put("dia", dia);
            crjson.put("meanDeviation", (int) meanDeviation);
            crjson.put("SMRDeviation", smrDeviation);
            crjson.put("RMSDeviation", (int) rmsDeviation);
        } catch (JSONException e) {}
        return crjson;
    }

    public Boolean equals(DiaDatum obj) {
        Boolean isEqual = true;
        if (dia != obj.dia) isEqual = false;
        if (meanDeviation != obj.meanDeviation) isEqual = false;
        if (smrDeviation != obj.smrDeviation) isEqual = false;
        if (rmsDeviation != obj.rmsDeviation) isEqual = false;
        return isEqual;
    }
}

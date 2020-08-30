package info.nightscout.androidaps.plugins.general.autotune.data;

import org.json.JSONException;
import org.json.JSONObject;

import info.nightscout.androidaps.utils.DateUtil;

/**
 * Created by Rumen Georgiev on 2/26/2018.
 */

public class CRDatum {
    public double crInitialIOB = 0d;
    public double crInitialBG = 0d;
    public long crInitialCarbTime = 0l;
    public double crEndIOB = 0d;
    public double crEndBG = 0d;
    public long crEndTime = 0l;
    public double crCarbs = 0d;
    public double crInsulin = 0d;
    public double crInsulinTotal = 0d;

    public CRDatum() {}

    public CRDatum(JSONObject json) {
        try {
            if (json.has("CRInitialIOB")) crInitialIOB = json.getDouble("CRInitialIOB");
            if (json.has("CRInitialBG")) crInitialBG = json.getDouble("CRInitialBG");
            if (json.has("CRInitialCarbTime")) crInitialCarbTime = DateUtil.fromISODateString(json.getString("CRInitialCarbTime")).getTime();
            if (json.has("CREndIOB")) crEndIOB = json.getDouble("CREndIOB");
            if (json.has("CREndBG")) crEndBG = json.getDouble("CREndBG");
            if (json.has("CREndTime")) crEndTime = DateUtil.fromISODateString(json.getString("CREndTime")).getTime();
            if (json.has("CRCarbs")) crCarbs = json.getDouble("CRCarbs");
            if (json.has("CRInsulin")) crInsulin = json.getDouble("CRInsulin");
        } catch (JSONException e) {}
    }

    public JSONObject toJSON() {
        JSONObject crjson = new JSONObject();
        try {
            crjson.put("CRInitialIOB", crInitialIOB);
            crjson.put("CRInitialBG", (int) crInitialBG);
            crjson.put("CRInitialCarbTime",DateUtil.toISOString(crInitialCarbTime));
            crjson.put("CREndIOB", crEndIOB);
            crjson.put("CREndBG", (int) crEndBG);
            crjson.put("CREndTime", DateUtil.toISOString(crEndTime));
            crjson.put("CRCarbs", (int) crCarbs);
            crjson.put("CRInsulin", crInsulin);
        } catch (JSONException e) {}
        return crjson;
    }

    public Boolean equals(CRDatum obj) {
        Boolean isEqual = true;
        if (crInitialIOB != obj.crInitialIOB) isEqual = false;
        if (crInitialBG != obj.crInitialBG) isEqual = false;
        if (crInitialCarbTime/1000 != obj.crInitialCarbTime/1000) isEqual = false;
        if (crEndIOB != obj.crEndIOB) isEqual = false;
        if (crEndBG != obj.crEndBG) isEqual = false;
        if (crEndTime/1000 != obj.crEndTime/1000) isEqual = false;
        if (crCarbs != obj.crCarbs) isEqual = false;
        if (crInsulin != obj.crInsulin) isEqual = false;
        return isEqual;
    }
}

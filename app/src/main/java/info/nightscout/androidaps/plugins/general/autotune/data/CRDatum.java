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

    public JSONObject toJSON() throws JSONException {
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
}

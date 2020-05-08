package info.nightscout.androidaps.plugins.general.autotune.data;

import org.json.JSONException;
import org.json.JSONObject;

import info.nightscout.androidaps.utils.DateUtil;

/**
 * Created by Rumen Georgiev on 2/26/2018.
 */

public class CRDatum {
    public double CRInitialIOB = 0d;
    public double CRInitialBG = 0d;
    public long CRInitialCarbTime = 0l;
    public double CREndIOB = 0d;
    public double CREndBG = 0d;
    public long CREndTime = 0l;
    public double CRCarbs = 0d;
    public double CRInsulin = 0d;
    public double CRInsulinTotal = 0d;

    public JSONObject toJSON() throws JSONException {
        JSONObject crjson = new JSONObject();
        try {
            crjson.put("CRInitialIOB",CRInitialIOB);
            crjson.put("CRInitialBG", (int) CRInitialBG);
            crjson.put("CRInitialCarbTime",DateUtil.toISOString(CRInitialCarbTime));
            crjson.put("CREndIOB",CREndIOB);
            crjson.put("CREndBG", (int) CREndBG);
            crjson.put("CREndTime", DateUtil.toISOString(CREndTime));
            crjson.put("CRCarbs", (int) CRCarbs);
            crjson.put("CRInsulin",CRInsulin);
        } catch (JSONException e) {}
        return crjson;
    }
}

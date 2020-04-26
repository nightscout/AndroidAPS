package info.nightscout.androidaps.plugins.TuneProfile;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import info.nightscout.androidaps.utils.DateUtil;

public class PrepOutput {
    public List<CRDatum> CRData = new ArrayList<CRDatum>();
    public List<BGDatum> CSFGlucoseData = new ArrayList<BGDatum>();
    public List<BGDatum> ISFGlucoseData = new ArrayList<BGDatum>();
    public List<BGDatum> basalGlucoseData = new ArrayList<BGDatum>();

    // to generate same king of json string than oref0-autotune-prep
    public String toString() { return toString(0); }

    public String toString(int indent) {
        JSONObject json = new JSONObject();

        Date now = new Date(System.currentTimeMillis());
        int utcOffset = (int) ((DateUtil.fromISODateString(DateUtil.toISOString(now,null,null)).getTime()  - DateUtil.fromISODateString(DateUtil.toISOString(now)).getTime()) / (60 * 1000));

        try {
            JSONArray crjson = new JSONArray();
            for (CRDatum crd : CRData ) {
                JSONObject crdjson = new JSONObject();
                crdjson.put("CRInitialIOB",crd.CRInitialIOB);
                crdjson.put("CRInitialBG",crd.CRInitialBG);
                crdjson.put("CRInitialCarbTime", DateUtil.toISOAsUTC(crd.CRInitialCarbTime));
                crdjson.put("CREndIOB",crd.CREndIOB);
                crdjson.put("CREndBG",crd.CREndBG);
                crdjson.put("CREndTime",DateUtil.toISOAsUTC(crd.CREndTime));
                crdjson.put("CRCarbs",crd.CRCarbs);
                crdjson.put("CRInsulin",crd.CRInsulin);
                crjson.put(crdjson);
            }

            JSONArray csfjson = new JSONArray();
            for (BGDatum bgd:CSFGlucoseData) {
                JSONObject bgdjson = new JSONObject();
                bgdjson.put("_id",bgd._id);
                bgdjson.put("date", bgd.date);
                bgdjson.put("dateString", DateUtil.toISOAsUTC(bgd.date));
                bgdjson.put("sgv",bgd.value);
                bgdjson.put("direction",bgd.direction);
                bgdjson.put("type","sgv");
                bgdjson.put("sysTime",DateUtil.toISOAsUTC(bgd.date));
                bgdjson.put("utcOffset",utcOffset);
                bgdjson.put("glucose",bgd.value);
                bgdjson.put("avgDelta",bgd.AvgDelta);
                bgdjson.put("BGI",bgd.BGI);
                bgdjson.put("deviation",bgd.deviation);
                bgdjson.put("mealAbsorption",bgd.mealAbsorption);
                bgdjson.put("mealCarbs",bgd.mealCarbs);
                csfjson.put(bgdjson);
            }

            JSONArray isfjson = new JSONArray();
            for (BGDatum bgd:ISFGlucoseData) {
                JSONObject bgdjson = new JSONObject();
                bgdjson.put("_id",bgd._id);
                bgdjson.put("date", bgd.date);
                bgdjson.put("dateString", DateUtil.toISOAsUTC(bgd.date));
                bgdjson.put("sgv",bgd.value);
                bgdjson.put("direction",bgd.direction);
                bgdjson.put("type","sgv");
                bgdjson.put("sysTime",DateUtil.toISOAsUTC(bgd.date));
                bgdjson.put("utcOffset",utcOffset);
                bgdjson.put("glucose",bgd.value);
                bgdjson.put("avgDelta",bgd.AvgDelta);
                bgdjson.put("BGI",bgd.BGI);
                bgdjson.put("deviation",bgd.deviation);
                isfjson.put(bgdjson);
            }

            JSONArray basaljson = new JSONArray();
            for (BGDatum bgd:basalGlucoseData) {
                JSONObject bgdjson = new JSONObject();
                bgdjson.put("_id",bgd._id);
                bgdjson.put("date", bgd.date);
                bgdjson.put("dateString", DateUtil.toISOAsUTC(bgd.date));
                bgdjson.put("sgv",bgd.value);
                bgdjson.put("direction",bgd.direction);
                bgdjson.put("type","sgv");
                bgdjson.put("sysTime",DateUtil.toISOAsUTC(bgd.date));
                bgdjson.put("utcOffset",utcOffset);
                bgdjson.put("glucose",bgd.value);
                bgdjson.put("avgDelta",bgd.AvgDelta);
                bgdjson.put("BGI",bgd.BGI);
                bgdjson.put("deviation",bgd.deviation);
                basaljson.put(bgdjson);
            }

            json.put("CRData", crjson);
            json.put("CSFGlucoseData", csfjson);
            json.put("ISFGlucoseData", isfjson);
            json.put("basalGlucoseData", basaljson);

            if (indent != 0)
                return json.toString(indent);
        } catch (JSONException e) {}

        return json.toString();
    }


}

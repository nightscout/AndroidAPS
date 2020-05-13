package info.nightscout.androidaps.plugins.general.autotune.data;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import info.nightscout.androidaps.utils.DateUtil;

public class PreppedGlucose {
    public List<CRDatum> crData = new ArrayList<CRDatum>();
    public List<BGDatum> csfGlucoseData = new ArrayList<BGDatum>();
    public List<BGDatum> isfGlucoseData = new ArrayList<BGDatum>();
    public List<BGDatum> basalGlucoseData = new ArrayList<BGDatum>();

    // to generate same king of json string than oref0-autotune-prep
    public String toString() { return toString(0); }

    public PreppedGlucose(List<CRDatum> crData, List<BGDatum> csfGlucoseData, List<BGDatum> isfGlucoseData, List<BGDatum> basalGlucoseData) {
        this.crData = crData;
        this.csfGlucoseData =csfGlucoseData;
        this.isfGlucoseData =isfGlucoseData;
        basalGlucoseData=basalGlucoseData;
    }


    public String toString(int indent) {
        JSONObject json = new JSONObject();

        Date now = new Date(System.currentTimeMillis());
        int utcOffset = (int) ((DateUtil.fromISODateString(DateUtil.toISOString(now,null,null)).getTime()  - DateUtil.fromISODateString(DateUtil.toISOString(now)).getTime()) / (60 * 1000));

        try {
            JSONArray crjson = new JSONArray();
            for (CRDatum crd : crData) {
                JSONObject crdjson = new JSONObject();
                crdjson.put("CRInitialIOB",crd.crInitialIOB);
                crdjson.put("CRInitialBG",crd.crInitialBG);
                crdjson.put("CRInitialCarbTime", DateUtil.toISOAsUTC(crd.crInitialCarbTime));
                crdjson.put("CREndIOB",crd.crEndIOB);
                crdjson.put("CREndBG",crd.crEndBG);
                crdjson.put("CREndTime",DateUtil.toISOAsUTC(crd.crEndTime));
                crdjson.put("CRCarbs",crd.crCarbs);
                crdjson.put("CRInsulin",crd.crInsulin);
                crjson.put(crdjson);
            }

            JSONArray csfjson = new JSONArray();
            for (BGDatum bgd: csfGlucoseData) {
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
            for (BGDatum bgd: isfGlucoseData) {
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

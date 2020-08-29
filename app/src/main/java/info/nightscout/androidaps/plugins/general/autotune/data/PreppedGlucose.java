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
    public List<DiaDatum> diaDeviations = new ArrayList<DiaDatum>();
    public List<PeakDatum> peakDeviations = new ArrayList<PeakDatum>();
    public long from;

    // to generate same king of json string than oref0-autotune-prep
    public String toString() { return toString(0); }

    public PreppedGlucose(long from, List<CRDatum> crData, List<BGDatum> csfGlucoseData, List<BGDatum> isfGlucoseData, List<BGDatum> basalGlucoseData) {
        this.from = from;
        this.crData = crData;
        this.csfGlucoseData =csfGlucoseData;
        this.isfGlucoseData =isfGlucoseData;
        this.basalGlucoseData=basalGlucoseData;
    }

    public PreppedGlucose(JSONObject json) {
        if (json == null) return;
        crData = null;
        csfGlucoseData = null;
        isfGlucoseData = null;
        basalGlucoseData = null;

        try {
            crData = JsonCRDataToList(json.getJSONArray("CRData"));
            csfGlucoseData = JsonGlucoseDataToList(json.getJSONArray("CSFGlucoseData"));
            isfGlucoseData = JsonGlucoseDataToList(json.getJSONArray("ISFGlucoseData"));
            basalGlucoseData = JsonGlucoseDataToList(json.getJSONArray("basalGlucoseData"));
        } catch (JSONException e) {
        }
    }

    private List<BGDatum> JsonGlucoseDataToList(JSONArray array) {
        List<BGDatum> bgData = new ArrayList<BGDatum>();
        for (int index = 0; index < array.length(); index++) {
            try {
                final JSONObject o = array.getJSONObject(index);
                bgData.add(new BGDatum(o));
            } catch (Exception e) {
            }
        }
        return bgData;
    }

    private List<CRDatum> JsonCRDataToList(JSONArray array) {
        List<CRDatum> crData = new ArrayList<CRDatum>();
        for (int index = 0; index < array.length(); index++) {
            try {
                final JSONObject o = array.getJSONObject(index);
                crData.add(new CRDatum(o));
            } catch (Exception e) {
            }
        }
        return crData;
    }

    public String toString(int indent) {
        String jsonString = "";
        JSONObject json = new JSONObject();

        try {
            JSONArray crjson = new JSONArray();
            for (CRDatum crd : crData) {
                crjson.put(crd.toJSON());
            }
            JSONArray csfjson = new JSONArray();
            for (BGDatum bgd: csfGlucoseData) {
                csfjson.put(bgd.toJSON(true));
            }
            JSONArray isfjson = new JSONArray();
            for (BGDatum bgd: isfGlucoseData) {
                isfjson.put(bgd.toJSON(false));
            }
            JSONArray basaljson = new JSONArray();
            for (BGDatum bgd:basalGlucoseData) {
                basaljson.put(bgd.toJSON(false));
            }

            json.put("CRData", crjson);
            json.put("CSFGlucoseData", csfjson);
            json.put("ISFGlucoseData", isfjson);
            json.put("basalGlucoseData", basaljson);

            if (indent != 0)
                jsonString = json.toString(indent);
            else
                jsonString = json.toString();

        } catch (JSONException e) {}

        return jsonString;
    }


}

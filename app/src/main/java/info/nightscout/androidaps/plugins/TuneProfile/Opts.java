package info.nightscout.androidaps.plugins.TuneProfile;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Date;

import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.db.BgReading;
import info.nightscout.androidaps.db.CareportalEvent;
import info.nightscout.androidaps.db.ExtendedBolus;
import info.nightscout.androidaps.db.TemporaryBasal;
import info.nightscout.androidaps.plugins.treatments.Treatment;
import info.nightscout.androidaps.utils.DateUtil;

public class Opts {
    public static List<Treatment> treatments;
    public static Profile profile;
    public static Profile pumpprofile;
    public List<BgReading> glucose;
    public List<CareportalEvent> pumpHistory;
    public List<ExtendedBolus> pumpExtBolusHistory;
    public List<TemporaryBasal> pumpTempBasalHistory;
    public long start;
    public long end;
    public boolean categorize_uam_as_basal;
    public boolean tune_insulin_curve;

    // on each loop glucose containts only one day BG Value
    public JSONArray glucosetoJSON()  {
        JSONArray glucoseJson = new JSONArray();
        Date now = new Date(System.currentTimeMillis());
        int utcOffset = (int) ((DateUtil.fromISODateString(DateUtil.toISOString(now,null,null)).getTime()  - DateUtil.fromISODateString(DateUtil.toISOString(now)).getTime()) / (60 * 1000));

        try {
            for (BgReading bgreading:glucose ) {
                JSONObject bgjson = new JSONObject();
                bgjson.put("_id",bgreading._id);
                bgjson.put("date",bgreading.date);
                bgjson.put("dateString", DateUtil.toISOAsUTC(bgreading.date));
                bgjson.put("sgv",bgreading.value);
                bgjson.put("direction",bgreading.direction);
                bgjson.put("type","sgv");
                bgjson.put("systime", DateUtil.toISOAsUTC(bgreading.date));
                bgjson.put("utcOffset", utcOffset);
                glucoseJson.put(bgjson);
            }
        } catch (JSONException e) {}
        return glucoseJson;
    }

    //For treatment export, add starttime and endtime to export dedicated files for each loop
    public JSONArray pumpHistorytoJSON(long starttime, long endtime)  {
        JSONArray json = new JSONArray();
        try {
            for (CareportalEvent cp:pumpHistory ) {
                JSONObject cPjson = new JSONObject();

                if(cp.date >= starttime && cp.date <= endtime && cp.isValid) {
                    cPjson.put("_id", cp._id);
                    cPjson.put("eventType",cp.eventType);
                    cPjson.put("date",cp.date);
                    cPjson.put("dateString",DateUtil.toISOAsUTC(cp.date));
                    JSONObject object = new JSONObject(cp.json);
                    Iterator it = object.keys();
                    while (it.hasNext()) {
                        String key = (String)it.next();
                        cPjson.put(key, object.get(key));
                    }
                }
                json.put(cPjson);
            }
        } catch (JSONException e) {}

        return json;
    }

    //For treatment export, add starttime and endtime to export dedicated files for each loop
    public JSONArray extBolustoJSON(long starttime, long endtime)  {
        JSONArray json = new JSONArray();
        try {
            for (ExtendedBolus cp:pumpExtBolusHistory ) {
                JSONObject cPjson = new JSONObject();

                if(cp.date >= starttime && cp.date <= endtime && cp.isValid) {
                    cPjson.put("_id", cp._id);
                    cPjson.put("eventType","Extended Bolus");
                    cPjson.put("date",cp.date);
                    cPjson.put("dateString",DateUtil.toISOAsUTC(cp.date));
                    cPjson.put("insulin",cp.insulin);
                    cPjson.put("totalDuration",cp.durationInMinutes);
                    cPjson.put("realDuration",cp.getRealDuration());
                }
                json.put(cPjson);
            }
        } catch (JSONException e) {}

        return json;
    }
}

package info.nightscout.androidaps.plugins.TuneProfile;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Date;

import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.db.BgReading;
import info.nightscout.androidaps.plugins.treatments.Treatment;
import info.nightscout.androidaps.utils.DateUtil;

public class Opts {
    public static List<Treatment> treatments;
    public static Profile profile;
    public static Profile pumpprofile;
    public List<BgReading> glucose;
    public List<Treatment> pumpHistory;
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
                bgjson.put("dateString", DateUtil.toISOString(bgreading.date));
                bgjson.put("sgv",bgreading.value);
                bgjson.put("direction",bgreading.direction);
                bgjson.put("type","sgv");
                bgjson.put("systime", DateUtil.toISOString(bgreading.date));
                bgjson.put("utcOffset", utcOffset);
                glucoseJson.put(bgjson);
            }
        } catch (JSONException e) {}
        return glucoseJson;
    }

    //For treatment export, add starttime and endtime to export dedicated files for each loop
    public JSONArray treatmentstoJSON(List<Treatment> treatments, long starttime, long endtime)  {
        JSONArray treatmentsJson = new JSONArray();
        try {
            for (Treatment treatment:treatments ) {
                JSONObject treatmentJson = treatment.createJson();
                if(treatmentJson!=null && treatment.date >= starttime && treatment.date <= endtime)
                    treatmentsJson.put(treatmentJson);
            }
        } catch (JSONException e) {}
        return treatmentsJson;
    }

}

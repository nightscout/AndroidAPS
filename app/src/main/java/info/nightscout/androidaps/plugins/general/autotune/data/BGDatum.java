package info.nightscout.androidaps.plugins.general.autotune.data;

import org.json.JSONException;
import org.json.JSONObject;


import info.nightscout.androidaps.db.BgReading;
import info.nightscout.androidaps.utils.DateUtil;

/**
 * Created by Rumen Georgiev on 2/24/2018.
 */

public class BGDatum extends BgReading {
    //Added by Rumen for autotune
    public double deviation = 0d;
    public double BGI = 0d;
    public String mealAbsorption = "";
    public  int mealCarbs = 0;
    public String uamAbsorption = "";
    public long CRInitialCarbTime;
    public long CREndTime;
    public double CRInsulin;
    public double AvgDelta;
    private BgReading bgreading;
    public BGDatum() {
    }

    public BGDatum(BgReading bgReading) {
        // Used like from NS sgv
        date = bgReading.date;
        value = bgReading.value;
        raw = bgReading.raw;
        direction = bgReading.direction;
        _id = bgReading._id;
        bgreading=bgReading;
    }

    public BgReading getBgReading() {return bgreading; }

    public JSONObject toJSON() throws JSONException {
        JSONObject bgjson = new JSONObject();
        try {
            bgjson.put("_id",_id);
            bgjson.put("date",date);
            bgjson.put("dateString", DateUtil.toISOString(date));
            bgjson.put("sgv",value);
            bgjson.put("direction",direction);
            bgjson.put("type","sgv");
            bgjson.put("systime", DateUtil.toISOString(date));
            bgjson.put("utcOffset", (int) DateUtil.getTimeZoneOffsetMs()/60/1000L);
        } catch (JSONException e) {}
        return bgjson;
    }
}
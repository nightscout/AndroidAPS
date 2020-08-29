package info.nightscout.androidaps.plugins.general.autotune.data;

import org.json.JSONException;
import org.json.JSONObject;


import java.util.Date;

import info.nightscout.androidaps.db.BgReading;
import info.nightscout.androidaps.utils.DateUtil;

/**
 * Created by Rumen Georgiev on 2/24/2018.
 */

public class BGDatum extends BgReading {
    //Added by Rumen for autotune
    public String _id = "";
    public double deviation = 0d;
    public double BGI = 0d;
    public String mealAbsorption = "";
    public  int mealCarbs = 0;
    public String uamAbsorption = "";
    public double AvgDelta;
    private BgReading bgreading;
    public BGDatum() {
    }

    public BGDatum(JSONObject json) {
        try {
            if (json.has("_id")) _id = json.getString("_id");
            if (json.has("date")) date = DateUtil.fromISODateString(json.getString("date")).getTime();
            if (json.has("sgv")) value = json.getDouble("sgv");
            if (json.has("direction")) direction = json.getString("direction");
            if (json.has("deviation")) deviation = json.getDouble("deviation");
            if (json.has("BGI")) BGI = json.getDouble("BGI");
            if (json.has("avgDelta")) AvgDelta = json.getDouble("avgDelta");
            if (json.has("mealAbsorption")) mealAbsorption = json.getString("mealAbsorption");
            if (json.has("mealCarbs")) mealCarbs = json.getInt("mealCarbs");
        } catch (JSONException e) {}
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

    public JSONObject toJSON(Boolean mealData) {
        JSONObject bgjson = new JSONObject();
        Date now = new Date(System.currentTimeMillis());
        int utcOffset = (int) ((DateUtil.fromISODateString(DateUtil.toISOString(now,null,null)).getTime()  - DateUtil.fromISODateString(DateUtil.toISOString(now)).getTime()) / (60 * 1000));
        try {
            bgjson.put("_id",_id);
            bgjson.put("date", date);
            bgjson.put("dateString", DateUtil.toISOAsUTC(date));
            bgjson.put("sgv",value);
            bgjson.put("direction",direction);
            bgjson.put("type","sgv");
            bgjson.put("sysTime",DateUtil.toISOAsUTC(date));
            bgjson.put("utcOffset",utcOffset);
            bgjson.put("glucose",value);
            bgjson.put("avgDelta", AvgDelta);
            bgjson.put("BGI", BGI);
            bgjson.put("deviation",deviation);
            if (mealData) {
                bgjson.put("mealAbsorption", mealAbsorption);
                bgjson.put("mealCarbs", mealCarbs);
            }

        } catch (JSONException e) {}
        return bgjson;
    }
}
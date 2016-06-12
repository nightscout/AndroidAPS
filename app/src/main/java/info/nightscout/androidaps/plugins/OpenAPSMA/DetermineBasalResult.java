package info.nightscout.androidaps.plugins.OpenAPSMA;

import com.eclipsesource.v8.V8Object;

import org.json.JSONObject;

import info.nightscout.androidaps.plugins.APSResult;

public class DetermineBasalResult extends APSResult {

    public JSONObject json = new JSONObject();
    public final double eventualBG;
    public final double snoozeBG;
    public final String mealAssist;

    public DetermineBasalResult(V8Object result, JSONObject j) {
        json = j;
        reason = result.getString("reason");
        eventualBG = result.getDouble("eventualBG");
        snoozeBG = result.getDouble("snoozeBG");
        if(result.contains("rate")) {
            rate = result.getDouble("rate");
            if (rate < 0d) rate = 0d;
            changeRequested = true;
        } else {
            rate = -1;
            changeRequested = false;
        }
        if(result.contains("duration")) {
            duration = result.getInteger("duration");
            changeRequested = changeRequested & true;
        } else {
            duration = -1;
            changeRequested = false;
        }
        if(result.contains("mealAssist")) {
            mealAssist = result.getString("mealAssist");
        } else mealAssist = "";

        result.release();
    }
}

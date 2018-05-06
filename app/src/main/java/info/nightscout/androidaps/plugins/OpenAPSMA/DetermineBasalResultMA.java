package info.nightscout.androidaps.plugins.OpenAPSMA;

import org.json.JSONException;
import org.json.JSONObject;
import org.mozilla.javascript.NativeObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.plugins.Loop.APSResult;

public class DetermineBasalResultMA extends APSResult {
    private static Logger log = LoggerFactory.getLogger(DetermineBasalResultMA.class);

    public JSONObject json = new JSONObject();
    public double eventualBG;
    public double snoozeBG;
    public String mealAssist;

    public DetermineBasalResultMA(NativeObject result, JSONObject j) {
        json = j;
        if (result.containsKey("error")) {
            reason = (String) result.get("error");
            tempBasalRequested = false;
            rate = -1;
            duration = -1;
            mealAssist = "";
        } else {
            reason = result.get("reason").toString();
            eventualBG = (Double) result.get("eventualBG");
            snoozeBG = (Double) result.get("snoozeBG");
            if (result.containsKey("rate")) {
                rate = (Double) result.get("rate");
                if (rate < 0d) rate = 0d;
                tempBasalRequested = true;
            } else {
                rate = -1;
                tempBasalRequested = false;
            }
            if (result.containsKey("duration")) {
                duration = ((Double) result.get("duration")).intValue();
                //changeRequested as above
            } else {
                duration = -1;
                tempBasalRequested = false;
            }
            if (result.containsKey("mealAssist")) {
                mealAssist = result.get("mealAssist").toString();
            } else mealAssist = "";
        }
    }

    public DetermineBasalResultMA() {
    }

    @Override
    public DetermineBasalResultMA clone() {
        DetermineBasalResultMA newResult = new DetermineBasalResultMA();
        newResult.reason = new String(reason);
        newResult.rate = rate;
        newResult.duration = duration;
        newResult.tempBasalRequested = isChangeRequested();
        newResult.rate = rate;
        newResult.duration = duration;
        newResult.tempBasalRequested = isChangeRequested();

        try {
            newResult.json = new JSONObject(json.toString());
        } catch (JSONException e) {
            log.error("Unhandled exception", e);
        }
        newResult.eventualBG = eventualBG;
        newResult.snoozeBG = snoozeBG;
        newResult.mealAssist = mealAssist;
        return newResult;
    }

    @Override
    public JSONObject json() {
        try {
            JSONObject ret = new JSONObject(this.json.toString());
            return ret;
        } catch (JSONException e) {
            log.error("Unhandled exception", e);
        }
        return null;
    }

}

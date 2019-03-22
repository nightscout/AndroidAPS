package info.nightscout.androidaps.plugins.aps.openAPSMA;

import org.json.JSONException;
import org.json.JSONObject;
import org.mozilla.javascript.NativeObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.aps.loop.APSResult;

public class DetermineBasalResultMA extends APSResult {
    private static Logger log = LoggerFactory.getLogger(L.APS);

    private double eventualBG;
    private double snoozeBG;
    private String mealAssist;

    DetermineBasalResultMA(NativeObject result, JSONObject j) {
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

    private DetermineBasalResultMA() {
    }

    @Override
    public DetermineBasalResultMA clone() {
        DetermineBasalResultMA newResult = new DetermineBasalResultMA();
        doClone(newResult);

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

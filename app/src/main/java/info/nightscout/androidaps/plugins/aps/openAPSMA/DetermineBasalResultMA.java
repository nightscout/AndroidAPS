package info.nightscout.androidaps.plugins.aps.openAPSMA;

import org.json.JSONException;
import org.json.JSONObject;
import org.mozilla.javascript.NativeObject;

import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.aps.loop.APSResult;

public class DetermineBasalResultMA extends APSResult {
    private AAPSLogger aapsLogger;

    private double eventualBG;
    private double snoozeBG;
    private String mealAssist;

    DetermineBasalResultMA(NativeObject result, JSONObject j, AAPSLogger aapsLogger) {
        this(aapsLogger);
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

    private DetermineBasalResultMA(AAPSLogger aapsLogger) {
        this.aapsLogger = aapsLogger;
    }

    @Override
    public DetermineBasalResultMA clone() {
        DetermineBasalResultMA newResult = new DetermineBasalResultMA(aapsLogger);
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
            aapsLogger.error(LTag.APS, "Unhandled exception", e);
        }
        return null;
    }

}

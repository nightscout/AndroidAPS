package info.nightscout.androidaps.plugins.aps.openAPSAMA;

import org.json.JSONException;
import org.json.JSONObject;
import org.mozilla.javascript.NativeObject;

import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.aps.loop.APSResult;
import info.nightscout.androidaps.utils.DateUtil;

public class DetermineBasalResultAMA extends APSResult {
    private AAPSLogger aapsLogger;

    private double eventualBG;
    private double snoozeBG;

    DetermineBasalResultAMA(NativeObject result, JSONObject j, AAPSLogger aapsLogger) {
        this(aapsLogger);
        date = DateUtil.now();
        json = j;
        if (result.containsKey("error")) {
            reason = result.get("error").toString();
            tempBasalRequested = false;
            rate = -1;
            duration = -1;
        } else {
            reason = result.get("reason").toString();
            if (result.containsKey("eventualBG")) eventualBG = (Double) result.get("eventualBG");
            if (result.containsKey("snoozeBG")) snoozeBG = (Double) result.get("snoozeBG");
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
        }
        bolusRequested = false;
    }

    private DetermineBasalResultAMA(AAPSLogger aapsLogger) {
        hasPredictions = true;
        this.aapsLogger = aapsLogger;
    }

    @Override
    public DetermineBasalResultAMA clone() {
        DetermineBasalResultAMA newResult = new DetermineBasalResultAMA(aapsLogger);
        doClone(newResult);

        newResult.eventualBG = eventualBG;
        newResult.snoozeBG = snoozeBG;
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

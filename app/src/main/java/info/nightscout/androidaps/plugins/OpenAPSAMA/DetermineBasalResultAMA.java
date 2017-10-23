package info.nightscout.androidaps.plugins.OpenAPSAMA;

import com.eclipsesource.v8.V8Object;

import org.json.JSONException;
import org.json.JSONObject;
import org.mozilla.javascript.NativeObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

import info.nightscout.androidaps.plugins.Loop.APSResult;

public class DetermineBasalResultAMA extends APSResult {
    private static Logger log = LoggerFactory.getLogger(DetermineBasalResultAMA.class);

    public double eventualBG;
    public double snoozeBG;

    public DetermineBasalResultAMA(NativeObject result, JSONObject j) {
        this();
        date = new Date();
        json = j;
        if (result.containsKey("error")) {
            reason = result.get("error").toString();
            changeRequested = false;
            rate = -1;
            duration = -1;
        } else {
            reason = result.get("reason").toString();
            if (result.containsKey("eventualBG")) eventualBG = (Double) result.get("eventualBG");
            if (result.containsKey("snoozeBG")) snoozeBG = (Double) result.get("snoozeBG");
            if (result.containsKey("rate")) {
                rate =  (Double) result.get("rate");
                if (rate < 0d) rate = 0d;
                changeRequested = true;
            } else {
                rate = -1;
                changeRequested = false;
            }
            if (result.containsKey("duration")) {
                duration = ((Double)result.get("duration")).intValue();
                //changeRequested as above
            } else {
                duration = -1;
                changeRequested = false;
            }
        }
    }

    public DetermineBasalResultAMA() {
        hasPredictions = true;
    }

    @Override
    public DetermineBasalResultAMA clone() {
        DetermineBasalResultAMA newResult = new DetermineBasalResultAMA();
        newResult.reason = new String(reason);
        newResult.rate = rate;
        newResult.duration = duration;
        newResult.changeRequested = changeRequested;
        newResult.rate = rate;
        newResult.duration = duration;
        newResult.changeRequested = changeRequested;

        try {
            newResult.json = new JSONObject(json.toString());
        } catch (JSONException e) {
            log.error("Unhandled exception", e);
        }
        newResult.eventualBG = eventualBG;
        newResult.snoozeBG = snoozeBG;
        newResult.date = date;
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

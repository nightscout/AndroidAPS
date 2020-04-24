package info.nightscout.androidaps.plugins.aps.openAPSAMA;

import org.json.JSONException;
import org.json.JSONObject;
import org.mozilla.javascript.NativeObject;

import dagger.android.HasAndroidInjector;
import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.aps.loop.APSResult;
import info.nightscout.androidaps.utils.DateUtil;

public class DetermineBasalResultAMA extends APSResult {
    private AAPSLogger aapsLogger;

    private double eventualBG;
    private double snoozeBG;

    DetermineBasalResultAMA(HasAndroidInjector injector, NativeObject result, JSONObject j) {
        this(injector);
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

    private DetermineBasalResultAMA(HasAndroidInjector injector) {
        super(injector);
        hasPredictions = true;
    }

    @Override
    public DetermineBasalResultAMA newAndClone(HasAndroidInjector injector) {
        DetermineBasalResultAMA newResult = new DetermineBasalResultAMA(injector);
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

package info.nightscout.androidaps.plugins.aps.openAPSSMB;

import org.json.JSONException;
import org.json.JSONObject;

import javax.inject.Inject;

import dagger.android.HasAndroidInjector;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.plugins.aps.loop.APSResult;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.sharedPreferences.SP;

public class DetermineBasalResultSMB extends APSResult {
    @Inject SP sp;

    private double eventualBG;
    private double snoozeBG;

    private DetermineBasalResultSMB(HasAndroidInjector injector) {
        super(injector);
        hasPredictions = true;
    }

    DetermineBasalResultSMB(HasAndroidInjector injector, JSONObject result) {
        this(injector);
        date = DateUtil.now();
        json = result;
        try {
            if (result.has("error")) {
                reason = result.getString("error");
                return;
            }

            reason = result.getString("reason");
            if (result.has("eventualBG")) eventualBG = result.getDouble("eventualBG");
            if (result.has("snoozeBG")) snoozeBG = result.getDouble("snoozeBG");
            //if (result.has("insulinReq")) insulinReq = result.getDouble("insulinReq");

            if (result.has("carbsReq")) carbsReq = result.getInt("carbsReq");
            if (result.has("carbsReqWithin")) carbsReqWithin = result.getInt("carbsReqWithin");
            

            if (result.has("rate") && result.has("duration")) {
                tempBasalRequested = true;
                rate = result.getDouble("rate");
                if (rate < 0d) rate = 0d;
                duration = result.getInt("duration");
            } else {
                rate = -1;
                duration = -1;
            }

            if (result.has("units")) {
                bolusRequested = true;
                smb = result.getDouble("units");
            } else {
                smb = 0d;
            }
            if (result.has("targetBG")) {
                targetBG = result.getDouble("targetBG");
            }

            if (result.has("deliverAt")) {
                String date = result.getString("deliverAt");
                try {
                    deliverAt = DateUtil.fromISODateString(date).getTime();
                } catch (Exception e) {
                    aapsLogger.error(LTag.APS, "Error parsing 'deliverAt' date: " + date, e);
                }
            }
        } catch (JSONException e) {
            aapsLogger.error(LTag.APS, "Error parsing determine-basal result JSON", e);
        }
    }

    @Override
    public DetermineBasalResultSMB newAndClone(HasAndroidInjector injector) {
        DetermineBasalResultSMB newResult = new DetermineBasalResultSMB(injector);
        doClone(newResult);

        newResult.eventualBG = eventualBG;
        newResult.snoozeBG = snoozeBG;
        return newResult;
    }

    @Override
    public JSONObject json() {
        try {
            return new JSONObject(this.json.toString());
        } catch (JSONException e) {
            aapsLogger.error(LTag.APS, "Error converting determine-basal result to JSON", e);
        }
        return null;
    }
}

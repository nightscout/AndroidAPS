package info.nightscout.androidaps.plugins.aps.openAPSSMB;

import org.json.JSONException;
import org.json.JSONObject;

import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.aps.loop.APSResult;
import info.nightscout.androidaps.utils.DateUtil;

public class DetermineBasalResultSMB extends APSResult {
    private final AAPSLogger aapsLogger;

    private double eventualBG;
    private double snoozeBG;

    DetermineBasalResultSMB(JSONObject result, AAPSLogger aapsLogger) {
        this(aapsLogger);
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
            //if (result.has("carbsReq")) carbsReq = result.getDouble("carbsReq");

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

    private DetermineBasalResultSMB(AAPSLogger aapsLogger) {
        hasPredictions = true;
        this.aapsLogger = aapsLogger;
    }

    @Override
    public DetermineBasalResultSMB clone() {
        DetermineBasalResultSMB newResult = new DetermineBasalResultSMB(aapsLogger);
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

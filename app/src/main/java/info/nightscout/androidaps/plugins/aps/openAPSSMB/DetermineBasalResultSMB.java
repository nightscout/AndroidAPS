package info.nightscout.androidaps.plugins.aps.openAPSSMB;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.aps.loop.APSResult;
import info.nightscout.androidaps.utils.DateUtil;

public class DetermineBasalResultSMB extends APSResult {
    private static final Logger log = LoggerFactory.getLogger(L.APS);

    private double eventualBG;
    private double snoozeBG;

    DetermineBasalResultSMB(JSONObject result) {
        this();
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
                    log.warn("Error parsing 'deliverAt' date: " + date, e);
                }
            }
        } catch (JSONException e) {
            log.error("Error parsing determine-basal result JSON", e);
        }
    }

    private DetermineBasalResultSMB() {
        hasPredictions = true;
    }

    @Override
    public DetermineBasalResultSMB clone() {
        DetermineBasalResultSMB newResult = new DetermineBasalResultSMB();
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
            log.error("Error converting determine-basal result to JSON", e);
        }
        return null;
    }
}

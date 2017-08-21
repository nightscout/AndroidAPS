package info.nightscout.androidaps.plugins.OpenAPSSMB;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Date;

import info.nightscout.androidaps.plugins.Loop.APSResult;
import info.nightscout.utils.DateUtil;

public class DetermineBasalResultSMB extends APSResult {
    public double eventualBG;
    public double snoozeBG;

    public DetermineBasalResultSMB(JSONObject result) {
        this();
        date = new Date();
        json = result;
        try {
            if (result.has("error")) {
                reason = result.getString("error");
                changeRequested = false;
                rate = -1;
                duration = -1;
            } else {
                reason = result.getString("reason");
                if (result.has("eventualBG")) eventualBG = result.getDouble("eventualBG");
                if (result.has("snoozeBG")) snoozeBG = result.getDouble("snoozeBG");
                if (result.has("rate")) {
                    rate = result.getDouble("rate");
                    if (rate < 0d) rate = 0d;
                    changeRequested = true;
                } else {
                    rate = -1;
                    changeRequested = false;
                }
                if (result.has("duration")) {
                    duration = result.getInt("duration");
                    //changeRequested as above
                } else {
                    duration = -1;
                    changeRequested = false;
                }
                if (result.has("units")) {
                    changeRequested = true;
                    smb = result.getDouble("units");
                } else {
                    smb = 0d;
                    //changeRequested as above
                }
                if (result.has("deliverAt")) {
                    String date = result.getString("deliverAt");
                    try {
                        deliverAt = DateUtil.fromISODateString(date).getTime();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public DetermineBasalResultSMB() {
        hasPredictions = true;
    }

    @Override
    public DetermineBasalResultSMB clone() {
        DetermineBasalResultSMB newResult = new DetermineBasalResultSMB();
        newResult.reason = new String(reason);
        newResult.rate = rate;
        newResult.duration = duration;
        newResult.changeRequested = changeRequested;
        newResult.rate = rate;
        newResult.duration = duration;
        newResult.smb = smb;
        newResult.deliverAt = deliverAt;

        try {
            newResult.json = new JSONObject(json.toString());
        } catch (JSONException e) {
            e.printStackTrace();
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
            e.printStackTrace();
        }
        return null;
    }
}

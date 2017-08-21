package info.nightscout.androidaps.plugins.OpenAPSSMB;

import com.eclipsesource.v8.V8Object;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import info.nightscout.androidaps.data.IobTotal;
import info.nightscout.androidaps.db.BgReading;
import info.nightscout.androidaps.plugins.Loop.APSResult;
import info.nightscout.utils.DateUtil;

public class DetermineBasalResultSMB extends APSResult {
    public Date date;
    public JSONObject json = new JSONObject();
    public double eventualBG;
    public double snoozeBG;

    public DetermineBasalResultSMB(JSONObject result) {
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

    public List<BgReading> getPredictions() {
        List<BgReading> array = new ArrayList<>();
        try {
            long startTime = date.getTime();
            if (json.has("predBGs")) {
                JSONObject predBGs = json.getJSONObject("predBGs");
                if (predBGs.has("IOB")) {
                    JSONArray iob = predBGs.getJSONArray("IOB");
                    for (int i = 1; i < iob.length(); i++) {
                        BgReading bg = new BgReading();
                        bg.value = iob.getInt(i);
                        bg.date = startTime + i * 5 * 60 * 1000L;
                        bg.isPrediction = true;
                        array.add(bg);
                    }
                }
                if (predBGs.has("aCOB")) {
                    JSONArray iob = predBGs.getJSONArray("aCOB");
                    for (int i = 1; i < iob.length(); i++) {
                        BgReading bg = new BgReading();
                        bg.value = iob.getInt(i);
                        bg.date = startTime + i * 5 * 60 * 1000L;
                        bg.isPrediction = true;
                        array.add(bg);
                    }
                }
                if (predBGs.has("COB")) {
                    JSONArray iob = predBGs.getJSONArray("COB");
                    for (int i = 1; i < iob.length(); i++) {
                        BgReading bg = new BgReading();
                        bg.value = iob.getInt(i);
                        bg.date = startTime + i * 5 * 60 * 1000L;
                        bg.isPrediction = true;
                        array.add(bg);
                    }
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return array;
    }

    public long getLatestPredictionsTime() {
        long latest = 0;
        try {
            long startTime = date.getTime();
            if (json.has("predBGs")) {
                JSONObject predBGs = json.getJSONObject("predBGs");
                if (predBGs.has("IOB")) {
                    JSONArray iob = predBGs.getJSONArray("IOB");
                    latest = Math.max(latest, startTime + (iob.length() - 1) * 5 * 60 * 1000L);
                }
                if (predBGs.has("aCOB")) {
                    JSONArray iob = predBGs.getJSONArray("aCOB");
                    latest = Math.max(latest, startTime + (iob.length() - 1) * 5 * 60 * 1000L);
                }
                if (predBGs.has("COB")) {
                    JSONArray iob = predBGs.getJSONArray("COB");
                    latest = Math.max(latest, startTime + (iob.length() - 1) * 5 * 60 * 1000L);
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return latest;
    }
}

package info.nightscout.androidaps.plugins.OpenAPSAMA;

import com.eclipsesource.v8.V8Object;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.mozilla.javascript.NativeObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import info.nightscout.androidaps.db.BgReading;
import info.nightscout.androidaps.plugins.Loop.APSResult;
import info.nightscout.androidaps.data.IobTotal;

public class DetermineBasalResultAMA extends APSResult {
    private static Logger log = LoggerFactory.getLogger(DetermineBasalResultAMA.class);

    public Date date;
    public JSONObject json = new JSONObject();
    public double eventualBG;
    public double snoozeBG;
    public IobTotal iob;

    public DetermineBasalResultAMA(NativeObject result, JSONObject j) {
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

    public List<BgReading> getPredictions() {
        List<BgReading> array = new ArrayList<>();
        try {
            long startTime = date.getTime();
            if (json.has("predBGs")) {
                JSONObject predBGs = json.getJSONObject("predBGs");
                if (predBGs.has("IOB")) {
                    JSONArray iob = predBGs.getJSONArray("IOB");
                    for (int i = 1; i < iob.length(); i ++) {
                        BgReading bg = new BgReading();
                        bg.value = iob.getInt(i);
                        bg.date = startTime + i * 5 * 60 * 1000L;
                        bg.isPrediction = true;
                        array.add(bg);
                    }
                }
                if (predBGs.has("aCOB")) {
                    JSONArray iob = predBGs.getJSONArray("aCOB");
                    for (int i = 1; i < iob.length(); i ++) {
                        BgReading bg = new BgReading();
                        bg.value = iob.getInt(i);
                        bg.date = startTime + i * 5 * 60 * 1000L;
                        bg.isPrediction = true;
                        array.add(bg);
                    }
                }
                if (predBGs.has("COB")) {
                    JSONArray iob = predBGs.getJSONArray("COB");
                    for (int i = 1; i < iob.length(); i ++) {
                        BgReading bg = new BgReading();
                        bg.value = iob.getInt(i);
                        bg.date = startTime + i * 5 * 60 * 1000L;
                        bg.isPrediction = true;
                        array.add(bg);
                    }
                }
            }
        } catch (JSONException e) {
            log.error("Unhandled exception", e);
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
            log.error("Unhandled exception", e);
        }

        return latest;
    }
}

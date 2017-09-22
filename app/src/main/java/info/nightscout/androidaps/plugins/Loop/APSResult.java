package info.nightscout.androidaps.plugins.Loop;

import android.text.Html;
import android.text.Spanned;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.IobTotal;
import info.nightscout.androidaps.db.BgReading;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.utils.DecimalFormatter;

/**
 * Created by mike on 09.06.2016.
 */
public class APSResult {
    private static Logger log = LoggerFactory.getLogger(APSResult.class);

    public Date date;
    public String reason;
    public double rate;
    public int duration;
    public boolean changeRequested = false;
    public IobTotal iob;
    public JSONObject json = new JSONObject();
    public boolean hasPredictions = false;
    public double smb = 0d; // super micro bolus in units
    public long deliverAt = 0;

    @Override
    public String toString() {
        final ConfigBuilderPlugin configBuilder = MainApp.getConfigBuilder();
        if (changeRequested) {
            if (rate == 0 && duration == 0)
                return MainApp.sResources.getString(R.string.canceltemp);
            else
                return MainApp.sResources.getString(R.string.rate) + ": " + DecimalFormatter.to2Decimal(rate) + " U/h " +
                        "(" + DecimalFormatter.to2Decimal(rate / configBuilder.getBaseBasalRate() * 100) + "%)\n" +
                        MainApp.sResources.getString(R.string.duration) + ": " + DecimalFormatter.to0Decimal(duration) + " min\n" +
                        (smb != 0 ? ("SMB: " + DecimalFormatter.to2Decimal(smb) + " U\n") : "") +
                        MainApp.sResources.getString(R.string.reason) + ": " + reason;
        } else
            return MainApp.sResources.getString(R.string.nochangerequested);
    }

    public Spanned toSpanned() {
        final ConfigBuilderPlugin configBuilder = MainApp.getConfigBuilder();
        if (changeRequested) {
            String ret = "";
            if (rate == 0 && duration == 0) ret = MainApp.sResources.getString(R.string.canceltemp);
            else
                ret = "<b>" + MainApp.sResources.getString(R.string.rate) + "</b>: " + DecimalFormatter.to2Decimal(rate) + " U/h " +
                        "(" + DecimalFormatter.to2Decimal(rate / configBuilder.getBaseBasalRate() * 100) + "%)<br>" +
                        "<b>" + MainApp.sResources.getString(R.string.duration) + "</b>: " + DecimalFormatter.to2Decimal(duration) + " min<br>" +
                        (smb != 0 ? ("<b>" + "SMB" + "</b>: " + DecimalFormatter.to2Decimal(smb) + " U<br>") : "") +
                        "<b>" + MainApp.sResources.getString(R.string.reason) + "</b>: " + reason.replace("<", "&lt;").replace(">", "&gt;");
            return Html.fromHtml(ret);
        } else
            return Html.fromHtml(MainApp.sResources.getString(R.string.nochangerequested));
    }

    public APSResult() {
    }

    public APSResult clone() {
        APSResult newResult = new APSResult();
        newResult.reason = new String(reason);
        newResult.rate = rate;
        newResult.duration = duration;
        newResult.changeRequested = changeRequested;
        newResult.iob = iob;
        return newResult;
    }

    public JSONObject json() {
        JSONObject json = new JSONObject();
        try {
            if (changeRequested) {
                json.put("rate", rate);
                json.put("duration", duration);
                json.put("reason", reason);
            }
        } catch (JSONException e) {
            log.error("Unhandled exception", e);
        }
        return json;
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
                        bg.isIOBPrediction = true;
                        array.add(bg);
                    }
                }
                if (predBGs.has("aCOB")) {
                    JSONArray iob = predBGs.getJSONArray("aCOB");
                    for (int i = 1; i < iob.length(); i++) {
                        BgReading bg = new BgReading();
                        bg.value = iob.getInt(i);
                        bg.date = startTime + i * 5 * 60 * 1000L;
                        bg.isaCOBPrediction = true;
                        array.add(bg);
                    }
                }
                if (predBGs.has("COB")) {
                    JSONArray iob = predBGs.getJSONArray("COB");
                    for (int i = 1; i < iob.length(); i++) {
                        BgReading bg = new BgReading();
                        bg.value = iob.getInt(i);
                        bg.date = startTime + i * 5 * 60 * 1000L;
                        bg.isCOBPrediction = true;
                        array.add(bg);
                    }
                }
                if (predBGs.has("UAM")) {
                    JSONArray iob = predBGs.getJSONArray("UAM");
                    for (int i = 1; i < iob.length(); i++) {
                        BgReading bg = new BgReading();
                        bg.value = iob.getInt(i);
                        bg.date = startTime + i * 5 * 60 * 1000L;
                        bg.isUAMPrediction = true;
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
                if (predBGs.has("UAM")) {
                    JSONArray iob = predBGs.getJSONArray("UAM");
                    latest = Math.max(latest, startTime + (iob.length() - 1) * 5 * 60 * 1000L);
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return latest;
    }

}

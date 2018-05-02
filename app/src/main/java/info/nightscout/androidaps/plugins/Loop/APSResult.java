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
import info.nightscout.androidaps.interfaces.Constraint;
import info.nightscout.androidaps.interfaces.PumpInterface;
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
    public boolean tempBasalRequested = false;
    public boolean bolusRequested = false;
    public IobTotal iob;
    public JSONObject json = new JSONObject();
    public boolean hasPredictions = false;
    public double smb = 0d; // super micro bolus in units
    public long deliverAt = 0;

    public Constraint<Double> inputConstraints;

    public Constraint<Double> rateConstraint;
    public Constraint<Double> smbConstraint;

    @Override
    public String toString() {
        final PumpInterface pump = ConfigBuilderPlugin.getActivePump();
        if (isChangeRequested()) {
            String ret;
            // rate
            if (rate == 0 && duration == 0)
                ret = MainApp.gs(R.string.canceltemp) + "\n";
            else if (rate == -1)
                ret = MainApp.gs(R.string.let_temp_basal_run) + "\n";
            else
                ret = MainApp.gs(R.string.rate) + ": " + DecimalFormatter.to2Decimal(rate) + " U/h " +
                        "(" + DecimalFormatter.to2Decimal(rate / pump.getBaseBasalRate() * 100) + "%) \n" +
                        MainApp.gs(R.string.duration) + ": " + DecimalFormatter.to2Decimal(duration) + " min\n";

            // smb
            if (smb != 0)
                ret += ("SMB: " + DecimalFormatter.toPumpSupportedBolus(smb) + " U\n");

            // reason
            ret += MainApp.gs(R.string.reason) + ": " + reason;
            return ret;
        } else
            return MainApp.gs(R.string.nochangerequested);
    }

    public Spanned toSpanned() {
        final PumpInterface pump = ConfigBuilderPlugin.getActivePump();
        if (isChangeRequested()) {
            String ret;
            // rate
            if (rate == 0 && duration == 0)
                ret = MainApp.gs(R.string.canceltemp) + "<br>";
            else if (rate == -1)
                ret = MainApp.gs(R.string.let_temp_basal_run) + "<br>";
            else
                ret = "<b>" + MainApp.gs(R.string.rate) + "</b>: " + DecimalFormatter.to2Decimal(rate) + " U/h " +
                        "(" + DecimalFormatter.to2Decimal(rate / pump.getBaseBasalRate() * 100) + "%) <br>" +
                        "<b>" + MainApp.gs(R.string.duration) + "</b>: " + DecimalFormatter.to2Decimal(duration) + " min<br>";

            // smb
            if (smb != 0)
                ret += ("<b>" + "SMB" + "</b>: " + DecimalFormatter.toPumpSupportedBolus(smb) + " U<br>");

            // reason
            ret += "<b>" + MainApp.gs(R.string.reason) + "</b>: " + reason.replace("<", "&lt;").replace(">", "&gt;");
            return Html.fromHtml(ret);
        } else
            return Html.fromHtml(MainApp.gs(R.string.nochangerequested));
    }

    public APSResult() {
    }

    public APSResult clone() {
        APSResult newResult = new APSResult();
        newResult.reason = reason;
        newResult.rate = rate;
        newResult.duration = duration;
        newResult.tempBasalRequested = tempBasalRequested;
        newResult.bolusRequested = bolusRequested;
        newResult.iob = iob;
        newResult.json = json;
        newResult.hasPredictions = hasPredictions;
        newResult.smb = smb;
        newResult.deliverAt = deliverAt;
        newResult.rateConstraint = rateConstraint;
        newResult.smbConstraint = smbConstraint;
        return newResult;
    }

    public JSONObject json() {
        JSONObject json = new JSONObject();
        try {
            if (isChangeRequested()) {
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
                if (predBGs.has("ZT")) {
                    JSONArray iob = predBGs.getJSONArray("ZT");
                    for (int i = 1; i < iob.length(); i++) {
                        BgReading bg = new BgReading();
                        bg.value = iob.getInt(i);
                        bg.date = startTime + i * 5 * 60 * 1000L;
                        bg.isZTPrediction = true;
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
            long startTime = date != null ? date.getTime() : 0;
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
                if (predBGs.has("ZT")) {
                    JSONArray iob = predBGs.getJSONArray("ZT");
                    latest = Math.max(latest, startTime + (iob.length() - 1) * 5 * 60 * 1000L);
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return latest;
    }

    public boolean isChangeRequested() {
        return tempBasalRequested || bolusRequested;
    }
}

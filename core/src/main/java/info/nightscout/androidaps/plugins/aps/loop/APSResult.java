package info.nightscout.androidaps.plugins.aps.loop;

import android.text.Spanned;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import dagger.android.HasAndroidInjector;
import info.nightscout.androidaps.core.R;
import info.nightscout.androidaps.data.IobTotal;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.db.BgReading;
import info.nightscout.androidaps.db.TemporaryBasal;
import info.nightscout.androidaps.interfaces.ActivePluginProvider;
import info.nightscout.androidaps.interfaces.Constraint;
import info.nightscout.androidaps.interfaces.ProfileFunction;
import info.nightscout.androidaps.interfaces.PumpDescription;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.interfaces.TreatmentsInterface;
import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.configBuilder.ConstraintChecker;
import info.nightscout.androidaps.utils.DecimalFormatter;
import info.nightscout.androidaps.utils.HtmlHelper;
import info.nightscout.androidaps.utils.resources.ResourceHelper;
import info.nightscout.androidaps.utils.sharedPreferences.SP;

/**
 * Created by mike on 09.06.2016.
 */
public class APSResult {
    @Inject HasAndroidInjector injector;
    @Inject public AAPSLogger aapsLogger;
    @Inject ConstraintChecker constraintChecker;
    @Inject SP sp;
    @Inject ActivePluginProvider activePlugin;
    @Inject TreatmentsInterface treatmentsPlugin;
    @Inject ProfileFunction profileFunction;
    @Inject ResourceHelper resourceHelper;

    @Inject
    public APSResult(HasAndroidInjector injector) {
        injector.androidInjector().inject(this);
    }

    public long date = 0;
    public String reason;
    public double rate;
    public int percent;
    public boolean usePercent = false;
    public int duration;
    public boolean tempBasalRequested = false;
    public boolean bolusRequested = false;
    public IobTotal iob;
    public JSONObject json = new JSONObject();
    public boolean hasPredictions = false;
    public double smb = 0d; // super micro bolus in units
    public long deliverAt = 0;
    public double targetBG = 0d;

    public int carbsReq = 0;
    public int carbsReqWithin = 0;

    public Constraint<Double> inputConstraints;

    public Constraint<Double> rateConstraint;
    public Constraint<Integer> percentConstraint;
    public Constraint<Double> smbConstraint;

    public APSResult rate(double rate) {
        this.rate = rate;
        return this;
    }

    public APSResult duration(int duration) {
        this.duration = duration;
        return this;
    }

    public APSResult percent(int percent) {
        this.percent = percent;
        return this;
    }

    public APSResult tempBasalRequested(boolean tempBasalRequested) {
        this.tempBasalRequested = tempBasalRequested;
        return this;
    }

    public APSResult usePercent(boolean usePercent) {
        this.usePercent = usePercent;
        return this;
    }

    public APSResult json(JSONObject json) {
        this.json = json;
        return this;
    }

    public String getCarbsRequiredText() {
        return String.format(resourceHelper.gs(R.string.carbsreq), carbsReq, carbsReqWithin);
    }

    @Override
    public String toString() {
        final PumpInterface pump = activePlugin.getActivePump();
        if (isChangeRequested()) {
            String ret;
            // rate
            if (rate == 0 && duration == 0)
                ret = resourceHelper.gs(R.string.canceltemp) + "\n";
            else if (rate == -1)
                ret = resourceHelper.gs(R.string.let_temp_basal_run) + "\n";
            else if (usePercent)
                ret = resourceHelper.gs(R.string.rate) + ": " + DecimalFormatter.to2Decimal(percent) + "% " +
                        "(" + DecimalFormatter.to2Decimal(percent * pump.getBaseBasalRate() / 100d) + " U/h)\n" +
                        resourceHelper.gs(R.string.duration) + ": " + DecimalFormatter.to2Decimal(duration) + " min\n";
            else
                ret = resourceHelper.gs(R.string.rate) + ": " + DecimalFormatter.to2Decimal(rate) + " U/h " +
                        "(" + DecimalFormatter.to2Decimal(rate / pump.getBaseBasalRate() * 100) + "%) \n" +
                        resourceHelper.gs(R.string.duration) + ": " + DecimalFormatter.to2Decimal(duration) + " min\n";

            // smb
            if (smb != 0)
                ret += ("SMB: " + DecimalFormatter.toPumpSupportedBolus(smb, activePlugin.getActivePump(), resourceHelper) + "\n");

            if (isCarbsRequired()) {
                ret += getCarbsRequiredText()+"\n";
            }

            // reason
            ret += resourceHelper.gs(R.string.reason) + ": " + reason;
            return ret;
        }

        if (isCarbsRequired()) {
            return getCarbsRequiredText();
        }

        return resourceHelper.gs(R.string.nochangerequested);
    }

    public Spanned toSpanned() {
        final PumpInterface pump = activePlugin.getActivePump();
        if (isChangeRequested()) {
            String ret;
            // rate
            if (rate == 0 && duration == 0)
                ret = resourceHelper.gs(R.string.canceltemp) + "<br>";
            else if (rate == -1)
                ret = resourceHelper.gs(R.string.let_temp_basal_run) + "<br>";
            else if (usePercent)
                ret = "<b>" + resourceHelper.gs(R.string.rate) + "</b>: " + DecimalFormatter.to2Decimal(percent) + "% " +
                        "(" + DecimalFormatter.to2Decimal(percent * pump.getBaseBasalRate() / 100d) + " U/h)<br>" +
                        "<b>" + resourceHelper.gs(R.string.duration) + "</b>: " + DecimalFormatter.to2Decimal(duration) + " min<br>";
            else
                ret = "<b>" + resourceHelper.gs(R.string.rate) + "</b>: " + DecimalFormatter.to2Decimal(rate) + " U/h " +
                        "(" + DecimalFormatter.to2Decimal(rate / pump.getBaseBasalRate() * 100d) + "%) <br>" +
                        "<b>" + resourceHelper.gs(R.string.duration) + "</b>: " + DecimalFormatter.to2Decimal(duration) + " min<br>";

            // smb
            if (smb != 0)
                ret += ("<b>" + "SMB" + "</b>: " + DecimalFormatter.toPumpSupportedBolus(smb, activePlugin.getActivePump(), resourceHelper) + "<br>");

            if (isCarbsRequired()) {
                ret += getCarbsRequiredText()+"<br>";
            }

            // reason
            ret += "<b>" + resourceHelper.gs(R.string.reason) + "</b>: " + reason.replace("<", "&lt;").replace(">", "&gt;");
            return HtmlHelper.INSTANCE.fromHtml(ret);
        }

        if (isCarbsRequired()) {
            return HtmlHelper.INSTANCE.fromHtml(getCarbsRequiredText());
        }

        return HtmlHelper.INSTANCE.fromHtml(resourceHelper.gs(R.string.nochangerequested));
    }

    public APSResult newAndClone(HasAndroidInjector injector) {
        APSResult newResult = new APSResult(injector);
        doClone(newResult);
        return newResult;
    }

    protected void doClone(APSResult newResult) {
        newResult.date = date;
        newResult.reason = reason != null ? reason : null;
        newResult.rate = rate;
        newResult.duration = duration;
        newResult.tempBasalRequested = tempBasalRequested;
        newResult.bolusRequested = bolusRequested;
        newResult.iob = iob;
        try {
            newResult.json = new JSONObject(json.toString());
        } catch (JSONException e) {
            aapsLogger.error("Unhandled exception", e);
        }
        newResult.hasPredictions = hasPredictions;
        newResult.smb = smb;
        newResult.deliverAt = deliverAt;
        newResult.rateConstraint = rateConstraint;
        newResult.smbConstraint = smbConstraint;
        newResult.percent = percent;
        newResult.usePercent = usePercent;
        newResult.carbsReq = carbsReq;
        newResult.carbsReqWithin = carbsReqWithin;
        newResult.targetBG = targetBG;
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
            aapsLogger.error("Unhandled exception", e);
        }
        return json;
    }

    public List<BgReading> getPredictions() {
        List<BgReading> array = new ArrayList<>();
        try {
            long startTime = date;
            if (json != null && json.has("predBGs")) {
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
            aapsLogger.error("Unhandled exception", e);
        }
        return array;
    }

    public long getLatestPredictionsTime() {
        long latest = 0;
        try {
            long startTime = date;
            if (json != null && json.has("predBGs")) {
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
            aapsLogger.error("Unhandled exception", e);
        }

        return latest;
    }

    public boolean isCarbsRequired() {
        return carbsReq > 0;
    }

    public boolean isChangeRequested() {
        Constraint<Boolean> closedLoopEnabled = constraintChecker.isClosedLoopAllowed();
        // closed loop mode: handle change at driver level
        if (closedLoopEnabled.value()) {
            aapsLogger.debug(LTag.APS, "DEFAULT: Closed mode");
            return tempBasalRequested || bolusRequested;
        }

        // open loop mode: try to limit request
        if (!tempBasalRequested && !bolusRequested) {
            aapsLogger.debug(LTag.APS, "FALSE: No request");
            return false;
        }

        long now = System.currentTimeMillis();
        TemporaryBasal activeTemp = treatmentsPlugin.getTempBasalFromHistory(now);
        PumpInterface pump = activePlugin.getActivePump();
        Profile profile = profileFunction.getProfile();

        if (profile == null) {
            aapsLogger.error("FALSE: No Profile");
            return false;
        }

        if (usePercent) {
            if (activeTemp == null && percent == 100) {
                aapsLogger.debug(LTag.APS, "FALSE: No temp running, asking cancel temp");
                return false;
            }
            if (activeTemp != null && Math.abs(percent - activeTemp.tempBasalConvertedToPercent(now, profile)) < pump.getPumpDescription().basalStep) {
                aapsLogger.debug(LTag.APS, "FALSE: Temp equal");
                return false;
            }
            // always report zerotemp
            if (percent == 0) {
                aapsLogger.debug(LTag.APS, "TRUE: Zero temp");
                return true;
            }
            // always report hightemp
            if (pump.getPumpDescription().tempBasalStyle == PumpDescription.PERCENT) {
                double pumpLimit = pump.getPumpDescription().pumpType.getTbrSettings().getMaxDose();
                if (percent == pumpLimit) {
                    aapsLogger.debug(LTag.APS, "TRUE: Pump limit");
                    return true;
                }
            }
            // report change bigger than 30%
            double percentMinChangeChange = sp.getDouble(R.string.key_loop_openmode_min_change, 30d);
            percentMinChangeChange /= 100d;
            double lowThreshold = 1 - percentMinChangeChange;
            double highThreshold = 1 + percentMinChangeChange;
            double change = percent / 100d;
            if (activeTemp != null)
                change = percent / (double) activeTemp.tempBasalConvertedToPercent(now, profile);

            if (change < lowThreshold || change > highThreshold) {
                aapsLogger.debug(LTag.APS, "TRUE: Outside allowed range " + (change * 100d) + "%");
                return true;
            } else {
                aapsLogger.debug(LTag.APS, "TRUE: Inside allowed range " + (change * 100d) + "%");
                return false;
            }
        } else {
            if (activeTemp == null && rate == pump.getBaseBasalRate()) {
                aapsLogger.debug(LTag.APS, "FALSE: No temp running, asking cancel temp");
                return false;
            }
            if (activeTemp != null && Math.abs(rate - activeTemp.tempBasalConvertedToAbsolute(now, profile)) < pump.getPumpDescription().basalStep) {
                aapsLogger.debug(LTag.APS, "FALSE: Temp equal");
                return false;
            }
            // always report zerotemp
            if (rate == 0) {
                aapsLogger.debug(LTag.APS, "TRUE: Zero temp");
                return true;
            }
            // always report hightemp
            if (pump.getPumpDescription().tempBasalStyle == PumpDescription.ABSOLUTE) {
                double pumpLimit = pump.getPumpDescription().pumpType.getTbrSettings().getMaxDose();
                if (rate == pumpLimit) {
                    aapsLogger.debug(LTag.APS, "TRUE: Pump limit");
                    return true;
                }
            }
            // report change bigger than 30%
            double percentMinChangeChange = sp.getDouble(R.string.key_loop_openmode_min_change, 30d);
            percentMinChangeChange /= 100d;
            double lowThreshold = 1 - percentMinChangeChange;
            double highThreshold = 1 + percentMinChangeChange;
            double change = rate / profile.getBasal();
            if (activeTemp != null)
                change = rate / activeTemp.tempBasalConvertedToAbsolute(now, profile);

            if (change < lowThreshold || change > highThreshold) {
                aapsLogger.debug(LTag.APS, "TRUE: Outside allowed range " + (change * 100d) + "%");
                return true;
            } else {
                aapsLogger.debug(LTag.APS, "TRUE: Inside allowed range " + (change * 100d) + "%");
                return false;
            }
        }
    }
}

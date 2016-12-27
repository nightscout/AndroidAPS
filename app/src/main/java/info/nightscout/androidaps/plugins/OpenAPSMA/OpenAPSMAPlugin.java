package info.nightscout.androidaps.plugins.OpenAPSMA;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Date;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.Services.Intents;
import info.nightscout.androidaps.db.DatabaseHelper;
import info.nightscout.androidaps.interfaces.APSInterface;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.interfaces.TempBasalsInterface;
import info.nightscout.androidaps.interfaces.TreatmentsInterface;
import info.nightscout.androidaps.plugins.Loop.APSResult;
import info.nightscout.androidaps.plugins.Loop.ScriptReader;
import info.nightscout.androidaps.plugins.OpenAPSMA.events.EventOpenAPSMAUpdateGui;
import info.nightscout.androidaps.plugins.OpenAPSMA.events.EventOpenAPSMAUpdateResultGui;
import info.nightscout.androidaps.plugins.Treatments.TreatmentsPlugin;
import info.nightscout.client.data.DbLogger;
import info.nightscout.client.data.NSProfile;
import info.nightscout.utils.DateUtil;
import info.nightscout.utils.Round;
import info.nightscout.utils.SafeParse;
import info.nightscout.utils.ToastUtils;

/**
 * Created by mike on 05.08.2016.
 */
public class OpenAPSMAPlugin implements PluginBase, APSInterface {
    private static Logger log = LoggerFactory.getLogger(OpenAPSMAPlugin.class);

    // last values
    DetermineBasalAdapterJS lastDetermineBasalAdapterJS = null;
    Date lastAPSRun = null;
    DetermineBasalResult lastAPSResult = null;

    boolean fragmentEnabled = false;
    boolean fragmentVisible = true;

    @Override
    public String getName() {
        return MainApp.instance().getString(R.string.openapsma);
    }

    @Override
    public boolean isEnabled(int type) {
        return type == APS && fragmentEnabled && MainApp.getConfigBuilder().getPumpDescription().isTempBasalCapable;
    }

    @Override
    public boolean isVisibleInTabs(int type) {
        return type == APS && fragmentVisible && MainApp.getConfigBuilder().getPumpDescription().isTempBasalCapable;
    }

    @Override
    public boolean canBeHidden(int type) {
        return true;
    }

    @Override
    public void setFragmentVisible(int type, boolean fragmentVisible) {
        if (type == APS) this.fragmentVisible = fragmentVisible;
    }

    @Override
    public void setFragmentEnabled(int type, boolean fragmentEnabled) {
        if (type == APS) this.fragmentEnabled = fragmentEnabled;
    }

    @Override
    public int getType() {
        return PluginBase.APS;
    }

    @Override
    public String getFragmentClass() {
        return OpenAPSMAFragment.class.getName();
    }

    @Override
    public APSResult getLastAPSResult() {
        return lastAPSResult;
    }

    @Override
    public Date getLastAPSRun() {
        return lastAPSRun;
    }

    @Override
    public void invoke() {
        lastAPSResult = null;
        DetermineBasalAdapterJS determineBasalAdapterJS = null;
        try {
            determineBasalAdapterJS = new DetermineBasalAdapterJS(new ScriptReader(MainApp.instance().getBaseContext()));
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            return;
        }

        DatabaseHelper.GlucoseStatus glucoseStatus = MainApp.getDbHelper().getGlucoseStatusData();
        NSProfile profile = MainApp.getConfigBuilder().getActiveProfile().getProfile();
        PumpInterface pump = MainApp.getConfigBuilder();

        if (!isEnabled(PluginBase.APS)) {
            MainApp.bus().post(new EventOpenAPSMAUpdateResultGui(MainApp.instance().getString(R.string.openapsma_disabled)));
            if (Config.logAPSResult)
                log.debug(MainApp.instance().getString(R.string.openapsma_disabled));
            return;
        }

        if (glucoseStatus == null) {
            MainApp.bus().post(new EventOpenAPSMAUpdateResultGui(MainApp.instance().getString(R.string.openapsma_noglucosedata)));
            if (Config.logAPSResult)
                log.debug(MainApp.instance().getString(R.string.openapsma_noglucosedata));
            return;
        }

        if (profile == null) {
            MainApp.bus().post(new EventOpenAPSMAUpdateResultGui(MainApp.instance().getString(R.string.openapsma_noprofile)));
            if (Config.logAPSResult)
                log.debug(MainApp.instance().getString(R.string.openapsma_noprofile));
            return;
        }

        if (pump == null) {
            MainApp.bus().post(new EventOpenAPSMAUpdateResultGui(MainApp.instance().getString(R.string.openapsma_nopump)));
            if (Config.logAPSResult)
                log.debug(MainApp.instance().getString(R.string.openapsma_nopump));
            return;
        }

        SharedPreferences SP = PreferenceManager.getDefaultSharedPreferences(MainApp.instance().getApplicationContext());
        String units = profile.getUnits();

        String maxBgDefault = "180";
        String minBgDefault = "100";
        String targetBgDefault = "150";
        if (!units.equals(Constants.MGDL)) {
            maxBgDefault = "10";
            minBgDefault = "5";
            targetBgDefault = "7";
        }

        Date now = new Date();

        double maxIob = SafeParse.stringToDouble(SP.getString("openapsma_max_iob", "1.5"));
        double maxBasal = SafeParse.stringToDouble(SP.getString("openapsma_max_basal", "1"));
        double minBg = NSProfile.toMgdl(SafeParse.stringToDouble(SP.getString("openapsma_min_bg", minBgDefault)), units);
        double maxBg = NSProfile.toMgdl(SafeParse.stringToDouble(SP.getString("openapsma_max_bg", maxBgDefault)), units);
        double targetBg = NSProfile.toMgdl(SafeParse.stringToDouble(SP.getString("openapsma_target_bg", targetBgDefault)), units);

        minBg = Round.roundTo(minBg, 0.1d);
        maxBg = Round.roundTo(maxBg, 0.1d);

        TreatmentsInterface treatments = MainApp.getConfigBuilder().getActiveTreatments();
        TempBasalsInterface tempBasals = MainApp.getConfigBuilder().getActiveTempBasals();
        treatments.updateTotalIOB();
        tempBasals.updateTotalIOB();
        IobTotal bolusIob = treatments.getLastCalculation();
        IobTotal basalIob = tempBasals.getLastCalculation();

        IobTotal iobTotal = IobTotal.combine(bolusIob, basalIob).round();

        TreatmentsPlugin.MealData mealData = treatments.getMealData();

        maxIob = MainApp.getConfigBuilder().applyMaxIOBConstraints(maxIob);

        minBg = verifyHardLimits(minBg, "minBg", 72, 180);
        maxBg = verifyHardLimits(maxBg, "maxBg", 100, 270);
        targetBg = verifyHardLimits(targetBg, "targetBg", 80, 200);
        maxIob = verifyHardLimits(maxIob, "maxIob", 0, 7);
        maxBasal = verifyHardLimits(maxBasal, "max_basal", 0.1, 10);

        if (!checkOnlyHardLimits(profile.getCarbAbsorbtionRate(), "carbs_hr", 4, 100)) return;
        if (!checkOnlyHardLimits(profile.getDia(), "dia", 2, 7)) return;
        if (!checkOnlyHardLimits(profile.getIc(profile.secondsFromMidnight()), "carbratio", 2, 100)) return;
        if (!checkOnlyHardLimits(NSProfile.toMgdl(profile.getIsf(NSProfile.secondsFromMidnight()).doubleValue(), units), "sens", 2, 900)) return;
        if (!checkOnlyHardLimits(profile.getMaxDailyBasal(), "max_daily_basal", 0.1, 10)) return;
        if (!checkOnlyHardLimits(pump.getBaseBasalRate(), "current_basal", 0.01, 5)) return;

        determineBasalAdapterJS.setData(profile, maxIob, maxBasal, minBg, maxBg, targetBg, pump, iobTotal, glucoseStatus, mealData);


        DetermineBasalResult determineBasalResult = determineBasalAdapterJS.invoke();
        // Fix bug determine basal
        if (determineBasalResult.rate == 0d && determineBasalResult.duration == 0 && !MainApp.getConfigBuilder().isTempBasalInProgress())
            determineBasalResult.changeRequested = false;
        // limit requests on openloop mode
        if (!MainApp.getConfigBuilder().isClosedModeEnabled()) {
            if (MainApp.getConfigBuilder().isTempBasalInProgress() && Math.abs(determineBasalResult.rate - MainApp.getConfigBuilder().getTempBasalAbsoluteRate()) < 0.1)
                determineBasalResult.changeRequested = false;
            if (!MainApp.getConfigBuilder().isTempBasalInProgress() && Math.abs(determineBasalResult.rate - MainApp.getConfigBuilder().getBaseBasalRate()) < 0.1)
                determineBasalResult.changeRequested = false;
        }

        determineBasalResult.iob = iobTotal;

        determineBasalAdapterJS.release();

        try {
            determineBasalResult.json.put("timestamp", DateUtil.toISOString(now));
        } catch (JSONException e) {
            e.printStackTrace();
        }

        lastDetermineBasalAdapterJS = determineBasalAdapterJS;
        lastAPSResult = determineBasalResult;
        lastAPSRun = now;
        MainApp.bus().post(new EventOpenAPSMAUpdateGui());

        //deviceStatus.suggested = determineBasalResult.json;
    }

    // safety checks
    public static boolean checkOnlyHardLimits(Double value, String valueName, double lowLimit, double highLimit) {
        return value.equals(verifyHardLimits(value, valueName, lowLimit, highLimit));
    }

    public static Double verifyHardLimits(Double value, String valueName, double lowLimit, double highLimit) {
        if (value < lowLimit || value > highLimit) {
            String msg = String.format(MainApp.sResources.getString(R.string.openapsma_valueoutofrange), valueName);
            log.error(msg);
            MainApp.getConfigBuilder().uploadError(msg);
            ToastUtils.showToastInUiThread(MainApp.instance().getApplicationContext(), msg, R.raw.error);
            value = Math.max(value, lowLimit);
            value = Math.min(value, highLimit);
        }
        return value;
    }

}

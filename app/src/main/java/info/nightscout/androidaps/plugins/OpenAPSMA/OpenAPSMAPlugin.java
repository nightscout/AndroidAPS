package info.nightscout.androidaps.plugins.OpenAPSMA;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import org.json.JSONException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Date;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.GlucoseStatus;
import info.nightscout.androidaps.data.IobTotal;
import info.nightscout.androidaps.data.MealData;
import info.nightscout.androidaps.db.TempTarget;
import info.nightscout.androidaps.interfaces.APSInterface;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.interfaces.TempBasalsInterface;
import info.nightscout.androidaps.interfaces.TreatmentsInterface;
import info.nightscout.androidaps.plugins.Loop.APSResult;
import info.nightscout.androidaps.plugins.Loop.ScriptReader;
import info.nightscout.androidaps.plugins.OpenAPSMA.events.EventOpenAPSUpdateGui;
import info.nightscout.androidaps.plugins.OpenAPSMA.events.EventOpenAPSUpdateResultGui;
import info.nightscout.androidaps.plugins.TempTargetRange.TempTargetRangePlugin;
import info.nightscout.client.data.NSProfile;
import info.nightscout.utils.DateUtil;
import info.nightscout.utils.Round;
import info.nightscout.utils.SafeParse;
import info.nightscout.utils.ToastUtils;

import static info.nightscout.androidaps.plugins.OpenAPSAMA.OpenAPSAMAPlugin.checkOnlyHardLimits;
import static info.nightscout.androidaps.plugins.OpenAPSAMA.OpenAPSAMAPlugin.verifyHardLimits;

/**
 * Created by mike on 05.08.2016.
 */
public class OpenAPSMAPlugin implements PluginBase, APSInterface {
    private static Logger log = LoggerFactory.getLogger(OpenAPSMAPlugin.class);

    // last values
    DetermineBasalAdapterMAJS lastDetermineBasalAdapterMAJS = null;
    Date lastAPSRun = null;
    DetermineBasalResultMA lastAPSResult = null;

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
    public void invoke(String initiator) {
        log.debug("invoke from " + initiator);
        lastAPSResult = null;
        DetermineBasalAdapterMAJS determineBasalAdapterMAJS = null;
        try {
            determineBasalAdapterMAJS = new DetermineBasalAdapterMAJS(new ScriptReader(MainApp.instance().getBaseContext()));
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            return;
        }

        GlucoseStatus glucoseStatus = GlucoseStatus.getGlucoseStatusData();
        NSProfile profile = MainApp.getConfigBuilder().getActiveProfile().getProfile();
        PumpInterface pump = MainApp.getConfigBuilder();

        if (!isEnabled(PluginBase.APS)) {
            MainApp.bus().post(new EventOpenAPSUpdateResultGui(MainApp.instance().getString(R.string.openapsma_disabled)));
            if (Config.logAPSResult)
                log.debug(MainApp.instance().getString(R.string.openapsma_disabled));
            return;
        }

        if (glucoseStatus == null) {
            MainApp.bus().post(new EventOpenAPSUpdateResultGui(MainApp.instance().getString(R.string.openapsma_noglucosedata)));
            if (Config.logAPSResult)
                log.debug(MainApp.instance().getString(R.string.openapsma_noglucosedata));
            return;
        }

        if (profile == null) {
            MainApp.bus().post(new EventOpenAPSUpdateResultGui(MainApp.instance().getString(R.string.openapsma_noprofile)));
            if (Config.logAPSResult)
                log.debug(MainApp.instance().getString(R.string.openapsma_noprofile));
            return;
        }

        if (pump == null) {
            MainApp.bus().post(new EventOpenAPSUpdateResultGui(MainApp.instance().getString(R.string.openapsma_nopump)));
            if (Config.logAPSResult)
                log.debug(MainApp.instance().getString(R.string.openapsma_nopump));
            return;
        }

        SharedPreferences SP = PreferenceManager.getDefaultSharedPreferences(MainApp.instance().getApplicationContext());
        String units = profile.getUnits();

        String maxBgDefault = Constants.MAX_BG_DEFAULT_MGDL;
        String minBgDefault = Constants.MIN_BG_DEFAULT_MGDL;
        String targetBgDefault = Constants.TARGET_BG_DEFAULT_MGDL;
        if (!units.equals(Constants.MGDL)) {
            maxBgDefault = Constants.MAX_BG_DEFAULT_MMOL;
            minBgDefault = Constants.MIN_BG_DEFAULT_MMOL;
            targetBgDefault = Constants.TARGET_BG_DEFAULT_MMOL;
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

        MealData mealData = treatments.getMealData();

        maxIob = MainApp.getConfigBuilder().applyMaxIOBConstraints(maxIob);

        minBg = verifyHardLimits(minBg, "minBg", Constants.VERY_HARD_LIMIT_MIN_BG[0], Constants.VERY_HARD_LIMIT_MIN_BG[1]);
        maxBg = verifyHardLimits(maxBg, "maxBg", Constants.VERY_HARD_LIMIT_MAX_BG[0], Constants.VERY_HARD_LIMIT_MAX_BG[1]);
        targetBg = verifyHardLimits(targetBg, "targetBg", Constants.VERY_HARD_LIMIT_TARGET_BG[0], Constants.VERY_HARD_LIMIT_TARGET_BG[1]);
        
        TempTargetRangePlugin tempTargetRangePlugin = (TempTargetRangePlugin) MainApp.getSpecificPlugin(TempTargetRangePlugin.class);
        if (tempTargetRangePlugin != null && tempTargetRangePlugin.isEnabled(PluginBase.GENERAL)) {
            TempTarget tempTarget = tempTargetRangePlugin.getTempTargetInProgress(new Date().getTime());
            if (tempTarget != null) {
                minBg = verifyHardLimits(tempTarget.low, "minBg", Constants.VERY_HARD_LIMIT_TEMP_MIN_BG[0], Constants.VERY_HARD_LIMIT_TEMP_MIN_BG[1]);
                maxBg = verifyHardLimits(tempTarget.high, "maxBg", Constants.VERY_HARD_LIMIT_TEMP_MAX_BG[0], Constants.VERY_HARD_LIMIT_TEMP_MAX_BG[1]);
                targetBg = verifyHardLimits((tempTarget.low + tempTarget.high) / 2, "targetBg", Constants.VERY_HARD_LIMIT_TEMP_TARGET_BG[0], Constants.VERY_HARD_LIMIT_TEMP_TARGET_BG[1]);
            }
        }

        maxIob = verifyHardLimits(maxIob, "maxIob", 0, 7);
        maxBasal = verifyHardLimits(maxBasal, "max_basal", 0.1, 10);

        if (!checkOnlyHardLimits(profile.getCarbAbsorbtionRate(), "carbs_hr", 4, 100)) return;
        if (!checkOnlyHardLimits(profile.getDia(), "dia", 2, 7)) return;
        if (!checkOnlyHardLimits(profile.getIc(profile.secondsFromMidnight()), "carbratio", 2, 100)) return;
        if (!checkOnlyHardLimits(NSProfile.toMgdl(profile.getIsf(NSProfile.secondsFromMidnight()).doubleValue(), units), "sens", 2, 900)) return;
        if (!checkOnlyHardLimits(profile.getMaxDailyBasal(), "max_daily_basal", 0.1, 10)) return;
        if (!checkOnlyHardLimits(pump.getBaseBasalRate(), "current_basal", 0.01, 5)) return;

        determineBasalAdapterMAJS.setData(profile, maxIob, maxBasal, minBg, maxBg, targetBg, pump, iobTotal, glucoseStatus, mealData);


        DetermineBasalResultMA determineBasalResultMA = determineBasalAdapterMAJS.invoke();
        // Fix bug determine basal
        if (determineBasalResultMA.rate == 0d && determineBasalResultMA.duration == 0 && !MainApp.getConfigBuilder().isTempBasalInProgress())
            determineBasalResultMA.changeRequested = false;
        // limit requests on openloop mode
        if (!MainApp.getConfigBuilder().isClosedModeEnabled()) {
            if (MainApp.getConfigBuilder().isTempBasalInProgress() && Math.abs(determineBasalResultMA.rate - MainApp.getConfigBuilder().getTempBasalAbsoluteRate()) < 0.1)
                determineBasalResultMA.changeRequested = false;
            if (!MainApp.getConfigBuilder().isTempBasalInProgress() && Math.abs(determineBasalResultMA.rate - MainApp.getConfigBuilder().getBaseBasalRate()) < 0.1)
                determineBasalResultMA.changeRequested = false;
        }

        determineBasalResultMA.iob = iobTotal;

        determineBasalAdapterMAJS.release();

        try {
            determineBasalResultMA.json.put("timestamp", DateUtil.toISOString(now));
        } catch (JSONException e) {
            e.printStackTrace();
        }

        lastDetermineBasalAdapterMAJS = determineBasalAdapterMAJS;
        lastAPSResult = determineBasalResultMA;
        lastAPSRun = now;
        MainApp.bus().post(new EventOpenAPSUpdateGui());

        //deviceStatus.suggested = determineBasalResultMA.json;
    }


}

package info.nightscout.androidaps.plugins.OpenAPSAMA;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import org.json.JSONException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Date;
import java.util.List;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.GlucoseStatus;
import info.nightscout.androidaps.data.MealData;
import info.nightscout.androidaps.db.BgReading;
import info.nightscout.androidaps.db.TempTarget;
import info.nightscout.androidaps.interfaces.APSInterface;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.interfaces.TempBasalsInterface;
import info.nightscout.androidaps.plugins.Loop.APSResult;
import info.nightscout.androidaps.plugins.Loop.ScriptReader;
import info.nightscout.androidaps.data.IobTotal;
import info.nightscout.androidaps.plugins.OpenAPSMA.events.EventOpenAPSUpdateGui;
import info.nightscout.androidaps.plugins.OpenAPSMA.events.EventOpenAPSUpdateResultGui;
import info.nightscout.androidaps.plugins.TempTargetRange.TempTargetRangePlugin;
import info.nightscout.client.data.NSProfile;
import info.nightscout.utils.DateUtil;
import info.nightscout.utils.Round;
import info.nightscout.utils.SafeParse;
import info.nightscout.utils.ToastUtils;

/**
 * Created by mike on 05.08.2016.
 */
public class OpenAPSAMAPlugin implements PluginBase, APSInterface {
    private static Logger log = LoggerFactory.getLogger(OpenAPSAMAPlugin.class);

    // last values
    DetermineBasalAdapterAMAJS lastDetermineBasalAdapterAMAJS = null;
    Date lastAPSRun = null;
    DetermineBasalResultAMA lastAPSResult = null;
    AutosensResult lastAutosensResult = null;

    boolean fragmentEnabled = false;
    boolean fragmentVisible = true;

    @Override
    public String getName() {
        return MainApp.instance().getString(R.string.openapsama);
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
        return OpenAPSAMAFragment.class.getName();
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
        DetermineBasalAdapterAMAJS determineBasalAdapterAMAJS = null;
        try {
            determineBasalAdapterAMAJS = new DetermineBasalAdapterAMAJS(new ScriptReader(MainApp.instance().getBaseContext()));
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

        IobTotal[] iobArray = IobTotal.calculateIobArrayInDia();

        MealData mealData = MainApp.getConfigBuilder().getActiveTreatments().getMealData();

        maxIob = MainApp.getConfigBuilder().applyMaxIOBConstraints(maxIob);

        minBg = verifyHardLimits(minBg, "minBg", Constants.VERY_HARD_LIMIT_MIN_BG[0], Constants.VERY_HARD_LIMIT_MIN_BG[1]);
        maxBg = verifyHardLimits(maxBg, "maxBg", Constants.VERY_HARD_LIMIT_MAX_BG[0], Constants.VERY_HARD_LIMIT_MAX_BG[1]);
        targetBg = verifyHardLimits(targetBg, "targetBg", Constants.VERY_HARD_LIMIT_TARGET_BG[0], Constants.VERY_HARD_LIMIT_TARGET_BG[1]);

        boolean isTempTarget = false;
        TempTargetRangePlugin tempTargetRangePlugin = (TempTargetRangePlugin) MainApp.getSpecificPlugin(TempTargetRangePlugin.class);
        if (tempTargetRangePlugin != null && tempTargetRangePlugin.isEnabled(PluginBase.GENERAL)) {
            TempTarget tempTarget = tempTargetRangePlugin.getTempTargetInProgress(new Date().getTime());
            if (tempTarget != null) {
                isTempTarget = true;
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

        long oldestDataAvailable = MainApp.getConfigBuilder().getActiveTempBasals().oldestDataAvaialable();
        List<BgReading> bgReadings = MainApp.getDbHelper().getBgreadingsDataFromTime(Math.max(oldestDataAvailable, (long) (new Date().getTime() - 60 * 60 * 1000L * (24 + profile.getDia()))), false);
        log.debug("Limiting data to oldest available temps: " + new Date(oldestDataAvailable).toString() + " (" + bgReadings.size() + " records)");
        lastAutosensResult = Autosens.detectSensitivityandCarbAbsorption(bgReadings, new Date().getTime());

        determineBasalAdapterAMAJS.setData(profile, maxIob, maxBasal, minBg, maxBg, targetBg, pump, iobArray, glucoseStatus, mealData,
                lastAutosensResult.ratio, //autosensDataRatio
                isTempTarget,
                Constants.MIN_5M_CARBIMPACT //min_5m_carbimpact
                );


        DetermineBasalResultAMA determineBasalResultAMA = determineBasalAdapterAMAJS.invoke();
        // Fix bug determine basal
        if (determineBasalResultAMA.rate == 0d && determineBasalResultAMA.duration == 0 && !MainApp.getConfigBuilder().isTempBasalInProgress())
            determineBasalResultAMA.changeRequested = false;
        // limit requests on openloop mode
        if (!MainApp.getConfigBuilder().isClosedModeEnabled()) {
            if (MainApp.getConfigBuilder().isTempBasalInProgress() && Math.abs(determineBasalResultAMA.rate - MainApp.getConfigBuilder().getTempBasalAbsoluteRate()) < 0.1)
                determineBasalResultAMA.changeRequested = false;
            if (!MainApp.getConfigBuilder().isTempBasalInProgress() && Math.abs(determineBasalResultAMA.rate - MainApp.getConfigBuilder().getBaseBasalRate()) < 0.1)
                determineBasalResultAMA.changeRequested = false;
        }

        determineBasalResultAMA.iob = iobArray[0];

        determineBasalAdapterAMAJS.release();

        try {
            determineBasalResultAMA.json.put("timestamp", DateUtil.toISOString(now));
        } catch (JSONException e) {
            e.printStackTrace();
        }

        lastDetermineBasalAdapterAMAJS = determineBasalAdapterAMAJS;
        lastAPSResult = determineBasalResultAMA;
        lastAPSRun = now;
        MainApp.bus().post(new EventOpenAPSUpdateGui());

        //deviceStatus.suggested = determineBasalResultAMA.json;
    }

    // safety checks
    public static boolean checkOnlyHardLimits(Double value, String valueName, double lowLimit, double highLimit) {
        return value.equals(verifyHardLimits(value, valueName, lowLimit, highLimit));
    }

    public static Double verifyHardLimits(Double value, String valueName, double lowLimit, double highLimit) {
        Double newvalue = value;
        if (newvalue < lowLimit || newvalue > highLimit) {
            newvalue = Math.max(newvalue, lowLimit);
            newvalue = Math.min(newvalue, highLimit);
            String msg = String.format(MainApp.sResources.getString(R.string.openapsma_valueoutofrange), valueName);
            msg += ".\n";
            msg += String.format(MainApp.sResources.getString(R.string.openapsma_valuelimitedto), value, newvalue);
            log.error(msg);
            MainApp.getConfigBuilder().uploadError(msg);
            ToastUtils.showToastInUiThread(MainApp.instance().getApplicationContext(), msg, R.raw.error);
        }
        return newvalue;
    }

}

package info.nightscout.androidaps.plugins.OpenAPSAMA;

import org.json.JSONException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Date;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.GlucoseStatus;
import info.nightscout.androidaps.data.IobTotal;
import info.nightscout.androidaps.data.MealData;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.db.TempTarget;
import info.nightscout.androidaps.db.TemporaryBasal;
import info.nightscout.androidaps.interfaces.APSInterface;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.interfaces.Constraint;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.IobCobCalculator.AutosensResult;
import info.nightscout.androidaps.plugins.IobCobCalculator.IobCobCalculatorPlugin;
import info.nightscout.androidaps.plugins.Loop.APSResult;
import info.nightscout.androidaps.plugins.Loop.ScriptReader;
import info.nightscout.androidaps.plugins.OpenAPSMA.events.EventOpenAPSUpdateGui;
import info.nightscout.androidaps.plugins.OpenAPSMA.events.EventOpenAPSUpdateResultGui;
import info.nightscout.utils.DateUtil;
import info.nightscout.utils.HardLimits;
import info.nightscout.utils.Profiler;
import info.nightscout.utils.Round;
import info.nightscout.utils.SP;

/**
 * Created by mike on 05.08.2016.
 */
public class OpenAPSAMAPlugin implements PluginBase, APSInterface {
    private static Logger log = LoggerFactory.getLogger(OpenAPSAMAPlugin.class);

    private static OpenAPSAMAPlugin openAPSAMAPlugin;

    public static OpenAPSAMAPlugin getPlugin() {
        if (openAPSAMAPlugin == null) {
            openAPSAMAPlugin = new OpenAPSAMAPlugin();
        }
        return openAPSAMAPlugin;
    }

    // last values
    DetermineBasalAdapterAMAJS lastDetermineBasalAdapterAMAJS = null;
    Date lastAPSRun = null;
    DetermineBasalResultAMA lastAPSResult = null;
    AutosensResult lastAutosensResult = null;

    private boolean fragmentEnabled = false;
    private boolean fragmentVisible = false;

    @Override
    public String getName() {
        return MainApp.instance().getString(R.string.openapsama);
    }

    @Override
    public String getNameShort() {
        String name = MainApp.sResources.getString(R.string.oaps_shortname);
        if (!name.trim().isEmpty()) {
            //only if translation exists
            return name;
        }
        // use long name as fallback
        return getName();
    }

    @Override
    public boolean isEnabled(int type) {
        boolean pumpCapable = ConfigBuilderPlugin.getActivePump() == null || ConfigBuilderPlugin.getActivePump().getPumpDescription().isTempBasalCapable;
        return type == APS && fragmentEnabled && pumpCapable;
    }

    @Override
    public boolean isVisibleInTabs(int type) {
        boolean pumpCapable = ConfigBuilderPlugin.getActivePump() == null || ConfigBuilderPlugin.getActivePump().getPumpDescription().isTempBasalCapable;
        return type == APS && fragmentVisible && pumpCapable;
    }

    @Override
    public boolean canBeHidden(int type) {
        return true;
    }

    @Override
    public boolean hasFragment() {
        return true;
    }

    @Override
    public boolean showInList(int type) {
        return true;
    }

    @Override
    public void setFragmentVisible(int type, boolean fragmentVisible) {
        if (type == APS) this.fragmentVisible = fragmentVisible;
    }

    @Override
    public int getPreferencesId() {
        return R.xml.pref_openapsama;
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
        DetermineBasalAdapterAMAJS determineBasalAdapterAMAJS;
        try {
            determineBasalAdapterAMAJS = new DetermineBasalAdapterAMAJS(new ScriptReader(MainApp.instance().getBaseContext()));
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            return;
        }

        GlucoseStatus glucoseStatus = GlucoseStatus.getGlucoseStatusData();
        Profile profile = MainApp.getConfigBuilder().getProfile();
        PumpInterface pump = ConfigBuilderPlugin.getActivePump();

        if (profile == null) {
            MainApp.bus().post(new EventOpenAPSUpdateResultGui(MainApp.instance().getString(R.string.noprofileselected)));
            if (Config.logAPSResult)
                log.debug(MainApp.instance().getString(R.string.noprofileselected));
            return;
        }

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

        String units = profile.getUnits();

        double maxIob = SP.getDouble("openapsma_max_iob", 1.5d);
        double maxBasal = SP.getDouble("openapsma_max_basal", 1d);
        double minBg = Profile.toMgdl(profile.getTargetLow(), units);
        double maxBg = Profile.toMgdl(profile.getTargetHigh(), units);
        double targetBg = Profile.toMgdl(profile.getTarget(), units);

        minBg = Round.roundTo(minBg, 0.1d);
        maxBg = Round.roundTo(maxBg, 0.1d);

        Date start = new Date();
        Date startPart = new Date();
        IobTotal[] iobArray = IobCobCalculatorPlugin.getPlugin().calculateIobArrayInDia();
        Profiler.log(log, "calculateIobArrayInDia()", startPart);

        startPart = new Date();
        MealData mealData = MainApp.getConfigBuilder().getMealData();
        Profiler.log(log, "getMealData()", startPart);

        maxIob = MainApp.getConstraintChecker().applyMaxIOBConstraints(maxIob);

        minBg = HardLimits.verifyHardLimits(minBg, "minBg", HardLimits.VERY_HARD_LIMIT_MIN_BG[0], HardLimits.VERY_HARD_LIMIT_MIN_BG[1]);
        maxBg = HardLimits.verifyHardLimits(maxBg, "maxBg", HardLimits.VERY_HARD_LIMIT_MAX_BG[0], HardLimits.VERY_HARD_LIMIT_MAX_BG[1]);
        targetBg = HardLimits.verifyHardLimits(targetBg, "targetBg", HardLimits.VERY_HARD_LIMIT_TARGET_BG[0], HardLimits.VERY_HARD_LIMIT_TARGET_BG[1]);

        boolean isTempTarget = false;
        TempTarget tempTarget = MainApp.getConfigBuilder().getTempTargetFromHistory(System.currentTimeMillis());
        if (tempTarget != null) {
            isTempTarget = true;
            minBg = HardLimits.verifyHardLimits(tempTarget.low, "minBg", HardLimits.VERY_HARD_LIMIT_TEMP_MIN_BG[0], HardLimits.VERY_HARD_LIMIT_TEMP_MIN_BG[1]);
            maxBg = HardLimits.verifyHardLimits(tempTarget.high, "maxBg", HardLimits.VERY_HARD_LIMIT_TEMP_MAX_BG[0], HardLimits.VERY_HARD_LIMIT_TEMP_MAX_BG[1]);
            targetBg = HardLimits.verifyHardLimits(tempTarget.target(), "targetBg", HardLimits.VERY_HARD_LIMIT_TEMP_TARGET_BG[0], HardLimits.VERY_HARD_LIMIT_TEMP_TARGET_BG[1]);
        }


        maxIob = HardLimits.verifyHardLimits(maxIob, "maxIob", 0, HardLimits.maxIobAMA());
        maxBasal = HardLimits.verifyHardLimits(maxBasal, "max_basal", 0.1, HardLimits.maxBasal());

        if (!HardLimits.checkOnlyHardLimits(profile.getDia(), "dia", HardLimits.MINDIA, HardLimits.MAXDIA))
            return;
        if (!HardLimits.checkOnlyHardLimits(profile.getIcTimeFromMidnight(profile.secondsFromMidnight()), "carbratio", HardLimits.MINIC, HardLimits.MAXIC))
            return;
        if (!HardLimits.checkOnlyHardLimits(Profile.toMgdl(profile.getIsf(), units), "sens", HardLimits.MINISF, HardLimits.MAXISF))
            return;
        if (!HardLimits.checkOnlyHardLimits(profile.getMaxDailyBasal(), "max_daily_basal", 0.05, HardLimits.maxBasal()))
            return;
        if (!HardLimits.checkOnlyHardLimits(pump.getBaseBasalRate(), "current_basal", 0.01, HardLimits.maxBasal()))
            return;

        startPart = new Date();
        if (MainApp.getConstraintChecker().isAMAModeEnabled()) {
            lastAutosensResult = IobCobCalculatorPlugin.getPlugin().detectSensitivityWithLock(IobCobCalculatorPlugin.oldestDataAvailable(), System.currentTimeMillis());
        } else {
            lastAutosensResult = new AutosensResult();
        }
        Profiler.log(log, "detectSensitivityandCarbAbsorption()", startPart);
        Profiler.log(log, "AMA data gathering", start);

        start = new Date();

        try {
            determineBasalAdapterAMAJS.setData(profile, maxIob, maxBasal, minBg, maxBg, targetBg, ConfigBuilderPlugin.getActivePump().getBaseBasalRate(), iobArray, glucoseStatus, mealData,
                    lastAutosensResult.ratio, //autosensDataRatio
                    isTempTarget
            );
        } catch (JSONException e) {
            log.error("Unable to set data: " + e.toString());
        }


        DetermineBasalResultAMA determineBasalResultAMA = determineBasalAdapterAMAJS.invoke();
        Profiler.log(log, "AMA calculation", start);
        // Fix bug determine basal
        if (determineBasalResultAMA.rate == 0d && determineBasalResultAMA.duration == 0 && !MainApp.getConfigBuilder().isTempBasalInProgress())
            determineBasalResultAMA.tempBasalReqested = false;
        // limit requests on openloop mode
        if (!MainApp.getConstraintChecker().limitClosedLoop(new Constraint<>(true)).get()) {
            long now = System.currentTimeMillis();
            TemporaryBasal activeTemp = MainApp.getConfigBuilder().getTempBasalFromHistory(now);
            if (activeTemp != null && determineBasalResultAMA.rate == 0 && determineBasalResultAMA.duration == 0) {
                // going to cancel
            } else if (activeTemp != null && Math.abs(determineBasalResultAMA.rate - activeTemp.tempBasalConvertedToAbsolute(now, profile)) < 0.1) {
                determineBasalResultAMA.tempBasalReqested = false;
            } else if (activeTemp == null && Math.abs(determineBasalResultAMA.rate - ConfigBuilderPlugin.getActivePump().getBaseBasalRate()) < 0.1)
                determineBasalResultAMA.tempBasalReqested = false;
        }

        determineBasalResultAMA.iob = iobArray[0];

        Date now = new Date();

        try {
            determineBasalResultAMA.json.put("timestamp", DateUtil.toISOString(now));
        } catch (JSONException e) {
            log.error("Unhandled exception", e);
        }

        lastDetermineBasalAdapterAMAJS = determineBasalAdapterAMAJS;
        lastAPSResult = determineBasalResultAMA;
        lastAPSRun = now;
        MainApp.bus().post(new EventOpenAPSUpdateGui());

        //deviceStatus.suggested = determineBasalResultAMA.json;
    }

}

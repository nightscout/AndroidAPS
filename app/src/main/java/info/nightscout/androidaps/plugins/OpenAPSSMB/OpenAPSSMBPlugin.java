package info.nightscout.androidaps.plugins.OpenAPSSMB;

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
import info.nightscout.androidaps.interfaces.Constraint;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.PluginDescription;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.IobCobCalculator.AutosensResult;
import info.nightscout.androidaps.plugins.IobCobCalculator.IobCobCalculatorPlugin;
import info.nightscout.androidaps.plugins.Loop.APSResult;
import info.nightscout.androidaps.plugins.Loop.ScriptReader;
import info.nightscout.androidaps.plugins.OpenAPSMA.events.EventOpenAPSUpdateGui;
import info.nightscout.androidaps.plugins.OpenAPSMA.events.EventOpenAPSUpdateResultGui;
import info.nightscout.androidaps.plugins.Treatments.TreatmentsPlugin;
import info.nightscout.utils.DateUtil;
import info.nightscout.utils.HardLimits;
import info.nightscout.utils.NSUpload;
import info.nightscout.utils.Profiler;
import info.nightscout.utils.Round;
import info.nightscout.utils.ToastUtils;

/**
 * Created by mike on 05.08.2016.
 */
public class OpenAPSSMBPlugin extends PluginBase implements APSInterface {
    private static Logger log = LoggerFactory.getLogger(OpenAPSSMBPlugin.class);

    private static OpenAPSSMBPlugin openAPSSMBPlugin;

    public static OpenAPSSMBPlugin getPlugin() {
        if (openAPSSMBPlugin == null) {
            openAPSSMBPlugin = new OpenAPSSMBPlugin();
        }
        return openAPSSMBPlugin;
    }

    // last values
    DetermineBasalAdapterSMBJS lastDetermineBasalAdapterSMBJS = null;
    Date lastAPSRun = null;
    DetermineBasalResultSMB lastAPSResult = null;
    AutosensResult lastAutosensResult = null;

    private OpenAPSSMBPlugin() {
        super(new PluginDescription()
                .mainType(PluginType.APS)
                .fragmentClass(OpenAPSSMBFragment.class.getName())
                .pluginName(R.string.openapssmb)
                .shortName(R.string.smb_shortname)
                .preferencesId(R.xml.pref_openapssmb)
        );
    }

    @Override
    public boolean specialEnableCondition() {
        PumpInterface pump = ConfigBuilderPlugin.getActivePump();
        return pump == null || pump.getPumpDescription().isTempBasalCapable;
    }

    @Override
    public boolean specialShowInListCondition() {
        PumpInterface pump = ConfigBuilderPlugin.getActivePump();
        return pump == null || pump.getPumpDescription().isTempBasalCapable;
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
    public void invoke(String initiator, boolean tempBasalFallback) {
        log.debug("invoke from " + initiator + " tempBasalFallback: "  + tempBasalFallback);
        lastAPSResult = null;
        DetermineBasalAdapterSMBJS determineBasalAdapterSMBJS = null;
        try {
            determineBasalAdapterSMBJS = new DetermineBasalAdapterSMBJS(new ScriptReader(MainApp.instance().getBaseContext()));
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            return;
        }

        GlucoseStatus glucoseStatus = GlucoseStatus.getGlucoseStatusData();
        Profile profile = MainApp.getConfigBuilder().getProfile();
        PumpInterface pump = ConfigBuilderPlugin.getActivePump();

        if (profile == null) {
            MainApp.bus().post(new EventOpenAPSUpdateResultGui(MainApp.gs(R.string.noprofileselected)));
            if (Config.logAPSResult)
                log.debug(MainApp.gs(R.string.noprofileselected));
            return;
        }

        if (!isEnabled(PluginType.APS)) {
            MainApp.bus().post(new EventOpenAPSUpdateResultGui(MainApp.gs(R.string.openapsma_disabled)));
            if (Config.logAPSResult)
                log.debug(MainApp.gs(R.string.openapsma_disabled));
            return;
        }

        if (glucoseStatus == null) {
            MainApp.bus().post(new EventOpenAPSUpdateResultGui(MainApp.gs(R.string.openapsma_noglucosedata)));
            if (Config.logAPSResult)
                log.debug(MainApp.gs(R.string.openapsma_noglucosedata));
            return;
        }

        String units = profile.getUnits();

        Constraint<Double> inputConstraints = new Constraint<>(0d); // fake. only for collecting all results

        Constraint<Double> maxBasalConstraint = MainApp.getConstraintChecker().getMaxBasalAllowed(profile);
        inputConstraints.copyReasons(maxBasalConstraint);
        double maxBasal = maxBasalConstraint.value();
        double minBg = Profile.toMgdl(profile.getTargetLow(), units);
        double maxBg = Profile.toMgdl(profile.getTargetHigh(), units);
        double targetBg = Profile.toMgdl(profile.getTarget(), units);

        minBg = Round.roundTo(minBg, 0.1d);
        maxBg = Round.roundTo(maxBg, 0.1d);

        Date start = new Date();
        Date startPart = new Date();
        IobTotal[] iobArray = IobCobCalculatorPlugin.getPlugin().calculateIobArrayForSMB(profile);
        Profiler.log(log, "calculateIobArrayInDia()", startPart);

        startPart = new Date();
        MealData mealData = TreatmentsPlugin.getPlugin().getMealData();
        Profiler.log(log, "getMealData()", startPart);

        double maxIob = MainApp.getConstraintChecker().getMaxIOBAllowed().value();

        minBg = verifyHardLimits(minBg, "minBg", HardLimits.VERY_HARD_LIMIT_MIN_BG[0], HardLimits.VERY_HARD_LIMIT_MIN_BG[1]);
        maxBg = verifyHardLimits(maxBg, "maxBg", HardLimits.VERY_HARD_LIMIT_MAX_BG[0], HardLimits.VERY_HARD_LIMIT_MAX_BG[1]);
        targetBg = verifyHardLimits(targetBg, "targetBg", HardLimits.VERY_HARD_LIMIT_TARGET_BG[0], HardLimits.VERY_HARD_LIMIT_TARGET_BG[1]);

        boolean isTempTarget = false;
        TempTarget tempTarget = TreatmentsPlugin.getPlugin().getTempTargetFromHistory(System.currentTimeMillis());
        if (tempTarget != null) {
            isTempTarget = true;
            minBg = verifyHardLimits(tempTarget.low, "minBg", HardLimits.VERY_HARD_LIMIT_TEMP_MIN_BG[0], HardLimits.VERY_HARD_LIMIT_TEMP_MIN_BG[1]);
            maxBg = verifyHardLimits(tempTarget.high, "maxBg", HardLimits.VERY_HARD_LIMIT_TEMP_MAX_BG[0], HardLimits.VERY_HARD_LIMIT_TEMP_MAX_BG[1]);
            targetBg = verifyHardLimits(tempTarget.target(), "targetBg", HardLimits.VERY_HARD_LIMIT_TEMP_TARGET_BG[0], HardLimits.VERY_HARD_LIMIT_TEMP_TARGET_BG[1]);
        }


        if (!checkOnlyHardLimits(profile.getDia(), "dia", HardLimits.MINDIA, HardLimits.MAXDIA))
            return;
        if (!checkOnlyHardLimits(profile.getIcTimeFromMidnight(profile.secondsFromMidnight()), "carbratio", HardLimits.MINIC, HardLimits.MAXIC))
            return;
        if (!checkOnlyHardLimits(Profile.toMgdl(profile.getIsf(), units), "sens", HardLimits.MINISF, HardLimits.MAXISF))
            return;
        if (!checkOnlyHardLimits(profile.getMaxDailyBasal(), "max_daily_basal", 0.05, HardLimits.maxBasal()))
            return;
        if (!checkOnlyHardLimits(pump.getBaseBasalRate(), "current_basal", 0.01, HardLimits.maxBasal()))
            return;

        startPart = new Date();
        if (MainApp.getConstraintChecker().isAutosensModeEnabled().value()) {
            lastAutosensResult = IobCobCalculatorPlugin.getPlugin().detectSensitivityWithLock(IobCobCalculatorPlugin.getPlugin().oldestDataAvailable(), System.currentTimeMillis());
        } else {
            lastAutosensResult = new AutosensResult();
        }

        Constraint<Boolean> smbAllowed = new Constraint<>(!tempBasalFallback);
        MainApp.getConstraintChecker().isSMBModeEnabled(smbAllowed);
        inputConstraints.copyReasons(smbAllowed);

        Constraint<Boolean> advancedFiltering = new Constraint<>(!tempBasalFallback);
        MainApp.getConstraintChecker().isAdvancedFilteringEnabled(advancedFiltering);
        inputConstraints.copyReasons(advancedFiltering);

        Profiler.log(log, "detectSensitivityandCarbAbsorption()", startPart);
        Profiler.log(log, "SMB data gathering", start);

        start = new Date();
        try {
            determineBasalAdapterSMBJS.setData(profile, maxIob, maxBasal, minBg, maxBg, targetBg, ConfigBuilderPlugin.getActivePump().getBaseBasalRate(), iobArray, glucoseStatus, mealData,
                    lastAutosensResult.ratio, //autosensDataRatio
                    isTempTarget,
                    smbAllowed.value(),
                    advancedFiltering.value()
            );
        } catch (JSONException e) {
            log.error(e.getMessage());
            return;
        }

        long now = System.currentTimeMillis();

        DetermineBasalResultSMB determineBasalResultSMB = determineBasalAdapterSMBJS.invoke();
        Profiler.log(log, "SMB calculation", start);
        // TODO still needed with oref1?
        // Fix bug determine basal
        if (determineBasalResultSMB.rate == 0d && determineBasalResultSMB.duration == 0 && !TreatmentsPlugin.getPlugin().isTempBasalInProgress())
            determineBasalResultSMB.tempBasalRequested = false;
        // limit requests on openloop mode
        if (!MainApp.getConstraintChecker().isClosedLoopAllowed().value()) {
            TemporaryBasal activeTemp = TreatmentsPlugin.getPlugin().getTempBasalFromHistory(now);
            if (activeTemp != null && determineBasalResultSMB.rate == 0 && determineBasalResultSMB.duration == 0) {
                // going to cancel
            } else if (activeTemp != null && Math.abs(determineBasalResultSMB.rate - activeTemp.tempBasalConvertedToAbsolute(now, profile)) < 0.1) {
                determineBasalResultSMB.tempBasalRequested = false;
            } else if (activeTemp == null && Math.abs(determineBasalResultSMB.rate - ConfigBuilderPlugin.getActivePump().getBaseBasalRate()) < 0.1)
                determineBasalResultSMB.tempBasalRequested = false;
        }

        determineBasalResultSMB.iob = iobArray[0];

        try {
            determineBasalResultSMB.json.put("timestamp", DateUtil.toISOString(now));
        } catch (JSONException e) {
            log.error("Unhandled exception", e);
        }

        determineBasalResultSMB.inputConstraints = inputConstraints;

        lastDetermineBasalAdapterSMBJS = determineBasalAdapterSMBJS;
        lastAPSResult = determineBasalResultSMB;
        lastAPSRun = new Date(now);
        MainApp.bus().post(new EventOpenAPSUpdateGui());

        //deviceStatus.suggested = determineBasalResultAMA.json;
    }

    // safety checks
    private static boolean checkOnlyHardLimits(Double value, String valueName, double lowLimit, double highLimit) {
        return value.equals(verifyHardLimits(value, valueName, lowLimit, highLimit));
    }

    private static Double verifyHardLimits(Double value, String valueName, double lowLimit, double highLimit) {
        Double newvalue = value;
        if (newvalue < lowLimit || newvalue > highLimit) {
            newvalue = Math.max(newvalue, lowLimit);
            newvalue = Math.min(newvalue, highLimit);
            String msg = String.format(MainApp.gs(R.string.valueoutofrange), valueName);
            msg += ".\n";
            msg += String.format(MainApp.gs(R.string.valuelimitedto), value, newvalue);
            log.error(msg);
            NSUpload.uploadError(msg);
            ToastUtils.showToastInUiThread(MainApp.instance().getApplicationContext(), msg, R.raw.error);
        }
        return newvalue;
    }

}

package info.nightscout.androidaps.plugins.aps.openAPSSMB;

import org.jetbrains.annotations.NotNull;
import org.json.JSONException;

import javax.inject.Inject;
import javax.inject.Singleton;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.IobTotal;
import info.nightscout.androidaps.data.MealData;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.db.TempTarget;
import info.nightscout.androidaps.interfaces.APSInterface;
import info.nightscout.androidaps.interfaces.Constraint;
import info.nightscout.androidaps.interfaces.ConstraintsInterface;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.PluginDescription;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.aps.loop.APSResult;
import info.nightscout.androidaps.plugins.aps.loop.ScriptReader;
import info.nightscout.androidaps.plugins.aps.openAPSMA.events.EventOpenAPSUpdateGui;
import info.nightscout.androidaps.plugins.aps.openAPSMA.events.EventOpenAPSUpdateResultGui;
import info.nightscout.androidaps.plugins.bus.RxBus;
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.configBuilder.ConstraintChecker;
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunctions;
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload;
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.AutosensData;
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.AutosensResult;
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.GlucoseStatus;
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.IobCobCalculatorPlugin;
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.FabricPrivacy;
import info.nightscout.androidaps.utils.HardLimits;
import info.nightscout.androidaps.utils.Profiler;
import info.nightscout.androidaps.utils.Round;
import info.nightscout.androidaps.utils.ToastUtils;

@Singleton
public class OpenAPSSMBPlugin extends PluginBase implements APSInterface, ConstraintsInterface {

    private static OpenAPSSMBPlugin openAPSSMBPlugin;
    private final AAPSLogger aapsLogger;

    /**
     * @deprecated Use dagger to get an instance
     */
    @Deprecated
    static public OpenAPSSMBPlugin getPlugin() {
        if (openAPSSMBPlugin == null)
            throw new IllegalStateException("Accessing OpenAPSSMBPlugin before first instantiation");
        return openAPSSMBPlugin;
    }

    // last values
    DetermineBasalAdapterSMBJS lastDetermineBasalAdapterSMBJS = null;
    long lastAPSRun = 0;
    DetermineBasalResultSMB lastAPSResult = null;
    AutosensResult lastAutosensResult = null;

    @Inject
    public OpenAPSSMBPlugin(AAPSLogger aapsLogger) {
        super(new PluginDescription()
                .mainType(PluginType.APS)
                .fragmentClass(OpenAPSSMBFragment.class.getName())
                .pluginName(R.string.openapssmb)
                .shortName(R.string.smb_shortname)
                .preferencesId(R.xml.pref_openapssmb)
                .description(R.string.description_smb)
        );
        this.openAPSSMBPlugin = this;  // TODO: only while transitioning to Dagger
        this.aapsLogger = aapsLogger;
    }

    @Override
    public boolean specialEnableCondition() {
        PumpInterface pump = ConfigBuilderPlugin.getPlugin().getActivePump();
        return pump == null || pump.getPumpDescription().isTempBasalCapable;
    }

    @Override
    public boolean specialShowInListCondition() {
        PumpInterface pump = ConfigBuilderPlugin.getPlugin().getActivePump();
        return pump == null || pump.getPumpDescription().isTempBasalCapable;
    }

    @Override
    public APSResult getLastAPSResult() {
        return lastAPSResult;
    }

    @Override
    public long getLastAPSRun() {
        return lastAPSRun;
    }

    @Override
    public void invoke(String initiator, boolean tempBasalFallback) {
            aapsLogger.debug(LTag.APS, "invoke from " + initiator + " tempBasalFallback: " + tempBasalFallback);
        lastAPSResult = null;
        DetermineBasalAdapterSMBJS determineBasalAdapterSMBJS;
        determineBasalAdapterSMBJS = new DetermineBasalAdapterSMBJS(new ScriptReader(MainApp.instance().getBaseContext()), aapsLogger);

        GlucoseStatus glucoseStatus = GlucoseStatus.getGlucoseStatusData();
        Profile profile = ProfileFunctions.getInstance().getProfile();
        PumpInterface pump = ConfigBuilderPlugin.getPlugin().getActivePump();

        if (profile == null) {
            RxBus.Companion.getINSTANCE().send(new EventOpenAPSUpdateResultGui(MainApp.gs(R.string.noprofileselected)));
                aapsLogger.debug(LTag.APS, MainApp.gs(R.string.noprofileselected));
            return;
        }

        if (pump == null) {
            RxBus.Companion.getINSTANCE().send(new EventOpenAPSUpdateResultGui(MainApp.gs(R.string.nopumpselected)));
                aapsLogger.debug(LTag.APS, MainApp.gs(R.string.nopumpselected));
            return;
        }

        if (!isEnabled(PluginType.APS)) {
            RxBus.Companion.getINSTANCE().send(new EventOpenAPSUpdateResultGui(MainApp.gs(R.string.openapsma_disabled)));
                aapsLogger.debug(LTag.APS, MainApp.gs(R.string.openapsma_disabled));
            return;
        }

        if (glucoseStatus == null) {
            RxBus.Companion.getINSTANCE().send(new EventOpenAPSUpdateResultGui(MainApp.gs(R.string.openapsma_noglucosedata)));
                aapsLogger.debug(LTag.APS, MainApp.gs(R.string.openapsma_noglucosedata));
            return;
        }

        Constraint<Double> inputConstraints = new Constraint<>(0d); // fake. only for collecting all results

        Constraint<Double> maxBasalConstraint = ConstraintChecker.getInstance().getMaxBasalAllowed(profile);
        inputConstraints.copyReasons(maxBasalConstraint);
        double maxBasal = maxBasalConstraint.value();
        double minBg = profile.getTargetLowMgdl();
        double maxBg = profile.getTargetHighMgdl();
        double targetBg = profile.getTargetMgdl();

        minBg = Round.roundTo(minBg, 0.1d);
        maxBg = Round.roundTo(maxBg, 0.1d);

        long start = System.currentTimeMillis();
        long startPart = System.currentTimeMillis();

        MealData mealData = TreatmentsPlugin.getPlugin().getMealData();
            Profiler.log(aapsLogger, LTag.APS, "getMealData()", startPart);

        Constraint<Double> maxIOBAllowedConstraint = ConstraintChecker.getInstance().getMaxIOBAllowed();
        inputConstraints.copyReasons(maxIOBAllowedConstraint);
        double maxIob = maxIOBAllowedConstraint.value();

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
        if (!checkOnlyHardLimits(profile.getIcTimeFromMidnight(Profile.secondsFromMidnight()), "carbratio", HardLimits.MINIC, HardLimits.MAXIC))
            return;
        if (!checkOnlyHardLimits(profile.getIsfMgdl(), "sens", HardLimits.MINISF, HardLimits.MAXISF))
            return;
        if (!checkOnlyHardLimits(profile.getMaxDailyBasal(), "max_daily_basal", 0.02, HardLimits.maxBasal()))
            return;
        if (!checkOnlyHardLimits(pump.getBaseBasalRate(), "current_basal", 0.01, HardLimits.maxBasal()))
            return;

        startPart = System.currentTimeMillis();
        if (ConstraintChecker.getInstance().isAutosensModeEnabled().value()) {
            AutosensData autosensData = IobCobCalculatorPlugin.getPlugin().getLastAutosensDataSynchronized("OpenAPSPlugin");
            if (autosensData == null) {
                RxBus.Companion.getINSTANCE().send(new EventOpenAPSUpdateResultGui(MainApp.gs(R.string.openaps_noasdata)));
                return;
            }
            lastAutosensResult = autosensData.autosensResult;
        } else {
            lastAutosensResult = new AutosensResult();
            lastAutosensResult.sensResult = "autosens disabled";
        }

        IobTotal[] iobArray = IobCobCalculatorPlugin.getPlugin().calculateIobArrayForSMB(lastAutosensResult, SMBDefaults.exercise_mode, SMBDefaults.half_basal_exercise_target, isTempTarget);
            Profiler.log(aapsLogger, LTag.APS, "calculateIobArrayInDia()", startPart);

        startPart = System.currentTimeMillis();
        Constraint<Boolean> smbAllowed = new Constraint<>(!tempBasalFallback);
        ConstraintChecker.getInstance().isSMBModeEnabled(smbAllowed);
        inputConstraints.copyReasons(smbAllowed);

        Constraint<Boolean> advancedFiltering = new Constraint<>(!tempBasalFallback);
        ConstraintChecker.getInstance().isAdvancedFilteringEnabled(advancedFiltering);
        inputConstraints.copyReasons(advancedFiltering);

        Constraint<Boolean> uam = new Constraint<>(true);
        ConstraintChecker.getInstance().isUAMEnabled(uam);
        inputConstraints.copyReasons(uam);

            Profiler.log(aapsLogger, LTag.APS, "detectSensitivityandCarbAbsorption()", startPart);
            Profiler.log(aapsLogger, LTag.APS, "SMB data gathering", start);

        start = System.currentTimeMillis();
        try {
            determineBasalAdapterSMBJS.setData(profile, maxIob, maxBasal, minBg, maxBg, targetBg, ConfigBuilderPlugin.getPlugin().getActivePump().getBaseBasalRate(), iobArray, glucoseStatus, mealData,
                    lastAutosensResult.ratio, //autosensDataRatio
                    isTempTarget,
                    smbAllowed.value(),
                    uam.value(),
                    advancedFiltering.value()
            );
        } catch (JSONException e) {
            FabricPrivacy.logException(e);
            return;
        }

        long now = System.currentTimeMillis();

        DetermineBasalResultSMB determineBasalResultSMB = determineBasalAdapterSMBJS.invoke();
            Profiler.log(aapsLogger, LTag.APS, "SMB calculation", start);
        if (determineBasalResultSMB == null) {
                aapsLogger.error(LTag.APS, "SMB calculation returned null");
            lastDetermineBasalAdapterSMBJS = null;
            lastAPSResult = null;
            lastAPSRun = 0;
        } else {
            // TODO still needed with oref1?
            // Fix bug determine basal
            if (determineBasalResultSMB.rate == 0d && determineBasalResultSMB.duration == 0 && !TreatmentsPlugin.getPlugin().isTempBasalInProgress())
                determineBasalResultSMB.tempBasalRequested = false;

            determineBasalResultSMB.iob = iobArray[0];

            try {
                determineBasalResultSMB.json.put("timestamp", DateUtil.toISOString(now));
            } catch (JSONException e) {
                aapsLogger.error(LTag.APS, "Unhandled exception", e);
            }

            determineBasalResultSMB.inputConstraints = inputConstraints;

            lastDetermineBasalAdapterSMBJS = determineBasalAdapterSMBJS;
            lastAPSResult = determineBasalResultSMB;
            lastAPSRun = now;
        }
        RxBus.Companion.getINSTANCE().send(new EventOpenAPSUpdateGui());

        //deviceStatus.suggested = determineBasalResultAMA.json;
    }

    // safety checks
    private boolean checkOnlyHardLimits(Double value, String valueName, double lowLimit, double highLimit) {
        return value.equals(verifyHardLimits(value, valueName, lowLimit, highLimit));
    }

    private Double verifyHardLimits(Double value, String valueName, double lowLimit, double highLimit) {
        Double newvalue = value;
        if (newvalue < lowLimit || newvalue > highLimit) {
            newvalue = Math.max(newvalue, lowLimit);
            newvalue = Math.min(newvalue, highLimit);
            String msg = String.format(MainApp.gs(R.string.valueoutofrange), valueName);
            msg += ".\n";
            msg += String.format(MainApp.gs(R.string.valuelimitedto), value, newvalue);
            aapsLogger.error(LTag.APS, msg);
            NSUpload.uploadError(msg);
            ToastUtils.showToastInUiThread(MainApp.instance().getApplicationContext(), msg, R.raw.error);
        }
        return newvalue;
    }

    @NotNull
    @Override
    public Constraint<Boolean> isSuperBolusEnabled(Constraint<Boolean> value) {
        value.set(false);
        return value;
    }

}

package info.nightscout.androidaps.plugins.aps.openAPSSMB;

import android.content.Context;

import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreference;

import org.jetbrains.annotations.NotNull;
import org.json.JSONException;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.android.HasAndroidInjector;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.IobTotal;
import info.nightscout.androidaps.data.MealData;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.db.TempTarget;
import info.nightscout.androidaps.interfaces.APSInterface;
import info.nightscout.androidaps.interfaces.ActivePluginProvider;
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
import info.nightscout.androidaps.plugins.aps.events.EventOpenAPSUpdateGui;
import info.nightscout.androidaps.plugins.aps.events.EventOpenAPSUpdateResultGui;
import info.nightscout.androidaps.plugins.bus.RxBusWrapper;
import info.nightscout.androidaps.plugins.configBuilder.ConstraintChecker;
import info.nightscout.androidaps.interfaces.ProfileFunction;
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.data.AutosensData;
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.AutosensResult;
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.GlucoseStatus;
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.IobCobCalculatorPlugin;
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.FabricPrivacy;
import info.nightscout.androidaps.utils.HardLimits;
import info.nightscout.androidaps.utils.Profiler;
import info.nightscout.androidaps.utils.Round;
import info.nightscout.androidaps.utils.resources.ResourceHelper;
import info.nightscout.androidaps.utils.sharedPreferences.SP;

@Singleton
public class OpenAPSSMBPlugin extends PluginBase implements APSInterface, ConstraintsInterface {
    private final ConstraintChecker constraintChecker;
    private final ResourceHelper resourceHelper;
    private final ProfileFunction profileFunction;
    private final Context context;
    private final RxBusWrapper rxBus;
    private final ActivePluginProvider activePlugin;
    private final TreatmentsPlugin treatmentsPlugin;
    private final IobCobCalculatorPlugin iobCobCalculatorPlugin;
    private final HardLimits hardLimits;
    private final Profiler profiler;
    private final FabricPrivacy fabricPrivacy;
    private final SP sp;

    // last values
    DetermineBasalAdapterSMBJS lastDetermineBasalAdapterSMBJS = null;
    long lastAPSRun = 0;
    DetermineBasalResultSMB lastAPSResult = null;
    AutosensResult lastAutosensResult = null;

    @Inject
    public OpenAPSSMBPlugin(
            HasAndroidInjector injector,
            AAPSLogger aapsLogger,
            RxBusWrapper rxBus,
            ConstraintChecker constraintChecker,
            ResourceHelper resourceHelper,
            ProfileFunction profileFunction,
            Context context,
            ActivePluginProvider activePlugin,
            TreatmentsPlugin treatmentsPlugin,
            IobCobCalculatorPlugin iobCobCalculatorPlugin,
            HardLimits hardLimits,
            Profiler profiler,
            FabricPrivacy fabricPrivacy,
            SP sp
    ) {
        super(new PluginDescription()
                        .mainType(PluginType.APS)
                        .fragmentClass(OpenAPSSMBFragment.class.getName())
                        .pluginName(R.string.openapssmb)
                        .shortName(R.string.smb_shortname)
                        .preferencesId(R.xml.pref_openapssmb)
                        .description(R.string.description_smb)
                        .setDefault(),
                aapsLogger, resourceHelper, injector
        );
        this.constraintChecker = constraintChecker;
        this.resourceHelper = resourceHelper;
        this.profileFunction = profileFunction;
        this.rxBus = rxBus;
        this.context = context;
        this.activePlugin = activePlugin;
        this.treatmentsPlugin = treatmentsPlugin;
        this.iobCobCalculatorPlugin = iobCobCalculatorPlugin;
        this.hardLimits = hardLimits;
        this.profiler = profiler;
        this.fabricPrivacy = fabricPrivacy;
        this.sp = sp;
    }

    @Override
    public boolean specialEnableCondition() {
        try {
            PumpInterface pump = activePlugin.getActivePump();
            return pump.getPumpDescription().isTempBasalCapable;
        } catch (Exception ignored) {
            // may fail during initialization
            return true;
        }
    }

    @Override
    public boolean specialShowInListCondition() {
        PumpInterface pump = activePlugin.getActivePump();
        return pump.getPumpDescription().isTempBasalCapable;
    }

    @Override
    public void preprocessPreferences(@NotNull PreferenceFragmentCompat preferenceFragment) {
        super.preprocessPreferences(preferenceFragment);
        boolean smbAlwaysEnabled = sp.getBoolean(R.string.key_enableSMB_always, false);

        SwitchPreference withCOB = preferenceFragment.findPreference(resourceHelper.gs(R.string.key_enableSMB_with_COB));
        if (withCOB != null) {
            withCOB.setVisible(!smbAlwaysEnabled);
        }
        SwitchPreference withTempTarget = preferenceFragment.findPreference(resourceHelper.gs(R.string.key_enableSMB_with_temptarget));
        if (withTempTarget != null) {
            withTempTarget.setVisible(!smbAlwaysEnabled);
        }
        SwitchPreference afterCarbs = preferenceFragment.findPreference(resourceHelper.gs(R.string.key_enableSMB_after_carbs));
        if (afterCarbs != null) {
            afterCarbs.setVisible(!smbAlwaysEnabled);
        }
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
        getAapsLogger().debug(LTag.APS, "invoke from " + initiator + " tempBasalFallback: " + tempBasalFallback);
        lastAPSResult = null;
        DetermineBasalAdapterSMBJS determineBasalAdapterSMBJS;
        determineBasalAdapterSMBJS = new DetermineBasalAdapterSMBJS(new ScriptReader(context), getInjector());

        GlucoseStatus glucoseStatus = new GlucoseStatus(getInjector()).getGlucoseStatusData();
        Profile profile = profileFunction.getProfile();
        PumpInterface pump = activePlugin.getActivePump();

        if (profile == null) {
            rxBus.send(new EventOpenAPSUpdateResultGui(resourceHelper.gs(R.string.noprofileselected)));
            getAapsLogger().debug(LTag.APS, resourceHelper.gs(R.string.noprofileselected));
            return;
        }

        if (!isEnabled(PluginType.APS)) {
            rxBus.send(new EventOpenAPSUpdateResultGui(resourceHelper.gs(R.string.openapsma_disabled)));
            getAapsLogger().debug(LTag.APS, resourceHelper.gs(R.string.openapsma_disabled));
            return;
        }

        if (glucoseStatus == null) {
            rxBus.send(new EventOpenAPSUpdateResultGui(resourceHelper.gs(R.string.openapsma_noglucosedata)));
            getAapsLogger().debug(LTag.APS, resourceHelper.gs(R.string.openapsma_noglucosedata));
            return;
        }

        Constraint<Double> inputConstraints = new Constraint<>(0d); // fake. only for collecting all results

        Constraint<Double> maxBasalConstraint = constraintChecker.getMaxBasalAllowed(profile);
        inputConstraints.copyReasons(maxBasalConstraint);
        double maxBasal = maxBasalConstraint.value();
        double minBg = profile.getTargetLowMgdl();
        double maxBg = profile.getTargetHighMgdl();
        double targetBg = profile.getTargetMgdl();

        minBg = Round.roundTo(minBg, 0.1d);
        maxBg = Round.roundTo(maxBg, 0.1d);

        long start = System.currentTimeMillis();
        long startPart = System.currentTimeMillis();

        MealData mealData = iobCobCalculatorPlugin.getMealData();
        profiler.log(LTag.APS, "getMealData()", startPart);

        Constraint<Double> maxIOBAllowedConstraint = constraintChecker.getMaxIOBAllowed();
        inputConstraints.copyReasons(maxIOBAllowedConstraint);
        double maxIob = maxIOBAllowedConstraint.value();

        minBg = hardLimits.verifyHardLimits(minBg, "minBg", hardLimits.getVERY_HARD_LIMIT_MIN_BG()[0], hardLimits.getVERY_HARD_LIMIT_MIN_BG()[1]);
        maxBg = hardLimits.verifyHardLimits(maxBg, "maxBg", hardLimits.getVERY_HARD_LIMIT_MAX_BG()[0], hardLimits.getVERY_HARD_LIMIT_MAX_BG()[1]);
        targetBg = hardLimits.verifyHardLimits(targetBg, "targetBg", hardLimits.getVERY_HARD_LIMIT_TARGET_BG()[0], hardLimits.getVERY_HARD_LIMIT_TARGET_BG()[1]);

        boolean isTempTarget = false;
        TempTarget tempTarget = treatmentsPlugin.getTempTargetFromHistory(System.currentTimeMillis());
        if (tempTarget != null) {
            isTempTarget = true;
            minBg = hardLimits.verifyHardLimits(tempTarget.low, "minBg", hardLimits.getVERY_HARD_LIMIT_TEMP_MIN_BG()[0], hardLimits.getVERY_HARD_LIMIT_TEMP_MIN_BG()[1]);
            maxBg = hardLimits.verifyHardLimits(tempTarget.high, "maxBg", hardLimits.getVERY_HARD_LIMIT_TEMP_MAX_BG()[0], hardLimits.getVERY_HARD_LIMIT_TEMP_MAX_BG()[1]);
            targetBg = hardLimits.verifyHardLimits(tempTarget.target(), "targetBg", hardLimits.getVERY_HARD_LIMIT_TEMP_TARGET_BG()[0], hardLimits.getVERY_HARD_LIMIT_TEMP_TARGET_BG()[1]);
        }


        if (!hardLimits.checkOnlyHardLimits(profile.getDia(), "dia", hardLimits.minDia(), hardLimits.maxDia()))
            return;
        if (!hardLimits.checkOnlyHardLimits(profile.getIcTimeFromMidnight(Profile.secondsFromMidnight()), "carbratio", hardLimits.minIC(), hardLimits.maxIC()))
            return;
        if (!hardLimits.checkOnlyHardLimits(profile.getIsfMgdl(), "sens", hardLimits.getMINISF(), hardLimits.getMAXISF()))
            return;
        if (!hardLimits.checkOnlyHardLimits(profile.getMaxDailyBasal(), "max_daily_basal", 0.02, hardLimits.maxBasal()))
            return;
        if (!hardLimits.checkOnlyHardLimits(pump.getBaseBasalRate(), "current_basal", 0.01, hardLimits.maxBasal()))
            return;

        startPart = System.currentTimeMillis();
        if (constraintChecker.isAutosensModeEnabled().value()) {
            AutosensData autosensData = iobCobCalculatorPlugin.getLastAutosensDataSynchronized("OpenAPSPlugin");
            if (autosensData == null) {
                rxBus.send(new EventOpenAPSUpdateResultGui(resourceHelper.gs(R.string.openaps_noasdata)));
                return;
            }
            lastAutosensResult = autosensData.autosensResult;
        } else {
            lastAutosensResult = new AutosensResult();
            lastAutosensResult.sensResult = "autosens disabled";
        }

        IobTotal[] iobArray = iobCobCalculatorPlugin.calculateIobArrayForSMB(lastAutosensResult, SMBDefaults.exercise_mode, SMBDefaults.half_basal_exercise_target, isTempTarget);
        profiler.log(LTag.APS, "calculateIobArrayInDia()", startPart);

        startPart = System.currentTimeMillis();
        Constraint<Boolean> smbAllowed = new Constraint<>(!tempBasalFallback);
        constraintChecker.isSMBModeEnabled(smbAllowed);
        inputConstraints.copyReasons(smbAllowed);

        Constraint<Boolean> advancedFiltering = new Constraint<>(!tempBasalFallback);
        constraintChecker.isAdvancedFilteringEnabled(advancedFiltering);
        inputConstraints.copyReasons(advancedFiltering);

        Constraint<Boolean> uam = new Constraint<>(true);
        constraintChecker.isUAMEnabled(uam);
        inputConstraints.copyReasons(uam);

        profiler.log(LTag.APS, "detectSensitivityandCarbAbsorption()", startPart);
        profiler.log(LTag.APS, "SMB data gathering", start);

        start = System.currentTimeMillis();
        try {
            determineBasalAdapterSMBJS.setData(profile, maxIob, maxBasal, minBg, maxBg, targetBg, activePlugin.getActivePump().getBaseBasalRate(), iobArray, glucoseStatus, mealData,
                    lastAutosensResult.ratio, //autosensDataRatio
                    isTempTarget,
                    smbAllowed.value(),
                    uam.value(),
                    advancedFiltering.value(),
                    activePlugin.getActiveBgSource().getClass().getSimpleName().equals("DexcomPlugin")
            );
        } catch (JSONException e) {
            fabricPrivacy.logException(e);
            return;
        }

        long now = System.currentTimeMillis();

        DetermineBasalResultSMB determineBasalResultSMB = determineBasalAdapterSMBJS.invoke();
        profiler.log(LTag.APS, "SMB calculation", start);
        if (determineBasalResultSMB == null) {
            getAapsLogger().error(LTag.APS, "SMB calculation returned null");
            lastDetermineBasalAdapterSMBJS = null;
            lastAPSResult = null;
            lastAPSRun = 0;
        } else {
            // TODO still needed with oref1?
            // Fix bug determine basal
            if (determineBasalResultSMB.rate == 0d && determineBasalResultSMB.duration == 0 && !treatmentsPlugin.isTempBasalInProgress())
                determineBasalResultSMB.tempBasalRequested = false;

            determineBasalResultSMB.iob = iobArray[0];

            try {
                determineBasalResultSMB.json.put("timestamp", DateUtil.toISOString(now));
            } catch (JSONException e) {
                getAapsLogger().error(LTag.APS, "Unhandled exception", e);
            }

            determineBasalResultSMB.inputConstraints = inputConstraints;

            lastDetermineBasalAdapterSMBJS = determineBasalAdapterSMBJS;
            lastAPSResult = determineBasalResultSMB;
            lastAPSRun = now;
        }
        rxBus.send(new EventOpenAPSUpdateGui());

        //deviceStatus.suggested = determineBasalResultAMA.json;
    }

    @NotNull
    @Override
    public Constraint<Boolean> isSuperBolusEnabled(Constraint<Boolean> value) {
        value.set(getAapsLogger(), false);
        return value;
    }

}

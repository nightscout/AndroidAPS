package info.nightscout.androidaps.plugins.aps.openAPSAMA;

import android.content.Context;

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

@Singleton
public class OpenAPSAMAPlugin extends PluginBase implements APSInterface {
    private final AAPSLogger aapsLogger;
    private final RxBusWrapper rxBus;
    private final ConstraintChecker constraintChecker;
    private final ResourceHelper resourceHelper;
    private final ProfileFunction profileFunction;
    private final Context context;
    private final ActivePluginProvider activePlugin;
    private final TreatmentsPlugin treatmentsPlugin;
    private final IobCobCalculatorPlugin iobCobCalculatorPlugin;
    private final HardLimits hardLimits;
    private final Profiler profiler;
    private final FabricPrivacy fabricPrivacy;

    // last values
    DetermineBasalAdapterAMAJS lastDetermineBasalAdapterAMAJS = null;
    long lastAPSRun = 0;
    DetermineBasalResultAMA lastAPSResult = null;
    AutosensResult lastAutosensResult = null;

    @Inject
    public OpenAPSAMAPlugin(
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
            FabricPrivacy fabricPrivacy
    ) {
        super(new PluginDescription()
                        .mainType(PluginType.APS)
                        .fragmentClass(OpenAPSAMAFragment.class.getName())
                        .pluginIcon(R.drawable.ic_generic_icon)
                        .pluginName(R.string.openapsama)
                        .shortName(R.string.oaps_shortname)
                        .preferencesId(R.xml.pref_openapsama)
                        .description(R.string.description_ama),
                aapsLogger, resourceHelper, injector
        );
        this.aapsLogger = aapsLogger;
        this.rxBus = rxBus;
        this.constraintChecker = constraintChecker;
        this.resourceHelper = resourceHelper;
        this.profileFunction = profileFunction;
        this.context = context;
        this.activePlugin = activePlugin;
        this.treatmentsPlugin = treatmentsPlugin;
        this.iobCobCalculatorPlugin = iobCobCalculatorPlugin;
        this.hardLimits = hardLimits;
        this.profiler = profiler;
        this.fabricPrivacy = fabricPrivacy;
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
        DetermineBasalAdapterAMAJS determineBasalAdapterAMAJS;
        determineBasalAdapterAMAJS = new DetermineBasalAdapterAMAJS(new ScriptReader(context), getInjector());

        GlucoseStatus glucoseStatus = new GlucoseStatus(getInjector()).getGlucoseStatusData();
        Profile profile = profileFunction.getProfile();
        PumpInterface pump = activePlugin.getActivePump();

        if (profile == null) {
            rxBus.send(new EventOpenAPSUpdateResultGui(resourceHelper.gs(R.string.noprofileselected)));
            aapsLogger.debug(LTag.APS, resourceHelper.gs(R.string.noprofileselected));
            return;
        }

        if (!isEnabled(PluginType.APS)) {
            rxBus.send(new EventOpenAPSUpdateResultGui(resourceHelper.gs(R.string.openapsma_disabled)));
            aapsLogger.debug(LTag.APS, resourceHelper.gs(R.string.openapsma_disabled));
            return;
        }

        if (glucoseStatus == null) {
            rxBus.send(new EventOpenAPSUpdateResultGui(resourceHelper.gs(R.string.openapsma_noglucosedata)));
            aapsLogger.debug(LTag.APS, resourceHelper.gs(R.string.openapsma_noglucosedata));
            return;
        }

        double maxBasal = constraintChecker.getMaxBasalAllowed(profile).value();
        double minBg = profile.getTargetLowMgdl();
        double maxBg = profile.getTargetHighMgdl();
        double targetBg = profile.getTargetMgdl();

        minBg = Round.roundTo(minBg, 0.1d);
        maxBg = Round.roundTo(maxBg, 0.1d);

        long start = System.currentTimeMillis();
        long startPart = System.currentTimeMillis();
        IobTotal[] iobArray = iobCobCalculatorPlugin.calculateIobArrayInDia(profile);
        profiler.log(LTag.APS, "calculateIobArrayInDia()", startPart);

        startPart = System.currentTimeMillis();
        MealData mealData = iobCobCalculatorPlugin.getMealData();
        profiler.log(LTag.APS, "getMealData()", startPart);

        double maxIob = constraintChecker.getMaxIOBAllowed().value();

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
        profiler.log(LTag.APS, "detectSensitivityandCarbAbsorption()", startPart);
        profiler.log(LTag.APS, "AMA data gathering", start);

        start = System.currentTimeMillis();

        try {
            determineBasalAdapterAMAJS.setData(profile, maxIob, maxBasal, minBg, maxBg, targetBg, activePlugin.getActivePump().getBaseBasalRate(), iobArray, glucoseStatus, mealData,
                    lastAutosensResult.ratio, //autosensDataRatio
                    isTempTarget
            );
        } catch (JSONException e) {
            fabricPrivacy.logException(e);
            return;
        }


        DetermineBasalResultAMA determineBasalResultAMA = determineBasalAdapterAMAJS.invoke();
        profiler.log(LTag.APS, "AMA calculation", start);
        // Fix bug determine basal
        if (determineBasalResultAMA == null) {
            aapsLogger.error(LTag.APS, "SMB calculation returned null");
            lastDetermineBasalAdapterAMAJS = null;
            lastAPSResult = null;
            lastAPSRun = 0;
        } else {
            if (determineBasalResultAMA.rate == 0d && determineBasalResultAMA.duration == 0 && !treatmentsPlugin.isTempBasalInProgress())
                determineBasalResultAMA.tempBasalRequested = false;

            determineBasalResultAMA.iob = iobArray[0];

            long now = System.currentTimeMillis();

            try {
                determineBasalResultAMA.json.put("timestamp", DateUtil.toISOString(now));
            } catch (JSONException e) {
                aapsLogger.error(LTag.APS, "Unhandled exception", e);
            }

            lastDetermineBasalAdapterAMAJS = determineBasalAdapterAMAJS;
            lastAPSResult = determineBasalResultAMA;
            lastAPSRun = now;
        }
        rxBus.send(new EventOpenAPSUpdateGui());

        //deviceStatus.suggested = determineBasalResultAMA.json;
    }

}

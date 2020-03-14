package info.nightscout.androidaps.plugins.aps.openAPSMA;

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
import info.nightscout.androidaps.plugins.aps.openAPSMA.events.EventOpenAPSUpdateGui;
import info.nightscout.androidaps.plugins.aps.openAPSMA.events.EventOpenAPSUpdateResultGui;
import info.nightscout.androidaps.plugins.bus.RxBusWrapper;
import info.nightscout.androidaps.plugins.configBuilder.ConstraintChecker;
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunction;
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
public class OpenAPSMAPlugin extends PluginBase implements APSInterface {
    private final RxBusWrapper rxBus;
    private final ConstraintChecker constraintChecker;
    private final ResourceHelper resourceHelper;
    private final ProfileFunction profileFunction;
    private final Context context;
    private final ActivePluginProvider activePlugin;
    private final TreatmentsPlugin treatmentsPlugin;
    private final IobCobCalculatorPlugin iobCobCalculatorPlugin;
    private final HardLimits hardLimits;

    // last values
    DetermineBasalAdapterMAJS lastDetermineBasalAdapterMAJS = null;
    long lastAPSRun = 0;
    DetermineBasalResultMA lastAPSResult = null;

    @Inject
    public OpenAPSMAPlugin(
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
            HardLimits hardLimits
    ) {
        super(new PluginDescription()
                        .mainType(PluginType.APS)
                        .fragmentClass(OpenAPSMAFragment.class.getName())
                        .pluginName(R.string.openapsma)
                        .shortName(R.string.oaps_shortname)
                        .preferencesId(R.xml.pref_openapsma)
                        .description(R.string.description_ma),
                aapsLogger, resourceHelper, injector
        );

        this.constraintChecker = constraintChecker;
        this.resourceHelper = resourceHelper;
        this.profileFunction = profileFunction;
        this.context = context;
        this.rxBus = rxBus;
        this.activePlugin = activePlugin;
        this.treatmentsPlugin = treatmentsPlugin;
        this.iobCobCalculatorPlugin = iobCobCalculatorPlugin;
        this.hardLimits = hardLimits;
    }

    @Override
    public boolean specialEnableCondition() {
        // main fail during init
        if (activePlugin != null) {
            PumpInterface pump = activePlugin.getActivePumpPlugin();
            return pump == null || pump.getPumpDescription().isTempBasalCapable;
        }
        return true;
    }

    @Override
    public boolean specialShowInListCondition() {
        PumpInterface pump = activePlugin.getActivePumpPlugin();
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
        getAapsLogger().debug(LTag.APS, "invoke from " + initiator + " tempBasalFallback: " + tempBasalFallback);
        lastAPSResult = null;
        DetermineBasalAdapterMAJS determineBasalAdapterMAJS;
        determineBasalAdapterMAJS = new DetermineBasalAdapterMAJS(new ScriptReader(context), getInjector());

        GlucoseStatus glucoseStatus = new GlucoseStatus(getInjector()).getGlucoseStatusData();
        Profile profile = profileFunction.getProfile();
        PumpInterface pump = activePlugin.getActivePumpPlugin();

        if (profile == null) {
            rxBus.send(new EventOpenAPSUpdateResultGui(resourceHelper.gs(R.string.noprofileselected)));
            getAapsLogger().debug(LTag.APS, resourceHelper.gs(R.string.noprofileselected));
            return;
        }

        if (pump == null) {
            rxBus.send(new EventOpenAPSUpdateResultGui(resourceHelper.gs(R.string.nopumpselected)));
            getAapsLogger().debug(LTag.APS, resourceHelper.gs(R.string.nopumpselected));
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

        double maxBasal = constraintChecker.getMaxBasalAllowed(profile).value();

        double minBg = profile.getTargetLowMgdl();
        double maxBg = profile.getTargetHighMgdl();
        double targetBg = profile.getTargetMgdl();

        minBg = Round.roundTo(minBg, 0.1d);
        maxBg = Round.roundTo(maxBg, 0.1d);

        long start = System.currentTimeMillis();
        treatmentsPlugin.updateTotalIOBTreatments();
        treatmentsPlugin.updateTotalIOBTempBasals();
        IobTotal bolusIob = treatmentsPlugin.getLastCalculationTreatments();
        IobTotal basalIob = treatmentsPlugin.getLastCalculationTempBasals();

        IobTotal iobTotal = IobTotal.combine(bolusIob, basalIob).round();

        MealData mealData = iobCobCalculatorPlugin.getMealData();

        double maxIob = constraintChecker.getMaxIOBAllowed().value();
        Profiler.log(getAapsLogger(), LTag.APS, "MA data gathering", start);

        minBg = hardLimits.verifyHardLimits(minBg, "minBg", hardLimits.getVERY_HARD_LIMIT_MIN_BG()[0], hardLimits.getVERY_HARD_LIMIT_MIN_BG()[1]);
        maxBg = hardLimits.verifyHardLimits(maxBg, "maxBg", hardLimits.getVERY_HARD_LIMIT_MAX_BG()[0], hardLimits.getVERY_HARD_LIMIT_MAX_BG()[1]);
        targetBg = hardLimits.verifyHardLimits(targetBg, "targetBg", hardLimits.getVERY_HARD_LIMIT_TARGET_BG()[0], hardLimits.getVERY_HARD_LIMIT_TARGET_BG()[1]);

        TempTarget tempTarget = treatmentsPlugin.getTempTargetFromHistory(System.currentTimeMillis());
        if (tempTarget != null) {
            minBg = hardLimits.verifyHardLimits(tempTarget.low, "minBg", hardLimits.getVERY_HARD_LIMIT_TEMP_MIN_BG()[0], hardLimits.getVERY_HARD_LIMIT_TEMP_MIN_BG()[1]);
            maxBg = hardLimits.verifyHardLimits(tempTarget.high, "maxBg", hardLimits.getVERY_HARD_LIMIT_TEMP_MAX_BG()[0], hardLimits.getVERY_HARD_LIMIT_TEMP_MAX_BG()[1]);
            targetBg = hardLimits.verifyHardLimits(tempTarget.target(), "targetBg", hardLimits.getVERY_HARD_LIMIT_TEMP_TARGET_BG()[0], hardLimits.getVERY_HARD_LIMIT_TEMP_TARGET_BG()[1]);
        }

        if (!hardLimits.checkOnlyHardLimits(profile.getDia(), "dia", hardLimits.getMINDIA(), hardLimits.getMAXDIA()))
            return;
        if (!hardLimits.checkOnlyHardLimits(profile.getIcTimeFromMidnight(Profile.secondsFromMidnight()), "carbratio", hardLimits.getMINIC(), hardLimits.getMAXIC()))
            return;
        if (!hardLimits.checkOnlyHardLimits(profile.getIsfMgdl(), "sens", hardLimits.getMINISF(), hardLimits.getMAXISF()))
            return;
        if (!hardLimits.checkOnlyHardLimits(profile.getMaxDailyBasal(), "max_daily_basal", 0.02, hardLimits.maxBasal()))
            return;
        if (!hardLimits.checkOnlyHardLimits(pump.getBaseBasalRate(), "current_basal", 0.01, hardLimits.maxBasal()))
            return;

        start = System.currentTimeMillis();
        try {
            determineBasalAdapterMAJS.setData(profile, maxIob, maxBasal, minBg, maxBg, targetBg, activePlugin.getActivePump().getBaseBasalRate(), iobTotal, glucoseStatus, mealData);
        } catch (JSONException e) {
            FabricPrivacy.getInstance().logException(e);
            return;
        }
        Profiler.log(getAapsLogger(), LTag.APS, "MA calculation", start);


        long now = System.currentTimeMillis();

        DetermineBasalResultMA determineBasalResultMA = determineBasalAdapterMAJS.invoke();
        if (determineBasalResultMA == null) {
            getAapsLogger().error(LTag.APS, "MA calculation returned null");
            lastDetermineBasalAdapterMAJS = null;
            lastAPSResult = null;
            lastAPSRun = 0;
        } else {
            // Fix bug determine basal
            if (determineBasalResultMA.rate == 0d && determineBasalResultMA.duration == 0 && !treatmentsPlugin.isTempBasalInProgress())
                determineBasalResultMA.tempBasalRequested = false;

            determineBasalResultMA.iob = iobTotal;

            try {
                determineBasalResultMA.json.put("timestamp", DateUtil.toISOString(now));
            } catch (JSONException e) {
                getAapsLogger().error(LTag.APS, "Unhandled exception", e);
            }

            lastDetermineBasalAdapterMAJS = determineBasalAdapterMAJS;
            lastAPSResult = determineBasalResultMA;
            lastAPSRun = now;
        }
        rxBus.send(new EventOpenAPSUpdateGui());
    }


}

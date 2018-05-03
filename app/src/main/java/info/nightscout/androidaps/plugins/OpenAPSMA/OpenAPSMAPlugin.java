package info.nightscout.androidaps.plugins.OpenAPSMA;

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
import info.nightscout.androidaps.interfaces.PluginDescription;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.Loop.APSResult;
import info.nightscout.androidaps.plugins.Loop.ScriptReader;
import info.nightscout.androidaps.plugins.OpenAPSMA.events.EventOpenAPSUpdateGui;
import info.nightscout.androidaps.plugins.OpenAPSMA.events.EventOpenAPSUpdateResultGui;
import info.nightscout.androidaps.plugins.Treatments.TreatmentsPlugin;
import info.nightscout.utils.DateUtil;
import info.nightscout.utils.HardLimits;
import info.nightscout.utils.Profiler;
import info.nightscout.utils.Round;

import static info.nightscout.utils.HardLimits.checkOnlyHardLimits;
import static info.nightscout.utils.HardLimits.verifyHardLimits;

/**
 * Created by mike on 05.08.2016.
 */
public class OpenAPSMAPlugin extends PluginBase implements APSInterface {
    private static Logger log = LoggerFactory.getLogger(OpenAPSMAPlugin.class);

    private static OpenAPSMAPlugin openAPSMAPlugin;

    public static OpenAPSMAPlugin getPlugin() {
        if (openAPSMAPlugin == null) {
            openAPSMAPlugin = new OpenAPSMAPlugin();
        }
        return openAPSMAPlugin;
    }

    // last values
    DetermineBasalAdapterMAJS lastDetermineBasalAdapterMAJS = null;
    Date lastAPSRun = null;
    DetermineBasalResultMA lastAPSResult = null;

    public OpenAPSMAPlugin() {
        super(new PluginDescription()
                .mainType(PluginType.APS)
                .fragmentClass(OpenAPSMAFragment.class.getName())
                .pluginName(R.string.openapsma)
                .shortName(R.string.oaps_shortname)
                .preferencesId(R.xml.pref_openapsma)
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
        DetermineBasalAdapterMAJS determineBasalAdapterMAJS = null;
        try {
            determineBasalAdapterMAJS = new DetermineBasalAdapterMAJS(new ScriptReader(MainApp.instance().getBaseContext()));
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

        double maxBasal = MainApp.getConstraintChecker().getMaxBasalAllowed(profile).value();

        double minBg = Profile.toMgdl(profile.getTargetLow(), units);
        double maxBg = Profile.toMgdl(profile.getTargetHigh(), units);
        double targetBg = Profile.toMgdl(profile.getTarget(), units);

        minBg = Round.roundTo(minBg, 0.1d);
        maxBg = Round.roundTo(maxBg, 0.1d);

        Date start = new Date();
        TreatmentsPlugin.getPlugin().updateTotalIOBTreatments();
        TreatmentsPlugin.getPlugin().updateTotalIOBTempBasals();
        IobTotal bolusIob = TreatmentsPlugin.getPlugin().getLastCalculationTreatments();
        IobTotal basalIob = TreatmentsPlugin.getPlugin().getLastCalculationTempBasals();

        IobTotal iobTotal = IobTotal.combine(bolusIob, basalIob).round();

        MealData mealData = TreatmentsPlugin.getPlugin().getMealData();

        double maxIob = MainApp.getConstraintChecker().getMaxIOBAllowed().value();
        Profiler.log(log, "MA data gathering", start);

        minBg = verifyHardLimits(minBg, "minBg", HardLimits.VERY_HARD_LIMIT_MIN_BG[0], HardLimits.VERY_HARD_LIMIT_MIN_BG[1]);
        maxBg = verifyHardLimits(maxBg, "maxBg", HardLimits.VERY_HARD_LIMIT_MAX_BG[0], HardLimits.VERY_HARD_LIMIT_MAX_BG[1]);
        targetBg = verifyHardLimits(targetBg, "targetBg", HardLimits.VERY_HARD_LIMIT_TARGET_BG[0], HardLimits.VERY_HARD_LIMIT_TARGET_BG[1]);

        TempTarget tempTarget = TreatmentsPlugin.getPlugin().getTempTargetFromHistory(System.currentTimeMillis());
        if (tempTarget != null) {
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

        start = new Date();
        try {
            determineBasalAdapterMAJS.setData(profile, maxIob, maxBasal, minBg, maxBg, targetBg, ConfigBuilderPlugin.getActivePump().getBaseBasalRate(), iobTotal, glucoseStatus, mealData);
        } catch (JSONException e) {
            log.error("Unhandled exception", e);
        }
        Profiler.log(log, "MA calculation", start);


        long now = System.currentTimeMillis();

        DetermineBasalResultMA determineBasalResultMA = determineBasalAdapterMAJS.invoke();
        // Fix bug determinef basal
        if (determineBasalResultMA.rate == 0d && determineBasalResultMA.duration == 0 && !TreatmentsPlugin.getPlugin().isTempBasalInProgress())
            determineBasalResultMA.tempBasalRequested = false;
        // limit requests on openloop mode
        if (!MainApp.getConstraintChecker().isClosedLoopAllowed().value()) {
            TemporaryBasal activeTemp = TreatmentsPlugin.getPlugin().getTempBasalFromHistory(now);
            if (activeTemp != null && determineBasalResultMA.rate == 0 && determineBasalResultMA.duration == 0) {
                // going to cancel
            } else if (activeTemp != null && Math.abs(determineBasalResultMA.rate - activeTemp.tempBasalConvertedToAbsolute(now, profile)) < 0.1) {
                determineBasalResultMA.tempBasalRequested = false;
            } else if (activeTemp == null && Math.abs(determineBasalResultMA.rate - ConfigBuilderPlugin.getActivePump().getBaseBasalRate()) < 0.1)
                determineBasalResultMA.tempBasalRequested = false;
        }

        determineBasalResultMA.iob = iobTotal;

        try {
            determineBasalResultMA.json.put("timestamp", DateUtil.toISOString(now));
        } catch (JSONException e) {
            log.error("Unhandled exception", e);
        }

        lastDetermineBasalAdapterMAJS = determineBasalAdapterMAJS;
        lastAPSResult = determineBasalResultMA;
        lastAPSRun = new Date(now);
        MainApp.bus().post(new EventOpenAPSUpdateGui());
    }


}

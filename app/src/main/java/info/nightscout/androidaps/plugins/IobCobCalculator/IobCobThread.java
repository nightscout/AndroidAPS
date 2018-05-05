package info.nightscout.androidaps.plugins.IobCobCalculator;

import android.content.Context;
import android.os.PowerManager;
import android.support.v4.util.LongSparseArray;

import com.crashlytics.android.answers.CustomEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import info.nightscout.androidaps.BuildConfig;
import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.IobTotal;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.db.BgReading;
import info.nightscout.androidaps.events.Event;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.plugins.IobCobCalculator.events.EventAutosensCalculationFinished;
import info.nightscout.androidaps.plugins.IobCobCalculator.events.EventIobCalculationProgress;
import info.nightscout.androidaps.plugins.OpenAPSSMB.SMBDefaults;
import info.nightscout.androidaps.plugins.SensitivityAAPS.SensitivityAAPSPlugin;
import info.nightscout.androidaps.plugins.SensitivityWeightedAverage.SensitivityWeightedAveragePlugin;
import info.nightscout.androidaps.plugins.Treatments.Treatment;
import info.nightscout.androidaps.plugins.Treatments.TreatmentsPlugin;
import info.nightscout.utils.DateUtil;
import info.nightscout.utils.FabricPrivacy;
import info.nightscout.utils.SP;

import static info.nightscout.utils.DateUtil.now;

/**
 * Created by mike on 23.01.2018.
 */

public class IobCobThread extends Thread {
    private static Logger log = LoggerFactory.getLogger(IobCobThread.class);
    private final Event cause;

    private IobCobCalculatorPlugin iobCobCalculatorPlugin;
    private boolean bgDataReload;
    private String from;
    private long start;

    private PowerManager.WakeLock mWakeLock;

    public IobCobThread(IobCobCalculatorPlugin plugin, String from, long start, boolean bgDataReload, Event cause) {
        super();

        this.iobCobCalculatorPlugin = plugin;
        this.bgDataReload = bgDataReload;
        this.from = from;
        this.cause = cause;
        this.start = start;

        PowerManager powerManager = (PowerManager) MainApp.instance().getApplicationContext().getSystemService(Context.POWER_SERVICE);
        mWakeLock = powerManager.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "iobCobThread");
    }

    @Override
    public final void run() {
        mWakeLock.acquire();
        try {
            if (MainApp.getConfigBuilder() == null) {
                log.debug("Aborting calculation thread (ConfigBuilder not ready): " + from);
                return; // app still initializing
            }
            if (!MainApp.getConfigBuilder().isProfileValid("IobCobThread")) {
                log.debug("Aborting calculation thread (No profile): " + from);
                return; // app still initializing
            }
            //log.debug("Locking calculateSensitivityData");

            long oldestTimeWithData = iobCobCalculatorPlugin.oldestDataAvailable();

            synchronized (iobCobCalculatorPlugin.dataLock) {
                if (bgDataReload) {
                    iobCobCalculatorPlugin.loadBgData(start);
                    iobCobCalculatorPlugin.createBucketedData();
                }
                List<BgReading> bucketed_data = iobCobCalculatorPlugin.getBucketedData();
                LongSparseArray<AutosensData> autosensDataTable = IobCobCalculatorPlugin.getPlugin().getAutosensDataTable();

                if (bucketed_data == null || bucketed_data.size() < 3) {
                    log.debug("Aborting calculation thread (No bucketed data available): " + from);
                    return;
                }

                long prevDataTime = IobCobCalculatorPlugin.roundUpTime(bucketed_data.get(bucketed_data.size() - 3).date);
                log.debug("Prev data time: " + new Date(prevDataTime).toLocaleString());
                AutosensData previous = autosensDataTable.get(prevDataTime);
                // start from oldest to be able sub cob
                for (int i = bucketed_data.size() - 4; i >= 0; i--) {
                    String progress = i + (MainApp.isDev() ? " (" + from + ")" : "");
                    MainApp.bus().post(new EventIobCalculationProgress(progress));

                    if (iobCobCalculatorPlugin.stopCalculationTrigger) {
                        iobCobCalculatorPlugin.stopCalculationTrigger = false;
                        log.debug("Aborting calculation thread (trigger): " + from);
                        return;
                    }
                    // check if data already exists
                    long bgTime = bucketed_data.get(i).date;
                    bgTime = IobCobCalculatorPlugin.roundUpTime(bgTime);
                    if (bgTime > IobCobCalculatorPlugin.roundUpTime(now()))
                        continue;

                    AutosensData existing;
                    if ((existing = autosensDataTable.get(bgTime)) != null) {
                        previous = existing;
                        continue;
                    }

                    Profile profile = MainApp.getConfigBuilder().getProfile(bgTime);
                    if (profile == null) {
                        log.debug("Aborting calculation thread (no profile): " + from);
                        return; // profile not set yet
                    }

                    if (Config.logAutosensData)
                        log.debug("Processing calculation thread: " + from + " (" + i + "/" + bucketed_data.size() + ")");

                    double sens = Profile.toMgdl(profile.getIsf(bgTime), profile.getUnits());

                    AutosensData autosensData = new AutosensData();
                    autosensData.time = bgTime;
                    if (previous != null)
                        autosensData.activeCarbsList = new ArrayList<>(previous.activeCarbsList);
                    else
                        autosensData.activeCarbsList = new ArrayList<>();

                    //console.error(bgTime , bucketed_data[i].glucose);
                    double bg;
                    double avgDelta;
                    double delta;
                    bg = bucketed_data.get(i).value;
                    if (bg < 39 || bucketed_data.get(i + 3).value < 39) {
                        log.error("! value < 39");
                        continue;
                    }
                    delta = (bg - bucketed_data.get(i + 1).value);
                    avgDelta = (bg - bucketed_data.get(i + 3).value) / 3;

                    IobTotal iob = iobCobCalculatorPlugin.calculateFromTreatmentsAndTemps(bgTime, profile);

                    double bgi = -iob.activity * sens * 5;
                    double deviation = delta - bgi;
                    double avgDeviation = Math.round((avgDelta - bgi) * 1000) / 1000;

                    double slopeFromMaxDeviation = 0;
                    double slopeFromMinDeviation = 999;
                    double maxDeviation = 0;
                    double minDeviation = 999;

                    // https://github.com/openaps/oref0/blob/master/lib/determine-basal/cob-autosens.js#L169
                    if (i < bucketed_data.size() - 16) { // we need 1h of data to calculate minDeviationSlope
                        long hourago = bgTime + 10 * 1000 - 60 * 60 * 1000L;
                        AutosensData hourAgoData = IobCobCalculatorPlugin.getPlugin().getAutosensData(hourago);
                        if (hourAgoData != null) {
                            int initialIndex = autosensDataTable.indexOfKey(hourAgoData.time);
                            if (Config.logAutosensData)
                                log.debug(">>>>> bucketed_data.size()=" + bucketed_data.size() + " i=" + i + "hourAgoData=" + hourAgoData.toString());
                            int past = 1;
                            try {
                                for (; past < 12; past++) {
                                    AutosensData ad = autosensDataTable.valueAt(initialIndex + past);
                                    double deviationSlope = (ad.avgDeviation - avgDeviation) / (ad.time - bgTime) * 1000 * 60 * 5;
                                    if (ad.avgDeviation > maxDeviation) {
                                        slopeFromMaxDeviation = Math.min(0, deviationSlope);
                                        maxDeviation = ad.avgDeviation;
                                    }
                                    if (ad.avgDeviation < minDeviation) {
                                        slopeFromMinDeviation = Math.max(0, deviationSlope);
                                        minDeviation = ad.avgDeviation;
                                    }

                                    //if (Config.logAutosensData)
                                    //    log.debug("Deviations: " + new Date(bgTime) + new Date(ad.time) + " avgDeviation=" + avgDeviation + " deviationSlope=" + deviationSlope + " slopeFromMaxDeviation=" + slopeFromMaxDeviation + " slopeFromMinDeviation=" + slopeFromMinDeviation);
                                }
                            } catch (Exception e) {
                                log.error("Unhandled exception", e);
                                FabricPrivacy.logException(e);
                                FabricPrivacy.getInstance().logCustom(new CustomEvent("CatchedError")
                                        .putCustomAttribute("buildversion", BuildConfig.BUILDVERSION)
                                        .putCustomAttribute("version", BuildConfig.VERSION)
                                        .putCustomAttribute("autosensDataTable", iobCobCalculatorPlugin.getAutosensDataTable().toString())
                                        .putCustomAttribute("for_data", ">>>>> bucketed_data.size()=" + bucketed_data.size() + " i=" + i + "hourAgoData=" + hourAgoData.toString())
                                        .putCustomAttribute("past", past)
                                );
                            }
                        }
                    }

                    List<Treatment> recentTreatments = TreatmentsPlugin.getPlugin().getTreatments5MinBackFromHistory(bgTime);
                    for (int ir = 0; ir < recentTreatments.size(); ir++) {
                        autosensData.carbsFromBolus += recentTreatments.get(ir).carbs;
                        autosensData.activeCarbsList.add(new AutosensData.CarbsInPast(recentTreatments.get(ir)));
                    }


                    // if we are absorbing carbs
                    if (previous != null && previous.cob > 0) {
                        // calculate sum of min carb impact from all active treatments
                        double totalMinCarbsImpact = 0d;
                        if (SensitivityAAPSPlugin.getPlugin().isEnabled(PluginType.SENSITIVITY) || SensitivityWeightedAveragePlugin.getPlugin().isEnabled(PluginType.SENSITIVITY)) {
                            //when the impact depends on a max time, sum them up as smaller carb sizes make them smaller
                            for (int ii = 0; ii < autosensData.activeCarbsList.size(); ++ii) {
                                AutosensData.CarbsInPast c = autosensData.activeCarbsList.get(ii);
                                totalMinCarbsImpact += c.min5minCarbImpact;
                            }
                        } else {
                            //Oref sensitivity
                            totalMinCarbsImpact = SP.getDouble(R.string.key_openapsama_min_5m_carbimpact, SMBDefaults.min_5m_carbimpact);
                        }

                        // figure out how many carbs that represents
                        // but always assume at least 3mg/dL/5m (default) absorption per active treatment
                        double ci = Math.max(deviation, totalMinCarbsImpact);
                        if (ci != deviation)
                            autosensData.failoverToMinAbsorbtionRate = true;
                        autosensData.absorbed = ci * profile.getIc(bgTime) / sens;
                        // and add that to the running total carbsAbsorbed
                        autosensData.cob = Math.max(previous.cob - autosensData.absorbed, 0d);
                        autosensData.substractAbosorbedCarbs();
                        autosensData.usedMinCarbsImpact = totalMinCarbsImpact;
                    }
                    autosensData.removeOldCarbs(bgTime);
                    autosensData.cob += autosensData.carbsFromBolus;
                    autosensData.deviation = deviation;
                    autosensData.bgi = bgi;
                    autosensData.delta = delta;
                    autosensData.avgDelta = avgDelta;
                    autosensData.avgDeviation = avgDeviation;
                    autosensData.slopeFromMaxDeviation = slopeFromMaxDeviation;
                    autosensData.slopeFromMinDeviation = slopeFromMinDeviation;


                    // calculate autosens only without COB
                    if (autosensData.cob <= 0) {
                        if (Math.abs(deviation) < Constants.DEVIATION_TO_BE_EQUAL) {
                            autosensData.pastSensitivity += "=";
                            autosensData.nonEqualDeviation = true;
                        } else if (deviation > 0) {
                            autosensData.pastSensitivity += "+";
                            autosensData.nonEqualDeviation = true;
                        } else {
                            autosensData.pastSensitivity += "-";
                            autosensData.nonEqualDeviation = true;
                        }
                        autosensData.nonCarbsDeviation = true;
                    } else {
                        autosensData.pastSensitivity += "C";
                    }
                    //log.debug("TIME: " + new Date(bgTime).toString() + " BG: " + bg + " SENS: " + sens + " DELTA: " + delta + " AVGDELTA: " + avgDelta + " IOB: " + iob.iob + " ACTIVITY: " + iob.activity + " BGI: " + bgi + " DEVIATION: " + deviation);

                    previous = autosensData;
                    autosensDataTable.put(bgTime, autosensData);
                    if (Config.logAutosensData)
                        log.debug("Running detectSensitivity from: " + DateUtil.dateAndTimeString(oldestTimeWithData) + " to: " + DateUtil.dateAndTimeString(bgTime));
                    autosensData.autosensRatio = iobCobCalculatorPlugin.detectSensitivity(oldestTimeWithData, bgTime).ratio;
                    if (Config.logAutosensData)
                        log.debug(autosensData.toString());
                }
            }
            MainApp.bus().post(new EventAutosensCalculationFinished(cause));
            log.debug("Finishing calculation thread: " + from);
        } finally {
            mWakeLock.release();
            MainApp.bus().post(new EventIobCalculationProgress(""));
        }
    }

}

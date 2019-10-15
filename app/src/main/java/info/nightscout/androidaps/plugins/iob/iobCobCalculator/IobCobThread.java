package info.nightscout.androidaps.plugins.iob.iobCobCalculator;

import android.content.Context;
import android.os.PowerManager;
import android.os.SystemClock;

import androidx.collection.LongSparseArray;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.IobTotal;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.db.BgReading;
import info.nightscout.androidaps.events.Event;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.aps.openAPSSMB.SMBDefaults;
import info.nightscout.androidaps.plugins.bus.RxBus;
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunctions;
import info.nightscout.androidaps.plugins.general.overview.events.EventNewNotification;
import info.nightscout.androidaps.plugins.general.overview.notifications.Notification;
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.events.EventAutosensCalculationFinished;
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.events.EventIobCalculationProgress;
import info.nightscout.androidaps.plugins.sensitivity.SensitivityAAPSPlugin;
import info.nightscout.androidaps.plugins.sensitivity.SensitivityWeightedAveragePlugin;
import info.nightscout.androidaps.plugins.treatments.Treatment;
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.DecimalFormatter;
import info.nightscout.androidaps.utils.FabricPrivacy;
import info.nightscout.androidaps.utils.MidnightTime;
import info.nightscout.androidaps.utils.Profiler;
import info.nightscout.androidaps.utils.SP;
import info.nightscout.androidaps.utils.T;

import static info.nightscout.androidaps.utils.DateUtil.now;

/**
 * Created by mike on 23.01.2018.
 */

public class IobCobThread extends Thread {
    private static Logger log = LoggerFactory.getLogger(L.AUTOSENS);
    private final Event cause;

    private IobCobCalculatorPlugin iobCobCalculatorPlugin;
    private boolean bgDataReload;
    private boolean limitDataToOldestAvailable;
    private String from;
    private long end;

    private PowerManager.WakeLock mWakeLock;

    IobCobThread(IobCobCalculatorPlugin plugin, String from, long end, boolean bgDataReload, boolean limitDataToOldestAvailable, Event cause) {
        super();

        this.iobCobCalculatorPlugin = plugin;
        this.bgDataReload = bgDataReload;
        this.limitDataToOldestAvailable = limitDataToOldestAvailable;
        this.from = from;
        this.cause = cause;
        this.end = end;

        PowerManager powerManager = (PowerManager) MainApp.instance().getApplicationContext().getSystemService(Context.POWER_SERVICE);
        if (powerManager != null)
            mWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, MainApp.gs(R.string.app_name) + ":iobCobThread");
    }

    @Override
    public final void run() {
        long start = DateUtil.now();
        if (mWakeLock != null)
            mWakeLock.acquire(T.mins(10).msecs());
        try {
            if (L.isEnabled(L.AUTOSENS))
                log.debug("AUTOSENSDATA thread started: " + from);
            if (ConfigBuilderPlugin.getPlugin() == null) {
                if (L.isEnabled(L.AUTOSENS))
                    log.debug("Aborting calculation thread (ConfigBuilder not ready): " + from);
                return; // app still initializing
            }
            if (!ProfileFunctions.getInstance().isProfileValid("IobCobThread")) {
                if (L.isEnabled(L.AUTOSENS))
                    log.debug("Aborting calculation thread (No profile): " + from);
                return; // app still initializing
            }
            //log.debug("Locking calculateSensitivityData");

            long oldestTimeWithData = iobCobCalculatorPlugin.calculateDetectionStart(end, limitDataToOldestAvailable);

            synchronized (iobCobCalculatorPlugin.getDataLock()) {
                if (bgDataReload) {
                    iobCobCalculatorPlugin.loadBgData(end);
                    iobCobCalculatorPlugin.createBucketedData();
                }
                List<BgReading> bucketed_data = iobCobCalculatorPlugin.getBucketedData();
                LongSparseArray<AutosensData> autosensDataTable = iobCobCalculatorPlugin.getAutosensDataTable();

                if (bucketed_data == null || bucketed_data.size() < 3) {
                    if (L.isEnabled(L.AUTOSENS))
                        log.debug("Aborting calculation thread (No bucketed data available): " + from);
                    return;
                }

                long prevDataTime = IobCobCalculatorPlugin.roundUpTime(bucketed_data.get(bucketed_data.size() - 3).date);
                if (L.isEnabled(L.AUTOSENS))
                    log.debug("Prev data time: " + new Date(prevDataTime).toLocaleString());
                AutosensData previous = autosensDataTable.get(prevDataTime);
                // start from oldest to be able sub cob
                for (int i = bucketed_data.size() - 4; i >= 0; i--) {
                    String progress = i + (MainApp.isDev() ? " (" + from + ")" : "");
                    RxBus.INSTANCE.send(new EventIobCalculationProgress(progress));

                    if (iobCobCalculatorPlugin.stopCalculationTrigger) {
                        iobCobCalculatorPlugin.stopCalculationTrigger = false;
                        if (L.isEnabled(L.AUTOSENS))
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

                    Profile profile = ProfileFunctions.getInstance().getProfile(bgTime);
                    if (profile == null) {
                        if (L.isEnabled(L.AUTOSENS))
                            log.debug("Aborting calculation thread (no profile): " + from);
                        return; // profile not set yet
                    }

                    if (L.isEnabled(L.AUTOSENS))
                        log.debug("Processing calculation thread: " + from + " (" + i + "/" + bucketed_data.size() + ")");

                    double sens = Profile.toMgdl(profile.getIsf(bgTime), profile.getUnits());

                    AutosensData autosensData = new AutosensData();
                    autosensData.time = bgTime;
                    if (previous != null)
                        autosensData.activeCarbsList = previous.cloneCarbsList();
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
                    autosensData.bg = bg;
                    delta = (bg - bucketed_data.get(i + 1).value);
                    avgDelta = (bg - bucketed_data.get(i + 3).value) / 3;

                    IobTotal iob = iobCobCalculatorPlugin.calculateFromTreatmentsAndTemps(bgTime, profile);

                    double bgi = -iob.activity * sens * 5;
                    double deviation = delta - bgi;
                    double avgDeviation = Math.round((avgDelta - bgi) * 1000) / 1000d;

                    double slopeFromMaxDeviation = 0;
                    double slopeFromMinDeviation = 999;
                    double maxDeviation = 0;
                    double minDeviation = 999;

                    // https://github.com/openaps/oref0/blob/master/lib/determine-basal/cob-autosens.js#L169
                    if (i < bucketed_data.size() - 16) { // we need 1h of data to calculate minDeviationSlope
                        long hourago = bgTime + 10 * 1000 - 60 * 60 * 1000L;
                        AutosensData hourAgoData = iobCobCalculatorPlugin.getAutosensData(hourago);
                        if (hourAgoData != null) {
                            int initialIndex = autosensDataTable.indexOfKey(hourAgoData.time);
                            if (L.isEnabled(L.AUTOSENS))
                                log.debug(">>>>> bucketed_data.size()=" + bucketed_data.size() + " i=" + i + " hourAgoData=" + hourAgoData.toString());
                            int past = 1;
                            try {
                                for (; past < 12; past++) {
                                    AutosensData ad = autosensDataTable.valueAt(initialIndex + past);
                                    if (L.isEnabled(L.AUTOSENS)) {
                                        log.debug(">>>>> past=" + past + " ad=" + (ad != null ? ad.toString() : null));
                                        if (ad == null) {
                                            log.debug(autosensDataTable.toString());
                                            log.debug(bucketed_data.toString());
                                            log.debug(IobCobCalculatorPlugin.getPlugin().getBgReadings().toString());
                                            Notification notification = new Notification(Notification.SENDLOGFILES, MainApp.gs(R.string.sendlogfiles), Notification.LOW);
                                            RxBus.INSTANCE.send(new EventNewNotification(notification));
                                            SP.putBoolean("log_AUTOSENS", true);
                                            break;
                                        }
                                    }
                                    // let it here crash on NPE to get more data as i cannot reproduce this bug
                                    double deviationSlope = (ad.avgDeviation - avgDeviation) / (ad.time - bgTime) * 1000 * 60 * 5;
                                    if (ad.avgDeviation > maxDeviation) {
                                        slopeFromMaxDeviation = Math.min(0, deviationSlope);
                                        maxDeviation = ad.avgDeviation;
                                    }
                                    if (ad.avgDeviation < minDeviation) {
                                        slopeFromMinDeviation = Math.max(0, deviationSlope);
                                        minDeviation = ad.avgDeviation;
                                    }

                                    //if (Config.isEnabled(L.AUTOSENS))
                                    //    log.debug("Deviations: " + new Date(bgTime) + new Date(ad.time) + " avgDeviation=" + avgDeviation + " deviationSlope=" + deviationSlope + " slopeFromMaxDeviation=" + slopeFromMaxDeviation + " slopeFromMinDeviation=" + slopeFromMinDeviation);
                                }
                            } catch (Exception e) {
                                log.error("Unhandled exception", e);
                                FabricPrivacy.logException(e);
                                log.debug(autosensDataTable.toString());
                                log.debug(bucketed_data.toString());
                                log.debug(IobCobCalculatorPlugin.getPlugin().getBgReadings().toString());
                                Notification notification = new Notification(Notification.SENDLOGFILES, MainApp.gs(R.string.sendlogfiles), Notification.LOW);
                                RxBus.INSTANCE.send(new EventNewNotification(notification));
                                SP.putBoolean("log_AUTOSENS", true);
                                break;
                            }
                        } else {
                            if (L.isEnabled(L.AUTOSENS))
                                log.debug(">>>>> bucketed_data.size()=" + bucketed_data.size() + " i=" + i + " hourAgoData=" + "null");
                        }
                    }

                    List<Treatment> recentTreatments = TreatmentsPlugin.getPlugin().getTreatments5MinBackFromHistory(bgTime);
                    for (int ir = 0; ir < recentTreatments.size(); ir++) {
                        autosensData.carbsFromBolus += recentTreatments.get(ir).carbs;
                        autosensData.activeCarbsList.add(new AutosensData.CarbsInPast(recentTreatments.get(ir)));
                        autosensData.pastSensitivity += "[" + DecimalFormatter.to0Decimal(recentTreatments.get(ir).carbs) + "g]";
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
                            autosensData.validDeviation = true;
                        } else if (deviation > 0) {
                            autosensData.pastSensitivity += "+";
                            autosensData.validDeviation = true;
                        } else {
                            autosensData.pastSensitivity += "-";
                            autosensData.validDeviation = true;
                        }
                    } else {
                        autosensData.pastSensitivity += "C";
                    }
                    //log.debug("TIME: " + new Date(bgTime).toString() + " BG: " + bg + " SENS: " + sens + " DELTA: " + delta + " AVGDELTA: " + avgDelta + " IOB: " + iob.iob + " ACTIVITY: " + iob.activity + " BGI: " + bgi + " DEVIATION: " + deviation);

                    previous = autosensData;
                    if (bgTime < now())
                        autosensDataTable.put(bgTime, autosensData);
                    if (L.isEnabled(L.AUTOSENS))
                        log.debug("Running detectSensitivity from: " + DateUtil.dateAndTimeString(oldestTimeWithData) + " to: " + DateUtil.dateAndTimeString(bgTime) + " lastDataTime:" + iobCobCalculatorPlugin.lastDataTime());
                    AutosensResult sensitivity = iobCobCalculatorPlugin.detectSensitivityWithLock(oldestTimeWithData, bgTime);
                    if (L.isEnabled(L.AUTOSENS))
                        log.debug("Sensitivity result: " + sensitivity.toString());
                    autosensData.autosensResult = sensitivity;
                    if (L.isEnabled(L.AUTOSENS))
                        log.debug(autosensData.toString());
                }
            }
            new Thread(() -> {
                SystemClock.sleep(1000);
                RxBus.INSTANCE.send(new EventAutosensCalculationFinished(cause));
            }).start();
        } finally {
            if (mWakeLock != null)
                mWakeLock.release();
            RxBus.INSTANCE.send(new EventIobCalculationProgress(""));
            if (L.isEnabled(L.AUTOSENS)) {
                log.debug("AUTOSENSDATA thread ended: " + from);
                log.debug("Midnights: " + MidnightTime.log());
            }
            Profiler.log(log, "IobCobThread", start);
        }
    }

}

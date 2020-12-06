package info.nightscout.androidaps.plugins.iob.iobCobCalculator;

import android.content.Context;
import android.os.PowerManager;
import android.os.SystemClock;

import androidx.collection.LongSparseArray;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import dagger.android.HasAndroidInjector;
import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.IobTotal;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.db.BgReading;
import info.nightscout.androidaps.events.Event;
import info.nightscout.androidaps.interfaces.ActivePluginProvider;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.aps.openAPSSMB.SMBDefaults;
import info.nightscout.androidaps.plugins.bus.RxBusWrapper;
import info.nightscout.androidaps.interfaces.ProfileFunction;
import info.nightscout.androidaps.plugins.general.overview.events.EventNewNotification;
import info.nightscout.androidaps.plugins.general.overview.notifications.Notification;
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.data.AutosensData;
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.events.EventAutosensBgLoaded;
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.events.EventAutosensCalculationFinished;
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.events.EventIobCalculationProgress;
import info.nightscout.androidaps.plugins.sensitivity.SensitivityAAPSPlugin;
import info.nightscout.androidaps.plugins.sensitivity.SensitivityWeightedAveragePlugin;
import info.nightscout.androidaps.db.Treatment;
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.DecimalFormatter;
import info.nightscout.androidaps.utils.FabricPrivacy;
import info.nightscout.androidaps.utils.MidnightTime;
import info.nightscout.androidaps.utils.Profiler;
import info.nightscout.androidaps.utils.T;
import info.nightscout.androidaps.utils.buildHelper.BuildHelper;
import info.nightscout.androidaps.utils.resources.ResourceHelper;
import info.nightscout.androidaps.utils.sharedPreferences.SP;

import static info.nightscout.androidaps.utils.DateUtil.now;

/**
 * Created by mike on 23.01.2018.
 */

public class IobCobThread extends Thread {
    private final Event cause;

    @Inject AAPSLogger aapsLogger;
    @Inject SP sp;
    @Inject RxBusWrapper rxBus;
    @Inject ResourceHelper resourceHelper;
    @Inject ProfileFunction profileFunction;
    @Inject Context context;
    @Inject SensitivityAAPSPlugin sensitivityAAPSPlugin;
    @Inject SensitivityWeightedAveragePlugin sensitivityWeightedAveragePlugin;
    @Inject BuildHelper buildHelper;
    @Inject Profiler profiler;
    @Inject FabricPrivacy fabricPrivacy;
    @Inject DateUtil dateUtil;

    private final HasAndroidInjector injector;
    private final IobCobCalculatorPlugin iobCobCalculatorPlugin; // cannot be injected : HistoryBrowser uses different instance
    private final TreatmentsPlugin treatmentsPlugin;             // cannot be injected : HistoryBrowser uses different instance
    private final boolean bgDataReload;
    private final boolean limitDataToOldestAvailable;
    private final String from;
    private final long end;

    private PowerManager.WakeLock mWakeLock;

    @Inject IobCobThread(HasAndroidInjector injector, IobCobCalculatorPlugin iobCobCalculatorPlugin, TreatmentsPlugin treatmentsPlugin, String from, long end, boolean bgDataReload, boolean limitDataToOldestAvailable, Event cause) {
        super();
        injector.androidInjector().inject(this);
        this.injector = injector;
        this.iobCobCalculatorPlugin = iobCobCalculatorPlugin;
        this.treatmentsPlugin = treatmentsPlugin;

        this.bgDataReload = bgDataReload;
        this.limitDataToOldestAvailable = limitDataToOldestAvailable;
        this.from = from;
        this.cause = cause;
        this.end = end;

        PowerManager powerManager = (PowerManager) context.getApplicationContext().getSystemService(Context.POWER_SERVICE);
        if (powerManager != null)
            mWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, resourceHelper.gs(R.string.app_name) + ":iobCobThread");
    }

    @Override
    public final void run() {
        long start = DateUtil.now();
        if (mWakeLock != null)
            mWakeLock.acquire(T.mins(10).msecs());
        try {
            aapsLogger.debug(LTag.AUTOSENS, "AUTOSENSDATA thread started: " + from);
            if (!profileFunction.isProfileValid("IobCobThread")) {
                aapsLogger.debug(LTag.AUTOSENS, "Aborting calculation thread (No profile): " + from);
                return; // app still initializing
            }
            //log.debug("Locking calculateSensitivityData");

            long oldestTimeWithData = iobCobCalculatorPlugin.calculateDetectionStart(end, limitDataToOldestAvailable);

            synchronized (iobCobCalculatorPlugin.getDataLock()) {
                if (bgDataReload) {
                    iobCobCalculatorPlugin.loadBgData(end);
                    iobCobCalculatorPlugin.createBucketedData();
                    rxBus.send(new EventAutosensBgLoaded(cause));
                }
                List<InMemoryGlucoseValue> bucketed_data = iobCobCalculatorPlugin.getBucketedData();
                LongSparseArray<AutosensData> autosensDataTable = iobCobCalculatorPlugin.getAutosensDataTable();

                if (bucketed_data == null || bucketed_data.size() < 3) {
                    aapsLogger.debug(LTag.AUTOSENS, "Aborting calculation thread (No bucketed data available): " + from);
                    return;
                }

                long prevDataTime = IobCobCalculatorPlugin.roundUpTime(bucketed_data.get(bucketed_data.size() - 3).getTimestamp());
                aapsLogger.debug(LTag.AUTOSENS, "Prev data time: " + dateUtil.dateAndTimeString(prevDataTime));
                AutosensData previous = autosensDataTable.get(prevDataTime);
                // start from oldest to be able sub cob
                for (int i = bucketed_data.size() - 4; i >= 0; i--) {
                    String progress = i + (buildHelper.isDev() ? " (" + from + ")" : "");
                    rxBus.send(new EventIobCalculationProgress(progress));

                    if (iobCobCalculatorPlugin.stopCalculationTrigger) {
                        iobCobCalculatorPlugin.stopCalculationTrigger = false;
                        aapsLogger.debug(LTag.AUTOSENS, "Aborting calculation thread (trigger): " + from);
                        return;
                    }
                    // check if data already exists
                    long bgTime = bucketed_data.get(i).getTimestamp();
                    bgTime = IobCobCalculatorPlugin.roundUpTime(bgTime);
                    if (bgTime > IobCobCalculatorPlugin.roundUpTime(now()))
                        continue;

                    AutosensData existing;
                    if ((existing = autosensDataTable.get(bgTime)) != null) {
                        previous = existing;
                        continue;
                    }

                    Profile profile = profileFunction.getProfile(bgTime);
                    if (profile == null) {
                        aapsLogger.debug(LTag.AUTOSENS, "Aborting calculation thread (no profile): " + from);
                        return; // profile not set yet
                    }

                    aapsLogger.debug(LTag.AUTOSENS, "Processing calculation thread: " + from + " (" + i + "/" + bucketed_data.size() + ")");

                    double sens = profile.getIsfMgdl(bgTime);

                    AutosensData autosensData = new AutosensData(injector);
                    autosensData.time = bgTime;
                    if (previous != null)
                        autosensData.activeCarbsList = previous.cloneCarbsList();
                    else
                        autosensData.activeCarbsList = new ArrayList<>();

                    //console.error(bgTime , bucketed_data[i].glucose);
                    double bg;
                    double avgDelta;
                    double delta;
                    bg = bucketed_data.get(i).getValue();
                    if (bg < 39 || bucketed_data.get(i + 3).getValue() < 39) {
                        aapsLogger.error("! value < 39");
                        continue;
                    }
                    autosensData.bg = bg;
                    delta = (bg - bucketed_data.get(i + 1).getValue());
                    avgDelta = (bg - bucketed_data.get(i + 3).getValue()) / 3;

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
                            aapsLogger.debug(LTag.AUTOSENS, ">>>>> bucketed_data.size()=" + bucketed_data.size() + " i=" + i + " hourAgoData=" + hourAgoData.toString());
                            int past = 1;
                            try {
                                for (; past < 12; past++) {
                                    AutosensData ad = autosensDataTable.valueAt(initialIndex + past);
                                    aapsLogger.debug(LTag.AUTOSENS, ">>>>> past=" + past + " ad=" + (ad != null ? ad.toString() : null));
                                    if (ad == null) {
                                        aapsLogger.debug(LTag.AUTOSENS, autosensDataTable.toString());
                                        aapsLogger.debug(LTag.AUTOSENS, bucketed_data.toString());
                                        aapsLogger.debug(LTag.AUTOSENS, iobCobCalculatorPlugin.getBgReadings().toString());
                                        Notification notification = new Notification(Notification.SENDLOGFILES, resourceHelper.gs(R.string.sendlogfiles), Notification.LOW);
                                        rxBus.send(new EventNewNotification(notification));
                                        sp.putBoolean("log_AUTOSENS", true);
                                        break;
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
                                aapsLogger.error("Unhandled exception", e);
                                fabricPrivacy.logException(e);
                                aapsLogger.debug(autosensDataTable.toString());
                                aapsLogger.debug(bucketed_data.toString());
                                aapsLogger.debug(iobCobCalculatorPlugin.getBgReadings().toString());
                                Notification notification = new Notification(Notification.SENDLOGFILES, resourceHelper.gs(R.string.sendlogfiles), Notification.LOW);
                                rxBus.send(new EventNewNotification(notification));
                                sp.putBoolean("log_AUTOSENS", true);
                                break;
                            }
                        } else {
                            aapsLogger.debug(LTag.AUTOSENS, ">>>>> bucketed_data.size()=" + bucketed_data.size() + " i=" + i + " hourAgoData=" + "null");
                        }
                    }

                    List<Treatment> recentCarbTreatments = treatmentsPlugin.getCarbTreatments5MinBackFromHistory(bgTime);
                    for (Treatment recentCarbTreatment : recentCarbTreatments) {
                        autosensData.carbsFromBolus += recentCarbTreatment.carbs;
                        boolean isAAPSOrWeighted = sensitivityAAPSPlugin.isEnabled() || sensitivityWeightedAveragePlugin.isEnabled();
                        autosensData.activeCarbsList.add(autosensData.new CarbsInPast(recentCarbTreatment, isAAPSOrWeighted));
                        autosensData.pastSensitivity += "[" + DecimalFormatter.to0Decimal(recentCarbTreatment.carbs) + "g]";
                    }


                    // if we are absorbing carbs
                    if (previous != null && previous.cob > 0) {
                        // calculate sum of min carb impact from all active treatments
                        double totalMinCarbsImpact = 0d;
                        if (sensitivityAAPSPlugin.isEnabled(PluginType.SENSITIVITY) || sensitivityWeightedAveragePlugin.isEnabled(PluginType.SENSITIVITY)) {
                            //when the impact depends on a max time, sum them up as smaller carb sizes make them smaller
                            for (int ii = 0; ii < autosensData.activeCarbsList.size(); ++ii) {
                                AutosensData.CarbsInPast c = autosensData.activeCarbsList.get(ii);
                                totalMinCarbsImpact += c.min5minCarbImpact;
                            }
                        } else {
                            //Oref sensitivity
                            totalMinCarbsImpact = sp.getDouble(R.string.key_openapsama_min_5m_carbimpact, SMBDefaults.min_5m_carbimpact);
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
                    boolean isAAPSOrWeighted = sensitivityAAPSPlugin.isEnabled() || sensitivityWeightedAveragePlugin.isEnabled();
                    autosensData.removeOldCarbs(bgTime, isAAPSOrWeighted);
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
                    aapsLogger.debug(LTag.AUTOSENS, "Running detectSensitivity from: " + dateUtil.dateAndTimeString(oldestTimeWithData) + " to: " + dateUtil.dateAndTimeString(bgTime) + " lastDataTime:" + iobCobCalculatorPlugin.lastDataTime());
                    AutosensResult sensitivity = iobCobCalculatorPlugin.detectSensitivityWithLock(oldestTimeWithData, bgTime);
                    aapsLogger.debug(LTag.AUTOSENS, "Sensitivity result: " + sensitivity.toString());
                    autosensData.autosensResult = sensitivity;
                    aapsLogger.debug(LTag.AUTOSENS, autosensData.toString());
                }
            }
            new Thread(() -> {
                SystemClock.sleep(1000);
                rxBus.send(new EventAutosensCalculationFinished(cause));
            }).start();
        } finally {
            if (mWakeLock != null)
                mWakeLock.release();
            rxBus.send(new EventIobCalculationProgress(""));
            aapsLogger.debug(LTag.AUTOSENS, "AUTOSENSDATA thread ended: " + from);
            aapsLogger.debug(LTag.AUTOSENS, "Midnights: " + MidnightTime.log());
            profiler.log(LTag.AUTOSENS, "IobCobThread", start);
        }
    }

}

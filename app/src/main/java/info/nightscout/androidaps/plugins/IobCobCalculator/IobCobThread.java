package info.nightscout.androidaps.plugins.IobCobCalculator;

import android.content.Context;
import android.os.PowerManager;
import android.support.v4.util.LongSparseArray;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.data.IobTotal;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.db.BgReading;
import info.nightscout.androidaps.db.Treatment;
import info.nightscout.androidaps.plugins.IobCobCalculator.events.EventAutosensCalculationFinished;
import info.nightscout.androidaps.queue.QueueThread;

import static info.nightscout.androidaps.plugins.IobCobCalculator.IobCobCalculatorPlugin.getBucketedData;
import static info.nightscout.androidaps.plugins.IobCobCalculator.IobCobCalculatorPlugin.oldestDataAvailable;
import static info.nightscout.androidaps.plugins.IobCobCalculator.IobCobCalculatorPlugin.roundUpTime;

/**
 * Created by mike on 23.01.2018.
 */

public class IobCobThread extends Thread {
    private static Logger log = LoggerFactory.getLogger(QueueThread.class);

    private IobCobCalculatorPlugin iobCobCalculatorPlugin;
    private boolean bgDataReload;
    private String from;

    private PowerManager.WakeLock mWakeLock;

    public IobCobThread(IobCobCalculatorPlugin plugin, String from, boolean bgDataReload) {
        super();

        this.iobCobCalculatorPlugin = plugin;
        this.bgDataReload = bgDataReload;
        this.from = from;

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
            if (MainApp.getConfigBuilder().getProfile() == null) {
                log.debug("Aborting calculation thread (No profile): " + from);
                return; // app still initializing
            }
            //log.debug("Locking calculateSensitivityData");

            Object dataLock = iobCobCalculatorPlugin.dataLock;

            long oldestTimeWithData = oldestDataAvailable();

            synchronized (dataLock) {
                if (bgDataReload) {
                    iobCobCalculatorPlugin.loadBgData();
                    iobCobCalculatorPlugin.createBucketedData();
                }
                List<BgReading> bucketed_data = getBucketedData();
                LongSparseArray<AutosensData> autosensDataTable = iobCobCalculatorPlugin.getAutosensDataTable();

                if (bucketed_data == null || bucketed_data.size() < 3) {
                    log.debug("Aborting calculation thread (No bucketed data available): " + from);
                    return;
                }

                long prevDataTime = roundUpTime(bucketed_data.get(bucketed_data.size() - 3).date);
                log.debug("Prev data time: " + new Date(prevDataTime).toLocaleString());
                AutosensData previous = autosensDataTable.get(prevDataTime);
                // start from oldest to be able sub cob
                for (int i = bucketed_data.size() - 4; i >= 0; i--) {
                    if (iobCobCalculatorPlugin.stopCalculationTrigger) {
                        iobCobCalculatorPlugin.stopCalculationTrigger = false;
                        log.debug("Aborting calculation thread (trigger): " + from);
                        return;
                    }
                    // check if data already exists
                    long bgTime = bucketed_data.get(i).date;
                    bgTime = roundUpTime(bgTime);
                    if (bgTime > System.currentTimeMillis())
                        continue;
                    Profile profile = MainApp.getConfigBuilder().getProfile(bgTime);

                    AutosensData existing;
                    if ((existing = autosensDataTable.get(bgTime)) != null) {
                        previous = existing;
                        continue;
                    }

                    if (profile == null) {
                        log.debug("Aborting calculation thread (no profile): " + from);
                        return; // profile not set yet
                    }

                    if (profile.getIsf(bgTime) == null) {
                        log.debug("Aborting calculation thread (no ISF): " + from);
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

                    IobTotal iob = iobCobCalculatorPlugin.calculateFromTreatmentsAndTemps(bgTime);

                    double bgi = -iob.activity * sens * 5;
                    double deviation = delta - bgi;

                    List<Treatment> recentTreatments = MainApp.getConfigBuilder().getTreatments5MinBackFromHistory(bgTime);
                    for (int ir = 0; ir < recentTreatments.size(); ir++) {
                        autosensData.carbsFromBolus += recentTreatments.get(ir).carbs;
                        autosensData.activeCarbsList.add(new AutosensData.CarbsInPast(recentTreatments.get(ir)));
                    }


                    // if we are absorbing carbs
                    if (previous != null && previous.cob > 0) {
                        // calculate sum of min carb impact from all active treatments
                        double totalMinCarbsImpact = 0d;
                        for (int ii = 0; ii < autosensData.activeCarbsList.size(); ++ii) {
                            AutosensData.CarbsInPast c = autosensData.activeCarbsList.get(ii);
                            totalMinCarbsImpact += c.min5minCarbImpact;
                        }

                        // figure out how many carbs that represents
                        // but always assume at least 3mg/dL/5m (default) absorption per active treatment
                        double ci = Math.max(deviation, totalMinCarbsImpact);
                        autosensData.absorbed = ci * profile.getIc(bgTime) / sens;
                        // and add that to the running total carbsAbsorbed
                        autosensData.cob = Math.max(previous.cob - autosensData.absorbed, 0d);
                        autosensData.substractAbosorbedCarbs();
                    }
                    autosensData.removeOldCarbs(bgTime);
                    autosensData.cob += autosensData.carbsFromBolus;
                    autosensData.deviation = deviation;
                    autosensData.bgi = bgi;
                    autosensData.delta = delta;

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
                    autosensData.autosensRatio = iobCobCalculatorPlugin.detectSensitivity(oldestTimeWithData, bgTime).ratio;
                    if (Config.logAutosensData)
                        log.debug(autosensData.log(bgTime));
                }
            }
            MainApp.bus().post(new EventAutosensCalculationFinished());
            log.debug("Finishing calculation thread: " + from);
        } finally {
            mWakeLock.release();
        }
    }

}

package info.nightscout.androidaps.plugins.IobCobCalculator;

import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.Nullable;
import android.support.v4.util.LongSparseArray;

import com.squareup.otto.Subscribe;

import org.json.JSONArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.data.IobTotal;
import info.nightscout.androidaps.db.BgReading;
import info.nightscout.androidaps.db.Treatment;
import info.nightscout.androidaps.events.EventNewBG;
import info.nightscout.androidaps.events.EventNewBasalProfile;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.TreatmentsInterface;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.IobCobCalculator.events.EventAutosensCalculationFinished;
import info.nightscout.androidaps.plugins.IobCobCalculator.events.EventNewHistoryData;
import info.nightscout.androidaps.plugins.NSClientInternal.data.NSProfile;
import info.nightscout.utils.Round;
import info.nightscout.utils.SP;
import info.nightscout.utils.SafeParse;

/**
 * Created by mike on 24.04.2017.
 */

public class IobCobCalculatorPlugin implements PluginBase {
    private static Logger log = LoggerFactory.getLogger(IobCobCalculatorPlugin.class);

    private static LongSparseArray<IobTotal> iobTable = new LongSparseArray<>(); // oldest at index 0
    private static LongSparseArray<AutosensData> autosensDataTable = new LongSparseArray<>(); // oldest at index 0

    private static volatile List<BgReading> bgReadings = null; // newest at index 0
    private static volatile List<BgReading> bucketed_data = null;

    private static double dia = Constants.defaultDIA;

    private static Handler sHandler = null;
    private static HandlerThread sHandlerThread = null;

    private static Object dataLock = new Object();

    @Override
    public int getType() {
        return GENERAL;
    }

    @Override
    public String getFragmentClass() {
        return IobCobCalculatorFragment.class.getName();
    }

    @Override
    public String getName() {
        return "IOB COB Calculator";
    }

    @Override
    public String getNameShort() {
        return "IOC";
    }

    @Override
    public boolean isEnabled(int type) {
        return type == GENERAL;
    }

    @Override
    public boolean isVisibleInTabs(int type) {
        return false;
    }

    @Override
    public boolean canBeHidden(int type) {
        return false;
    }

    @Override
    public boolean hasFragment() {
        return false;
    }

    @Override
    public boolean showInList(int type) {
        return false;
    }

    @Override
    public void setFragmentEnabled(int type, boolean fragmentEnabled) {

    }

    @Override
    public void setFragmentVisible(int type, boolean fragmentVisible) {

    }

    public IobCobCalculatorPlugin() {
        MainApp.bus().register(this);
        if (sHandlerThread == null) {
            sHandlerThread = new HandlerThread(IobCobCalculatorPlugin.class.getSimpleName());
            sHandlerThread.start();
            sHandler = new Handler(sHandlerThread.getLooper());
        }
        onNewBg(new EventNewBG());
    }

    @Nullable
    public static List<BgReading> getBucketedData(long fromTime) {
        //log.debug("Locking getBucketedData");
        synchronized (dataLock) {
            if (bucketed_data == null) {
                log.debug("No bucketed data available");
                return null;
            }
            int index = indexNewerThan(fromTime);
            if (index > -1) {
                List<BgReading> part = bucketed_data.subList(0, index);
                log.debug("Bucketed data striped off: " + part.size() + "/" + bucketed_data.size());
                return part;
            }
        }
        //log.debug("Releasing getBucketedData");
        return null;
    }

    private static int indexNewerThan(long time) {
        for (int index = 0; index < bucketed_data.size(); index++) {
            if (bucketed_data.get(index).timeIndex < time)
                return index - 1;
        }
        return -1;
    }

    public static long roundUpTime(long time) {
        if (time % 60000 == 0)
            return time;
        long rouded = (time / 60000 + 1) * 60000;
        return rouded;
    }

    private void loadBgData() {
        //log.debug("Locking loadBgData");
        synchronized (dataLock) {
            onNewProfile(new EventNewBasalProfile(null, "IobCobCalculator init"));
            bgReadings = MainApp.getDbHelper().getBgreadingsDataFromTime((long) (new Date().getTime() - 60 * 60 * 1000L * (24 + dia)), false);
            log.debug("BG data loaded. Size: " + bgReadings.size());
        }
        //log.debug("Releasing loadBgData");
    }

    public void createBucketedData() {
        //log.debug("Locking createBucketedData");
        synchronized (dataLock) {
            if (bgReadings == null || bgReadings.size() < 3) {
                bucketed_data = null;
                return;
            }

            bucketed_data = new ArrayList<>();
            bucketed_data.add(bgReadings.get(0));
            int j = 0;
            for (int i = 1; i < bgReadings.size(); ++i) {
                long bgTime = bgReadings.get(i).getTimeIndex();
                long lastbgTime = bgReadings.get(i - 1).getTimeIndex();
                //log.error("Processing " + i + ": " + new Date(bgTime).toString() + " " + bgReadings.get(i).value + "   Previous: " + new Date(lastbgTime).toString() + " " + bgReadings.get(i - 1).value);
                if (bgReadings.get(i).value < 39 || bgReadings.get(i - 1).value < 39) {
                    continue;
                }

                long elapsed_minutes = (bgTime - lastbgTime) / (60 * 1000);
                if (Math.abs(elapsed_minutes) > 8) {
                    // interpolate missing data points
                    double lastbg = bgReadings.get(i - 1).value;
                    elapsed_minutes = Math.abs(elapsed_minutes);
                    //console.error(elapsed_minutes);
                    long nextbgTime;
                    while (elapsed_minutes > 5) {
                        nextbgTime = lastbgTime - 5 * 60 * 1000;
                        j++;
                        BgReading newBgreading = new BgReading();
                        newBgreading.timeIndex = nextbgTime;
                        double gapDelta = bgReadings.get(i).value - lastbg;
                        //console.error(gapDelta, lastbg, elapsed_minutes);
                        double nextbg = lastbg + (5d / elapsed_minutes * gapDelta);
                        newBgreading.value = Math.round(nextbg);
                        //console.error("Interpolated", bucketed_data[j]);
                        bucketed_data.add(newBgreading);
                        //log.error("******************************************************************************************************* Adding:" + new Date(newBgreading.timeIndex).toString() + " " + newBgreading.value);

                        elapsed_minutes = elapsed_minutes - 5;
                        lastbg = nextbg;
                        lastbgTime = nextbgTime;
                    }
                    j++;
                    BgReading newBgreading = new BgReading();
                    newBgreading.value = bgReadings.get(i).value;
                    newBgreading.timeIndex = bgTime;
                    bucketed_data.add(newBgreading);
                    //log.error("******************************************************************************************************* Copying:" + new Date(newBgreading.timeIndex).toString() + " " + newBgreading.value);
                } else if (Math.abs(elapsed_minutes) > 2) {
                    j++;
                    BgReading newBgreading = new BgReading();
                    newBgreading.value = bgReadings.get(i).value;
                    newBgreading.timeIndex = bgTime;
                    bucketed_data.add(newBgreading);
                    //log.error("******************************************************************************************************* Copying:" + new Date(newBgreading.timeIndex).toString() + " " + newBgreading.value);
                } else {
                    bucketed_data.get(j).value = (bucketed_data.get(j).value + bgReadings.get(i).value) / 2;
                    //log.error("***** Average");
                }
            }
            log.debug("Bucketed data created. Size: " + bucketed_data.size());
        }
        //log.debug("Releasing createBucketedData");
    }

    public void calculateSensitivityData() {
        //log.debug("Locking calculateSensitivityData");
        synchronized (dataLock) {
            NSProfile profile = ConfigBuilderPlugin.getActiveProfile() != null ? ConfigBuilderPlugin.getActiveProfile().getProfile() : null;

            if (profile == null || profile.getIsf(NSProfile.secondsFromMidnight()) == null || profile.getIc(NSProfile.secondsFromMidnight()) == null) {
                log.debug("calculateSensitivityData: No profile available");
                return;
            }

            if (ConfigBuilderPlugin.getActiveTreatments() == null) {
                log.debug("calculateSensitivityData: No treatments plugin");
                return;
            }

            TreatmentsInterface treatmentsInterface = ConfigBuilderPlugin.getActiveTreatments();
            if (bucketed_data == null || bucketed_data.size() < 3) {
                log.debug("calculateSensitivityData: No bucketed data available");
                return;
            }

            long prevDataTime = roundUpTime(bucketed_data.get(bucketed_data.size() - 3).timeIndex);
            log.debug("Prev data time: " + new Date(prevDataTime).toLocaleString());
            AutosensData previous = autosensDataTable.get(prevDataTime);
            // start from oldest to be able sub cob
            for (int i = bucketed_data.size() - 4; i >= 0; i--) {
                // check if data already exists
                long bgTime = bucketed_data.get(i).timeIndex;
                bgTime = roundUpTime(bgTime);

                AutosensData existing;
                if ((existing = autosensDataTable.get(bgTime)) != null) {
                    previous = existing;
                    continue;
                }

                int secondsFromMidnight = NSProfile.secondsFromMidnight(bgTime);
                double sens = NSProfile.toMgdl(profile.getIsf(secondsFromMidnight), profile.getUnits());

                AutosensData autosensData = new AutosensData();
                autosensData.time = bgTime;

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

                IobTotal iob = calulateFromTreatmentsAndTemps(bgTime);

                double bgi = -iob.activity * sens * 5;
                double deviation = delta - bgi;

                List<Treatment> recentTreatments = treatmentsInterface.getTreatments5MinBack(bgTime);
                for (int ir = 0; ir < recentTreatments.size(); ir++) {
                    autosensData.carbsFromBolus += recentTreatments.get(ir).carbs;
                }

                // if we are absorbing carbs
                if (previous != null && previous.cob > 0) {
                    // figure out how many carbs that represents
                    // but always assume at least 3mg/dL/5m (default) absorption
                    double ci = Math.max(deviation, SP.getDouble("openapsama_min_5m_carbimpact", 3.0));
                    autosensData.absorbed = ci * profile.getIc(secondsFromMidnight) / sens;
                    // and add that to the running total carbsAbsorbed
                    autosensData.cob = Math.max(previous.cob - autosensData.absorbed, 0d);
                }
                autosensData.cob += autosensData.carbsFromBolus;
                autosensData.deviation = deviation;
                autosensData.bgi = bgi;
                autosensData.delta = delta;

                // calculate autosens only without COB
                if (autosensData.cob <= 0) {
                    if (Math.abs(deviation) < Constants.DEVIATION_TO_BE_EQUAL) {
                        autosensData.pastSensitivity += "=";
                    } else if (deviation > 0) {
                        autosensData.pastSensitivity += "+";
                    } else {
                        autosensData.pastSensitivity += "-";
                    }
                    autosensData.calculateWithDeviation = true;
                } else {
                    autosensData.pastSensitivity += "C";
                }
                //log.debug("TIME: " + new Date(bgTime).toString() + " BG: " + bg + " SENS: " + sens + " DELTA: " + delta + " AVGDELTA: " + avgDelta + " IOB: " + iob.iob + " ACTIVITY: " + iob.activity + " BGI: " + bgi + " DEVIATION: " + deviation);

                previous = autosensData;
                autosensDataTable.put(bgTime, autosensData);
                log.debug(autosensData.log(bgTime));
            }
        }
        MainApp.bus().post(new EventAutosensCalculationFinished());
        //log.debug("Releasing calculateSensitivityData");
    }

    public static IobTotal calulateFromTreatmentsAndTemps(long time) {
        long now = new Date().getTime();
        time = roundUpTime(time);
        if (time < now && iobTable.get(time) != null) {
            //log.debug(">>> Cache hit");
            return iobTable.get(time);
        } else {
            //log.debug(">>> Cache miss " + new Date(time).toLocaleString());
        }
        IobTotal bolusIob = ConfigBuilderPlugin.getActiveTreatments().getCalculationToTime(time).round();
        IobTotal basalIob = ConfigBuilderPlugin.getActiveTempBasals().getCalculationToTime(time).round();
/*
        if (basalIob.basaliob > 0) {
            log.debug(new Date(time).toLocaleString() + " basaliob: " + basalIob.basaliob );
        }
*/
        IobTotal iobTotal = IobTotal.combine(bolusIob, basalIob).round();
        if (time < new Date().getTime()) {
            iobTable.put(time, iobTotal);
        }
        return iobTotal;
    }

    private static Long findPreviousTimeFromBucketedData(long time) {
        if (bucketed_data == null)
            return null;
        for (int index = 0; index < bucketed_data.size(); index++) {
            if (bucketed_data.get(index).timeIndex < time)
                return bucketed_data.get(index).timeIndex;
        }
        return null;
    }

    public static AutosensData getAutosensData(long time) {
        long now = new Date().getTime();
        if (time > now)
            return null;
        Long previous = findPreviousTimeFromBucketedData(time);
        if (previous == null)
            return null;
        time = roundUpTime(previous);
        AutosensData data = autosensDataTable.get(time);
        if (data != null) {
            //log.debug(">>> Cache hit " + data.log(time));
            return data;
        } else {
            //log.debug(">>> Cache miss " + new Date(time).toLocaleString());
            return null;
        }
    }

    public static AutosensData getLastAutosensData() {
        if (autosensDataTable.size() < 1)
            return null;
        AutosensData data = autosensDataTable.valueAt(autosensDataTable.size() - 1);
        if (data.time < new Date().getTime() - 5 * 60 * 1000) {
            return null;
        } else {
            return data;
        }
    }

    public static IobTotal[] calculateIobArrayInDia() {
        NSProfile profile = ConfigBuilderPlugin.getActiveProfile().getProfile();
        // predict IOB out to DIA plus 30m
        long time = new Date().getTime();
        int len = (int) ((profile.getDia() * 60 + 30) / 5);
        IobTotal[] array = new IobTotal[len];
        int pos = 0;
        for (int i = 0; i < len; i++) {
            long t = time + i * 5 * 60000;
            IobTotal iob = calulateFromTreatmentsAndTemps(t);
            array[pos] = iob;
            pos++;
        }
        return array;
    }

    public static AutosensResult detectSensitivity(long fromTime) {
        //log.debug("Locking detectSensitivity");
        synchronized (dataLock) {
            if (autosensDataTable == null || autosensDataTable.size() < 4) {
                log.debug("No autosens data available");
                return new AutosensResult();
            }

            AutosensData current = getLastAutosensData();
            if (current == null) {
                log.debug("No current autosens data available");
                return new AutosensResult();
            }


            List<Double> deviationsArray = new ArrayList<>();
            String pastSensitivity = "";
            int index = 0;
            while (index < autosensDataTable.size()) {
                AutosensData autosensData = autosensDataTable.valueAt(index);

                if (autosensData.time < fromTime) {
                    index++;
                    continue;
                }

                if (autosensData.calculateWithDeviation)
                    deviationsArray.add(autosensData.deviation);

                pastSensitivity += autosensData.pastSensitivity;
                int secondsFromMidnight = NSProfile.secondsFromMidnight(autosensData.time);
                if (secondsFromMidnight % 3600 < 2.5 * 60 || secondsFromMidnight % 3600 > 57.5 * 60) {
                    pastSensitivity += "(" + Math.round(secondsFromMidnight / 3600d) + ")";
                }
                index++;
            }

            Double[] deviations = new Double[deviationsArray.size()];
            deviations = deviationsArray.toArray(deviations);

            if (ConfigBuilderPlugin.getActiveProfile() == null || ConfigBuilderPlugin.getActiveProfile().getProfile() == null) {
                log.debug("No profile available");
                return new AutosensResult();
            }

            NSProfile profile = ConfigBuilderPlugin.getActiveProfile().getProfile();

            Double sens = profile.getIsf(NSProfile.secondsFromMidnight());

            if (sens == null || profile.getMaxDailyBasal() == 0) {
                log.debug("No profile available");
                return new AutosensResult();
            }

            double ratio = 1;
            String ratioLimit = "";
            String sensResult = "";

            log.debug("Records: " + index + "   " + pastSensitivity);
            Arrays.sort(deviations);

            for (double i = 0.9; i > 0.1; i = i - 0.02) {
                if (percentile(deviations, (i + 0.02)) >= 0 && percentile(deviations, i) < 0) {
                    log.debug(Math.round(100 * i) + "% of non-meal deviations negative (target 45%-50%)");
                }
            }
            double pSensitive = percentile(deviations, 0.50);
            double pResistant = percentile(deviations, 0.45);

            double basalOff = 0;

            if (pSensitive < 0) { // sensitive
                basalOff = pSensitive * (60 / 5) / NSProfile.toMgdl(sens, profile.getUnits());
                sensResult = "Excess insulin sensitivity detected";
            } else if (pResistant > 0) { // resistant
                basalOff = pResistant * (60 / 5) / NSProfile.toMgdl(sens, profile.getUnits());
                sensResult = "Excess insulin resistance detected";
            } else {
                sensResult = "Sensitivity normal";
            }
            log.debug(sensResult);
            ratio = 1 + (basalOff / profile.getMaxDailyBasal());

            double rawRatio = ratio;
            ratio = Math.max(ratio, SafeParse.stringToDouble(SP.getString("openapsama_autosens_min", "0.7")));
            ratio = Math.min(ratio, SafeParse.stringToDouble(SP.getString("openapsama_autosens_max", "1.2")));

            if (ratio != rawRatio) {
                ratioLimit = "Ratio limited from " + rawRatio + " to " + ratio;
                log.debug(ratioLimit);
            }

            double newisf = Math.round(NSProfile.toMgdl(sens, profile.getUnits()) / ratio);
            if (ratio != 1) {
                log.debug("ISF adjusted from " + NSProfile.toMgdl(sens, profile.getUnits()) + " to " + newisf);
            }

            AutosensResult output = new AutosensResult();
            output.ratio = Round.roundTo(ratio, 0.01);
            output.carbsAbsorbed = Round.roundTo(current.cob, 0.01);
            output.pastSensitivity = pastSensitivity;
            output.ratioLimit = ratioLimit;
            output.sensResult = sensResult;
            return output;
        }
        //log.debug("Releasing detectSensitivity");
    }


    public static JSONArray convertToJSONArray(IobTotal[] iobArray) {
        JSONArray array = new JSONArray();
        for (int i = 0; i < iobArray.length; i++) {
            array.put(iobArray[i].determineBasalJson());
        }
        return array;
    }

    @Subscribe
    public void onNewBg(EventNewBG ev) {
        sHandler.post(new Runnable() {
            @Override
            public void run() {
                loadBgData();
                createBucketedData();
                calculateSensitivityData();
            }
        });
    }

    @Subscribe
    public void onNewProfile(EventNewBasalProfile ev) {
        if (MainApp.getConfigBuilder().getActiveProfile() == null)
            return;
        NSProfile profile = MainApp.getConfigBuilder().getActiveProfile().getProfile();
        if (profile != null) {
            dia = profile.getDia();
        }
        if (ev.newNSProfile == null) { // on init no need of reset
            return;
        }
        synchronized (dataLock) {
            log.debug("Invalidating cached data because of new profile from " + ev.from + ". IOB: " + iobTable.size() + " Autosens: " + autosensDataTable.size() + " records");
            iobTable = new LongSparseArray<>();
            autosensDataTable = new LongSparseArray<>();
        }
        sHandler.post(new Runnable() {
            @Override
            public void run() {
                calculateSensitivityData();
            }
        });
    }

    // When historical data is changed (comming from NS etc) finished calculations after this date must be invalidated
    @Subscribe
    public void onNewHistoryData(EventNewHistoryData ev) {
        //log.debug("Locking onNewHistoryData");
        synchronized (dataLock) {
            long time = ev.time;
            log.debug("Invalidating cached data to: " + new Date(time).toLocaleString());
            for (int index = iobTable.size() - 1; index >= 0; index--) {
                if (iobTable.keyAt(index) > time) {
                    log.debug("Removing from iobTable: " + new Date(iobTable.keyAt(index)).toLocaleString());
                    iobTable.removeAt(index);
                } else {
                    break;
                }
            }
            for (int index = autosensDataTable.size() - 1; index >= 0; index--) {
                if (autosensDataTable.keyAt(index) > time) {
                    log.debug("Removing from autosensDataTable: " + new Date(autosensDataTable.keyAt(index)).toLocaleString());
                    autosensDataTable.removeAt(index);
                } else {
                    break;
                }
            }
        }
        sHandler.post(new Runnable() {
            @Override
            public void run() {
                calculateSensitivityData();
            }
        });
        //log.debug("Releasing onNewHistoryData");
    }

    // From https://gist.github.com/IceCreamYou/6ffa1b18c4c8f6aeaad2
    // Returns the value at a given percentile in a sorted numeric array.
    // "Linear interpolation between closest ranks" method
    public static double percentile(Double[] arr, double p) {
        if (arr.length == 0) return 0;
        if (p <= 0) return arr[0];
        if (p >= 1) return arr[arr.length - 1];

        double index = arr.length * p,
                lower = Math.floor(index),
                upper = lower + 1,
                weight = index % 1;

        if (upper >= arr.length) return arr[(int) lower];
        return arr[(int) lower] * (1 - weight) + arr[(int) upper] * weight;
    }
}

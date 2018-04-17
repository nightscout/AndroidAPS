package info.nightscout.androidaps.plugins.IobCobCalculator;

import android.os.SystemClock;
import android.support.annotation.Nullable;
import android.support.v4.util.LongSparseArray;

import com.squareup.otto.Subscribe;

import org.json.JSONArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.IobTotal;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.db.BgReading;
import info.nightscout.androidaps.db.TemporaryBasal;
import info.nightscout.androidaps.events.EventAppInitialized;
import info.nightscout.androidaps.events.EventConfigBuilderChange;
import info.nightscout.androidaps.events.EventNewBG;
import info.nightscout.androidaps.events.EventNewBasalProfile;
import info.nightscout.androidaps.events.EventPreferenceChange;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.IobCobCalculator.events.EventNewHistoryData;
import info.nightscout.utils.DateUtil;

/**
 * Created by mike on 24.04.2017.
 */

public class IobCobCalculatorPlugin implements PluginBase {
    private static Logger log = LoggerFactory.getLogger(IobCobCalculatorPlugin.class);

    private static LongSparseArray<IobTotal> iobTable = new LongSparseArray<>(); // oldest at index 0
    private static LongSparseArray<AutosensData> autosensDataTable = new LongSparseArray<>(); // oldest at index 0
    private static LongSparseArray<BasalData> basalDataTable = new LongSparseArray<>(); // oldest at index 0

    private static volatile List<BgReading> bgReadings = null; // newest at index 0
    private static volatile List<BgReading> bucketed_data = null;

    private static double dia = Constants.defaultDIA;

    static final Object dataLock = new Object();

    boolean stopCalculationTrigger = false;
    IobCobThread thread = null;

    private static IobCobCalculatorPlugin plugin = null;

    public static IobCobCalculatorPlugin getPlugin() {
        if (plugin == null)
            plugin = new IobCobCalculatorPlugin();
        return plugin;
    }

    public static LongSparseArray<AutosensData> getAutosensDataTable() {
        return autosensDataTable;
    }

    public static List<BgReading> getBucketedData() {
        return bucketed_data;
    }

    @Override
    public int getType() {
        return GENERAL;
    }

    @Override
    public String getFragmentClass() {
        return null;
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

    @Override
    public int getPreferencesId() {
        return -1;
    }

    IobCobCalculatorPlugin() {
        MainApp.bus().register(this);
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
            if (bucketed_data.get(index).date < time)
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

    void loadBgData() {
        bgReadings = MainApp.getDbHelper().getBgreadingsDataFromTime((long) (System.currentTimeMillis() - 60 * 60 * 1000L * (24 + dia)), false);
        log.debug("BG data loaded. Size: " + bgReadings.size());
    }

    private boolean isAbout5minData() {
        synchronized (dataLock) {
            if (bgReadings == null || bgReadings.size() < 3) {
                return true;
            }
            long totalDiff = 0;
            for (int i = 1; i < bgReadings.size(); ++i) {
                long bgTime = bgReadings.get(i).date;
                long lastbgTime = bgReadings.get(i - 1).date;
                long diff = lastbgTime - bgTime;
                totalDiff += diff;
                if (diff > 30 * 1000 && diff < 270 * 1000) { // 0:30 - 4:30
                    log.debug("Interval detection: values: " + bgReadings.size() + " diff: " + (diff / 1000) + "sec is5minData: " + false);
                    return false;
                }
            }
            double intervals = totalDiff / (5 * 60 * 1000d);
            double variability = Math.abs(intervals - Math.round(intervals));
            boolean is5mindata = variability < 0.02;
            log.debug("Interval detection: values: " + bgReadings.size() + " variability: " + variability + " is5minData: " + is5mindata);
            return is5mindata;
        }
    }

    void createBucketedData() {
        if (isAbout5minData())
            createBucketedData5min();
        else
            createBucketedDataRecalculated();
    }

    @Nullable
    private BgReading findNewer(long time) {
        BgReading lastFound = bgReadings.get(0);
        if (lastFound.date < time) return null;
        for (int i = 1; i < bgReadings.size(); ++i) {
            if (bgReadings.get(i).date > time) continue;
            lastFound = bgReadings.get(i);
            if (bgReadings.get(i).date < time) break;
        }
        return lastFound;
    }

    @Nullable
    private BgReading findOlder(long time) {
        BgReading lastFound = bgReadings.get(bgReadings.size() - 1);
        if (lastFound.date > time) return null;
        for (int i = bgReadings.size() - 2; i >= 0; --i) {
            if (bgReadings.get(i).date < time) continue;
            lastFound = bgReadings.get(i);
            if (bgReadings.get(i).date > time) break;
        }
        return lastFound;
    }

    private void createBucketedDataRecalculated() {
        if (bgReadings == null || bgReadings.size() < 3) {
            bucketed_data = null;
            return;
        }

        bucketed_data = new ArrayList<>();
        long currentTime = bgReadings.get(0).date + 5 * 60 * 1000 - bgReadings.get(0).date % (5 * 60 * 1000) - 5 * 60 * 1000L;
        //log.debug("First reading: " + new Date(currentTime).toLocaleString());

        while (true) {
            // test if current value is older than current time
            BgReading newer = findNewer(currentTime);
            BgReading older = findOlder(currentTime);
            if (newer == null || older == null)
                break;

            double bgDelta = newer.value - older.value;
            long timeDiffToNew = newer.date - currentTime;

            double currentBg = newer.value - (double) timeDiffToNew / (newer.date - older.date) * bgDelta;
            BgReading newBgreading = new BgReading();
            newBgreading.date = currentTime;
            newBgreading.value = Math.round(currentBg);
            bucketed_data.add(newBgreading);
            //log.debug("BG: " + newBgreading.value + " (" + new Date(newBgreading.date).toLocaleString() + ") Prev: " + older.value + " (" + new Date(older.date).toLocaleString() + ") Newer: " + newer.value + " (" + new Date(newer.date).toLocaleString() + ")");
            currentTime -= 5 * 60 * 1000L;

        }
    }


    public void createBucketedData5min() {
        if (bgReadings == null || bgReadings.size() < 3) {
            bucketed_data = null;
            return;
        }

        bucketed_data = new ArrayList<>();
        bucketed_data.add(bgReadings.get(0));
        int j = 0;
        for (int i = 1; i < bgReadings.size(); ++i) {
            long bgTime = bgReadings.get(i).date;
            long lastbgTime = bgReadings.get(i - 1).date;
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
                    newBgreading.date = nextbgTime;
                    double gapDelta = bgReadings.get(i).value - lastbg;
                    //console.error(gapDelta, lastbg, elapsed_minutes);
                    double nextbg = lastbg + (5d / elapsed_minutes * gapDelta);
                    newBgreading.value = Math.round(nextbg);
                    //console.error("Interpolated", bucketed_data[j]);
                    bucketed_data.add(newBgreading);
                    //log.error("******************************************************************************************************* Adding:" + new Date(newBgreading.date).toString() + " " + newBgreading.value);

                    elapsed_minutes = elapsed_minutes - 5;
                    lastbg = nextbg;
                    lastbgTime = nextbgTime;
                }
                j++;
                BgReading newBgreading = new BgReading();
                newBgreading.value = bgReadings.get(i).value;
                newBgreading.date = bgTime;
                bucketed_data.add(newBgreading);
                //log.error("******************************************************************************************************* Copying:" + new Date(newBgreading.date).toString() + " " + newBgreading.value);
            } else if (Math.abs(elapsed_minutes) > 2) {
                j++;
                BgReading newBgreading = new BgReading();
                newBgreading.value = bgReadings.get(i).value;
                newBgreading.date = bgTime;
                bucketed_data.add(newBgreading);
                //log.error("******************************************************************************************************* Copying:" + new Date(newBgreading.date).toString() + " " + newBgreading.value);
            } else {
                bucketed_data.get(j).value = (bucketed_data.get(j).value + bgReadings.get(i).value) / 2;
                //log.error("***** Average");
            }
        }
        log.debug("Bucketed data created. Size: " + bucketed_data.size());
    }

    public static long oldestDataAvailable() {
        long now = System.currentTimeMillis();

        long oldestDataAvailable = MainApp.getConfigBuilder().oldestDataAvailable();
        long getBGDataFrom = Math.max(oldestDataAvailable, (long) (now - 60 * 60 * 1000L * (24 + MainApp.getConfigBuilder().getProfile().getDia())));
        log.debug("Limiting data to oldest available temps: " + new Date(oldestDataAvailable).toString());
        return getBGDataFrom;
    }

    public static IobTotal calculateFromTreatmentsAndTempsSynchronized(long time) {
        synchronized (dataLock) {
            return calculateFromTreatmentsAndTemps(time);
        }
    }

    public static IobTotal calculateFromTreatmentsAndTemps(long time) {
        long now = System.currentTimeMillis();
        time = roundUpTime(time);
        if (time < now && iobTable.get(time) != null) {
            //og.debug(">>> calculateFromTreatmentsAndTemps Cache hit " + new Date(time).toLocaleString());
            return iobTable.get(time);
        } else {
            //log.debug(">>> calculateFromTreatmentsAndTemps Cache miss " + new Date(time).toLocaleString());
        }
        IobTotal bolusIob = MainApp.getConfigBuilder().getCalculationToTimeTreatments(time).round();
        IobTotal basalIob = MainApp.getConfigBuilder().getCalculationToTimeTempBasals(time).round();

        IobTotal iobTotal = IobTotal.combine(bolusIob, basalIob).round();
        if (time < System.currentTimeMillis()) {
            iobTable.put(time, iobTotal);
        }
        return iobTotal;
    }

    @Nullable
    private static Long findPreviousTimeFromBucketedData(long time) {
        if (bucketed_data == null)
            return null;
        for (int index = 0; index < bucketed_data.size(); index++) {
            if (bucketed_data.get(index).date < time)
                return bucketed_data.get(index).date;
        }
        return null;
    }

    public static BasalData getBasalData(long time) {
        long now = System.currentTimeMillis();
        time = roundUpTime(time);
        BasalData retval = basalDataTable.get(time);
        if (retval == null) {
            retval = new BasalData();
            TemporaryBasal tb = MainApp.getConfigBuilder().getTempBasalFromHistory(time);
            retval.basal = MainApp.getConfigBuilder().getProfile(time).getBasal(time);
            if (tb != null) {
                retval.isTempBasalRunning = true;
                retval.tempBasalAbsolute = tb.tempBasalConvertedToAbsolute(time);
            } else {
                retval.isTempBasalRunning = false;
                retval.tempBasalAbsolute = retval.basal;
            }
            if (time < now) {
                basalDataTable.append(time, retval);
            }
            //log.debug(">>> getBasalData Cache miss " + new Date(time).toLocaleString());
        } else {
            //log.debug(">>> getBasalData Cache hit " +  new Date(time).toLocaleString());
        }
        return retval;
    }

    @Nullable
    public static AutosensData getAutosensData(long time) {
        synchronized (dataLock) {
            long now = System.currentTimeMillis();
            if (time > now)
                return null;
            Long previous = findPreviousTimeFromBucketedData(time);
            if (previous == null)
                return null;
            time = roundUpTime(previous);
            AutosensData data = autosensDataTable.get(time);
            if (data != null) {
                //log.debug(">>> getAutosensData Cache hit " + data.log(time));
                return data;
            } else {
                if (time > now) {
                    // data may not be calculated yet, use last data
                    return getLastAutosensData("getAutosensData");
                }
                //log.debug(">>> getAutosensData Cache miss " + new Date(time).toLocaleString());
                return null;
            }
        }
    }

    @Nullable
    public static AutosensData getLastAutosensDataSynchronized(String reason) {
        synchronized (dataLock) {
            return getLastAutosensData(reason);
        }
    }


    @Nullable
    public static AutosensData getLastAutosensData(String reason) {
        if (autosensDataTable.size() < 1) {
            log.debug("AUTOSENSDATA null: autosensDataTable empty (" + reason + ")");
            return null;
        }
        AutosensData data = null;
        try {
            data = autosensDataTable.valueAt(autosensDataTable.size() - 1);
        } catch (Exception e) {
            // data can be processed on the background
            // in this rare case better return null and do not block UI
            // APS plugin should use getLastAutosensDataSynchronized where the blocking is not an issue
            log.debug("AUTOSENSDATA null: Exception catched (" + reason + ")");
            return null;
        }
        if (data.time < System.currentTimeMillis() - 11 * 60 * 1000) {
            log.debug("AUTOSENSDATA null: data is old (" + reason + ") size()=" + autosensDataTable.size() + " lastdata=" + DateUtil.dateAndTimeString(data.time));
            return null;
        } else {
            if (data == null)
                log.debug("AUTOSENSDATA null: data == null (" + " " + reason + ") size()=" + autosensDataTable.size() + " lastdata=" + DateUtil.dateAndTimeString(data.time));
            return data;
        }
    }

    public static IobTotal[] calculateIobArrayInDia() {
        Profile profile = MainApp.getConfigBuilder().getProfile();
        // predict IOB out to DIA plus 30m
        long time = System.currentTimeMillis();
        time = roundUpTime(time);
        int len = (int) ((profile.getDia() * 60 + 30) / 5);
        IobTotal[] array = new IobTotal[len];
        int pos = 0;
        for (int i = 0; i < len; i++) {
            long t = time + i * 5 * 60000;
            IobTotal iob = calculateFromTreatmentsAndTempsSynchronized(t);
            array[pos] = iob;
            pos++;
        }
        return array;
    }

    public static AutosensResult detectSensitivityWithLock(long fromTime, long toTime) {
        synchronized (dataLock) {
            return detectSensitivity(fromTime, toTime);
        }
    }

    static AutosensResult detectSensitivity(long fromTime, long toTime) {
        return ConfigBuilderPlugin.getActiveSensitivity().detectSensitivity(fromTime, toTime);
    }

    public static JSONArray convertToJSONArray(IobTotal[] iobArray) {
        JSONArray array = new JSONArray();
        for (int i = 0; i < iobArray.length; i++) {
            array.put(iobArray[i].determineBasalJson());
        }
        return array;
    }

    @Subscribe
    public void onEventAppInitialized(EventAppInitialized ev) {
        runCalculation("onEventAppInitialized", true);
    }

    @Subscribe
    public void onEventNewBG(EventNewBG ev) {
        stopCalculation("onEventNewBG");
        runCalculation("onEventNewBG", true);
    }

    private void stopCalculation(String from) {
        if (thread != null && thread.getState() != Thread.State.TERMINATED) {
            stopCalculationTrigger = true;
            log.debug("Stopping calculation thread: " + from);
            while (thread.getState() != Thread.State.TERMINATED) {
                SystemClock.sleep(100);
            }
            log.debug("Calculation thread stopped: " + from);
        }
    }

    private void runCalculation(String from, boolean bgDataReload) {
        log.debug("Starting calculation thread: " + from);
        if (thread == null || thread.getState() == Thread.State.TERMINATED) {
            thread = new IobCobThread(this, from, bgDataReload);
            thread.start();
        }
    }

    @Subscribe
    public void onNewProfile(EventNewBasalProfile ev) {
        if (MainApp.getConfigBuilder() == null)
            return; // app still initializing
        Profile profile = MainApp.getConfigBuilder().getProfile();
        if (profile == null)
            return; // app still initializing
        dia = profile.getDia();
        if (ev == null) { // on init no need of reset
            return;
        }
        stopCalculation("onNewProfile");
        synchronized (dataLock) {
            log.debug("Invalidating cached data because of new profile. IOB: " + iobTable.size() + " Autosens: " + autosensDataTable.size() + " records");
            iobTable = new LongSparseArray<>();
            autosensDataTable = new LongSparseArray<>();
        }
        runCalculation("onNewProfile", false);
    }

    @Subscribe
    public void onEventPreferenceChange(EventPreferenceChange ev) {
        if (ev.isChanged(R.string.key_openapsama_autosens_period) ||
                ev.isChanged(R.string.key_age) ||
                ev.isChanged(R.string.key_absorption_maxtime)
                ) {
            stopCalculation("onEventPreferenceChange");
            synchronized (dataLock) {
                log.debug("Invalidating cached data because of preference change. IOB: " + iobTable.size() + " Autosens: " + autosensDataTable.size() + " records");
                iobTable = new LongSparseArray<>();
                autosensDataTable = new LongSparseArray<>();
            }
            runCalculation("onEventPreferenceChange", false);
        }
    }

    @Subscribe
    public void onEventConfigBuilderChange(EventConfigBuilderChange ev) {
        stopCalculation("onEventConfigBuilderChange");
        synchronized (dataLock) {
            log.debug("Invalidating cached data because of configuration change. IOB: " + iobTable.size() + " Autosens: " + autosensDataTable.size() + " records");
            iobTable = new LongSparseArray<>();
            autosensDataTable = new LongSparseArray<>();
        }
        runCalculation("onEventConfigBuilderChange", false);
    }

    // When historical data is changed (comming from NS etc) finished calculations after this date must be invalidated
    @Subscribe
    public void onEventNewHistoryData(EventNewHistoryData ev) {
        //log.debug("Locking onNewHistoryData");
        stopCalculation("onEventNewHistoryData");
        synchronized (dataLock) {
            // clear up 5 min back for proper COB calculation
            long time = ev.time - 5 * 60 * 1000L;
            log.debug("Invalidating cached data to: " + new Date(time).toLocaleString());
            for (int index = iobTable.size() - 1; index >= 0; index--) {
                if (iobTable.keyAt(index) > time) {
                    if (Config.logAutosensData)
                        log.debug("Removing from iobTable: " + new Date(iobTable.keyAt(index)).toLocaleString());
                    iobTable.removeAt(index);
                } else {
                    break;
                }
            }
            for (int index = autosensDataTable.size() - 1; index >= 0; index--) {
                if (autosensDataTable.keyAt(index) > time) {
                    if (Config.logAutosensData)
                        log.debug("Removing from autosensDataTable: " + new Date(autosensDataTable.keyAt(index)).toLocaleString());
                    autosensDataTable.removeAt(index);
                } else {
                    break;
                }
            }
            for (int index = basalDataTable.size() - 1; index >= 0; index--) {
                if (basalDataTable.keyAt(index) > time) {
                    if (Config.logAutosensData)
                        log.debug("Removing from basalDataTable: " + new Date(basalDataTable.keyAt(index)).toLocaleString());
                    basalDataTable.removeAt(index);
                } else {
                    break;
                }
            }
        }
        runCalculation("onEventNewHistoryData", false);
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

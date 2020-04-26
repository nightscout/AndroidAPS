package info.nightscout.androidaps.plugins.iob.iobCobCalculator;

import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.collection.LongSparseArray;

import org.json.JSONArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.IobTotal;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.db.BgReading;
import info.nightscout.androidaps.db.TemporaryBasal;
import info.nightscout.androidaps.events.Event;
import info.nightscout.androidaps.events.EventAppInitialized;
import info.nightscout.androidaps.events.EventConfigBuilderChange;
import info.nightscout.androidaps.events.EventNewBG;
import info.nightscout.androidaps.events.EventNewBasalProfile;
import info.nightscout.androidaps.events.EventPreferenceChange;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.PluginDescription;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.aps.openAPSSMB.OpenAPSSMBPlugin;
import info.nightscout.androidaps.plugins.bus.RxBus;
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunctions;
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.events.EventNewHistoryData;
import info.nightscout.androidaps.plugins.sensitivity.SensitivityOref1Plugin;
import info.nightscout.androidaps.plugins.treatments.Treatment;
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.DecimalFormatter;
import info.nightscout.androidaps.utils.FabricPrivacy;
import info.nightscout.androidaps.utils.T;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;

import static info.nightscout.androidaps.utils.DateUtil.now;

/**
 * Created by mike on 24.04.2017.
 */

public class IobCobCalculatorPlugin extends PluginBase {
    private Logger log = LoggerFactory.getLogger(L.AUTOSENS);
    private CompositeDisposable disposable = new CompositeDisposable();

    private static IobCobCalculatorPlugin plugin = null;

    public static IobCobCalculatorPlugin getPlugin() {
        if (plugin == null)
            plugin = new IobCobCalculatorPlugin();
        return plugin;
    }

    private LongSparseArray<IobTotal> iobTable = new LongSparseArray<>(); // oldest at index 0
    private LongSparseArray<AutosensData> autosensDataTable = new LongSparseArray<>(); // oldest at index 0
    private LongSparseArray<BasalData> basalDataTable = new LongSparseArray<>(); // oldest at index 0

    private volatile List<BgReading> bgReadings = null; // newest at index 0
    private volatile List<BgReading> bucketed_data = null;

    private final Object dataLock = new Object();

    boolean stopCalculationTrigger = false;
    private Thread thread = null;

    public IobCobCalculatorPlugin() {
        super(new PluginDescription()
                .mainType(PluginType.GENERAL)
                .pluginName(R.string.iobcobcalculator)
                .showInList(false)
                .neverVisible(true)
                .alwaysEnabled(true)
        );
    }

    @Override
    protected void onStart() {
        super.onStart();
        // EventConfigBuilderChange
        disposable.add(RxBus.INSTANCE
                .toObservable(EventConfigBuilderChange.class)
                .observeOn(Schedulers.io())
                .subscribe(event -> {
                    if (this != getPlugin()) {
                        if (L.isEnabled(L.AUTOSENS))
                            log.debug("Ignoring event for non default instance");
                        return;
                    }
                    stopCalculation("onEventConfigBuilderChange");
                    synchronized (dataLock) {
                        if (L.isEnabled(L.AUTOSENS))
                            log.debug("Invalidating cached data because of configuration change. IOB: " + iobTable.size() + " Autosens: " + autosensDataTable.size() + " records");
                        iobTable = new LongSparseArray<>();
                        autosensDataTable = new LongSparseArray<>();
                    }
                    runCalculation("onEventConfigBuilderChange", System.currentTimeMillis(), false, true, event);
                }, FabricPrivacy::logException)
        );
        // EventNewBasalProfile
        disposable.add(RxBus.INSTANCE
                .toObservable(EventNewBasalProfile.class)
                .observeOn(Schedulers.io())
                .subscribe(event -> {
                    if (this != getPlugin()) {
                        if (L.isEnabled(L.AUTOSENS))
                            log.debug("Ignoring event for non default instance");
                        return;
                    }
                    if (ConfigBuilderPlugin.getPlugin() == null)
                        return; // app still initializing
                    if (event == null) { // on init no need of reset
                        return;
                    }
                    stopCalculation("onNewProfile");
                    synchronized (dataLock) {
                        if (L.isEnabled(L.AUTOSENS))
                            log.debug("Invalidating cached data because of new profile. IOB: " + iobTable.size() + " Autosens: " + autosensDataTable.size() + " records");
                        iobTable = new LongSparseArray<>();
                        autosensDataTable = new LongSparseArray<>();
                        basalDataTable = new LongSparseArray<>();
                    }
                    runCalculation("onNewProfile", System.currentTimeMillis(), false, true, event);
                }, FabricPrivacy::logException)
        );
        // EventNewBG
        disposable.add(RxBus.INSTANCE
                .toObservable(EventNewBG.class)
                .observeOn(Schedulers.io())
                .subscribe(event -> {
                    if (this != getPlugin()) {
                        if (L.isEnabled(L.AUTOSENS))
                            log.debug("Ignoring event for non default instance");
                        return;
                    }
                    stopCalculation("onEventNewBG");
                    runCalculation("onEventNewBG", System.currentTimeMillis(), true, true, event);
                }, FabricPrivacy::logException)
        );
        // EventPreferenceChange
        disposable.add(RxBus.INSTANCE
                .toObservable(EventPreferenceChange.class)
                .observeOn(Schedulers.io())
                .subscribe(event -> {
                    if (this != getPlugin()) {
                        if (L.isEnabled(L.AUTOSENS))
                            log.debug("Ignoring event for non default instance");
                        return;
                    }
                    if (event.isChanged(R.string.key_openapsama_autosens_period) ||
                            event.isChanged(R.string.key_age) ||
                            event.isChanged(R.string.key_absorption_maxtime) ||
                            event.isChanged(R.string.key_openapsama_min_5m_carbimpact) ||
                            event.isChanged(R.string.key_absorption_cutoff) ||
                            event.isChanged(R.string.key_openapsama_autosens_max) ||
                            event.isChanged(R.string.key_openapsama_autosens_min) ||
                            event.isChanged(R.string.key_insulin_oref_peak)
                    ) {
                        stopCalculation("onEventPreferenceChange");
                        synchronized (dataLock) {
                            if (L.isEnabled(L.AUTOSENS))
                                log.debug("Invalidating cached data because of preference change. IOB: " + iobTable.size() + " Autosens: " + autosensDataTable.size() + " records" + " BasalData: " + basalDataTable.size() + " records");
                            iobTable = new LongSparseArray<>();
                            autosensDataTable = new LongSparseArray<>();
                            basalDataTable = new LongSparseArray<>();
                        }
                        runCalculation("onEventPreferenceChange", System.currentTimeMillis(), false, true, event);
                    }
                }, FabricPrivacy::logException)
        );
        // EventAppInitialized
        disposable.add(RxBus.INSTANCE
                .toObservable(EventAppInitialized.class)
                .observeOn(Schedulers.io())
                .subscribe(event -> {
                    if (this != getPlugin()) {
                        if (L.isEnabled(L.AUTOSENS))
                            log.debug("Ignoring event for non default instance");
                        return;
                    }
                    runCalculation("onEventAppInitialized", System.currentTimeMillis(), true, true, event);
                }, FabricPrivacy::logException)
        );
        // EventNewHistoryData
        disposable.add(RxBus.INSTANCE
                .toObservable(EventNewHistoryData.class)
                .observeOn(Schedulers.io())
                .subscribe(event -> newHistoryData(event), FabricPrivacy::logException)
        );
    }

    @Override
    protected void onStop() {
        disposable.clear();
        super.onStop();
    }

    public LongSparseArray<AutosensData> getAutosensDataTable() {
        return autosensDataTable;
    }

    public List<BgReading> getBgReadings() {
        return bgReadings;
    }

    public void setBgReadings(List<BgReading> bgReadings) {
        this.bgReadings = bgReadings;
    }

    public List<BgReading> getBucketedData() {
        return bucketed_data;
    }

    public Object getDataLock() {
        return dataLock;
    }

    // roundup to whole minute
    public static long roundUpTime(long time) {
        if (time % 60000 == 0)
            return time;
        long rounded = (time / 60000 + 1) * 60000;
        return rounded;
    }

    void loadBgData(long to) {
        Profile profile = ProfileFunctions.getInstance().getProfile(to);
        double dia = Constants.defaultDIA;
        if (profile != null) dia = profile.getDia();
        long start = to - T.hours((long) (24 + dia)).msecs();
        if (DateUtil.isCloseToNow(to)) {
            // if close to now expect there can be some readings with time in close future (caused by wrong time setting)
            // so read all records
            bgReadings = MainApp.getDbHelper().getBgreadingsDataFromTime(start, false);
            if (L.isEnabled(L.AUTOSENS))
                log.debug("BG data loaded. Size: " + bgReadings.size() + " Start date: " + DateUtil.dateAndTimeString(start));
        } else {
            bgReadings = MainApp.getDbHelper().getBgreadingsDataFromTime(start, to, false);
            if (L.isEnabled(L.AUTOSENS))
                log.debug("BG data loaded. Size: " + bgReadings.size() + " Start date: " + DateUtil.dateAndTimeString(start) + " End date: " + DateUtil.dateAndTimeString(to));
        }
    }

    public boolean isAbout5minData() {
        synchronized (dataLock) {
            if (bgReadings == null || bgReadings.size() < 3) {
                return true;
            }
            long totalDiff = 0;
            for (int i = 1; i < bgReadings.size(); ++i) {
                long bgTime = bgReadings.get(i).date;
                long lastbgTime = bgReadings.get(i - 1).date;
                long diff = lastbgTime - bgTime;
                diff %= T.mins(5).msecs();
                if (diff > T.mins(2).plus(T.secs(30)).msecs())
                    diff = diff - T.mins(5).msecs();
                totalDiff += diff;
                diff = Math.abs(diff);
                if (diff > T.secs(30).msecs()) {
                    if (L.isEnabled(L.AUTOSENS))
                        log.debug("Interval detection: values: " + bgReadings.size() + " diff: " + (diff / 1000) + "[s] is5minData: " + false);
                    return false;
                }
            }
            long averageDiff = totalDiff / bgReadings.size() / 1000;
            boolean is5mindata = averageDiff < 1;
            if (L.isEnabled(L.AUTOSENS))
                log.debug("Interval detection: values: " + bgReadings.size() + " averageDiff: " + averageDiff + "[s] is5minData: " + is5mindata);
            return is5mindata;
        }
    }

    public void createBucketedData() {
        if (isAbout5minData())
            createBucketedData5min();
        else
            createBucketedDataRecalculated();
    }

    @Nullable
    public BgReading findNewer(long time) {
        BgReading lastFound = bgReadings.get(0);
        if (lastFound.date < time) return null;
        for (int i = 1; i < bgReadings.size(); ++i) {
            if (bgReadings.get(i).date == time) return bgReadings.get(i);
            if (bgReadings.get(i).date > time) continue;
            lastFound = bgReadings.get(i - 1);
            if (bgReadings.get(i).date < time) break;
        }
        return lastFound;
    }

    @Nullable
    public BgReading findOlder(long time) {
        BgReading lastFound = bgReadings.get(bgReadings.size() - 1);
        if (lastFound.date > time) return null;
        for (int i = bgReadings.size() - 2; i >= 0; --i) {
            if (bgReadings.get(i).date == time) return bgReadings.get(i);
            if (bgReadings.get(i).date < time) continue;
            lastFound = bgReadings.get(i + 1);
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
        long currentTime = bgReadings.get(0).date - bgReadings.get(0).date % T.mins(5).msecs();
        //log.debug("First reading: " + new Date(currentTime).toLocaleString());

        while (true) {
            // test if current value is older than current time
            BgReading newer = findNewer(currentTime);
            BgReading older = findOlder(currentTime);
            if (newer == null || older == null)
                break;

            if (older.date == newer.date) { // direct hit
                bucketed_data.add(newer);
            } else {
                double bgDelta = newer.value - older.value;
                long timeDiffToNew = newer.date - currentTime;

                double currentBg = newer.value - (double) timeDiffToNew / (newer.date - older.date) * bgDelta;
                BgReading newBgreading = new BgReading();
                newBgreading.date = currentTime;
                newBgreading.value = Math.round(currentBg);
                bucketed_data.add(newBgreading);
                //log.debug("BG: " + newBgreading.value + " (" + new Date(newBgreading.date).toLocaleString() + ") Prev: " + older.value + " (" + new Date(older.date).toLocaleString() + ") Newer: " + newer.value + " (" + new Date(newer.date).toLocaleString() + ")");
            }
            currentTime -= T.mins(5).msecs();

        }
    }


    private void createBucketedData5min() {
        if (bgReadings == null || bgReadings.size() < 3) {
            bucketed_data = null;
            return;
        }

        bucketed_data = new ArrayList<>();
        bucketed_data.add(bgReadings.get(0));
        if (L.isEnabled(L.AUTOSENS))
            log.debug("Adding. bgTime: " + DateUtil.toISOString(bgReadings.get(0).date) + " lastbgTime: " + "none-first-value" + " " + bgReadings.get(0).toString());
        int j = 0;
        for (int i = 1; i < bgReadings.size(); ++i) {
            long bgTime = bgReadings.get(i).date;
            long lastbgTime = bgReadings.get(i - 1).date;
            //log.error("Processing " + i + ": " + new Date(bgTime).toString() + " " + bgReadings.get(i).value + "   Previous: " + new Date(lastbgTime).toString() + " " + bgReadings.get(i - 1).value);
            if (bgReadings.get(i).value < 39 || bgReadings.get(i - 1).value < 39) {
                throw new IllegalStateException("<39");
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
                    if (L.isEnabled(L.AUTOSENS))
                        log.debug("Adding. bgTime: " + DateUtil.toISOString(bgTime) + " lastbgTime: " + DateUtil.toISOString(lastbgTime) + " " + newBgreading.toString());

                    elapsed_minutes = elapsed_minutes - 5;
                    lastbg = nextbg;
                    lastbgTime = nextbgTime;
                }
                j++;
                BgReading newBgreading = new BgReading();
                newBgreading.value = bgReadings.get(i).value;
                newBgreading.date = bgTime;
                bucketed_data.add(newBgreading);
                if (L.isEnabled(L.AUTOSENS))
                    log.debug("Adding. bgTime: " + DateUtil.toISOString(bgTime) + " lastbgTime: " + DateUtil.toISOString(lastbgTime) + " " + newBgreading.toString());
            } else if (Math.abs(elapsed_minutes) > 2) {
                j++;
                BgReading newBgreading = new BgReading();
                newBgreading.value = bgReadings.get(i).value;
                newBgreading.date = bgTime;
                bucketed_data.add(newBgreading);
                if (L.isEnabled(L.AUTOSENS))
                    log.debug("Adding. bgTime: " + DateUtil.toISOString(bgTime) + " lastbgTime: " + DateUtil.toISOString(lastbgTime) + " " + newBgreading.toString());
            } else {
                bucketed_data.get(j).value = (bucketed_data.get(j).value + bgReadings.get(i).value) / 2;
                //log.error("***** Average");
            }
        }

        // Normalize bucketed data
        for (int i = bucketed_data.size() - 2; i >= 0; i--) {
            BgReading current = bucketed_data.get(i);
            BgReading previous = bucketed_data.get(i + 1);
            long msecDiff = current.date - previous.date;
            long adjusted = (msecDiff - T.mins(5).msecs()) / 1000;
            if (L.isEnabled(L.AUTOSENS))
                log.debug("Adjusting bucketed data time. Current: " + DateUtil.toISOString(current.date) + " to: " + DateUtil.toISOString(previous.date + T.mins(5).msecs()) + " by " + adjusted + " sec");
            if (Math.abs(adjusted) > 90) {
                // too big adjustment, fallback to non 5 min data
                if (L.isEnabled(L.AUTOSENS))
                    log.debug("Fallback to non 5 min data");
                createBucketedDataRecalculated();
                return;
            }
            current.date = previous.date + T.mins(5).msecs();
        }

        if (L.isEnabled(L.AUTOSENS))
            log.debug("Bucketed data created. Size: " + bucketed_data.size());
    }

    public long calculateDetectionStart(long from, boolean limitDataToOldestAvailable) {
        Profile profile = ProfileFunctions.getInstance().getProfile(from);
        double dia = Constants.defaultDIA;
        if (profile != null) dia = profile.getDia();

        long oldestDataAvailable = TreatmentsPlugin.getPlugin().oldestDataAvailable();
        long getBGDataFrom;
        if (limitDataToOldestAvailable) {
            getBGDataFrom = Math.max(oldestDataAvailable, (long) (from - T.hours(1).msecs() * (24 + dia)));
            if (getBGDataFrom == oldestDataAvailable)
                if (L.isEnabled(L.AUTOSENS))
                    log.debug("Limiting data to oldest available temps: " + DateUtil.dateAndTimeFullString(oldestDataAvailable));
        } else
            getBGDataFrom = (long) (from - T.hours(1).msecs() * (24 + dia));
        return getBGDataFrom;
    }

    public IobTotal calculateFromTreatmentsAndTempsSynchronized(long time, Profile profile) {
        synchronized (dataLock) {
            return calculateFromTreatmentsAndTemps(time, profile);
        }
    }

    public IobTotal calculateFromTreatmentsAndTempsSynchronized(long time, AutosensResult lastAutosensResult, boolean exercise_mode, int half_basal_exercise_target, boolean isTempTarget) {
        synchronized (dataLock) {
            return calculateFromTreatmentsAndTemps(time, lastAutosensResult, exercise_mode, half_basal_exercise_target, isTempTarget);
        }
    }

    public IobTotal calculateFromTreatmentsAndTemps(long time, Profile profile) {
        long now = System.currentTimeMillis();
        time = roundUpTime(time);
        if (time < now && iobTable.get(time) != null) {
            //og.debug(">>> calculateFromTreatmentsAndTemps Cache hit " + new Date(time).toLocaleString());
            return iobTable.get(time);
        } else {
            //log.debug(">>> calculateFromTreatmentsAndTemps Cache miss " + new Date(time).toLocaleString());
        }
        IobTotal bolusIob = TreatmentsPlugin.getPlugin().getCalculationToTimeTreatments(time).round();
        IobTotal basalIob = TreatmentsPlugin.getPlugin().getCalculationToTimeTempBasals(time, true, now).round();
        if (OpenAPSSMBPlugin.getPlugin().isEnabled(PluginType.APS)) {
            // Add expected zero temp basal for next 240 mins
            IobTotal basalIobWithZeroTemp = basalIob.copy();
            TemporaryBasal t = new TemporaryBasal()
                    .date(now + 60 * 1000L)
                    .duration(240)
                    .absolute(0);
            if (t.date < time) {
                IobTotal calc = t.iobCalc(time, profile);
                basalIobWithZeroTemp.plus(calc);
            }

            basalIob.iobWithZeroTemp = IobTotal.combine(bolusIob, basalIobWithZeroTemp).round();
        }

        IobTotal iobTotal = IobTotal.combine(bolusIob, basalIob).round();
        if (time < System.currentTimeMillis()) {
            iobTable.put(time, iobTotal);
        }
        return iobTotal;
    }

    public IobTotal calculateFromTreatmentsAndTemps(long time, AutosensResult lastAutosensResult, boolean exercise_mode, int half_basal_exercise_target, boolean isTempTarget) {
        long now = DateUtil.now();

        IobTotal bolusIob = TreatmentsPlugin.getPlugin().getCalculationToTimeTreatments(time).round();
        IobTotal basalIob = TreatmentsPlugin.getPlugin().getCalculationToTimeTempBasals(time, now, lastAutosensResult, exercise_mode, half_basal_exercise_target, isTempTarget).round();
        if (OpenAPSSMBPlugin.getPlugin().isEnabled(PluginType.APS)) {
            // Add expected zero temp basal for next 240 mins
            IobTotal basalIobWithZeroTemp = basalIob.copy();
            TemporaryBasal t = new TemporaryBasal()
                    .date(now + 60 * 1000L)
                    .duration(240)
                    .absolute(0);
            if (t.date < time) {
                Profile profile = ProfileFunctions.getInstance().getProfile(t.date);
                if (profile != null) {
                    IobTotal calc = t.iobCalc(time, profile, lastAutosensResult, exercise_mode, half_basal_exercise_target, isTempTarget);
                    basalIobWithZeroTemp.plus(calc);
                }
            }

            basalIob.iobWithZeroTemp = IobTotal.combine(bolusIob, basalIobWithZeroTemp).round();
        }

        return IobTotal.combine(bolusIob, basalIob).round();
    }

    @Nullable
    public Long findPreviousTimeFromBucketedData(long time) {
        if (bucketed_data == null)
            return null;
        for (int index = 0; index < bucketed_data.size(); index++) {
            if (bucketed_data.get(index).date <= time)
                return bucketed_data.get(index).date;
        }
        return null;
    }

    public BasalData getBasalData(Profile profile, long time) {
        synchronized (dataLock) {
            long now = System.currentTimeMillis();
            time = roundUpTime(time);
            BasalData retval = basalDataTable.get(time);
            if (retval == null) {
                retval = new BasalData();
                TemporaryBasal tb = TreatmentsPlugin.getPlugin().getTempBasalFromHistory(time);
                retval.basal = profile.getBasal(time);
                if (tb != null) {
                    retval.isTempBasalRunning = true;
                    retval.tempBasalAbsolute = tb.tempBasalConvertedToAbsolute(time, profile);
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
    }

    @Nullable
    public AutosensData getAutosensData(long time) {
        synchronized (dataLock) {
            long now = System.currentTimeMillis();
            if (time > now) {
                return null;
            }
            Long previous = findPreviousTimeFromBucketedData(time);
            if (previous == null) {
                return null;
            }
            time = roundUpTime(previous);
            AutosensData data = autosensDataTable.get(time);
            if (data != null) {
                //log.debug(">>> AUTOSENSDATA Cache hit " + data.toString());
                return data;
            } else {
                //log.debug(">>> AUTOSENSDATA Cache miss " + new Date(time).toLocaleString());
                return null;
            }
        }
    }

    @Nullable
    public AutosensData getLastAutosensDataSynchronized(String reason) {
        if (thread != null && thread.isAlive()) {
            if (L.isEnabled(L.AUTOSENS))
                log.debug("AUTOSENSDATA is waiting for calculation thread: " + reason);
            try {
                thread.join(5000);
            } catch (InterruptedException ignored) {
            }
            if (L.isEnabled(L.AUTOSENS))
                log.debug("AUTOSENSDATA finished waiting for calculation thread: " + reason);
        }
        synchronized (dataLock) {
            return getLastAutosensData(reason);
        }
    }


    @NonNull
    public CobInfo getCobInfo(boolean _synchronized, String reason) {
        AutosensData autosensData = _synchronized ? getLastAutosensDataSynchronized(reason) : getLastAutosensData(reason);
        Double displayCob = null;
        double futureCarbs = 0;
        long now = now();
        List<Treatment> treatments = TreatmentsPlugin.getPlugin().getTreatmentsFromHistory();

        if (autosensData != null) {
            displayCob = autosensData.cob;
            for (Treatment treatment : treatments) {
                if (!treatment.isValid) continue;
                if (IobCobCalculatorPlugin.roundUpTime(treatment.date) > IobCobCalculatorPlugin.roundUpTime(autosensData.time)
                        && treatment.date <= now && treatment.carbs > 0) {
                    displayCob += treatment.carbs;
                }
            }
        }
        for (Treatment treatment : treatments) {
            if (!treatment.isValid) continue;
            if (treatment.date > now && treatment.carbs > 0) {
                futureCarbs += treatment.carbs;
            }
        }
        return new CobInfo(displayCob, futureCarbs);
    }

    public double slowAbsorptionPercentage(int timeInMinutes) {
        double sum = 0;
        int count = 0;
        int valuesToProcess = timeInMinutes / 5;
        synchronized (dataLock) {
            for (int i = autosensDataTable.size() - 1; i >= 0 && count < valuesToProcess; i--) {
                if (autosensDataTable.valueAt(i).failoverToMinAbsorbtionRate)
                    sum++;
                count++;
            }
        }
        return sum / count;
    }

    @Nullable
    public AutosensData getLastAutosensData(String reason) {
        if (autosensDataTable.size() < 1) {
            if (L.isEnabled(L.AUTOSENS))
                log.debug("AUTOSENSDATA null: autosensDataTable empty (" + reason + ")");
            return null;
        }
        AutosensData data;
        try {
            data = autosensDataTable.valueAt(autosensDataTable.size() - 1);
        } catch (Exception e) {
            // data can be processed on the background
            // in this rare case better return null and do not block UI
            // APS plugin should use getLastAutosensDataSynchronized where the blocking is not an issue
            log.debug("AUTOSENSDATA null: Exception catched (" + reason + ")");
            return null;
        }
        if (data == null) {
            log.debug("AUTOSENSDATA null: data==null");
            return null;
        }
        if (data.time < System.currentTimeMillis() - 11 * 60 * 1000) {
            if (L.isEnabled(L.AUTOSENS))
                log.debug("AUTOSENSDATA null: data is old (" + reason + ") size()=" + autosensDataTable.size() + " lastdata=" + DateUtil.dateAndTimeString(data.time));
            return null;
        } else {
            if (L.isEnabled(L.AUTOSENS))
                log.debug("AUTOSENSDATA (" + reason + ") " + data.toString());
            return data;
        }
    }

    public String lastDataTime() {
        if (autosensDataTable.size() > 0)
            return DateUtil.dateAndTimeString(autosensDataTable.valueAt(autosensDataTable.size() - 1).time);
        else
            return "autosensDataTable empty";
    }

    public IobTotal[] calculateIobArrayInDia(Profile profile) {
        // predict IOB out to DIA plus 30m
        long time = System.currentTimeMillis();
        time = roundUpTime(time);
        int len = (int) ((profile.getDia() * 60 + 30) / 5);
        IobTotal[] array = new IobTotal[len];
        int pos = 0;
        for (int i = 0; i < len; i++) {
            long t = time + i * 5 * 60000;
            IobTotal iob = calculateFromTreatmentsAndTempsSynchronized(t, profile);
            array[pos] = iob;
            pos++;
        }
        return array;
    }

    public IobTotal[] calculateIobArrayForSMB(AutosensResult lastAutosensResult, boolean exercise_mode, int half_basal_exercise_target, boolean isTempTarget) {
        // predict IOB out to DIA plus 30m
        long now = DateUtil.now();
        int len = (4 * 60) / 5;
        IobTotal[] array = new IobTotal[len];
        int pos = 0;
        for (int i = 0; i < len; i++) {
            long t = now + i * 5 * 60000;
            IobTotal iob = calculateFromTreatmentsAndTempsSynchronized(t, lastAutosensResult, exercise_mode, half_basal_exercise_target, isTempTarget);
            array[pos] = iob;
            pos++;
        }
        return array;
    }

    public String iobArrayToString(IobTotal[] array) {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (IobTotal i : array) {
            sb.append(DecimalFormatter.to2Decimal(i.iob));
            sb.append(", ");
        }
        sb.append("]");
        return sb.toString();
    }

    public AutosensResult detectSensitivityWithLock(long fromTime, long toTime) {
        synchronized (dataLock) {
            return ConfigBuilderPlugin.getPlugin().getActiveSensitivity().detectSensitivity(this, fromTime, toTime);
        }
    }

    public static JSONArray convertToJSONArray(IobTotal[] iobArray) {
        JSONArray array = new JSONArray();
        for (int i = 0; i < iobArray.length; i++) {
            array.put(iobArray[i].determineBasalJson());
        }
        return array;
    }

    public void stopCalculation(String from) {
        if (thread != null && thread.getState() != Thread.State.TERMINATED) {
            stopCalculationTrigger = true;
            if (L.isEnabled(L.AUTOSENS))
                log.debug("Stopping calculation thread: " + from);
            while (thread.getState() != Thread.State.TERMINATED) {
                SystemClock.sleep(100);
            }
            if (L.isEnabled(L.AUTOSENS))
                log.debug("Calculation thread stopped: " + from);
        }
    }

    public void runCalculation(String from, long end, boolean bgDataReload, boolean limitDataToOldestAvailable, Event cause) {
        if (L.isEnabled(L.AUTOSENS))
            log.debug("Starting calculation thread: " + from + " to " + DateUtil.dateAndTimeString(end));
        if (thread == null || thread.getState() == Thread.State.TERMINATED) {
            if (SensitivityOref1Plugin.getPlugin().isEnabled(PluginType.SENSITIVITY))
                thread = new IobCobOref1Thread(this, from, end, bgDataReload, limitDataToOldestAvailable, cause);
            else
                thread = new IobCobThread(this, from, end, bgDataReload, limitDataToOldestAvailable, cause);
            thread.start();
        }
    }

    // When historical data is changed (comming from NS etc) finished calculations after this date must be invalidated
    public void newHistoryData(EventNewHistoryData ev) {
        if (this != getPlugin()) {
            if (L.isEnabled(L.AUTOSENS))
                log.debug("Ignoring event for non default instance");
            return;
        }
        //log.debug("Locking onNewHistoryData");
        stopCalculation("onEventNewHistoryData");
        synchronized (dataLock) {
            // clear up 5 min back for proper COB calculation
            long time = ev.getTime() - 5 * 60 * 1000L;
            if (L.isEnabled(L.AUTOSENS))
                log.debug("Invalidating cached data to: " + DateUtil.dateAndTimeFullString(time));
            for (int index = iobTable.size() - 1; index >= 0; index--) {
                if (iobTable.keyAt(index) > time) {
                    if (L.isEnabled(L.AUTOSENS))
                        log.debug("Removing from iobTable: " + DateUtil.dateAndTimeFullString(iobTable.keyAt(index)));
                    iobTable.removeAt(index);
                } else {
                    break;
                }
            }
            for (int index = autosensDataTable.size() - 1; index >= 0; index--) {
                if (autosensDataTable.keyAt(index) > time) {
                    if (L.isEnabled(L.AUTOSENS))
                        log.debug("Removing from autosensDataTable: " + DateUtil.dateAndTimeFullString(autosensDataTable.keyAt(index)));
                    autosensDataTable.removeAt(index);
                } else {
                    break;
                }
            }
            for (int index = basalDataTable.size() - 1; index >= 0; index--) {
                if (basalDataTable.keyAt(index) > time) {
                    if (L.isEnabled(L.AUTOSENS))
                        log.debug("Removing from basalDataTable: " + DateUtil.dateAndTimeFullString(basalDataTable.keyAt(index)));
                    basalDataTable.removeAt(index);
                } else {
                    break;
                }
            }
        }
        runCalculation("onEventNewHistoryData", System.currentTimeMillis(), false, true, ev);
        //log.debug("Releasing onNewHistoryData");
    }

    public void clearCache() {
        synchronized (dataLock) {
            if (L.isEnabled(L.AUTOSENS))
                log.debug("Clearing cached data.");
            iobTable = new LongSparseArray<>();
            autosensDataTable = new LongSparseArray<>();
            basalDataTable = new LongSparseArray<>();
        }
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
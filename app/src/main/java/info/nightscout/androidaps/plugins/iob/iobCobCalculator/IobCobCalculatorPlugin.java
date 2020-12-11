package info.nightscout.androidaps.plugins.iob.iobCobCalculator;

import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.collection.LongSparseArray;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.android.HasAndroidInjector;
import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.IobTotal;
import info.nightscout.androidaps.data.MealData;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.db.BgReading;
import info.nightscout.androidaps.db.TemporaryBasal;
import info.nightscout.androidaps.events.Event;
import info.nightscout.androidaps.events.EventAppInitialized;
import info.nightscout.androidaps.events.EventConfigBuilderChange;
import info.nightscout.androidaps.events.EventNewBG;
import info.nightscout.androidaps.events.EventNewBasalProfile;
import info.nightscout.androidaps.events.EventPreferenceChange;
import info.nightscout.androidaps.interfaces.ActivePluginProvider;
import info.nightscout.androidaps.interfaces.IobCobCalculatorInterface;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.PluginDescription;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.bus.RxBusWrapper;
import info.nightscout.androidaps.interfaces.ProfileFunction;
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.data.AutosensData;
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.events.EventNewHistoryBgData;
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.events.EventNewHistoryData;
import info.nightscout.androidaps.plugins.sensitivity.SensitivityAAPSPlugin;
import info.nightscout.androidaps.plugins.sensitivity.SensitivityOref1Plugin;
import info.nightscout.androidaps.plugins.sensitivity.SensitivityWeightedAveragePlugin;
import info.nightscout.androidaps.db.Treatment;
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.DecimalFormatter;
import info.nightscout.androidaps.utils.FabricPrivacy;
import info.nightscout.androidaps.utils.T;
import info.nightscout.androidaps.utils.resources.ResourceHelper;
import info.nightscout.androidaps.utils.sharedPreferences.SP;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;

import static info.nightscout.androidaps.utils.DateUtil.now;

@Singleton
public class IobCobCalculatorPlugin extends PluginBase implements IobCobCalculatorInterface {
    private final HasAndroidInjector injector;
    private final SP sp;
    private final RxBusWrapper rxBus;
    private final ResourceHelper resourceHelper;
    private final ProfileFunction profileFunction;
    private final ActivePluginProvider activePlugin;
    private final TreatmentsPlugin treatmentsPlugin;
    private final SensitivityOref1Plugin sensitivityOref1Plugin;
    private final SensitivityAAPSPlugin sensitivityAAPSPlugin;
    private final SensitivityWeightedAveragePlugin sensitivityWeightedAveragePlugin;
    private final FabricPrivacy fabricPrivacy;
    private final DateUtil dateUtil;

    private final CompositeDisposable disposable = new CompositeDisposable();

    private LongSparseArray<IobTotal> iobTable = new LongSparseArray<>(); // oldest at index 0
    private LongSparseArray<IobTotal> absIobTable = new LongSparseArray<>(); // oldest at index 0, absolute insulin in the body
    private LongSparseArray<AutosensData> autosensDataTable = new LongSparseArray<>(); // oldest at index 0
    private LongSparseArray<BasalData> basalDataTable = new LongSparseArray<>(); // oldest at index 0

    // we need to make sure that bucketed_data will always have the same timestamp for correct use of cached values
    // once referenceTime != null all bucketed data should be (x * 5min) from referenceTime
    Long referenceTime = null;
    private Boolean lastUsed5minCalculation = null; // true if used 5min bucketed data

    private volatile List<BgReading> bgReadings = null; // newest at index 0
    private volatile List<InMemoryGlucoseValue> bucketed_data = null;

    private final Object dataLock = new Object();

    boolean stopCalculationTrigger = false;
    private Thread thread = null;

    @Inject
    public IobCobCalculatorPlugin(
            HasAndroidInjector injector,
            AAPSLogger aapsLogger,
            RxBusWrapper rxBus,
            SP sp,
            ResourceHelper resourceHelper,
            ProfileFunction profileFunction,
            ActivePluginProvider activePlugin,
            TreatmentsPlugin treatmentsPlugin,
            SensitivityOref1Plugin sensitivityOref1Plugin,
            SensitivityAAPSPlugin sensitivityAAPSPlugin,
            SensitivityWeightedAveragePlugin sensitivityWeightedAveragePlugin,
            FabricPrivacy fabricPrivacy,
            DateUtil dateUtil
    ) {
        super(new PluginDescription()
                        .mainType(PluginType.GENERAL)
                        .pluginName(R.string.iobcobcalculator)
                        .showInList(false)
                        .neverVisible(true)
                        .alwaysEnabled(true),
                aapsLogger, resourceHelper, injector
        );
        this.injector = injector;
        this.sp = sp;
        this.rxBus = rxBus;
        this.resourceHelper = resourceHelper;
        this.profileFunction = profileFunction;
        this.activePlugin = activePlugin;
        this.treatmentsPlugin = treatmentsPlugin;
        this.sensitivityOref1Plugin = sensitivityOref1Plugin;
        this.sensitivityAAPSPlugin = sensitivityAAPSPlugin;
        this.sensitivityWeightedAveragePlugin = sensitivityWeightedAveragePlugin;
        this.fabricPrivacy = fabricPrivacy;
        this.dateUtil = dateUtil;
    }

    @Override
    protected void onStart() {
        super.onStart();
        // EventConfigBuilderChange
        disposable.add(rxBus
                .toObservable(EventConfigBuilderChange.class)
                .observeOn(Schedulers.io())
                .subscribe(event -> {
                    stopCalculation("onEventConfigBuilderChange");
                    synchronized (dataLock) {
                        getAapsLogger().debug(LTag.AUTOSENS, "Invalidating cached data because of configuration change.");
                        resetData();
                    }
                    runCalculation("onEventConfigBuilderChange", System.currentTimeMillis(), false, true, event);
                }, fabricPrivacy::logException)
        );
        // EventNewBasalProfile
        disposable.add(rxBus
                .toObservable(EventNewBasalProfile.class)
                .observeOn(Schedulers.io())
                .subscribe(event -> {
                    if (event == null) { // on init no need of reset
                        return;
                    }
                    stopCalculation("onNewProfile");
                    synchronized (dataLock) {
                        getAapsLogger().debug(LTag.AUTOSENS, "Invalidating cached data because of new profile.");
                        resetData();
                    }
                    runCalculation("onNewProfile", System.currentTimeMillis(), false, true, event);
                }, fabricPrivacy::logException)
        );
        // EventNewBG .... cannot be used for invalidating because only event with last BG is fired
        disposable.add(rxBus
                .toObservable(EventNewBG.class)
                .observeOn(Schedulers.io())
                .subscribe(event -> {
                    stopCalculation("onEventNewBG");
                    runCalculation("onEventNewBG", System.currentTimeMillis(), true, true, event);
                }, fabricPrivacy::logException)
        );
        // EventPreferenceChange
        disposable.add(rxBus
                .toObservable(EventPreferenceChange.class)
                .observeOn(Schedulers.io())
                .subscribe(event -> {
                    if (event.isChanged(resourceHelper, R.string.key_openapsama_autosens_period) ||
                            event.isChanged(resourceHelper, R.string.key_age) ||
                            event.isChanged(resourceHelper, R.string.key_absorption_maxtime) ||
                            event.isChanged(resourceHelper, R.string.key_openapsama_min_5m_carbimpact) ||
                            event.isChanged(resourceHelper, R.string.key_absorption_cutoff) ||
                            event.isChanged(resourceHelper, R.string.key_openapsama_autosens_max) ||
                            event.isChanged(resourceHelper, R.string.key_openapsama_autosens_min) ||
                            event.isChanged(resourceHelper, R.string.key_insulin_oref_peak)
                    ) {
                        stopCalculation("onEventPreferenceChange");
                        synchronized (dataLock) {
                            getAapsLogger().debug(LTag.AUTOSENS, "Invalidating cached data because of preference change.");
                            resetData();
                        }
                        runCalculation("onEventPreferenceChange", System.currentTimeMillis(), false, true, event);
                    }
                }, fabricPrivacy::logException)
        );
        // EventAppInitialized
        disposable.add(rxBus
                .toObservable(EventAppInitialized.class)
                .observeOn(Schedulers.io())
                .subscribe(event -> runCalculation("onEventAppInitialized", System.currentTimeMillis(), true, true, event), fabricPrivacy::logException)
        );
        // EventNewHistoryData
        disposable.add(rxBus
                .toObservable(EventNewHistoryData.class)
                .observeOn(Schedulers.io())
                .subscribe(event -> newHistoryData(event, false), fabricPrivacy::logException)
        );
        // EventNewHistoryBgData
        disposable.add(rxBus
                .toObservable(EventNewHistoryBgData.class)
                .observeOn(Schedulers.io())
                .subscribe(event -> newHistoryData(new EventNewHistoryData(event.getTimestamp()), true), fabricPrivacy::logException)
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

    public List<InMemoryGlucoseValue> getBucketedData() {
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

    long adjustToReferenceTime(long someTime) {
        if (referenceTime == null) {
            referenceTime = someTime;
            return someTime;
        }
        long diff = Math.abs(someTime - referenceTime);
        diff %= T.mins(5).msecs();
        if (diff > T.mins(2).plus(T.secs(30)).msecs())
            diff = diff - T.mins(5).msecs();
        long newTime = someTime + diff;
        return newTime;
    }

    void loadBgData(long to) {
        Profile profile = profileFunction.getProfile(to);
        double dia = Constants.defaultDIA;
        if (profile != null) dia = profile.getDia();
        long start = to - T.hours((long) (24 + dia)).msecs();
        if (DateUtil.isCloseToNow(to)) {
            // if close to now expect there can be some readings with time in close future (caused by wrong time setting)
            // so read all records
            bgReadings = MainApp.getDbHelper().getBgreadingsDataFromTime(start, false);
            getAapsLogger().debug(LTag.AUTOSENS, "BG data loaded. Size: " + bgReadings.size() + " Start date: " + dateUtil.dateAndTimeString(start));
        } else {
            bgReadings = MainApp.getDbHelper().getBgreadingsDataFromTime(start, to, false);
            getAapsLogger().debug(LTag.AUTOSENS, "BG data loaded. Size: " + bgReadings.size() + " Start date: " + dateUtil.dateAndTimeString(start) + " End date: " + dateUtil.dateAndTimeString(to));
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
                    getAapsLogger().debug(LTag.AUTOSENS, "Interval detection: values: " + bgReadings.size() + " diff: " + (diff / 1000) + "[s] is5minData: " + false);
                    return false;
                }
            }
            long averageDiff = totalDiff / bgReadings.size() / 1000;
            boolean is5mindata = averageDiff < 1;
            getAapsLogger().debug(LTag.AUTOSENS, "Interval detection: values: " + bgReadings.size() + " averageDiff: " + averageDiff + "[s] is5minData: " + is5mindata);
            return is5mindata;
        }
    }

    private void resetData() {
        synchronized (dataLock) {
            iobTable = new LongSparseArray<>();
            autosensDataTable = new LongSparseArray<>();
            basalDataTable = new LongSparseArray<>();
            absIobTable = new LongSparseArray<>();
        }
    }

    public void createBucketedData() {
        boolean fiveMinData = isAbout5minData();
        if (lastUsed5minCalculation != null && lastUsed5minCalculation != fiveMinData) {
            // changing mode => clear cache
            getAapsLogger().debug("Invalidating cached data because of changed mode.");
            resetData();
        }
        lastUsed5minCalculation = fiveMinData;
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
        currentTime = adjustToReferenceTime(currentTime);
        getAapsLogger().debug("Adjusted time " + dateUtil.dateAndTimeAndSecondsString(currentTime));
        //log.debug("First reading: " + new Date(currentTime).toLocaleString());

        while (true) {
            // test if current value is older than current time
            BgReading newer = findNewer(currentTime);
            BgReading older = findOlder(currentTime);
            if (newer == null || older == null)
                break;

            if (older.date == newer.date) { // direct hit
                bucketed_data.add(new InMemoryGlucoseValue(newer));
            } else {
                double bgDelta = newer.value - older.value;
                long timeDiffToNew = newer.date - currentTime;

                double currentBg = newer.value - (double) timeDiffToNew / (newer.date - older.date) * bgDelta;
                InMemoryGlucoseValue newBgreading = new InMemoryGlucoseValue(currentTime, Math.round(currentBg), true);
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
        bucketed_data.add(new InMemoryGlucoseValue(bgReadings.get(0)));
        getAapsLogger().debug(LTag.AUTOSENS, "Adding. bgTime: " + DateUtil.toISOString(bgReadings.get(0).date) + " lastbgTime: " + "none-first-value" + " " + bgReadings.get(0).toString());
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
                    double gapDelta = bgReadings.get(i).value - lastbg;
                    //console.error(gapDelta, lastbg, elapsed_minutes);
                    double nextbg = lastbg + (5d / elapsed_minutes * gapDelta);
                    InMemoryGlucoseValue newBgreading = new InMemoryGlucoseValue(nextbgTime, Math.round(nextbg), true);
                    //console.error("Interpolated", bucketed_data[j]);
                    bucketed_data.add(newBgreading);
                    getAapsLogger().debug(LTag.AUTOSENS, "Adding. bgTime: " + DateUtil.toISOString(bgTime) + " lastbgTime: " + DateUtil.toISOString(lastbgTime) + " " + newBgreading.toString());

                    elapsed_minutes = elapsed_minutes - 5;
                    lastbg = nextbg;
                    lastbgTime = nextbgTime;
                }
                j++;
                InMemoryGlucoseValue newBgreading = new InMemoryGlucoseValue(bgTime, bgReadings.get(i).value);
                bucketed_data.add(newBgreading);
                getAapsLogger().debug(LTag.AUTOSENS, "Adding. bgTime: " + DateUtil.toISOString(bgTime) + " lastbgTime: " + DateUtil.toISOString(lastbgTime) + " " + newBgreading.toString());
            } else if (Math.abs(elapsed_minutes) > 2) {
                j++;
                InMemoryGlucoseValue newBgreading = new InMemoryGlucoseValue(bgTime, bgReadings.get(i).value);
                bucketed_data.add(newBgreading);
                getAapsLogger().debug(LTag.AUTOSENS, "Adding. bgTime: " + DateUtil.toISOString(bgTime) + " lastbgTime: " + DateUtil.toISOString(lastbgTime) + " " + newBgreading.toString());
            } else {
                bucketed_data.get(j).setValue((bucketed_data.get(j).getValue() + bgReadings.get(i).value) / 2);
                //log.error("***** Average");
            }
        }

        // Normalize bucketed data
        InMemoryGlucoseValue oldest = bucketed_data.get(bucketed_data.size() - 1);
        oldest.setTimestamp(adjustToReferenceTime(oldest.getTimestamp()));
        getAapsLogger().debug("Adjusted time " + dateUtil.dateAndTimeAndSecondsString(oldest.getTimestamp()));
        for (int i = bucketed_data.size() - 2; i >= 0; i--) {
            InMemoryGlucoseValue current = bucketed_data.get(i);
            InMemoryGlucoseValue previous = bucketed_data.get(i + 1);
            long msecDiff = current.getTimestamp() - previous.getTimestamp();
            long adjusted = (msecDiff - T.mins(5).msecs()) / 1000;
            getAapsLogger().debug(LTag.AUTOSENS, "Adjusting bucketed data time. Current: " + dateUtil.dateAndTimeAndSecondsString(current.getTimestamp()) + " to: " + dateUtil.dateAndTimeAndSecondsString(previous.getTimestamp() + T.mins(5).msecs()) + " by " + adjusted + " sec");
            if (Math.abs(adjusted) > 90) {
                // too big adjustment, fallback to non 5 min data
                getAapsLogger().debug(LTag.AUTOSENS, "Fallback to non 5 min data");
                createBucketedDataRecalculated();
                return;
            }
            current.setTimestamp(previous.getTimestamp() + T.mins(5).msecs());
        }

        getAapsLogger().debug(LTag.AUTOSENS, "Bucketed data created. Size: " + bucketed_data.size());
    }

    long calculateDetectionStart(long from, boolean limitDataToOldestAvailable) {
        Profile profile = profileFunction.getProfile(from);
        double dia = Constants.defaultDIA;
        if (profile != null) dia = profile.getDia();

        long oldestDataAvailable = treatmentsPlugin.oldestDataAvailable();
        long getBGDataFrom;
        if (limitDataToOldestAvailable) {
            getBGDataFrom = Math.max(oldestDataAvailable, (long) (from - T.hours(1).msecs() * (24 + dia)));
            if (getBGDataFrom == oldestDataAvailable)
                getAapsLogger().debug(LTag.AUTOSENS, "Limiting data to oldest available temps: " + dateUtil.dateAndTimeAndSecondsString(oldestDataAvailable));
        } else
            getBGDataFrom = (long) (from - T.hours(1).msecs() * (24 + dia));
        return getBGDataFrom;
    }

    public IobTotal calculateFromTreatmentsAndTempsSynchronized(long time, Profile profile) {
        synchronized (dataLock) {
            return calculateFromTreatmentsAndTemps(time, profile);
        }
    }

    private IobTotal calculateFromTreatmentsAndTempsSynchronized(long time, AutosensResult lastAutosensResult, boolean exercise_mode, int half_basal_exercise_target, boolean isTempTarget) {
        synchronized (dataLock) {
            return calculateFromTreatmentsAndTemps(time, lastAutosensResult, exercise_mode, half_basal_exercise_target, isTempTarget);
        }
    }

    IobTotal calculateFromTreatmentsAndTemps(long time, Profile profile) {
        long now = System.currentTimeMillis();
        time = roundUpTime(time);
        if (time < now && iobTable.get(time) != null) {
            //og.debug(">>> calculateFromTreatmentsAndTemps Cache hit " + new Date(time).toLocaleString());
            return iobTable.get(time);
        } else {
            //log.debug(">>> calculateFromTreatmentsAndTemps Cache miss " + new Date(time).toLocaleString());
        }
        IobTotal bolusIob = treatmentsPlugin.getCalculationToTimeTreatments(time).round();
        IobTotal basalIob = treatmentsPlugin.getCalculationToTimeTempBasals(time, true, now).round();
        // OpenAPSSMB only
        // Add expected zero temp basal for next 240 mins
        IobTotal basalIobWithZeroTemp = basalIob.copy();
        TemporaryBasal t = new TemporaryBasal(injector)
                .date(now + 60 * 1000L)
                .duration(240)
                .absolute(0);
        if (t.date < time) {
            IobTotal calc = t.iobCalc(time, profile);
            basalIobWithZeroTemp.plus(calc);
        }

        basalIob.iobWithZeroTemp = IobTotal.combine(bolusIob, basalIobWithZeroTemp).round();

        IobTotal iobTotal = IobTotal.combine(bolusIob, basalIob).round();
        if (time < System.currentTimeMillis()) {
            iobTable.put(time, iobTotal);
        }
        return iobTotal;
    }

    public IobTotal calculateAbsInsulinFromTreatmentsAndTempsSynchronized(long time, Profile profile) {
        synchronized (dataLock) {
            long now = System.currentTimeMillis();
            time = roundUpTime(time);
            if (time < now && absIobTable.get(time) != null) {
                //og.debug(">>> calculateFromTreatmentsAndTemps Cache hit " + new Date(time).toLocaleString());
                return absIobTable.get(time);
            } else {
                //log.debug(">>> calculateFromTreatmentsAndTemps Cache miss " + new Date(time).toLocaleString());
            }
            IobTotal bolusIob = treatmentsPlugin.getCalculationToTimeTreatments(time).round();
            IobTotal basalIob = treatmentsPlugin.getAbsoluteIOBTempBasals(time).round();

            IobTotal iobTotal = IobTotal.combine(bolusIob, basalIob).round();
            if (time < System.currentTimeMillis()) {
                absIobTable.put(time, iobTotal);
            }
            return iobTotal;
        }
    }

    private IobTotal calculateFromTreatmentsAndTemps(long time, AutosensResult lastAutosensResult, boolean exercise_mode, int half_basal_exercise_target, boolean isTempTarget) {
        long now = DateUtil.now();

        IobTotal bolusIob = treatmentsPlugin.getCalculationToTimeTreatments(time).round();
        IobTotal basalIob = treatmentsPlugin.getCalculationToTimeTempBasals(time, now, lastAutosensResult, exercise_mode, half_basal_exercise_target, isTempTarget).round();
        // OpenAPSSMB only
        // Add expected zero temp basal for next 240 mins
        IobTotal basalIobWithZeroTemp = basalIob.copy();
        TemporaryBasal t = new TemporaryBasal(injector)
                .date(now + 60 * 1000L)
                .duration(240)
                .absolute(0);
        if (t.date < time) {
            Profile profile = profileFunction.getProfile(t.date);
            if (profile != null) {
                IobTotal calc = t.iobCalc(time, profile, lastAutosensResult, exercise_mode, half_basal_exercise_target, isTempTarget);
                basalIobWithZeroTemp.plus(calc);
            }
        }

        basalIob.iobWithZeroTemp = IobTotal.combine(bolusIob, basalIobWithZeroTemp).round();

        return IobTotal.combine(bolusIob, basalIob).round();
    }

    @Nullable
    public Long findPreviousTimeFromBucketedData(long time) {
        if (bucketed_data == null)
            return null;
        for (int index = 0; index < bucketed_data.size(); index++) {
            if (bucketed_data.get(index).getTimestamp() <= time)
                return bucketed_data.get(index).getTimestamp();
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
                TemporaryBasal tb = treatmentsPlugin.getTempBasalFromHistory(time);
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
            getAapsLogger().debug(LTag.AUTOSENS, "AUTOSENSDATA is waiting for calculation thread: " + reason);
            try {
                thread.join(5000);
            } catch (InterruptedException ignored) {
            }
            getAapsLogger().debug(LTag.AUTOSENS, "AUTOSENSDATA finished waiting for calculation thread: " + reason);
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
        List<Treatment> treatments = treatmentsPlugin.getTreatmentsFromHistory();

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
            getAapsLogger().debug(LTag.AUTOSENS, "AUTOSENSDATA null: autosensDataTable empty (" + reason + ")");
            return null;
        }
        AutosensData data;
        try {
            data = autosensDataTable.valueAt(autosensDataTable.size() - 1);
        } catch (Exception e) {
            // data can be processed on the background
            // in this rare case better return null and do not block UI
            // APS plugin should use getLastAutosensDataSynchronized where the blocking is not an issue
            getAapsLogger().error("AUTOSENSDATA null: Exception catched (" + reason + ")");
            return null;
        }
        if (data == null) {
            getAapsLogger().error("AUTOSENSDATA null: data==null");
            return null;
        }
        if (data.time < System.currentTimeMillis() - 11 * 60 * 1000) {
            getAapsLogger().debug(LTag.AUTOSENS, "AUTOSENSDATA null: data is old (" + reason + ") size()=" + autosensDataTable.size() + " lastdata=" + dateUtil.dateAndTimeAndSecondsString(data.time));
            return null;
        } else {
            getAapsLogger().debug(LTag.AUTOSENS, "AUTOSENSDATA (" + reason + ") " + data.toString());
            return data;
        }
    }

    @Override
    public String lastDataTime() {
        if (autosensDataTable.size() > 0)
            return dateUtil.dateAndTimeAndSecondsString(autosensDataTable.valueAt(autosensDataTable.size() - 1).time);
        else
            return "autosensDataTable empty";
    }

    public MealData getMealData() {
        MealData result = new MealData();

        Profile profile = profileFunction.getProfile();
        if (profile == null) return result;

        long now = System.currentTimeMillis();
        long dia_ago = now - (Double.valueOf(profile.getDia() * T.hours(1).msecs())).longValue();

        double maxAbsorptionHours = Constants.DEFAULT_MAX_ABSORPTION_TIME;
        if (sensitivityAAPSPlugin.isEnabled() || sensitivityWeightedAveragePlugin.isEnabled()) {
            maxAbsorptionHours = sp.getDouble(R.string.key_absorption_maxtime, Constants.DEFAULT_MAX_ABSORPTION_TIME);
        } else {
            maxAbsorptionHours = sp.getDouble(R.string.key_absorption_cutoff, Constants.DEFAULT_MAX_ABSORPTION_TIME);
        }
        long absorptionTime_ago = now - (Double.valueOf(maxAbsorptionHours * T.hours(1).msecs())).longValue();

        List<Treatment> treatments = treatmentsPlugin.getTreatmentsFromHistory();
        for (Treatment treatment : treatments) {
            if (!treatment.isValid)
                continue;
            long t = treatment.date;

            if (t > dia_ago && t <= now) {
                if (treatment.insulin > 0 && treatment.mealBolus) {
                    result.boluses += treatment.insulin;
                }
            }
            if (t > absorptionTime_ago && t <= now) {
                if (treatment.carbs >= 1) {
                    result.carbs += treatment.carbs;
                    if (t > result.lastCarbTime)
                        result.lastCarbTime = t;
                }
            }
        }

        AutosensData autosensData = getLastAutosensDataSynchronized("getMealData()");
        if (autosensData != null) {
            result.mealCOB = autosensData.cob;
            result.slopeFromMinDeviation = autosensData.slopeFromMinDeviation;
            result.slopeFromMaxDeviation = autosensData.slopeFromMaxDeviation;
            result.usedMinCarbsImpact = autosensData.usedMinCarbsImpact;
        }
        result.lastBolusTime = treatmentsPlugin.getLastBolusTime();
        return result;
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

    AutosensResult detectSensitivityWithLock(long fromTime, long toTime) {
        synchronized (dataLock) {
            return activePlugin.getActiveSensitivity().detectSensitivity(this, fromTime, toTime);
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
            getAapsLogger().debug(LTag.AUTOSENS, "Stopping calculation thread: " + from);
            while (thread.getState() != Thread.State.TERMINATED) {
                SystemClock.sleep(100);
            }
            getAapsLogger().debug(LTag.AUTOSENS, "Calculation thread stopped: " + from);
        }
    }

    public void runCalculation(String from, long end, boolean bgDataReload, boolean limitDataToOldestAvailable, Event cause) {
        getAapsLogger().debug(LTag.AUTOSENS, "Starting calculation thread: " + from + " to " + dateUtil.dateAndTimeAndSecondsString(end));
        if (thread == null || thread.getState() == Thread.State.TERMINATED) {
            if (sensitivityOref1Plugin.isEnabled())
                thread = new IobCobOref1Thread(injector, this, treatmentsPlugin, from, end, bgDataReload, limitDataToOldestAvailable, cause);
            else
                thread = new IobCobThread(injector, this, treatmentsPlugin, from, end, bgDataReload, limitDataToOldestAvailable, cause);
            thread.start();
        }
    }

    // When historical data is changed (comming from NS etc) finished calculations after this date must be invalidated
    private void newHistoryData(EventNewHistoryData ev, boolean bgDataReload) {
        //log.debug("Locking onNewHistoryData");
        stopCalculation("onEventNewHistoryData");
        synchronized (dataLock) {
            // clear up 5 min back for proper COB calculation
            long time = ev.getTime() - 5 * 60 * 1000L;
            getAapsLogger().debug(LTag.AUTOSENS, "Invalidating cached data to: " + dateUtil.dateAndTimeAndSecondsString(time));
            for (int index = iobTable.size() - 1; index >= 0; index--) {
                if (iobTable.keyAt(index) > time) {
                    getAapsLogger().debug(LTag.AUTOSENS, "Removing from iobTable: " + dateUtil.dateAndTimeAndSecondsString(iobTable.keyAt(index)));
                    iobTable.removeAt(index);
                } else {
                    break;
                }
            }
            for (int index = absIobTable.size() - 1; index >= 0; index--) {
                if (absIobTable.keyAt(index) > time) {
                    getAapsLogger().debug(LTag.AUTOSENS, "Removing from absIobTable: " + dateUtil.dateAndTimeAndSecondsString(absIobTable.keyAt(index)));
                    absIobTable.removeAt(index);
                } else {
                    break;
                }
            }
            for (int index = autosensDataTable.size() - 1; index >= 0; index--) {
                if (autosensDataTable.keyAt(index) > time) {
                    getAapsLogger().debug(LTag.AUTOSENS, "Removing from autosensDataTable: " + dateUtil.dateAndTimeAndSecondsString(autosensDataTable.keyAt(index)));
                    autosensDataTable.removeAt(index);
                } else {
                    break;
                }
            }
            for (int index = basalDataTable.size() - 1; index >= 0; index--) {
                if (basalDataTable.keyAt(index) > time) {
                    getAapsLogger().debug(LTag.AUTOSENS, "Removing from basalDataTable: " + dateUtil.dateAndTimeAndSecondsString(basalDataTable.keyAt(index)));
                    basalDataTable.removeAt(index);
                } else {
                    break;
                }
            }
        }
        runCalculation("onEventNewHistoryData", System.currentTimeMillis(), bgDataReload, true, ev);
        //log.debug("Releasing onNewHistoryData");
    }

    public void clearCache() {
        synchronized (dataLock) {
            getAapsLogger().debug(LTag.AUTOSENS, "Clearing cached data.");
            iobTable = new LongSparseArray<>();
            autosensDataTable = new LongSparseArray<>();
            basalDataTable = new LongSparseArray<>();
        }
    }

    /*
     * Return last BgReading from database or null if db is empty
     */
    @Nullable
    public BgReading lastBg() {
        List<BgReading> bgList = getBgReadings();

        if (bgList == null)
            return null;

        for (int i = 0; i < bgList.size(); i++)
            if (bgList.get(i).value >= 39)
                return bgList.get(i);
        return null;
    }

    /*
     * Return bg reading if not old ( <9 min )
     * or null if older
     */
    @Nullable
    public BgReading actualBg() {
        BgReading lastBg = lastBg();

        if (lastBg == null)
            return null;

        if (lastBg.date > System.currentTimeMillis() - 9 * 60 * 1000)
            return lastBg;

        return null;
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
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
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.data.IobTotal;
import info.nightscout.androidaps.data.MealData;
import info.nightscout.androidaps.db.BgReading;
import info.nightscout.androidaps.db.Treatment;
import info.nightscout.androidaps.events.EventNewBG;
import info.nightscout.androidaps.events.EventNewBasalProfile;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.TreatmentsInterface;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.IobCobCalculator.events.EventNewHistoryData;
import info.nightscout.androidaps.plugins.NSClientInternal.data.NSProfile;
import info.nightscout.androidaps.plugins.OpenAPSAMA.Autosens;
import info.nightscout.androidaps.plugins.Treatments.TreatmentsPlugin;
import info.nightscout.utils.SP;

/**
 * Created by mike on 24.04.2017.
 */

public class IobCobCalculatorPlugin implements PluginBase {
    private static Logger log = LoggerFactory.getLogger(IobCobCalculatorPlugin.class);

    private static LongSparseArray<IobTotal> iobTable = new LongSparseArray<>();
    private static LongSparseArray<AutosensData> autosensDataTable = new LongSparseArray<>();

    private static List<BgReading> bgReadings = null; // newest at index 0
    private static List<BgReading> bucketed_data = null;

    private static double dia = Constants.defaultDIA;

    private static Handler sHandler = null;
    private static HandlerThread sHandlerThread = null;

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
        if (bucketed_data == null) {
            log.debug("No bucketed data available");
            return null;
        }
        int index = indexNewerThan(fromTime);
        if (index > -1) {
            log.debug("Bucketed data striped off: " + index + "/" + bucketed_data.size());
            return bucketed_data.subList(0, index);
        }
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
        onNewProfile(new EventNewBasalProfile(null));
        bgReadings = MainApp.getDbHelper().getBgreadingsDataFromTime((long) (new Date().getTime() - 60 * 60 * 1000L * (24 + dia)), false);
        log.debug("BG data loaded. Size: " + bgReadings.size());
    }

    public void createBucketedData() {
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

    public void calculateSensitivityData() {
        NSProfile profile = ConfigBuilderPlugin.getActiveProfile() != null ? ConfigBuilderPlugin.getActiveProfile().getProfile() : null;

        if (profile == null) {
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

            //console.error(bgTime , bucketed_data[i].glucose);
            double bg;
            double avgDelta;
            double delta;
            bg = bucketed_data.get(i).value;
            if (bg < 39 || bucketed_data.get(i + 3).value < 39) {
                log.error("! value < 39");
                continue;
            }
            avgDelta = (bg - bucketed_data.get(i + 3).value) / 3;
            delta = (bg - bucketed_data.get(i + 1).value);

            IobTotal iob = calulateFromTreatmentsAndTemps(bgTime);

            double bgi = Math.round((-iob.activity * sens * 5) * 100) / 100d;
            double deviation = delta - bgi;

            List<Treatment> recentTreatments = treatmentsInterface.getTreatments5MinBack(bgTime);
            for (int ir = 0; ir < recentTreatments.size(); ir++) {
                autosensData.carbsFromBolus += recentTreatments.get(ir).carbs;
            }

            // if we absorbing carbs
            if (previous != null && previous.cob > 0) {
                // figure out how many carbs that represents
                // but always assume at least 3mg/dL/5m (default) absorption
                double ci = Math.max(deviation, SP.getDouble("openapsama_min_5m_carbimpact", 3.0));
                autosensData.absorbed = ci * profile.getIc(secondsFromMidnight) / sens;
                // and add that to the running total carbsAbsorbed
                autosensData.cob = Math.max(previous.cob - autosensData.absorbed, 0d);
            }
            autosensData.cob += autosensData.carbsFromBolus;

            // calculate autosens only without COB
            if (autosensData.cob <= 0) {
                if (deviation > 0) {
                    autosensData.pastSensitivity += "+";
                } else if (deviation == 0) {
                    autosensData.pastSensitivity += "=";
                } else {
                    autosensData.pastSensitivity += "-";
                }
                //avgDeltas[i] = avgDelta;
                //bgis[i] = bgi;
                autosensData.deviation = deviation;
            } else {
                autosensData.pastSensitivity += "C";
                //console.error(bgTime);
            }
            //log.debug("TIME: " + new Date(bgTime).toString() + " BG: " + bg + " SENS: " + sens + " DELTA: " + delta + " AVGDELTA: " + avgDelta + " IOB: " + iob.iob + " ACTIVITY: " + iob.activity + " BGI: " + bgi + " DEVIATION: " + deviation);

            previous = autosensData;
            autosensDataTable.put(bgTime, autosensData);
            log.debug(autosensData.log(bgTime));
        }

    }

    public static IobTotal calulateFromTreatmentsAndTemps(long time) {
        long now = new Date().getTime();
        time = roundUpTime(time);
        if (Config.CACHECALCULATIONS && time < now && iobTable.get(time) != null) {
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
        if (Config.CACHECALCULATIONS && time < new Date().getTime()) {
            iobTable.put(time, iobTotal);
        }
        return iobTotal;
    }

    public static AutosensData getAutosensData(long time) {
        long now = new Date().getTime();
        if (time > now )
            return null;
        time = roundUpTime(time);
        AutosensData data = autosensDataTable.get(time);
        if (Config.CACHECALCULATIONS && data != null) {
            log.debug(">>> Cache hit " + data.log(time));
            return data;
        } else {
            log.debug(">>> Cache miss " + new Date(time).toLocaleString());
            return null;
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
    }

    // When historical data is changed (comming from NS etc) finished calculations after this date must be invalidated
    @Subscribe
    public void onNewHistoryData(EventNewHistoryData ev) {
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

}

package info.nightscout.androidaps.plugins.IobCobCalculator;

import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.Nullable;

import com.squareup.otto.Subscribe;

import org.json.JSONArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.data.IobTotal;
import info.nightscout.androidaps.data.MealData;
import info.nightscout.androidaps.db.BgReading;
import info.nightscout.androidaps.events.EventNewBG;
import info.nightscout.androidaps.events.EventNewBasalProfile;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.NSClientInternal.data.NSProfile;

/**
 * Created by mike on 24.04.2017.
 */

public class IobCobCalculatorPlugin implements PluginBase {
    private static Logger log = LoggerFactory.getLogger(IobCobCalculatorPlugin.class);

    private static HashMap<Long, IobTotal> iobTable = new HashMap<Long, IobTotal>();
    private static HashMap<Long, MealData> mealDataTable = new HashMap<Long, MealData>();

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

    public static IobTotal calulateFromTreatmentsAndTemps() {
        ConfigBuilderPlugin.getActiveTreatments().updateTotalIOB();
        IobTotal bolusIob = ConfigBuilderPlugin.getActiveTreatments().getLastCalculation().round();
        ConfigBuilderPlugin.getActiveTempBasals().updateTotalIOB();
        IobTotal basalIob = ConfigBuilderPlugin.getActiveTempBasals().getLastCalculation().round();
        IobTotal iobTotal = IobTotal.combine(bolusIob, basalIob).round();
        return  iobTotal;
    }

    public static IobTotal calulateFromTreatmentsAndTemps(long time) {
        IobTotal bolusIob = ConfigBuilderPlugin.getActiveTreatments().getCalculationToTime(time).round();
        IobTotal basalIob = ConfigBuilderPlugin.getActiveTempBasals().getCalculationToTime(time).round();
        if (basalIob.basaliob > 0) {
            log.debug(new Date(time).toLocaleString() + " basaliob: " + basalIob.basaliob );
        }
        IobTotal iobTotal = IobTotal.combine(bolusIob, basalIob).round();
        return  iobTotal;
    }

    public static IobTotal[] calculateIobArrayInDia() {
        NSProfile profile = ConfigBuilderPlugin.getActiveProfile().getProfile();
        // predict IOB out to DIA plus 30m
        long time = new Date().getTime();
        int len = (int) ((profile.getDia() * 60 + 30) / 5);
        IobTotal[] array = new IobTotal[len];
        int pos = 0;
        for (int i = 0; i < len; i++){
            long t = time + i * 5 * 60000;
            IobTotal iob = calulateFromTreatmentsAndTemps(t);
            array[pos] = iob;
            pos++;
        }
        return array;
    }

    public static JSONArray convertToJSONArray(IobTotal[] iobArray) {
        JSONArray array = new JSONArray();
        for (int i = 0; i < iobArray.length; i ++) {
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

}

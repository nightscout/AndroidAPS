package info.nightscout.androidaps.plugins.SensitivityWeightedAverage;

import android.support.v4.util.LongSparseArray;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.PluginDescription;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.interfaces.SensitivityInterface;
import info.nightscout.androidaps.plugins.IobCobCalculator.AutosensData;
import info.nightscout.androidaps.plugins.IobCobCalculator.AutosensResult;
import info.nightscout.androidaps.plugins.IobCobCalculator.IobCobCalculatorPlugin;
import info.nightscout.utils.Round;
import info.nightscout.utils.SP;
import info.nightscout.utils.SafeParse;

/**
 * Created by mike on 24.06.2017.
 */

public class SensitivityWeightedAveragePlugin extends PluginBase implements SensitivityInterface {
    private static Logger log = LoggerFactory.getLogger(SensitivityWeightedAveragePlugin.class);

    private static SensitivityWeightedAveragePlugin plugin = null;

    public static SensitivityWeightedAveragePlugin getPlugin() {
        if (plugin == null)
            plugin = new SensitivityWeightedAveragePlugin();
        return plugin;
    }

    public SensitivityWeightedAveragePlugin() {
        super(new PluginDescription()
                .mainType(PluginType.SENSITIVITY)
                .pluginName(R.string.sensitivityweightedaverage)
                .shortName(R.string.sensitivity_shortname)
                .preferencesId(R.xml.pref_absorption_aaps)
        );
    }

    @Override
    public AutosensResult detectSensitivity(long fromTime, long toTime) {
        LongSparseArray<AutosensData> autosensDataTable = IobCobCalculatorPlugin.getPlugin().getAutosensDataTable();

        String age = SP.getString(R.string.key_age, "");
        int defaultHours = 24;
        if (age.equals(MainApp.gs(R.string.key_adult))) defaultHours = 24;
        if (age.equals(MainApp.gs(R.string.key_teenage))) defaultHours = 4;
        if (age.equals(MainApp.gs(R.string.key_child))) defaultHours = 4;
        int hoursForDetection = SP.getInt(R.string.key_openapsama_autosens_period, defaultHours);

        if (autosensDataTable == null || autosensDataTable.size() < 4) {
            if (Config.logAutosensData)
                log.debug("No autosens data available");
            return new AutosensResult();
        }

        AutosensData current = IobCobCalculatorPlugin.getPlugin().getAutosensData(toTime); // this is running inside lock already
        if (current == null) {
            if (Config.logAutosensData)
                log.debug("No autosens data available");
            return new AutosensResult();
        }


        Profile profile = MainApp.getConfigBuilder().getProfile();
        if (profile == null) {
            if (Config.logAutosensData)
                log.debug("No profile available");
            return new AutosensResult();
        }

        String pastSensitivity = "";
        int index = 0;
        LongSparseArray<Double> data = new LongSparseArray<>();

        while (index < autosensDataTable.size()) {
            AutosensData autosensData = autosensDataTable.valueAt(index);

            if (autosensData.time < fromTime) {
                index++;
                continue;
            }

            if (autosensData.time > toTime) {
                index++;
                continue;
            }

            if (autosensData.time < toTime - hoursForDetection * 60 * 60 * 1000L) {
                index++;
                continue;
            }

            //data.append(autosensData.time);
            long reverseWeight = (toTime - autosensData.time) / (5 * 60 * 1000L);
            data.append(reverseWeight, autosensData.nonEqualDeviation ? autosensData.deviation : 0d);
            //weights += reverseWeight;
            //weightedsum += reverseWeight * (autosensData.nonEqualDeviation ? autosensData.deviation : 0d);


            pastSensitivity += autosensData.pastSensitivity;
            int secondsFromMidnight = Profile.secondsFromMidnight(autosensData.time);
            if (secondsFromMidnight % 3600 < 2.5 * 60 || secondsFromMidnight % 3600 > 57.5 * 60) {
                pastSensitivity += "(" + Math.round(secondsFromMidnight / 3600d) + ")";
            }
            index++;
        }

        if (data.size() == 0) {
            return new AutosensResult();
        }

        double weightedsum = 0;
        double weights = 0;

        long hightestWeight = data.keyAt(data.size() - 1);
        for (int i = 0; i < data.size(); i++) {
            long reversedWeigth = data.keyAt(i);
            double value = data.valueAt(i);
            double weight = (hightestWeight - reversedWeigth) / 2;
            weights += weight;
            weightedsum += weight * value;
        }

        if (weights == 0) {
            return new AutosensResult();
        }

        double sens = profile.getIsf();

        String ratioLimit = "";
        String sensResult;

        if (Config.logAutosensData)
            log.debug("Records: " + index + "   " + pastSensitivity);

        double average = weightedsum / weights;
        double basalOff = average * (60 / 5) / Profile.toMgdl(sens, profile.getUnits());
        double ratio = 1 + (basalOff / profile.getMaxDailyBasal());

        if (average < 0) { // sensitive
            sensResult = "Excess insulin sensitivity detected";
        } else if (average > 0) { // resistant
            sensResult = "Excess insulin resistance detected";
        } else {
            sensResult = "Sensitivity normal";
        }

        if (Config.logAutosensData)
            log.debug(sensResult);

        double rawRatio = ratio;
        ratio = Math.max(ratio, SafeParse.stringToDouble(SP.getString(R.string.key_openapsama_autosens_min, "0.7")));
        ratio = Math.min(ratio, SafeParse.stringToDouble(SP.getString(R.string.key_openapsama_autosens_max, "1.2")));

        if (ratio != rawRatio) {
            ratioLimit = "Ratio limited from " + rawRatio + " to " + ratio;
            if (Config.logAutosensData)
                log.debug(ratioLimit);
        }

        if (Config.logAutosensData)
            log.debug("Sensitivity to: " + new Date(toTime).toLocaleString() + " weightedaverage: " + average + " ratio: " + ratio + " mealCOB: " + current.cob);

        AutosensResult output = new AutosensResult();
        output.ratio = Round.roundTo(ratio, 0.01);
        output.carbsAbsorbed = Round.roundTo(current.cob, 0.01);
        output.pastSensitivity = pastSensitivity;
        output.ratioLimit = ratioLimit;
        output.sensResult = sensResult;
        return output;
    }
}

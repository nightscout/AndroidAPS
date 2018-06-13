package info.nightscout.androidaps.plugins.SensitivityAAPS;

import android.support.v4.util.LongSparseArray;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

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

public class SensitivityAAPSPlugin extends PluginBase implements SensitivityInterface {
    private static Logger log = LoggerFactory.getLogger(SensitivityAAPSPlugin.class);

    static SensitivityAAPSPlugin plugin = null;

    public static SensitivityAAPSPlugin getPlugin() {
        if (plugin == null)
            plugin = new SensitivityAAPSPlugin();
        return plugin;
    }

    public SensitivityAAPSPlugin() {
        super(new PluginDescription()
                .mainType(PluginType.SENSITIVITY)
                .pluginName(R.string.sensitivityaaps)
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

        Profile profile = MainApp.getConfigBuilder().getProfile();

        if (profile == null) {
            log.debug("No profile");
            return new AutosensResult();
        }

        if (autosensDataTable == null || autosensDataTable.size() < 4) {
            log.debug("No autosens data available");
            return new AutosensResult();
        }

        AutosensData current = IobCobCalculatorPlugin.getPlugin().getAutosensData(toTime); // this is running inside lock already
        if (current == null) {
            log.debug("No autosens data available");
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

            if (autosensData.time > toTime) {
                index++;
                continue;
            }

            if (autosensData.time > toTime - hoursForDetection * 60 * 60 * 1000L)
                deviationsArray.add(autosensData.nonEqualDeviation ? autosensData.deviation : 0d);
            if (deviationsArray.size() > hoursForDetection * 60 / 5)
                deviationsArray.remove(0);


            pastSensitivity += autosensData.pastSensitivity;
            int secondsFromMidnight = Profile.secondsFromMidnight(autosensData.time);
            if (secondsFromMidnight % 3600 < 2.5 * 60 || secondsFromMidnight % 3600 > 57.5 * 60) {
                pastSensitivity += "(" + Math.round(secondsFromMidnight / 3600d) + ")";
            }
            index++;
        }

        Double[] deviations = new Double[deviationsArray.size()];
        deviations = deviationsArray.toArray(deviations);

        double sens = profile.getIsf();

        String ratioLimit = "";
        String sensResult = "";

        if (Config.logAutosensData)
            log.debug("Records: " + index + "   " + pastSensitivity);

        Arrays.sort(deviations);

        double percentile = IobCobCalculatorPlugin.percentile(deviations, 0.50);
        double basalOff = percentile * (60 / 5) / Profile.toMgdl(sens, profile.getUnits());
        double ratio = 1 + (basalOff / profile.getMaxDailyBasal());

        if (percentile < 0) { // sensitive
            sensResult = "Excess insulin sensitivity detected";
        } else if (percentile > 0) { // resistant
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
            log.debug(ratioLimit);
        }

        if (Config.logAutosensData) {
            log.debug("Sensitivity to: " + new Date(toTime).toLocaleString() + " percentile: " + percentile + " ratio: " + ratio + " mealCOB: " + current.cob);
            log.debug("Sensitivity to: deviations " + Arrays.toString(deviations));
        }

        AutosensResult output = new AutosensResult();
        output.ratio = Round.roundTo(ratio, 0.01);
        output.carbsAbsorbed = Round.roundTo(current.cob, 0.01);
        output.pastSensitivity = pastSensitivity;
        output.ratioLimit = ratioLimit;
        output.sensResult = sensResult;
        return output;
    }
}

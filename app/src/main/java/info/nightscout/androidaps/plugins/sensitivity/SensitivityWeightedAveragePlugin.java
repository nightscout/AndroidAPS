package info.nightscout.androidaps.plugins.sensitivity;

import androidx.collection.LongSparseArray;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.List;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.db.CareportalEvent;
import info.nightscout.androidaps.db.ProfileSwitch;
import info.nightscout.androidaps.interfaces.PluginDescription;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunctions;
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.AutosensData;
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.AutosensResult;
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.IobCobCalculatorPlugin;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.SP;

/**
 * Created by mike on 24.06.2017.
 */

public class SensitivityWeightedAveragePlugin extends AbstractSensitivityPlugin {
    private static Logger log = LoggerFactory.getLogger(L.AUTOSENS);

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
                .description(R.string.description_sensitivity_weighted_average)
        );
    }

    @Override
    public AutosensResult detectSensitivity(IobCobCalculatorPlugin iobCobCalculatorPlugin, long fromTime, long toTime) {
        LongSparseArray<AutosensData> autosensDataTable = iobCobCalculatorPlugin.getAutosensDataTable();

        String age = SP.getString(R.string.key_age, "");
        int defaultHours = 24;
        if (age.equals(MainApp.gs(R.string.key_adult))) defaultHours = 24;
        if (age.equals(MainApp.gs(R.string.key_teenage))) defaultHours = 4;
        if (age.equals(MainApp.gs(R.string.key_child))) defaultHours = 4;
        int hoursForDetection = SP.getInt(R.string.key_openapsama_autosens_period, defaultHours);

        if (autosensDataTable == null || autosensDataTable.size() < 4) {
            if (L.isEnabled(L.AUTOSENS))
                log.debug("No autosens data available. lastDataTime=" + iobCobCalculatorPlugin.lastDataTime());
            return new AutosensResult();
        }

        AutosensData current = iobCobCalculatorPlugin.getAutosensData(toTime); // this is running inside lock already
        if (current == null) {
            if (L.isEnabled(L.AUTOSENS))
                log.debug("No autosens data available. toTime: " + DateUtil.dateAndTimeString(toTime) + " lastDataTime: " + iobCobCalculatorPlugin.lastDataTime());
            return new AutosensResult();
        }


        Profile profile = ProfileFunctions.getInstance().getProfile();
        if (profile == null) {
            if (L.isEnabled(L.AUTOSENS))
                log.debug("No profile available");
            return new AutosensResult();
        }

        List<CareportalEvent> siteChanges = MainApp.getDbHelper().getCareportalEventsFromTime(fromTime, CareportalEvent.SITECHANGE, true);
        List<ProfileSwitch> profileSwitches = MainApp.getDbHelper().getProfileSwitchEventsFromTime(fromTime, true);

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

            // reset deviations after site change
            if (CareportalEvent.isEvent5minBack(siteChanges, autosensData.time)) {
                data.clear();
                pastSensitivity += "(SITECHANGE)";
            }

            // reset deviations after profile switch
            if (ProfileSwitch.isEvent5minBack(profileSwitches, autosensData.time, true)) {
                data.clear();
                pastSensitivity += "(PROFILESWITCH)";
            }

            double deviation = autosensData.deviation;

            //set positive deviations to zero if bg < 80
            if (autosensData.bg < 80 && deviation > 0)
                deviation = 0;

            //data.append(autosensData.time);
            long reverseWeight = (toTime - autosensData.time) / (5 * 60 * 1000L);
            if (autosensData.validDeviation)
                data.append(reverseWeight, deviation);
            //weights += reverseWeight;
            //weightedsum += reverseWeight * (autosensData.validDeviation ? autosensData.deviation : 0d);


            pastSensitivity += autosensData.pastSensitivity;
            int secondsFromMidnight = Profile.secondsFromMidnight(autosensData.time);
            if (secondsFromMidnight % 3600 < 2.5 * 60 || secondsFromMidnight % 3600 > 57.5 * 60) {
                pastSensitivity += "(" + Math.round(secondsFromMidnight / 3600d) + ")";
            }
            index++;
        }

        if (data.size() == 0) {
            if (L.isEnabled(L.AUTOSENS))
                log.debug("Data size: " + data.size() + " fromTime: " + DateUtil.dateAndTimeString(fromTime) + " toTime: " + DateUtil.dateAndTimeString(toTime));
            return new AutosensResult();
        } else {
            if (L.isEnabled(L.AUTOSENS))
                log.debug("Data size: " + data.size() + " fromTime: " + DateUtil.dateAndTimeString(fromTime) + " toTime: " + DateUtil.dateAndTimeString(toTime));
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

        if (L.isEnabled(L.AUTOSENS))
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

        if (L.isEnabled(L.AUTOSENS))
            log.debug(sensResult);

        AutosensResult output = fillResult(ratio, current.cob, pastSensitivity, ratioLimit,
                sensResult, data.size());

        if (L.isEnabled(L.AUTOSENS))
            log.debug("Sensitivity to: {}  weightedaverage: {} ratio: {} mealCOB: {}", new Date(toTime).toLocaleString(),
                    average, output.ratio, current.cob);

        return output;
    }
}

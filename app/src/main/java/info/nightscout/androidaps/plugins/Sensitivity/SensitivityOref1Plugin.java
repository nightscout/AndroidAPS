package info.nightscout.androidaps.plugins.Sensitivity;

import android.support.v4.util.LongSparseArray;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.db.CareportalEvent;
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
 * Created by mike on 19.06.2018.
 */

public class SensitivityOref1Plugin extends AbstractSensitivityPlugin {
    private static Logger log = LoggerFactory.getLogger(IobCobCalculatorPlugin.class);

    static SensitivityOref1Plugin plugin = null;

    public static SensitivityOref1Plugin getPlugin() {
        if (plugin == null)
            plugin = new SensitivityOref1Plugin();
        return plugin;
    }

    public SensitivityOref1Plugin() {
        super(new PluginDescription()
                .mainType(PluginType.SENSITIVITY)
                .pluginName(R.string.sensitivityoref1)
                .shortName(R.string.sensitivity_shortname)
                .preferencesId(R.xml.pref_absorption_oref1)
                .description(R.string.description_sensitivity_oref1)
        );
    }

    @Override
    public AutosensResult detectSensitivity(long fromTime, long toTime) {
        LongSparseArray<AutosensData> autosensDataTable = IobCobCalculatorPlugin.getPlugin().getAutosensDataTable();

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
            log.debug("No current autosens data available");
            return new AutosensResult();
        }

        List<CareportalEvent> siteChanges = MainApp.getDbHelper().getCareportalEventsFromTime(fromTime, CareportalEvent.SITECHANGE, true);

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

            // reset deviations after site change
            if (CareportalEvent.isEvent5minBack(siteChanges, autosensData.time)) {
                deviationsArray.clear();
                pastSensitivity += "(SITECHANGE)";
            }

            double deviation = autosensData.deviation;

            //set positive deviations to zero if bg < 80
            if (autosensData.bg < 80 && deviation > 0)
                deviation = 0;

            if (autosensData.validDeviation)
                deviationsArray.add(deviation);

            for (int i = 0; i < autosensData.extraDeviation.size(); i++)
                deviationsArray.add(autosensData.extraDeviation.get(i));
            if (deviationsArray.size() > 96)
                deviationsArray.remove(0);

            pastSensitivity += autosensData.pastSensitivity;
            int secondsFromMidnight = Profile.secondsFromMidnight(autosensData.time);
            if (secondsFromMidnight % 3600 < 2.5 * 60 || secondsFromMidnight % 3600 > 57.5 * 60) {
                pastSensitivity += "(" + Math.round(secondsFromMidnight / 3600d) + ")";
            }
            index++;
        }

        // when we have less than 8h worth of deviation data, add up to 90m of zero deviations
        // this dampens any large sensitivity changes detected based on too little data, without ignoring them completely
        log.debug("Using most recent " + deviationsArray.size() + " deviations");
        if (deviationsArray.size() < 96) {
            int pad = Math.round((1 - deviationsArray.size() / 96) * 18);
            log.debug("Adding " + pad + " more zero deviations");
            for (int d = 0; d < pad; d++) {
                //process.stderr.write(".");
                deviationsArray.add(0d);
            }
        }

        Double[] deviations = new Double[deviationsArray.size()];
        deviations = deviationsArray.toArray(deviations);

        double sens = profile.getIsf();

        double ratio = 1;
        String ratioLimit = "";
        String sensResult = "";

        if (Config.logAutosensData)
            log.debug("Records: " + index + "   " + pastSensitivity);

        Arrays.sort(deviations);

        for (double i = 0.9; i > 0.1; i = i - 0.01) {
            if (IobCobCalculatorPlugin.percentile(deviations, (i + 0.01)) >= 0 && IobCobCalculatorPlugin.percentile(deviations, i) < 0) {
                if (Config.logAutosensData)
                    log.debug(Math.round(100 * i) + "% of non-meal deviations negative (>50% = sensitivity)");
            }
            if (IobCobCalculatorPlugin.percentile(deviations, (i + 0.01)) > 0 && IobCobCalculatorPlugin.percentile(deviations, i) <= 0) {
                if (Config.logAutosensData)
                    log.debug(Math.round(100 * i) + "% of non-meal deviations negative (>50% = resistance)");
            }
        }
        double pSensitive = IobCobCalculatorPlugin.percentile(deviations, 0.50);
        double pResistant = IobCobCalculatorPlugin.percentile(deviations, 0.50);

        double basalOff = 0;

        if (pSensitive < 0) { // sensitive
            basalOff = pSensitive * (60 / 5) / Profile.toMgdl(sens, profile.getUnits());
            sensResult = "Excess insulin sensitivity detected";
        } else if (pResistant > 0) { // resistant
            basalOff = pResistant * (60 / 5) / Profile.toMgdl(sens, profile.getUnits());
            sensResult = "Excess insulin resistance detected";
        } else {
            sensResult = "Sensitivity normal";
        }

        if (Config.logAutosensData)
            log.debug(sensResult);

        ratio = 1 + (basalOff / profile.getMaxDailyBasal());

        AutosensResult output = fillResult(ratio, current.cob, pastSensitivity, ratioLimit,
                sensResult, deviationsArray.size());

        if (Config.logAutosensData)
            log.debug("Sensitivity to: {} ratio: {} mealCOB: {}",
                    new Date(toTime).toLocaleString(), output.ratio, current.cob);

        return output;
    }
}

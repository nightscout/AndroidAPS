package info.nightscout.androidaps.plugins.sensitivity;

import androidx.annotation.NonNull;
import androidx.collection.LongSparseArray;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.android.HasAndroidInjector;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.db.CareportalEvent;
import info.nightscout.androidaps.db.ProfileSwitch;
import info.nightscout.androidaps.interfaces.PluginDescription;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunction;
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.AutosensData;
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.AutosensResult;
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.IobCobCalculatorPlugin;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.resources.ResourceHelper;
import info.nightscout.androidaps.utils.sharedPreferences.SP;

/**
 * Created by mike on 24.06.2017.
 */
@Singleton
public class SensitivityOref0Plugin extends AbstractSensitivityPlugin {

    private ProfileFunction profileFunction;

    @Inject
    public SensitivityOref0Plugin(
            HasAndroidInjector injector,
            AAPSLogger aapsLogger,
            ResourceHelper resourceHelper,
            SP sp,
            ProfileFunction profileFunction
    ) {
        super(new PluginDescription()
                        .mainType(PluginType.SENSITIVITY)
                        .pluginName(R.string.sensitivityoref0)
                        .shortName(R.string.sensitivity_shortname)
                        .preferencesId(R.xml.pref_absorption_oref0)
                        .description(R.string.description_sensitivity_oref0),
                injector, aapsLogger, resourceHelper, sp
        );
        this.profileFunction = profileFunction;
    }

    @NonNull @Override
    public AutosensResult detectSensitivity(IobCobCalculatorPlugin iobCobCalculatorPlugin, long fromTime, long toTime) {
        LongSparseArray<AutosensData> autosensDataTable = iobCobCalculatorPlugin.getAutosensDataTable();

        int hoursForDetection = 24;

        Profile profile = profileFunction.getProfile();

        if (profile == null) {
            getAapsLogger().error("No profile");
            return new AutosensResult();
        }

        if (autosensDataTable == null || autosensDataTable.size() < 4) {
            getAapsLogger().debug(LTag.AUTOSENS, "No autosens data available. lastDataTime=" + iobCobCalculatorPlugin.lastDataTime());
            return new AutosensResult();
        }

        AutosensData current = iobCobCalculatorPlugin.getAutosensData(toTime); // this is running inside lock already
        if (current == null) {
            getAapsLogger().debug(LTag.AUTOSENS, "No autosens data available. toTime: " + DateUtil.dateAndTimeString(toTime) + " lastDataTime: " + iobCobCalculatorPlugin.lastDataTime());
            return new AutosensResult();
        }


        List<CareportalEvent> siteChanges = MainApp.getDbHelper().getCareportalEventsFromTime(fromTime, CareportalEvent.SITECHANGE, true);
        List<ProfileSwitch> profileSwitches = MainApp.getDbHelper().getProfileSwitchEventsFromTime(fromTime, true);

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

            // reset deviations after profile switch
            if (ProfileSwitch.isEvent5minBack(profileSwitches, autosensData.time, true)) {
                deviationsArray.clear();
                pastSensitivity += "(PROFILESWITCH)";
            }

            double deviation = autosensData.deviation;

            //set positive deviations to zero if bg < 80
            if (autosensData.bg < 80 && deviation > 0)
                deviation = 0;

            if (autosensData.validDeviation)
                if (autosensData.time > toTime - hoursForDetection * 60 * 60 * 1000L)
                    deviationsArray.add(deviation);
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

        double sens = profile.getIsfMgdl();

        double ratio;
        String ratioLimit = "";
        String sensResult;

        getAapsLogger().debug(LTag.AUTOSENS, "Records: " + index + "   " + pastSensitivity);

        Arrays.sort(deviations);

        for (double i = 0.9; i > 0.1; i = i - 0.02) {
            if (IobCobCalculatorPlugin.percentile(deviations, (i + 0.02)) >= 0 && IobCobCalculatorPlugin.percentile(deviations, i) < 0) {
                getAapsLogger().debug(LTag.AUTOSENS, Math.round(100 * i) + "% of non-meal deviations negative (target 45%-50%)");
            }
        }
        double pSensitive = IobCobCalculatorPlugin.percentile(deviations, 0.50);
        double pResistant = IobCobCalculatorPlugin.percentile(deviations, 0.45);

        double basalOff = 0;

        if (pSensitive < 0) { // sensitive
            basalOff = pSensitive * (60 / 5.0) / sens;
            sensResult = "Excess insulin sensitivity detected";
        } else if (pResistant > 0) { // resistant
            basalOff = pResistant * (60 / 5.0) / sens;
            sensResult = "Excess insulin resistance detected";
        } else {
            sensResult = "Sensitivity normal";
        }

        getAapsLogger().debug(LTag.AUTOSENS, sensResult);

        ratio = 1 + (basalOff / profile.getMaxDailyBasal());

        AutosensResult output = fillResult(ratio, current.cob, pastSensitivity, ratioLimit,
                sensResult, deviationsArray.size());

        getAapsLogger().debug(LTag.AUTOSENS, "Sensitivity to: "
                + DateUtil.dateAndTimeString(toTime) +
                " ratio: " + output.ratio
                + " mealCOB: " + current.cob);

        return output;
    }
}

package info.nightscout.androidaps.plugins.sensitivity;

import androidx.collection.LongSparseArray;

import org.jetbrains.annotations.NotNull;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.android.HasAndroidInjector;
import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.db.CareportalEvent;
import info.nightscout.androidaps.db.ProfileSwitch;
import info.nightscout.androidaps.interfaces.IobCobCalculatorInterface;
import info.nightscout.androidaps.interfaces.PluginDescription;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.interfaces.ProfileFunction;
import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.aps.openAPSSMB.SMBDefaults;
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.AutosensResult;
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.IobCobCalculatorPlugin;
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.data.AutosensData;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.resources.ResourceHelper;
import info.nightscout.androidaps.utils.sharedPreferences.SP;


/**
 * Created by mike on 19.06.2018.
 */
@Singleton
public class SensitivityOref1Plugin extends AbstractSensitivityPlugin {

    private final ProfileFunction profileFunction;
    private final DateUtil dateUtil;

    @Inject
    public SensitivityOref1Plugin(
            HasAndroidInjector injector,
            AAPSLogger aapsLogger,
            ResourceHelper resourceHelper,
            SP sp,
            ProfileFunction profileFunction,
            DateUtil dateUtil
    ) {
        super(new PluginDescription()
                        .mainType(PluginType.SENSITIVITY)
                        .pluginIcon(R.drawable.ic_generic_icon)
                        .pluginName(R.string.sensitivityoref1)
                        .shortName(R.string.sensitivity_shortname)
                        .enableByDefault(true)
                        .preferencesId(R.xml.pref_absorption_oref1)
                        .description(R.string.description_sensitivity_oref1)
                        .setDefault(),
                injector, aapsLogger, resourceHelper, sp
        );
        this.profileFunction = profileFunction;
        this.dateUtil = dateUtil;
    }

    @NotNull @Override
    public AutosensResult detectSensitivity(IobCobCalculatorInterface iobCobCalculatorPlugin, long fromTime, long toTime) {
        // todo this method is called from the IobCobCalculatorPlugin, which leads to a circular
        // dependency, this should be avoided
        LongSparseArray<AutosensData> autosensDataTable = iobCobCalculatorPlugin.getAutosensDataTable();

        Profile profile = profileFunction.getProfile();

        if (profile == null) {
            getAapsLogger().error("No profile");
            return new AutosensResult();
        }

        if (autosensDataTable == null || autosensDataTable.size() < 4) {
            getAapsLogger().debug(LTag.AUTOSENS, "No autosens data available. lastDataTime=" + iobCobCalculatorPlugin.lastDataTime());
            return new AutosensResult();
        }

        // the current
        AutosensData current = iobCobCalculatorPlugin.getAutosensData(toTime); // this is running inside lock already
        if (current == null) {
            getAapsLogger().debug(LTag.AUTOSENS, "No autosens data available. toTime: " + dateUtil.dateAndTimeString(toTime) + " lastDataTime: " + iobCobCalculatorPlugin.lastDataTime());
            return new AutosensResult();
        }

        List<CareportalEvent> siteChanges = MainApp.getDbHelper().getCareportalEventsFromTime(fromTime, CareportalEvent.SITECHANGE, true);
        List<ProfileSwitch> profileSwitches = MainApp.getDbHelper().getProfileSwitchEventsFromTime(fromTime, true);

        //[0] = 8 hour
        //[1] = 24 hour
        //Deviationshour has DeviationsArray
        List<ArrayList<Double>> deviationsHour = Arrays.asList(new ArrayList<>(), new ArrayList<>());
        List<String> pastSensitivityArray = Arrays.asList("", "");
        List<String> sensResultArray = Arrays.asList("", "");
        List<Double> ratioArray = Arrays.asList(0d, 0d);
        List<Double> deviationCategory = Arrays.asList(96d, 288d);
        List<String> ratioLimitArray = Arrays.asList("", "");
        List<Double> hoursDetection = Arrays.asList(8d, 24d);


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
            int hoursegment = 0;
            //hoursegment = 0 = 8 hour
            //hoursegment = 1 = 24 hour
            while (hoursegment < deviationsHour.size()) {
                ArrayList<Double> deviationsArray = deviationsHour.get(hoursegment);
                String pastSensitivity = pastSensitivityArray.get(hoursegment);

                // reset deviations after site change
                if (new CareportalEvent(getInjector()).isEvent5minBack(siteChanges, autosensData.time)) {
                    deviationsArray.clear();
                    pastSensitivity += "(SITECHANGE)";
                }

                // reset deviations after profile switch
                if (new ProfileSwitch(getInjector()).isEvent5minBack(profileSwitches, autosensData.time, true)) {
                    deviationsArray.clear();
                    pastSensitivity += "(PROFILESWITCH)";
                }

                double deviation = autosensData.deviation;

                //set positive deviations to zero if bg < 80
                if (autosensData.bg < 80 && deviation > 0)
                    deviation = 0;

                if (autosensData.validDeviation)
                    if (autosensData.time > toTime - hoursDetection.get(hoursegment) * 60 * 60 * 1000L)
                        deviationsArray.add(deviation);


                deviationsArray.addAll(autosensData.extraDeviation);

                if (deviationsArray.size() > deviationCategory.get(hoursegment)) {
                    deviationsArray.remove(0);
                }

                pastSensitivity += autosensData.pastSensitivity;
                int secondsFromMidnight = Profile.secondsFromMidnight(autosensData.time);

                if (secondsFromMidnight % 3600 < 2.5 * 60 || secondsFromMidnight % 3600 > 57.5 * 60) {
                    pastSensitivity += "(" + Math.round(secondsFromMidnight / 3600d) + ")";
                }

                //Update the data back to the parent
                deviationsHour.set(hoursegment, deviationsArray);
                pastSensitivityArray.set(hoursegment, pastSensitivity);
                hoursegment++;
            }
            index++;
        }

        // when we have less than 8h/24 worth of deviation data, add up to 90m of zero deviations
        // this dampens any large sensitivity changes detected based on too little data, without ignoring them completely

        for (int i = 0; i < deviationsHour.size(); i++) {
            ArrayList<Double> deviations = deviationsHour.get(i);
            getAapsLogger().debug(LTag.AUTOSENS, "Using most recent " + deviations.size() + " deviations");
            if (deviations.size() < deviationCategory.get(i)) {
                int pad = (int) Math.round((1 - (double) deviations.size() / deviationCategory.get(i)) * 18);
                getAapsLogger().debug(LTag.AUTOSENS, "Adding " + pad + " more zero deviations");
                for (int d = 0; d < pad; d++) {
                    deviations.add(0d);
                }
            }
            //Update the data back to the parent
            deviationsHour.set(i, deviations);

        }

        int hourused = 0;
        while (hourused < deviationsHour.size()) {
            ArrayList deviationsArray = deviationsHour.get(hourused);
            String pastSensitivity = pastSensitivityArray.get(hourused);
            String sensResult = "(8 hours) ";
            if (hourused == 1) sensResult = "(24 hours) ";
            String ratioLimit = "";

            Double[] deviations = new Double[deviationsArray.size()];
            deviations = (Double[]) deviationsArray.toArray(deviations);

            double sens = profile.getIsfMgdl();


            getAapsLogger().debug(LTag.AUTOSENS, "Records: " + index + "   " + pastSensitivity);

            Arrays.sort(deviations);
            double pSensitive = IobCobCalculatorPlugin.percentile(deviations, 0.50);
            double pResistant = IobCobCalculatorPlugin.percentile(deviations, 0.50);

            double basalOff = 0;

            if (pSensitive < 0) { // sensitive
                basalOff = pSensitive * (60.0 / 5) / sens;
                sensResult += "Excess insulin sensitivity detected";
            } else if (pResistant > 0) { // resistant
                basalOff = pResistant * (60.0 / 5) / sens;
                sensResult += "Excess insulin resistance detected";
            } else {
                sensResult += "Sensitivity normal";
            }

            getAapsLogger().debug(LTag.AUTOSENS, sensResult);

            double ratio = 1 + (basalOff / profile.getMaxDailyBasal());

            //Update the data back to the parent
            sensResultArray.set(hourused, sensResult);
            ratioArray.set(hourused, ratio);
            ratioLimitArray.set(hourused, ratioLimit);
            hourused++;
        }

        int key = 1;
        String comparison = " 8 h ratio " + ratioArray.get(0) + " vs 24h ratio " + ratioArray.get(1);
        //use 24 hour ratio by default
        //if the 8 hour ratio is less than the 24 hour ratio, the 8 hour ratio is used
        if (ratioArray.get(0) < ratioArray.get(1)) {
            key = 0;
        }
        //String message = hoursDetection.get(key) + " of sensitivity used";
        AutosensResult output = fillResult(ratioArray.get(key), current.cob, pastSensitivityArray.get(key), ratioLimitArray.get(key),
                sensResultArray.get(key) + comparison, deviationsHour.get(key).size());

        getAapsLogger().debug(LTag.AUTOSENS, "Sensitivity to: "
                + dateUtil.dateAndTimeString(toTime) +
                " ratio: " + output.ratio
                + " mealCOB: " + current.cob);

        return output;
    }

    @NotNull @Override public JSONObject configuration() {
        JSONObject c = new JSONObject();
        try {
            c.put(getResourceHelper().gs(R.string.key_openapsama_min_5m_carbimpact), getSp().getDouble(R.string.key_openapsama_min_5m_carbimpact, SMBDefaults.min_5m_carbimpact));
            c.put(getResourceHelper().gs(R.string.key_absorption_cutoff), getSp().getDouble(R.string.key_absorption_cutoff, Constants.DEFAULT_MAX_ABSORPTION_TIME));
            c.put(getResourceHelper().gs(R.string.key_openapsama_autosens_max), getSp().getDouble(R.string.key_openapsama_autosens_max, 1.2));
            c.put(getResourceHelper().gs(R.string.key_openapsama_autosens_min), getSp().getDouble(R.string.key_openapsama_autosens_min, 0.7));
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return c;
    }

    @Override public void applyConfiguration(@NotNull JSONObject configuration) {
        try {
            if (configuration.has(getResourceHelper().gs(R.string.key_openapsama_min_5m_carbimpact)))
                getSp().putDouble(R.string.key_openapsama_min_5m_carbimpact, configuration.getDouble(getResourceHelper().gs(R.string.key_openapsama_min_5m_carbimpact)));
            if (configuration.has(getResourceHelper().gs(R.string.key_absorption_cutoff)))
                getSp().putDouble(R.string.key_absorption_cutoff, configuration.getDouble(getResourceHelper().gs(R.string.key_absorption_cutoff)));
            if (configuration.has(getResourceHelper().gs(R.string.key_openapsama_autosens_max)))
                getSp().getDouble(R.string.key_openapsama_autosens_max, configuration.getDouble(getResourceHelper().gs(R.string.key_openapsama_autosens_max)));
            if (configuration.has(getResourceHelper().gs(R.string.key_openapsama_autosens_min)))
                getSp().getDouble(R.string.key_openapsama_autosens_min, configuration.getDouble(getResourceHelper().gs(R.string.key_openapsama_autosens_min)));
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @NotNull @Override public SensitivityType getId() {
        return SensitivityType.SENSITIVITY_OREF1;
    }
}

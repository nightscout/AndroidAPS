package info.nightscout.androidaps.plugins.sensitivity;

import androidx.collection.LongSparseArray;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
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

/**
 * Created by mike on 19.06.2018.
 */

public class SensitivityOref1Plugin extends AbstractSensitivityPlugin {
    private static Logger log = LoggerFactory.getLogger(L.AUTOSENS);

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
    public AutosensResult detectSensitivity(IobCobCalculatorPlugin iobCobCalculatorPlugin, long fromTime, long toTime) {
        // todo this method is called from the IobCobCalculatorPlugin, which leads to a circular
        // dependency, this should be avoided
        LongSparseArray<AutosensData> autosensDataTable = iobCobCalculatorPlugin.getAutosensDataTable();

        Profile profile = ProfileFunctions.getInstance().getProfile();

        if (profile == null) {
            log.error("No profile");
            return new AutosensResult();
        }

        if (autosensDataTable == null || autosensDataTable.size() < 4) {
            if (L.isEnabled(L.AUTOSENS))
                log.debug("No autosens data available. lastDataTime=" + iobCobCalculatorPlugin.lastDataTime());
            return new AutosensResult();
        }

        // the current
        AutosensData current = iobCobCalculatorPlugin.getAutosensData(toTime); // this is running inside lock already
        if (current == null) {
            if (L.isEnabled(L.AUTOSENS))
                log.debug("No autosens data available. toTime: " + DateUtil.dateAndTimeString(toTime) + " lastDataTime: " + iobCobCalculatorPlugin.lastDataTime());
            return new AutosensResult();
        }

        List<CareportalEvent> siteChanges = MainApp.getDbHelper().getCareportalEventsFromTime(fromTime, CareportalEvent.SITECHANGE, true);
        List<ProfileSwitch> profileSwitches = MainApp.getDbHelper().getProfileSwitchEventsFromTime(fromTime, true);

        //[0] = 8 hour
        //[1] = 24 hour
        //Deviationshour has DeviationsArray
        List<ArrayList> deviationshour = Arrays.asList(new ArrayList(),new ArrayList());
        List<String> pastSensitivityArray = Arrays.asList("","");
        List<String> sensResultArray = Arrays.asList("","");
        List<Double> ratioArray = Arrays.asList(0d,0d);
        List<String> ratioLimitArray = Arrays.asList("","");
        List<Double> hoursDetection = Arrays.asList(8d,24d);


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
            while (hoursegment < deviationshour.size()){
                ArrayList deviationsArray = deviationshour.get(hoursegment);
                String pastSensitivity = pastSensitivityArray.get(hoursegment);

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
                    if (autosensData.time > toTime - hoursDetection.get(hoursegment) * 60 * 60 * 1000L)
                        deviationsArray.add(deviation);

                if (hoursegment == 0) {
                    for (int i = 0; i < autosensData.extraDeviation.size(); i++)
                        deviationsArray.add(autosensData.extraDeviation.get(i));
                }
                if (deviationsArray.size() > hoursDetection.get(hoursegment) * 60 / 5){
                    deviationsArray.remove(0);
                }

                pastSensitivity += autosensData.pastSensitivity;
                int secondsFromMidnight = Profile.secondsFromMidnight(autosensData.time);

                if (secondsFromMidnight % 3600 < 2.5 * 60 || secondsFromMidnight % 3600 > 57.5 * 60) {
                    pastSensitivity += "(" + Math.round(secondsFromMidnight / 3600d) + ")";
                }

                //Update the data back to the parent
                deviationshour.set(hoursegment,deviationsArray);
                pastSensitivityArray.set(hoursegment,pastSensitivity);
                hoursegment++;
              }
              index++;
        }

        // when we have less than 8h worth of deviation data, add up to 90m of zero deviations
        // this dampens any large sensitivity changes detected based on too little data, without ignoring them completely
        // only apply for 8 hours of devations
        ArrayList dev8h = deviationshour.get(0);

        if (L.isEnabled(L.AUTOSENS))
            log.debug("Using most recent " + dev8h.size() + " deviations");
        if (dev8h.size() < 96) {
            int pad = (int) Math.round((1 - (double) dev8h.size() / 96) * 18);
            if (L.isEnabled(L.AUTOSENS))
                log.debug("Adding " + pad + " more zero deviations");
            for (int d = 0; d < pad; d++) { ;
                dev8h.add(0d);
            }
        }
        //Update the data back to the parent
        deviationshour.set(0,dev8h);

        int hourused = 0;
        while (hourused < deviationshour.size()){
            ArrayList deviationsArray = deviationshour.get(hourused);
            String pastSensitivity = pastSensitivityArray.get(hourused);
            String sensResult = "(8 hours) ";
            String senstime = sensResult;
            if (hourused == 1){
                senstime = "(24 hours) ";
                sensResult = senstime;
            }
            String ratioLimit = "";

            Double[] deviations = new Double[deviationsArray.size()];
            deviations = (Double[]) deviationsArray.toArray(deviations);

            double sens = profile.getIsfMgdl();

            if (L.isEnabled(L.AUTOSENS))
                log.debug(senstime + "Records: " + index + "   " + pastSensitivity);

            Arrays.sort(deviations);
            double pSensitive = IobCobCalculatorPlugin.percentile(deviations, 0.50);
            double pResistant = IobCobCalculatorPlugin.percentile(deviations, 0.50);

            double basalOff = 0;

            if (pSensitive < 0) { // sensitive
                basalOff = pSensitive * (60 / 5) / Profile.toMgdl(sens, profile.getUnits());
                sensResult+= "Excess insulin sensitivity detected";
            } else if (pResistant > 0) { // resistant
                basalOff = pResistant * (60 / 5) / Profile.toMgdl(sens, profile.getUnits());
                sensResult+= "Excess insulin resistance detected";
            } else {
                sensResult+= "Sensitivity normal";
            }

            if (L.isEnabled(L.AUTOSENS))
                log.debug(sensResult);

            double ratio = 1 + (basalOff / profile.getMaxDailyBasal());

            //Update the data back to the parent
            sensResultArray.set(hourused,sensResult);
            ratioArray.set(hourused,ratio);
            ratioLimitArray.set(hourused,ratioLimit);
            hourused++;
        }

        int key = 1;
        String comparison = " 8 h ratio " +ratioArray.get(0)+" vs 24h ratio "+ratioArray.get(1);
        //use 24 hour ratio by default
        //if the 8 hour ratio is less than the 24 hour ratio, the 8 hour ratio is used
        if(ratioArray.get(0) < ratioArray.get(1)){
          key = 0;
        }
        String message = hoursDetection.get(key)+" of sensitivity used";
        AutosensResult output = fillResult(ratioArray.get(key), current.cob, pastSensitivityArray.get(key), ratioLimitArray.get(key),
                sensResultArray.get(key)+comparison, deviationshour.get(key).size());

        if (L.isEnabled(L.AUTOSENS))
            log.debug(message+" Sensitivity to: {} ratio: {} mealCOB: {}",
                    new Date(toTime).toLocaleString(), output.ratio, current.cob);

        return output;
    }
}

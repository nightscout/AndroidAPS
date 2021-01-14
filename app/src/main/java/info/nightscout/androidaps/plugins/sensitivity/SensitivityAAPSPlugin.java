package info.nightscout.androidaps.plugins.sensitivity;

import androidx.annotation.NonNull;
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
import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.interfaces.ProfileFunction;
import info.nightscout.androidaps.plugins.aps.openAPSSMB.SMBDefaults;
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.data.AutosensData;
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.AutosensResult;
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.IobCobCalculatorPlugin;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.resources.ResourceHelper;
import info.nightscout.androidaps.utils.sharedPreferences.SP;

/**
 * Created by mike on 24.06.2017.
 */

@Singleton
public class SensitivityAAPSPlugin extends AbstractSensitivityPlugin {

    private final ProfileFunction profileFunction;
    private final DateUtil dateUtil;

    @Inject
    public SensitivityAAPSPlugin(
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
                        .pluginName(R.string.sensitivityaaps)
                        .shortName(R.string.sensitivity_shortname)
                        .preferencesId(R.xml.pref_absorption_aaps)
                        .description(R.string.description_sensitivity_aaps),
                injector, aapsLogger, resourceHelper, sp
        );
        this.profileFunction = profileFunction;
        this.dateUtil = dateUtil;
    }

    @NonNull @Override
    public AutosensResult detectSensitivity(IobCobCalculatorInterface iobCobCalculatorPlugin, long fromTime, long toTime) {
        LongSparseArray<AutosensData> autosensDataTable = iobCobCalculatorPlugin.getAutosensDataTable();

        String age = getSp().getString(R.string.key_age, "");
        int defaultHours = 24;
        if (age.equals(getResourceHelper().gs(R.string.key_adult))) defaultHours = 24;
        if (age.equals(getResourceHelper().gs(R.string.key_teenage))) defaultHours = 4;
        if (age.equals(getResourceHelper().gs(R.string.key_child))) defaultHours = 4;
        int hoursForDetection = getSp().getInt(R.string.key_openapsama_autosens_period, defaultHours);

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
            getAapsLogger().debug(LTag.AUTOSENS, "No autosens data available. toTime: " + dateUtil.dateAndTimeString(toTime) + " lastDataTime: " + iobCobCalculatorPlugin.lastDataTime());
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

        String ratioLimit = "";
        String sensResult = "";

        getAapsLogger().debug(LTag.AUTOSENS, "Records: " + index + "   " + pastSensitivity);

        Arrays.sort(deviations);

        double percentile = IobCobCalculatorPlugin.percentile(deviations, 0.50);
        double basalOff = percentile * (60.0 / 5.0) / sens;
        double ratio = 1 + (basalOff / profile.getMaxDailyBasal());

        if (percentile < 0) { // sensitive
            sensResult = "Excess insulin sensitivity detected";
        } else if (percentile > 0) { // resistant
            sensResult = "Excess insulin resistance detected";
        } else {
            sensResult = "Sensitivity normal";
        }

        getAapsLogger().debug(LTag.AUTOSENS, sensResult);

        AutosensResult output = fillResult(ratio, current.cob, pastSensitivity, ratioLimit,
                sensResult, deviationsArray.size());

        getAapsLogger().debug(LTag.AUTOSENS, "Sensitivity to: "
                + dateUtil.dateAndTimeString(toTime) +
                " ratio: " + output.ratio
                + " mealCOB: " + current.cob);
        getAapsLogger().debug(LTag.AUTOSENS, "Sensitivity to: deviations " + Arrays.toString(deviations));

        return output;
    }

    @NotNull @Override public SensitivityType getId() {
        return SensitivityType.SENSITIVITY_AAPS;
    }

    @NotNull @Override public JSONObject configuration() {
        JSONObject c = new JSONObject();
        try {
            c.put(getResourceHelper().gs(R.string.key_absorption_maxtime), getSp().getDouble(R.string.key_absorption_maxtime, Constants.DEFAULT_MAX_ABSORPTION_TIME));
            c.put(getResourceHelper().gs(R.string.key_openapsama_autosens_period), getSp().getInt(R.string.key_openapsama_autosens_period, 24));
            c.put(getResourceHelper().gs(R.string.key_openapsama_autosens_max), getSp().getDouble(R.string.key_openapsama_autosens_max, 1.2));
            c.put(getResourceHelper().gs(R.string.key_openapsama_autosens_min), getSp().getDouble(R.string.key_openapsama_autosens_min, 0.7));
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return c;
    }

    @Override public void applyConfiguration(@NotNull JSONObject configuration) {
        try {
            if (configuration.has(getResourceHelper().gs(R.string.key_absorption_maxtime)))
                getSp().putDouble(R.string.key_absorption_maxtime, configuration.getDouble(getResourceHelper().gs(R.string.key_absorption_maxtime)));
            if (configuration.has(getResourceHelper().gs(R.string.key_openapsama_autosens_period)))
                getSp().putDouble(R.string.key_openapsama_autosens_period, configuration.getDouble(getResourceHelper().gs(R.string.key_openapsama_autosens_period)));
            if (configuration.has(getResourceHelper().gs(R.string.key_openapsama_autosens_max)))
                getSp().getDouble(R.string.key_openapsama_autosens_max, configuration.getDouble(getResourceHelper().gs(R.string.key_openapsama_autosens_max)));
            if (configuration.has(getResourceHelper().gs(R.string.key_openapsama_autosens_min)))
                getSp().getDouble(R.string.key_openapsama_autosens_min, configuration.getDouble(getResourceHelper().gs(R.string.key_openapsama_autosens_min)));
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
}

package info.nightscout.androidaps.plugins.ConfigBuilder;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.DetailedBolusInfo;
import info.nightscout.androidaps.data.IobTotal;
import info.nightscout.androidaps.data.MealData;
import info.nightscout.androidaps.data.Intervals;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.data.ProfileIntervals;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.db.ExtendedBolus;
import info.nightscout.androidaps.db.ProfileSwitch;
import info.nightscout.androidaps.db.TempTarget;
import info.nightscout.androidaps.db.TemporaryBasal;
import info.nightscout.androidaps.db.Treatment;
import info.nightscout.androidaps.events.EventBolusRequested;
import info.nightscout.androidaps.interfaces.APSInterface;
import info.nightscout.androidaps.interfaces.BgSourceInterface;
import info.nightscout.androidaps.interfaces.ConstraintsInterface;
import info.nightscout.androidaps.interfaces.InsulinInterface;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.ProfileInterface;
import info.nightscout.androidaps.interfaces.PumpDescription;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.interfaces.SensitivityInterface;
import info.nightscout.androidaps.interfaces.TreatmentsInterface;
import info.nightscout.androidaps.plugins.Loop.APSResult;
import info.nightscout.androidaps.plugins.Loop.LoopPlugin;
import info.nightscout.androidaps.plugins.Overview.Dialogs.BolusProgressDialog;
import info.nightscout.androidaps.plugins.Overview.Dialogs.BolusProgressHelperActivity;
import info.nightscout.androidaps.plugins.Overview.Notification;
import info.nightscout.androidaps.plugins.Overview.events.EventDismissBolusprogressIfRunning;
import info.nightscout.androidaps.plugins.Overview.events.EventDismissNotification;
import info.nightscout.androidaps.plugins.Overview.events.EventNewNotification;
import info.nightscout.androidaps.plugins.PumpVirtual.VirtualPumpPlugin;
import info.nightscout.utils.NSUpload;
import info.nightscout.utils.SP;

/**
 * Created by mike on 05.08.2016.
 */
public class ConfigBuilderPlugin implements PluginBase, PumpInterface, ConstraintsInterface, TreatmentsInterface {
    private static Logger log = LoggerFactory.getLogger(ConfigBuilderPlugin.class);

    private static BgSourceInterface activeBgSource;
    private static PumpInterface activePump;
    private static ProfileInterface activeProfile;
    private static TreatmentsInterface activeTreatments;
    private static APSInterface activeAPS;
    private static LoopPlugin activeLoop;
    private static InsulinInterface activeInsulin;
    private static SensitivityInterface activeSensitivity;

    static public String nightscoutVersionName = "";
    static public Integer nightscoutVersionCode = 0;
    static public String nsClientVersionName = "";
    static public Integer nsClientVersionCode = 0;

    private static ArrayList<PluginBase> pluginList;

    private PowerManager.WakeLock mWakeLock;

    public ConfigBuilderPlugin() {
        MainApp.bus().register(this);
        PowerManager powerManager = (PowerManager) MainApp.instance().getApplicationContext().getSystemService(Context.POWER_SERVICE);
        mWakeLock = powerManager.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "ConfigBuilderPlugin");
    }

    @Override
    public int getType() {
        return PluginBase.GENERAL;
    }

    @Override
    public String getFragmentClass() {
        return ConfigBuilderFragment.class.getName();
    }

    @Override
    public String getName() {
        return MainApp.instance().getString(R.string.configbuilder);
    }

    @Override
    public String getNameShort() {
        String name = MainApp.sResources.getString(R.string.configbuilder_shortname);
        if (!name.trim().isEmpty()) {
            //only if translation exists
            return name;
        }
        // use long name as fallback
        return getName();
    }

    @Override
    public boolean isEnabled(int type) {
        return type == GENERAL;
    }

    @Override
    public boolean isVisibleInTabs(int type) {
        return type == GENERAL;
    }

    @Override
    public boolean canBeHidden(int type) {
        return false;
    }

    @Override
    public boolean hasFragment() {
        return true;
    }

    @Override
    public boolean showInList(int type) {
        return false;
    }

    @Override
    public void setFragmentEnabled(int type, boolean fragmentEnabled) {
        // Always enabled
    }

    @Override
    public void setFragmentVisible(int type, boolean fragmentVisible) {
        // Always visible
    }

    public void initialize() {
        pluginList = MainApp.getPluginsList();
        loadSettings();
    }

    public void storeSettings() {
        if (pluginList != null) {
            if (Config.logPrefsChange)
                log.debug("Storing settings");
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(MainApp.instance().getApplicationContext());
            SharedPreferences.Editor editor = settings.edit();

            for (int type = 1; type < PluginBase.LAST; type++) {
                for (PluginBase p : pluginList) {
                    String settingEnabled = "ConfigBuilder_" + type + "_" + p.getClass().getSimpleName() + "_Enabled";
                    String settingVisible = "ConfigBuilder_" + type + "_" + p.getClass().getSimpleName() + "_Visible";
                    editor.putBoolean(settingEnabled, p.isEnabled(type));
                    editor.putBoolean(settingVisible, p.isVisibleInTabs(type));
                }
            }
            editor.apply();
            verifySelectionInCategories();
        }
    }

    private void loadSettings() {
        if (Config.logPrefsChange)
            log.debug("Loading stored settings");
        SharedPreferences SP = PreferenceManager.getDefaultSharedPreferences(MainApp.instance().getApplicationContext());
        for (int type = 1; type < PluginBase.LAST; type++) {
            for (PluginBase p : pluginList) {
                try {
                    String settingEnabled = "ConfigBuilder_" + type + "_" + p.getClass().getSimpleName() + "_Enabled";
                    String settingVisible = "ConfigBuilder_" + type + "_" + p.getClass().getSimpleName() + "_Visible";
                    if (SP.contains(settingEnabled))
                        p.setFragmentEnabled(type, SP.getBoolean(settingEnabled, true));
                    if (SP.contains(settingVisible))
                        p.setFragmentVisible(type, SP.getBoolean(settingVisible, true) && SP.getBoolean(settingEnabled, true));
                } catch (Exception e) {
                    log.error("Unhandled exception", e);
                }
            }
        }
        verifySelectionInCategories();
    }

    public static BgSourceInterface getActiveBgSource() {
        return activeBgSource;
    }

    public static ProfileInterface getActiveProfileInterface() {
        return activeProfile;
    }

    public static InsulinInterface getActiveInsulin() {
        return activeInsulin;
    }

    public static APSInterface getActiveAPS() {
        return activeAPS;
    }

    public static LoopPlugin getActiveLoop() {
        return activeLoop;
    }

    public static PumpInterface getActivePump() {
        return activePump;
    }

    public static SensitivityInterface getActiveSensitivity() {
        return activeSensitivity;
    }

    void logPluginStatus() {
        for (PluginBase p : pluginList) {
            log.debug(p.getName() + ":" +
                    (p.isEnabled(1) ? " GENERAL" : "") +
                    (p.isEnabled(2) ? " TREATMENT" : "") +
                    (p.isEnabled(3) ? " SENSITIVITY" : "") +
                    (p.isEnabled(4) ? " PROFILE" : "") +
                    (p.isEnabled(5) ? " APS" : "") +
                    (p.isEnabled(6) ? " PUMP" : "") +
                    (p.isEnabled(7) ? " CONSTRAINTS" : "") +
                    (p.isEnabled(8) ? " LOOP" : "") +
                    (p.isEnabled(9) ? " BGSOURCE" : "") +
                    (p.isEnabled(10) ? " INSULIN" : "")
            );
        }
    }

    private void verifySelectionInCategories() {
        ArrayList<PluginBase> pluginsInCategory;

        // PluginBase.APS
        pluginsInCategory = MainApp.getSpecificPluginsListByInterface(APSInterface.class);
        activeAPS = (APSInterface) getTheOneEnabledInArray(pluginsInCategory, PluginBase.APS);
        if (activeAPS != null) {
            if (Config.logConfigBuilder)
                log.debug("Selected APS interface: " + ((PluginBase) activeAPS).getName());
            for (PluginBase p : pluginsInCategory) {
                if (!p.getName().equals(((PluginBase) activeAPS).getName())) {
                    p.setFragmentVisible(PluginBase.APS, false);
                }
            }
        }

        // PluginBase.INSULIN
        pluginsInCategory = MainApp.getSpecificPluginsListByInterface(InsulinInterface.class);
        activeInsulin = (InsulinInterface) getTheOneEnabledInArray(pluginsInCategory, PluginBase.INSULIN);
        if (Config.logConfigBuilder)
            log.debug("Selected insulin interface: " + ((PluginBase) activeInsulin).getName());
        for (PluginBase p : pluginsInCategory) {
            if (!p.getName().equals(((PluginBase) activeInsulin).getName())) {
                p.setFragmentVisible(PluginBase.INSULIN, false);
            }
        }

        // PluginBase.SENSITIVITY
        pluginsInCategory = MainApp.getSpecificPluginsListByInterface(SensitivityInterface.class);
        activeSensitivity = (SensitivityInterface) getTheOneEnabledInArray(pluginsInCategory, PluginBase.SENSITIVITY);
        if (Config.logConfigBuilder)
            log.debug("Selected sensitivity interface: " + ((PluginBase) activeSensitivity).getName());
        for (PluginBase p : pluginsInCategory) {
            if (!p.getName().equals(((PluginBase) activeSensitivity).getName())) {
                p.setFragmentVisible(PluginBase.SENSITIVITY, false);
            }
        }

        // PluginBase.PROFILE
        pluginsInCategory = MainApp.getSpecificPluginsListByInterface(ProfileInterface.class);
        activeProfile = (ProfileInterface) getTheOneEnabledInArray(pluginsInCategory, PluginBase.PROFILE);
        if (Config.logConfigBuilder)
            log.debug("Selected profile interface: " + ((PluginBase) activeProfile).getName());
        for (PluginBase p : pluginsInCategory) {
            if (!p.getName().equals(((PluginBase) activeProfile).getName())) {
                p.setFragmentVisible(PluginBase.PROFILE, false);
            }
        }

        // PluginBase.BGSOURCE
        pluginsInCategory = MainApp.getSpecificPluginsListByInterface(BgSourceInterface.class);
        activeBgSource = (BgSourceInterface) getTheOneEnabledInArray(pluginsInCategory, PluginBase.BGSOURCE);
        if (Config.logConfigBuilder)
            log.debug("Selected bgSource interface: " + ((PluginBase) activeBgSource).getName());
        for (PluginBase p : pluginsInCategory) {
            if (!p.getName().equals(((PluginBase) activeBgSource).getName())) {
                p.setFragmentVisible(PluginBase.BGSOURCE, false);
            }
        }

        // PluginBase.PUMP
        pluginsInCategory = MainApp.getSpecificPluginsList(PluginBase.PUMP);
        activePump = (PumpInterface) getTheOneEnabledInArray(pluginsInCategory, PluginBase.PUMP);
        if (activePump == null)
            activePump = VirtualPumpPlugin.getInstance(); // for NSClient build
        if (Config.logConfigBuilder)
            log.debug("Selected pump interface: " + ((PluginBase) activePump).getName());
        for (PluginBase p : pluginsInCategory) {
            if (!p.getName().equals(((PluginBase) activePump).getName())) {
                p.setFragmentVisible(PluginBase.PUMP, false);
            }
        }

        // PluginBase.LOOP
        pluginsInCategory = MainApp.getSpecificPluginsList(PluginBase.LOOP);
        activeLoop = (LoopPlugin) getTheOneEnabledInArray(pluginsInCategory, PluginBase.LOOP);
        if (activeLoop != null) {
            if (Config.logConfigBuilder)
                log.debug("Selected loop interface: " + activeLoop.getName());
            for (PluginBase p : pluginsInCategory) {
                if (!p.getName().equals(activeLoop.getName())) {
                    p.setFragmentVisible(PluginBase.LOOP, false);
                }
            }
        }

        // PluginBase.TREATMENT
        pluginsInCategory = MainApp.getSpecificPluginsList(PluginBase.TREATMENT);
        activeTreatments = (TreatmentsInterface) getTheOneEnabledInArray(pluginsInCategory, PluginBase.TREATMENT);
        if (Config.logConfigBuilder)
            log.debug("Selected treatment interface: " + ((PluginBase) activeTreatments).getName());
        for (PluginBase p : pluginsInCategory) {
            if (!p.getName().equals(((PluginBase) activeTreatments).getName())) {
                p.setFragmentVisible(PluginBase.TREATMENT, false);
            }
        }
    }

    @Nullable
    private PluginBase getTheOneEnabledInArray(ArrayList<PluginBase> pluginsInCategory, int type) {
        PluginBase found = null;
        for (PluginBase p : pluginsInCategory) {
            if (p.isEnabled(type) && found == null) {
                found = p;
            } else if (p.isEnabled(type)) {
                // set others disabled
                p.setFragmentEnabled(type, false);
            }
        }
        // If none enabled, enable first one
        if (found == null && pluginsInCategory.size() > 0)
            found = pluginsInCategory.get(0);
        return found;
    }

    /*
        * Pump interface
        *
        * Config builder return itself as a pump and check constraints before it passes command to pump driver
        */
    @Override
    public boolean isInitialized() {
        if (activePump != null)
            return activePump.isInitialized();
        else return true;
    }

    @Override
    public boolean isSuspended() {
        if (activePump != null)
            return activePump.isSuspended();
        else return false;
    }

    @Override
    public boolean isBusy() {
        if (activePump != null)
            return activePump.isBusy();
        else return false;
    }

    @Override
    public int setNewBasalProfile(Profile profile) {
        // Compare with pump limits
        Profile.BasalValue[] basalValues = profile.getBasalValues();

        for (int index = 0; index < basalValues.length; index++) {
            if (basalValues[index].value < getPumpDescription().basalMinimumRate) {
                Notification notification = new Notification(Notification.BASAL_VALUE_BELOW_MINIMUM, MainApp.sResources.getString(R.string.basalvaluebelowminimum), Notification.URGENT);
                MainApp.bus().post(new EventNewNotification(notification));
                return FAILED;
            }
        }

        MainApp.bus().post(new EventDismissNotification(Notification.BASAL_VALUE_BELOW_MINIMUM));

        if (isThisProfileSet(profile)) {
            log.debug("Correct profile already set");
            return NOT_NEEDED;
        } else if (activePump != null) {
            return activePump.setNewBasalProfile(profile);
        } else
            return SUCCESS;
    }

    @Override
    public boolean isThisProfileSet(Profile profile) {
        if (activePump != null)
            return activePump.isThisProfileSet(profile);
        else return true;
    }

    @Override
    public Date lastDataTime() {
        if (activePump != null)
            return activePump.lastDataTime();
        else return new Date();
    }

    @Override
    public void refreshDataFromPump(String reason) {
        if (activePump != null)
            activePump.refreshDataFromPump(reason);
    }

    @Override
    public double getBaseBasalRate() {
        if (activePump != null)
            return activePump.getBaseBasalRate();
        else
            return 0d;
    }

    @Override
    public PumpEnactResult deliverTreatment(DetailedBolusInfo detailedBolusInfo) {
        mWakeLock.acquire();
        PumpEnactResult result;
        detailedBolusInfo.insulin = applyBolusConstraints(detailedBolusInfo.insulin);
        detailedBolusInfo.carbs = applyCarbsConstraints((int) detailedBolusInfo.carbs);

        BolusProgressDialog bolusProgressDialog = null;
        if (detailedBolusInfo.context != null) {
            bolusProgressDialog = new BolusProgressDialog();
            bolusProgressDialog.setInsulin(detailedBolusInfo.insulin);
            bolusProgressDialog.show(((AppCompatActivity) detailedBolusInfo.context).getSupportFragmentManager(), "BolusProgress");
        } else {
            Intent i = new Intent();
            i.putExtra("insulin", detailedBolusInfo.insulin);
            i.setClass(MainApp.instance(), BolusProgressHelperActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            MainApp.instance().startActivity(i);
        }


        MainApp.bus().post(new EventBolusRequested(detailedBolusInfo.insulin));

        result = activePump.deliverTreatment(detailedBolusInfo);

        BolusProgressDialog.bolusEnded = true;
        MainApp.bus().post(new EventDismissBolusprogressIfRunning(result));

        mWakeLock.release();
        return result;
    }

    @Override
    public void stopBolusDelivering() {
        activePump.stopBolusDelivering();
    }

    /**
     * apply constraints, set temp based on absolute valus and expecting absolute result
     *
     * @param absoluteRate
     * @param durationInMinutes
     * @return
     */
    @Override
    public PumpEnactResult setTempBasalAbsolute(Double absoluteRate, Integer durationInMinutes, boolean force) {
        Double rateAfterConstraints = applyBasalConstraints(absoluteRate);
        PumpEnactResult result = activePump.setTempBasalAbsolute(rateAfterConstraints, durationInMinutes, force);
        if (Config.logCongigBuilderActions)
            log.debug("setTempBasalAbsolute rate: " + rateAfterConstraints + " durationInMinutes: " + durationInMinutes + " success: " + result.success + " enacted: " + result.enacted);
        return result;
    }

    public PumpEnactResult setTempBasalAbsolute(Double absoluteRate, Integer durationInMinutes) {
        return setTempBasalAbsolute(absoluteRate, durationInMinutes, false);
    }

    /**
     * apply constraints, set temp based on percent and expecting result in percent
     *
     * @param percent           0 ... 100 ...
     * @param durationInMinutes
     * @return result
     */
    @Override
    public PumpEnactResult setTempBasalPercent(Integer percent, Integer durationInMinutes) {
        Integer percentAfterConstraints = applyBasalConstraints(percent);
        PumpEnactResult result = activePump.setTempBasalPercent(percentAfterConstraints, durationInMinutes);
        if (Config.logCongigBuilderActions)
            log.debug("setTempBasalPercent percent: " + percentAfterConstraints + " durationInMinutes: " + durationInMinutes + " success: " + result.success + " enacted: " + result.enacted);
        return result;
    }

    @Override
    public PumpEnactResult setExtendedBolus(Double insulin, Integer durationInMinutes) {
        Double rateAfterConstraints = applyBolusConstraints(insulin);
        PumpEnactResult result = activePump.setExtendedBolus(rateAfterConstraints, durationInMinutes);
        if (Config.logCongigBuilderActions)
            log.debug("setExtendedBolus rate: " + rateAfterConstraints + " durationInMinutes: " + durationInMinutes + " success: " + result.success + " enacted: " + result.enacted);
        return result;
    }

    @Override
    public PumpEnactResult cancelTempBasal(boolean force) {
        PumpEnactResult result = activePump.cancelTempBasal(force);
        if (Config.logCongigBuilderActions)
            log.debug("cancelTempBasal success: " + result.success + " enacted: " + result.enacted);
        return result;
    }

    @Override
    public PumpEnactResult cancelExtendedBolus() {
        PumpEnactResult result = activePump.cancelExtendedBolus();
        if (Config.logCongigBuilderActions)
            log.debug("cancelExtendedBolus success: " + result.success + " enacted: " + result.enacted);
        return result;
    }

    /**
     * expect absolute request and allow both absolute and percent response based on pump capabilities
     *
     * @param request
     * @return
     */

    public PumpEnactResult applyAPSRequest(APSResult request) {
        request.rate = applyBasalConstraints(request.rate);
        PumpEnactResult result;

        if (!isInitialized()) {
            result = new PumpEnactResult();
            result.comment = MainApp.sResources.getString(R.string.pumpNotInitialized);
            result.enacted = false;
            result.success = false;
            log.debug("applyAPSRequest: " + MainApp.sResources.getString(R.string.pumpNotInitialized));
            return result;
        }

        if (isSuspended()) {
            result = new PumpEnactResult();
            result.comment = MainApp.sResources.getString(R.string.pumpsuspended);
            result.enacted = false;
            result.success = false;
            log.debug("applyAPSRequest: " + MainApp.sResources.getString(R.string.pumpsuspended));
            return result;
        }

        if (Config.logCongigBuilderActions)
            log.debug("applyAPSRequest: " + request.toString());
        if ((request.rate == 0 && request.duration == 0) || Math.abs(request.rate - getBaseBasalRate()) < 0.05) {
            if (isTempBasalInProgress()) {
                if (Config.logCongigBuilderActions)
                    log.debug("applyAPSRequest: cancelTempBasal()");
                result = cancelTempBasal(false);
            } else {
                result = new PumpEnactResult();
                result.absolute = request.rate;
                result.duration = 0;
                result.enacted = false;
                result.comment = "Basal set correctly";
                result.success = true;
                if (Config.logCongigBuilderActions)
                    log.debug("applyAPSRequest: Basal set correctly");
            }
        } else if (isTempBasalInProgress() && Math.abs(request.rate - getTempBasalAbsoluteRateHistory()) < getPumpDescription().basalStep) {
            result = new PumpEnactResult();
            result.absolute = getTempBasalAbsoluteRateHistory();
            result.duration = getTempBasalFromHistory(System.currentTimeMillis()).getPlannedRemainingMinutes();
            result.enacted = false;
            result.comment = "Temp basal set correctly";
            result.success = true;
            if (Config.logCongigBuilderActions)
                log.debug("applyAPSRequest: Temp basal set correctly");
        } else {
            if (Config.logCongigBuilderActions)
                log.debug("applyAPSRequest: setTempBasalAbsolute()");
            result = setTempBasalAbsolute(request.rate, request.duration);
        }
        return result;
    }

    @Nullable
    @Override
    public JSONObject getJSONStatus() {
        if (activePump != null)
            return activePump.getJSONStatus();
        else return null;
    }

    @Override
    public String deviceID() {
        if (activePump != null)
            return activePump.deviceID();
        else return "No Pump active!";
    }

    @Override
    public PumpDescription getPumpDescription() {
        if (activePump != null)
            return activePump.getPumpDescription();
        else {
            PumpDescription emptyDescription = new PumpDescription();
            emptyDescription.isBolusCapable = false;
            emptyDescription.isExtendedBolusCapable = false;
            emptyDescription.isSetBasalProfileCapable = false;
            emptyDescription.isTempBasalCapable = true; // needs to be true before real driver is selected
            emptyDescription.isRefillingCapable = false;
            return emptyDescription;
        }
    }

    @Override
    public String shortStatus(boolean veryShort) {
        if (activePump != null) {
            return activePump.shortStatus(veryShort);
        } else {
            return "No Pump active!";
        }
    }

    @Override
    public boolean isFakingTempsByExtendedBoluses() {
        return activePump.isFakingTempsByExtendedBoluses();
    }

    /**
     * Constraints interface
     **/
    @Override
    public boolean isLoopEnabled() {
        boolean result = true;

        ArrayList<PluginBase> constraintsPlugins = MainApp.getSpecificPluginsListByInterface(ConstraintsInterface.class);
        for (PluginBase p : constraintsPlugins) {
            ConstraintsInterface constrain = (ConstraintsInterface) p;
            if (!p.isEnabled(PluginBase.CONSTRAINTS)) continue;
            result = result && constrain.isLoopEnabled();
        }
        return result;
    }

    @Override
    public boolean isClosedModeEnabled() {
        boolean result = true;

        ArrayList<PluginBase> constraintsPlugins = MainApp.getSpecificPluginsListByInterface(ConstraintsInterface.class);
        for (PluginBase p : constraintsPlugins) {
            ConstraintsInterface constrain = (ConstraintsInterface) p;
            if (!p.isEnabled(PluginBase.CONSTRAINTS)) continue;
            result = result && constrain.isClosedModeEnabled();
        }
        return result;
    }

    @Override
    public boolean isAutosensModeEnabled() {
        boolean result = true;

        ArrayList<PluginBase> constraintsPlugins = MainApp.getSpecificPluginsListByInterface(ConstraintsInterface.class);
        for (PluginBase p : constraintsPlugins) {
            ConstraintsInterface constrain = (ConstraintsInterface) p;
            if (!p.isEnabled(PluginBase.CONSTRAINTS)) continue;
            result = result && constrain.isAutosensModeEnabled();
        }
        return result;
    }

    @Override
    public boolean isAMAModeEnabled() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(MainApp.instance().getApplicationContext());
        boolean result = preferences.getBoolean("openapsama_useautosens", false);

        ArrayList<PluginBase> constraintsPlugins = MainApp.getSpecificPluginsListByInterface(ConstraintsInterface.class);
        for (PluginBase p : constraintsPlugins) {
            ConstraintsInterface constrain = (ConstraintsInterface) p;
            if (!p.isEnabled(PluginBase.CONSTRAINTS)) continue;
            result = result && constrain.isAMAModeEnabled();
        }
        return result;
    }

    @Override
    public Double applyBasalConstraints(Double absoluteRate) {
        Double rateAfterConstrain = absoluteRate;
        ArrayList<PluginBase> constraintsPlugins = MainApp.getSpecificPluginsListByInterface(ConstraintsInterface.class);
        for (PluginBase p : constraintsPlugins) {
            ConstraintsInterface constrain = (ConstraintsInterface) p;
            if (!p.isEnabled(PluginBase.CONSTRAINTS)) continue;
            rateAfterConstrain = Math.min(constrain.applyBasalConstraints(absoluteRate), rateAfterConstrain);
        }
        return rateAfterConstrain;
    }

    @Override
    public Integer applyBasalConstraints(Integer percentRate) {
        Integer rateAfterConstrain = percentRate;
        ArrayList<PluginBase> constraintsPlugins = MainApp.getSpecificPluginsListByInterface(ConstraintsInterface.class);
        for (PluginBase p : constraintsPlugins) {
            ConstraintsInterface constrain = (ConstraintsInterface) p;
            if (!p.isEnabled(PluginBase.CONSTRAINTS)) continue;
            rateAfterConstrain = Math.min(constrain.applyBasalConstraints(percentRate), rateAfterConstrain);
        }
        return rateAfterConstrain;
    }

    @Override
    public Double applyBolusConstraints(Double insulin) {
        Double insulinAfterConstrain = insulin;
        ArrayList<PluginBase> constraintsPlugins = MainApp.getSpecificPluginsListByInterface(ConstraintsInterface.class);
        for (PluginBase p : constraintsPlugins) {
            ConstraintsInterface constrain = (ConstraintsInterface) p;
            if (!p.isEnabled(PluginBase.CONSTRAINTS)) continue;
            insulinAfterConstrain = Math.min(constrain.applyBolusConstraints(insulin), insulinAfterConstrain);
        }
        return insulinAfterConstrain;
    }

    @Override
    public Integer applyCarbsConstraints(Integer carbs) {
        Integer carbsAfterConstrain = carbs;
        ArrayList<PluginBase> constraintsPlugins = MainApp.getSpecificPluginsListByInterface(ConstraintsInterface.class);
        for (PluginBase p : constraintsPlugins) {
            ConstraintsInterface constrain = (ConstraintsInterface) p;
            if (!p.isEnabled(PluginBase.CONSTRAINTS)) continue;
            carbsAfterConstrain = Math.min(constrain.applyCarbsConstraints(carbs), carbsAfterConstrain);
        }
        return carbsAfterConstrain;
    }

    @Override
    public Double applyMaxIOBConstraints(Double maxIob) {
        Double maxIobAfterConstrain = maxIob;
        ArrayList<PluginBase> constraintsPlugins = MainApp.getSpecificPluginsListByInterface(ConstraintsInterface.class);
        for (PluginBase p : constraintsPlugins) {
            ConstraintsInterface constrain = (ConstraintsInterface) p;
            if (!p.isEnabled(PluginBase.CONSTRAINTS)) continue;
            maxIobAfterConstrain = Math.min(constrain.applyMaxIOBConstraints(maxIob), maxIobAfterConstrain);
        }
        return maxIobAfterConstrain;
    }

    //  ****** Treatments interface *****
    @Override
    public void updateTotalIOBTreatments() {
        activeTreatments.updateTotalIOBTreatments();
    }

    @Override
    public void updateTotalIOBTempBasals() {
        activeTreatments.updateTotalIOBTempBasals();
    }

    @Override
    public IobTotal getLastCalculationTreatments() {
        return activeTreatments.getLastCalculationTreatments();
    }

    @Override
    public IobTotal getCalculationToTimeTreatments(long time) {
        return activeTreatments.getCalculationToTimeTreatments(time);
    }

    @Override
    public IobTotal getLastCalculationTempBasals() {
        return activeTreatments.getLastCalculationTempBasals();
    }

    @Override
    public IobTotal getCalculationToTimeTempBasals(long time) {
        return activeTreatments.getCalculationToTimeTempBasals(time);
    }

    @Override
    public MealData getMealData() {
        return activeTreatments.getMealData();
    }

    @Override
    public List<Treatment> getTreatmentsFromHistory() {
        return activeTreatments.getTreatmentsFromHistory();
    }

    @Override
    public List<Treatment> getTreatments5MinBackFromHistory(long time) {
        return activeTreatments.getTreatments5MinBackFromHistory(time);
    }

    @Override
    public boolean isInHistoryRealTempBasalInProgress() {
        return activeTreatments.isInHistoryRealTempBasalInProgress();
    }

    @Override
    @Nullable
    public TemporaryBasal getRealTempBasalFromHistory(long time) {
        return activeTreatments.getRealTempBasalFromHistory(time);
    }

    @Override
    public boolean isTempBasalInProgress() {
        return activeTreatments.isTempBasalInProgress();
    }

    @Override
    @Nullable
    public TemporaryBasal getTempBasalFromHistory(long time) {
        return activeTreatments.getTempBasalFromHistory(time);
    }

    @Override
    public double getTempBasalAbsoluteRateHistory() {
        return activeTreatments.getTempBasalAbsoluteRateHistory();
    }

    @Override
    public double getTempBasalRemainingMinutesFromHistory() {
        return activeTreatments.getTempBasalRemainingMinutesFromHistory();
    }

    @Override
    public Intervals<TemporaryBasal> getTemporaryBasalsFromHistory() {
        return activeTreatments.getTemporaryBasalsFromHistory();
    }

    @Override
    public boolean addToHistoryTempBasal(TemporaryBasal tempBasal) {
        boolean newRecordCreated = activeTreatments.addToHistoryTempBasal(tempBasal);
        if (newRecordCreated) {
            if (tempBasal.durationInMinutes == 0)
                NSUpload.uploadTempBasalEnd(tempBasal.date, false, tempBasal.pumpId);
            else if (tempBasal.isAbsolute)
                NSUpload.uploadTempBasalStartAbsolute(tempBasal, null);
            else
                NSUpload.uploadTempBasalStartPercent(tempBasal);
        }
        return newRecordCreated;
    }

    @Override
    public boolean isInHistoryExtendedBoluslInProgress() {
        return activeTreatments.isInHistoryExtendedBoluslInProgress();
    }

    @Override
    @Nullable
    public ExtendedBolus getExtendedBolusFromHistory(long time) {
        return activeTreatments.getExtendedBolusFromHistory(time);
    }

    @Override
    public boolean addToHistoryExtendedBolus(ExtendedBolus extendedBolus) {
        boolean newRecordCreated = activeTreatments.addToHistoryExtendedBolus(extendedBolus);
        if (newRecordCreated) {
            if (extendedBolus.durationInMinutes == 0) {
                if (activePump.isFakingTempsByExtendedBoluses())
                    NSUpload.uploadTempBasalEnd(extendedBolus.date, true, extendedBolus.pumpId);
                else
                    NSUpload.uploadExtendedBolusEnd(extendedBolus.date, extendedBolus.pumpId);
            } else if (activePump.isFakingTempsByExtendedBoluses())
                NSUpload.uploadTempBasalStartAbsolute(new TemporaryBasal(extendedBolus), extendedBolus.insulin);
            else
                NSUpload.uploadExtendedBolus(extendedBolus);
        }
        return newRecordCreated;
    }

    @Override
    public Intervals<ExtendedBolus> getExtendedBolusesFromHistory() {
        return activeTreatments.getExtendedBolusesFromHistory();
    }

    @Override
    // return true if new record is created
    public boolean addToHistoryTreatment(DetailedBolusInfo detailedBolusInfo) {
        boolean newRecordCreated = activeTreatments.addToHistoryTreatment(detailedBolusInfo);
        if (newRecordCreated && detailedBolusInfo.isValid)
            NSUpload.uploadBolusWizardRecord(detailedBolusInfo);
        return newRecordCreated;
    }

    @Override
    @Nullable
    public TempTarget getTempTargetFromHistory() {
        return activeTreatments.getTempTargetFromHistory(System.currentTimeMillis());
    }

    @Override
    @Nullable
    public TempTarget getTempTargetFromHistory(long time) {
        return activeTreatments.getTempTargetFromHistory(time);
    }

    @Override
    public Intervals<TempTarget> getTempTargetsFromHistory() {
        return activeTreatments.getTempTargetsFromHistory();
    }

    @Override
    @Nullable
    public ProfileSwitch getProfileSwitchFromHistory(long time) {
        return activeTreatments.getProfileSwitchFromHistory(time);
    }

    @Override
    public ProfileIntervals<ProfileSwitch> getProfileSwitchesFromHistory() {
        return activeTreatments.getProfileSwitchesFromHistory();
    }

    @Override
    public void addToHistoryProfileSwitch(ProfileSwitch profileSwitch) {
        activeTreatments.addToHistoryProfileSwitch(profileSwitch);
        NSUpload.uploadProfileSwitch(profileSwitch);
    }

    @Override
    public long oldestDataAvailable() {
        return activeTreatments.oldestDataAvailable();
    }

    public String getProfileName() {
        return getProfileName(System.currentTimeMillis());
    }

    public String getProfileName(long time) {
        return getProfileName(time, true);
    }

    public String getProfileName(long time, boolean customized) {
        ProfileSwitch profileSwitch = getProfileSwitchFromHistory(time);
        if (profileSwitch != null) {
            if (profileSwitch.profileJson != null) {
                return customized ? profileSwitch.getCustomizedName() : profileSwitch.profileName;
            } else {
                Profile profile = activeProfile.getProfile().getSpecificProfile(profileSwitch.profileName);
                if (profile != null)
                    return profileSwitch.profileName;
            }
        }
        // Unable to determine profile, failover to default
        String defaultProfile = activeProfile.getProfile().getDefaultProfileName();
        if (defaultProfile != null)
            return defaultProfile;
        // If default from plugin fails .... create empty
        return "Default";
    }

    public Profile getProfile() {
        return getProfile(System.currentTimeMillis());
    }

    public String getProfileUnits() {
        return getProfile().getUnits();
    }

    public Profile getProfile(long time) {
        if (activeTreatments == null)
            return null; //app not initialized
        //log.debug("Profile for: " + new Date(time).toLocaleString() + " : " + getProfileName(time));
        boolean ignoreProfileSwitchEvents = SP.getBoolean(R.string.key_do_not_track_profile_switch, false);
        if (!ignoreProfileSwitchEvents) {
            ProfileSwitch profileSwitch = getProfileSwitchFromHistory(time);
            if (profileSwitch != null) {
                if (profileSwitch.profileJson != null) {
                    return profileSwitch.getProfileObject();
                } else if (activeProfile.getProfile() != null) {
                    Profile profile = activeProfile.getProfile().getSpecificProfile(profileSwitch.profileName);
                    if (profile != null)
                        return profile;
                }
            }
            // Unable to determine profile, failover to default
            if (activeProfile.getProfile() == null)
                return null; //app not initialized
        }
        Profile defaultProfile = activeProfile.getProfile().getDefaultProfile();
        if (defaultProfile != null)
            return defaultProfile;
        // If default from plugin fails .... create empty
        try {
            Notification noisf = new Notification(Notification.ISF_MISSING, MainApp.sResources.getString(R.string.isfmissing), Notification.URGENT);
            MainApp.bus().post(new EventNewNotification(noisf));
            Notification noic = new Notification(Notification.IC_MISSING, MainApp.sResources.getString(R.string.icmissing), Notification.URGENT);
            MainApp.bus().post(new EventNewNotification(noic));
            Notification nobasal = new Notification(Notification.BASAL_MISSING, MainApp.sResources.getString(R.string.basalmissing), Notification.URGENT);
            MainApp.bus().post(new EventNewNotification(nobasal));
            Notification notarget = new Notification(Notification.TARGET_MISSING, MainApp.sResources.getString(R.string.targetmissing), Notification.URGENT);
            MainApp.bus().post(new EventNewNotification(notarget));
            return new Profile(new JSONObject("{\"dia\":\"3\",\"carbratio\":[{\"time\":\"00:00\",\"value\":\"20\"}],\"carbs_hr\":\"20\",\"delay\":\"20\",\"sens\":[{\"time\":\"00:00\",\"value\":\"20\"}],\"timezone\":\"UTC\",\"basal\":[{\"time\":\"00:00\",\"value\":\"0.1\"}],\"target_low\":[{\"time\":\"00:00\",\"value\":\"6\"}],\"target_high\":[{\"time\":\"00:00\",\"value\":\"8\"}],\"startDate\":\"1970-01-01T00:00:00.000Z\",\"units\":\"mmol\"}}"), 100, 0);
        } catch (JSONException e) {
            log.error("Unhandled exception", e);
        }
        return null;
    }
}

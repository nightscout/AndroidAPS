package info.nightscout.androidaps.plugins.ConfigBuilder;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;

import com.squareup.otto.Subscribe;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Date;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.Services.Intents;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.db.TempBasal;
import info.nightscout.androidaps.db.Treatment;
import info.nightscout.androidaps.events.EventNewBG;
import info.nightscout.androidaps.events.EventTempBasalChange;
import info.nightscout.androidaps.events.EventTreatmentChange;
import info.nightscout.androidaps.interfaces.APSInterface;
import info.nightscout.androidaps.interfaces.BgSourceInterface;
import info.nightscout.androidaps.interfaces.ConstraintsInterface;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.ProfileInterface;
import info.nightscout.androidaps.interfaces.PumpDescription;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.interfaces.TempBasalsInterface;
import info.nightscout.androidaps.interfaces.TreatmentsInterface;
import info.nightscout.androidaps.plugins.DanaR.comm.MsgError;
import info.nightscout.androidaps.plugins.Loop.APSResult;
import info.nightscout.androidaps.plugins.Loop.DeviceStatus;
import info.nightscout.androidaps.plugins.Loop.LoopPlugin;
import info.nightscout.androidaps.plugins.OpenAPSAMA.DetermineBasalResultAMA;
import info.nightscout.androidaps.plugins.OpenAPSMA.DetermineBasalResultMA;
import info.nightscout.androidaps.plugins.Overview.Dialogs.BolusProgressDialog;
import info.nightscout.androidaps.plugins.Overview.Notification;
import info.nightscout.androidaps.plugins.Overview.events.EventDismissNotification;
import info.nightscout.androidaps.plugins.Overview.events.EventNewNotification;
import info.nightscout.client.data.DbLogger;
import info.nightscout.client.data.NSProfile;
import info.nightscout.utils.DateUtil;

/**
 * Created by mike on 05.08.2016.
 */
public class ConfigBuilderPlugin implements PluginBase, PumpInterface, ConstraintsInterface {
    private static Logger log = LoggerFactory.getLogger(ConfigBuilderPlugin.class);

    static BgSourceInterface activeBgSource;
    static PumpInterface activePump;
    static ProfileInterface activeProfile;
    static TreatmentsInterface activeTreatments;
    static TempBasalsInterface activeTempBasals;
    static APSInterface activeAPS;
    static LoopPlugin activeLoop;

    static public String nightscoutVersionName = "";
    static public Integer nightscoutVersionCode = 0;
    static public String nsClientVersionName = "";
    static public Integer nsClientVersionCode = 0;

    static ArrayList<PluginBase> pluginList;

    private static final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor();
    private static ScheduledFuture<?> scheduledPost = null;

    PowerManager.WakeLock mWakeLock;

    public ConfigBuilderPlugin() {
        MainApp.bus().register(this);
        PowerManager powerManager = (PowerManager) MainApp.instance().getApplicationContext().getSystemService(Context.POWER_SERVICE);
        mWakeLock = powerManager.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "ConfigBuilderPlugin");
        ;
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
        return type == GENERAL && true;
    }

    @Override
    public boolean isVisibleInTabs(int type) {
        return type == GENERAL && true;
    }

    @Override
    public boolean canBeHidden(int type) {
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
                        p.setFragmentVisible(type, SP.getBoolean(settingVisible, true));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        verifySelectionInCategories();
    }

    public static BgSourceInterface getActiveBgSource() {
        return activeBgSource;
    }

    @Nullable
    public static ProfileInterface getActiveProfile() {
        return activeProfile;
    }

    public static TreatmentsInterface getActiveTreatments() {
        return activeTreatments;
    }

    public static TempBasalsInterface getActiveTempBasals() {
        return activeTempBasals;
    }

    public static APSInterface getActiveAPS() {
        return activeAPS;
    }

    public static LoopPlugin getActiveLoop() {
        return activeLoop;
    }

    public void logPluginStatus() {
        for (PluginBase p : pluginList) {
            log.debug(p.getName() + ":" +
                    (p.isEnabled(1) ? " GENERAL" : "") +
                    (p.isEnabled(2) ? " TREATMENT" : "") +
                    (p.isEnabled(3) ? " TEMPBASAL" : "") +
                    (p.isEnabled(4) ? " PROFILE" : "") +
                    (p.isEnabled(5) ? " APS" : "") +
                    (p.isEnabled(6) ? " PUMP" : "") +
                    (p.isEnabled(7) ? " CONSTRAINTS" : "") +
                    (p.isEnabled(8) ? " LOOP" : "") +
                    (p.isEnabled(9) ? " BGSOURCE" : "")
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

        // PluginBase.TEMPBASAL
        pluginsInCategory = MainApp.getSpecificPluginsList(PluginBase.TEMPBASAL);
        activeTempBasals = (TempBasalsInterface) getTheOneEnabledInArray(pluginsInCategory, PluginBase.TEMPBASAL);
        if (Config.logConfigBuilder)
            log.debug("Selected tempbasal interface: " + ((PluginBase) activeTempBasals).getName());
        for (PluginBase p : pluginsInCategory) {
            if (!p.getName().equals(((PluginBase) activeTempBasals).getName())) {
                p.setFragmentVisible(PluginBase.TEMPBASAL, false);
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
        return activePump.isInitialized();
    }

    @Override
    public boolean isSuspended() {
        return activePump.isSuspended();
    }

    @Override
    public boolean isBusy() {
        return activePump.isBusy();
    }

    @Override
    public boolean isTempBasalInProgress() {
        return activePump.isTempBasalInProgress();
    }

    @Override
    public boolean isExtendedBoluslInProgress() {
        return activePump.isExtendedBoluslInProgress();
    }

    @Override
    public int setNewBasalProfile(NSProfile profile) {
        // Compare with pump limits
        NSProfile.BasalValue[] basalValues = profile.getBasalValues();

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
        } else {
            return activePump.setNewBasalProfile(profile);
        }
    }

    @Override
    public boolean isThisProfileSet(NSProfile profile) {
        return activePump.isThisProfileSet(profile);
    }

    @Override
    public Date lastStatusTime() {
        return activePump.lastStatusTime();
    }

    @Override
    public void updateStatus(String reason) {
        activePump.updateStatus(reason);
    }

    @Override
    public double getBaseBasalRate() {
        return activePump.getBaseBasalRate();
    }

    @Override
    public double getTempBasalAbsoluteRate() {
        return activePump.getTempBasalAbsoluteRate();
    }

    @Override
    public double getTempBasalRemainingMinutes() {
        return activePump.getTempBasalRemainingMinutes();
    }

    @Override
    public TempBasal getTempBasal(Date time) {
        return activePump.getTempBasal(time);
    }

    @Override
    public TempBasal getTempBasal() {
        return activePump.getTempBasal();
    }

    @Override
    public TempBasal getExtendedBolus() {
        return activePump.getExtendedBolus();
    }

    public PumpEnactResult deliverTreatmentFromBolusWizard(Context context, Double insulin, Integer carbs, Double glucose, String glucoseType, int carbTime, JSONObject boluscalc) {
        mWakeLock.acquire();
        insulin = applyBolusConstraints(insulin);
        carbs = applyCarbsConstraints(carbs);

        BolusProgressDialog bolusProgressDialog = null;
        if (context != null) {
            bolusProgressDialog = new BolusProgressDialog();
            bolusProgressDialog.setInsulin(insulin);
            bolusProgressDialog.show(((AppCompatActivity) context).getSupportFragmentManager(), "BolusProgress");
        }

        PumpEnactResult result = activePump.deliverTreatment(insulin, carbs, context);

        BolusProgressDialog.bolusEnded = true;

        if (bolusProgressDialog != null && BolusProgressDialog.running) {
            try {
                bolusProgressDialog.dismiss();
            } catch (Exception e) {
                e.printStackTrace(); // TODO: handle this better
            }
        }

        if (result.success) {
            Treatment t = new Treatment();
            t.insulin = result.bolusDelivered;
            if (carbTime == 0)
                t.carbs = (double) result.carbsDelivered; // with different carbTime record will come back from nightscout
            t.created_at = new Date();
            t.mealBolus = result.carbsDelivered > 0;
            MainApp.getDbHelper().create(t);
            t.setTimeIndex(t.getTimeIndex());
            t.carbs = (double) result.carbsDelivered;
            uploadBolusWizardRecord(t, glucose, glucoseType, carbTime, boluscalc);
        }
        mWakeLock.release();
        return result;
    }

    @Override
    public PumpEnactResult deliverTreatment(Double insulin, Integer carbs, Context context) {
        return deliverTreatment(insulin, carbs, context, true);
    }

    public PumpEnactResult deliverTreatment(Double insulin, Integer carbs, Context context, boolean createTreatment) {
        mWakeLock.acquire();
        insulin = applyBolusConstraints(insulin);
        carbs = applyCarbsConstraints(carbs);

        BolusProgressDialog bolusProgressDialog = null;
        if (context != null) {
            bolusProgressDialog = new BolusProgressDialog();
            bolusProgressDialog.setInsulin(insulin);
            bolusProgressDialog.show(((AppCompatActivity) context).getSupportFragmentManager(), "BolusProgress");
        }

        PumpEnactResult result = activePump.deliverTreatment(insulin, carbs, context);

        BolusProgressDialog.bolusEnded = true;

        if (bolusProgressDialog != null && BolusProgressDialog.running) {
            try {
                bolusProgressDialog.dismiss();
            } catch (Exception e) {
                e.printStackTrace(); // TODO: handle this better
            }
        }

        if (Config.logCongigBuilderActions)
            log.debug("deliverTreatment insulin: " + insulin + " carbs: " + carbs + " success: " + result.success + " enacted: " + result.enacted + " bolusDelivered: " + result.bolusDelivered);

        if (result.success && createTreatment) {
            Treatment t = new Treatment();
            t.insulin = result.bolusDelivered;
            t.carbs = (double) result.carbsDelivered;
            t.created_at = new Date();
            t.mealBolus = t.carbs > 0;
            MainApp.getDbHelper().create(t);
            t.setTimeIndex(t.getTimeIndex());
            t.sendToNSClient();
        }
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
    public PumpEnactResult setTempBasalAbsolute(Double absoluteRate, Integer durationInMinutes) {
        Double rateAfterConstraints = applyBasalConstraints(absoluteRate);
        PumpEnactResult result = activePump.setTempBasalAbsolute(rateAfterConstraints, durationInMinutes);
        if (Config.logCongigBuilderActions)
            log.debug("setTempBasalAbsolute rate: " + rateAfterConstraints + " durationInMinutes: " + durationInMinutes + " success: " + result.success + " enacted: " + result.enacted);
        if (result.enacted && result.success) {
            if (result.isPercent) {
                uploadTempBasalStartPercent(result.percent, result.duration);
            } else {
                uploadTempBasalStartAbsolute(result.absolute, result.duration);
            }
            MainApp.bus().post(new EventTempBasalChange());
        }
        return result;
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
        if (result.enacted && result.success) {
            uploadTempBasalStartPercent(result.percent, result.duration);
            MainApp.bus().post(new EventTempBasalChange());
        }
        return result;
    }

    @Override
    public PumpEnactResult setExtendedBolus(Double insulin, Integer durationInMinutes) {
        Double rateAfterConstraints = applyBolusConstraints(insulin);
        PumpEnactResult result = activePump.setExtendedBolus(rateAfterConstraints, durationInMinutes);
        if (Config.logCongigBuilderActions)
            log.debug("setExtendedBolus rate: " + rateAfterConstraints + " durationInMinutes: " + durationInMinutes + " success: " + result.success + " enacted: " + result.enacted);
        if (result.enacted && result.success) {
            uploadExtendedBolus(result.bolusDelivered, result.duration);
            MainApp.bus().post(new EventTreatmentChange());
        }
        return result;
    }

    @Override
    public PumpEnactResult cancelTempBasal() {
        PumpEnactResult result = activePump.cancelTempBasal();
        if (Config.logCongigBuilderActions)
            log.debug("cancelTempBasal success: " + result.success + " enacted: " + result.enacted);
        if (result.enacted && result.success) {
            uploadTempBasalEnd();
            MainApp.bus().post(new EventTempBasalChange());
        }
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
                result = cancelTempBasal();
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
        } else if (isTempBasalInProgress() && Math.abs(request.rate - getTempBasalAbsoluteRate()) < 0.05) {
            result = new PumpEnactResult();
            result.absolute = getTempBasalAbsoluteRate();
            result.duration = activePump.getTempBasal().getPlannedRemainingMinutes();
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
        else return "Unknown";
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

    @Subscribe
    public void onStatusEvent(final EventNewBG ev) {
        uploadDeviceStatus(120);
    }

    public void uploadTempBasalStartAbsolute(Double absolute, double durationInMinutes) {
        try {
            Context context = MainApp.instance().getApplicationContext();
            JSONObject data = new JSONObject();
            data.put("eventType", "Temp Basal");
            data.put("duration", durationInMinutes);
            data.put("absolute", absolute);
            data.put("created_at", DateUtil.toISOString(new Date()));
            data.put("enteredBy", MainApp.instance().getString(R.string.app_name));
            data.put("notes", MainApp.sResources.getString(R.string.androidaps_tempbasalstartnote) + " " + absolute + "u/h " + durationInMinutes + " min"); // ECOR
            Bundle bundle = new Bundle();
            bundle.putString("action", "dbAdd");
            bundle.putString("collection", "treatments");
            bundle.putString("data", data.toString());
            Intent intent = new Intent(Intents.ACTION_DATABASE);
            intent.putExtras(bundle);
            intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
            context.sendBroadcast(intent);
            DbLogger.dbAdd(intent, data.toString(), ConfigBuilderPlugin.class);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void uploadTempBasalStartPercent(Integer percent, double durationInMinutes) {
        try {
            SharedPreferences SP = PreferenceManager.getDefaultSharedPreferences(MainApp.instance().getApplicationContext());
            boolean useAbsolute = SP.getBoolean("ns_sync_use_absolute", false);
            if (useAbsolute) {
                double absolute = getBaseBasalRate() * percent / 100d;
                uploadTempBasalStartAbsolute(absolute, durationInMinutes);
            } else {
                Context context = MainApp.instance().getApplicationContext();
                JSONObject data = new JSONObject();
                data.put("eventType", "Temp Basal");
                data.put("duration", durationInMinutes);
                data.put("percent", percent - 100);
                data.put("created_at", DateUtil.toISOString(new Date()));
                data.put("enteredBy", MainApp.instance().getString(R.string.app_name));
                data.put("notes", MainApp.sResources.getString(R.string.androidaps_tempbasalstartnote) + " " + percent + "% " + durationInMinutes + " min"); // ECOR
                Bundle bundle = new Bundle();
                bundle.putString("action", "dbAdd");
                bundle.putString("collection", "treatments");
                bundle.putString("data", data.toString());
                Intent intent = new Intent(Intents.ACTION_DATABASE);
                intent.putExtras(bundle);
                intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
                context.sendBroadcast(intent);
                DbLogger.dbAdd(intent, data.toString(), ConfigBuilderPlugin.class);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void uploadTempBasalEnd() {
        try {
            Context context = MainApp.instance().getApplicationContext();
            JSONObject data = new JSONObject();
            data.put("eventType", "Temp Basal");
            data.put("created_at", DateUtil.toISOString(new Date()));
            data.put("enteredBy", MainApp.instance().getString(R.string.app_name));
            data.put("notes", MainApp.sResources.getString(R.string.androidaps_tempbasalendnote)); // ECOR
            Bundle bundle = new Bundle();
            bundle.putString("action", "dbAdd");
            bundle.putString("collection", "treatments");
            bundle.putString("data", data.toString());
            Intent intent = new Intent(Intents.ACTION_DATABASE);
            intent.putExtras(bundle);
            intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
            context.sendBroadcast(intent);
            DbLogger.dbAdd(intent, data.toString(), ConfigBuilderPlugin.class);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void uploadExtendedBolus(Double insulin, double durationInMinutes) {
        try {
            Context context = MainApp.instance().getApplicationContext();
            JSONObject data = new JSONObject();
            data.put("eventType", "Combo Bolus");
            data.put("duration", durationInMinutes);
            data.put("splitNow", 0);
            data.put("splitExt", 100);
            data.put("enteredinsulin", insulin);
            data.put("relative", insulin);
            data.put("created_at", DateUtil.toISOString(new Date()));
            data.put("enteredBy", MainApp.instance().getString(R.string.app_name));
            Bundle bundle = new Bundle();
            bundle.putString("action", "dbAdd");
            bundle.putString("collection", "treatments");
            bundle.putString("data", data.toString());
            Intent intent = new Intent(Intents.ACTION_DATABASE);
            intent.putExtras(bundle);
            intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
            context.sendBroadcast(intent);
            DbLogger.dbAdd(intent, data.toString(), ConfigBuilderPlugin.class);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void doUploadDeviceStatus() {
        DeviceStatus deviceStatus = new DeviceStatus();
        try {
            LoopPlugin.LastRun lastRun = LoopPlugin.lastRun;
            if (lastRun != null && lastRun.lastAPSRun.getTime() > new Date().getTime() - 60 * 1000L) {
                // do not send if result is older than 1 min
                APSResult apsResult = lastRun.request;
                apsResult.json().put("timestamp", DateUtil.toISOString(lastRun.lastAPSRun));
                deviceStatus.suggested = apsResult.json();

                if (lastRun.request instanceof DetermineBasalResultMA) {
                    DetermineBasalResultMA result = (DetermineBasalResultMA) lastRun.request;
                    deviceStatus.iob = result.iob.json();
                    deviceStatus.iob.put("time", DateUtil.toISOString(lastRun.lastAPSRun));
                }

                if (lastRun.request instanceof DetermineBasalResultAMA) {
                    DetermineBasalResultAMA result = (DetermineBasalResultAMA) lastRun.request;
                    deviceStatus.iob = result.iob.json();
                    deviceStatus.iob.put("time", DateUtil.toISOString(lastRun.lastAPSRun));
                }

                if (lastRun.setByPump != null && lastRun.setByPump.enacted) { // enacted
                    deviceStatus.enacted = lastRun.request.json();
                    deviceStatus.enacted.put("rate", lastRun.setByPump.json().get("rate"));
                    deviceStatus.enacted.put("duration", lastRun.setByPump.json().get("duration"));
                    deviceStatus.enacted.put("recieved", true);
                    JSONObject requested = new JSONObject();
                    requested.put("duration", lastRun.request.duration);
                    requested.put("rate", lastRun.request.rate);
                    requested.put("temp", "absolute");
                    deviceStatus.enacted.put("requested", requested);
                }
            }
            if (activePump != null) {
                deviceStatus.device = "openaps://" + deviceID();
                deviceStatus.pump = getJSONStatus();

                deviceStatus.created_at = DateUtil.toISOString(new Date());

                deviceStatus.sendToNSClient();
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    static public void uploadDeviceStatus(int sec) {
        class PostRunnable implements Runnable {
            public void run() {
                MainApp.getConfigBuilder().doUploadDeviceStatus();
                scheduledPost = null;
            }
        }
        // prepare task for execution
        // cancel waiting task to prevent sending multiple posts
        if (scheduledPost != null)
            scheduledPost.cancel(false);
        Runnable task = new PostRunnable();
        scheduledPost = worker.schedule(task, sec, TimeUnit.SECONDS);
        log.debug("Scheduling devicestatus upload in " + sec + " sec");
    }

    public void uploadBolusWizardRecord(Treatment t, double glucose, String glucoseType, int carbTime, JSONObject boluscalc) {
        JSONObject data = new JSONObject();
        try {
            data.put("eventType", "Bolus Wizard");
            if (t.insulin != 0d) data.put("insulin", t.insulin);
            if (t.carbs != 0d) data.put("carbs", t.carbs.intValue());
            data.put("created_at", DateUtil.toISOString(t.created_at));
            data.put("timeIndex", t.timeIndex);
            if (glucose != 0d) data.put("glucose", glucose);
            data.put("glucoseType", glucoseType);
            data.put("boluscalc", boluscalc);
            if (carbTime != 0) data.put("preBolus", carbTime);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        uploadCareportalEntryToNS(data);
    }

    public static void uploadCareportalEntryToNS(JSONObject data) {
        try {
            if (data.has("preBolus") && data.has("carbs")) {
                JSONObject prebolus = new JSONObject();
                prebolus.put("carbs", data.get("carbs"));
                data.remove("carbs");
                prebolus.put("eventType", data.get("eventType"));
                if (data.has("enteredBy")) prebolus.put("enteredBy", data.get("enteredBy"));
                if (data.has("notes")) prebolus.put("notes", data.get("notes"));
                long mills = DateUtil.fromISODateString(data.getString("created_at")).getTime();
                Date preBolusDate = new Date(mills + data.getInt("preBolus") * 60000L);
                prebolus.put("created_at", DateUtil.toISOString(preBolusDate));
                uploadCareportalEntryToNS(prebolus);
            }
            Context context = MainApp.instance().getApplicationContext();
            Bundle bundle = new Bundle();
            bundle.putString("action", "dbAdd");
            bundle.putString("collection", "treatments");
            bundle.putString("data", data.toString());
            Intent intent = new Intent(Intents.ACTION_DATABASE);
            intent.putExtras(bundle);
            intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
            context.sendBroadcast(intent);
            DbLogger.dbAdd(intent, data.toString(), ConfigBuilderPlugin.class);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public static void removeCareportalEntryFromNS(String _id) {
        try {
            Context context = MainApp.instance().getApplicationContext();
            Bundle bundle = new Bundle();
            bundle.putString("action", "dbRemove");
            bundle.putString("collection", "treatments");
            bundle.putString("_id", _id);
            Intent intent = new Intent(Intents.ACTION_DATABASE);
            intent.putExtras(bundle);
            intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
            context.sendBroadcast(intent);
            DbLogger.dbRemove(intent, _id, ConfigBuilderPlugin.class);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public void uploadError(String error) {
        Context context = MainApp.instance().getApplicationContext();
        Bundle bundle = new Bundle();
        bundle.putString("action", "dbAdd");
        bundle.putString("collection", "treatments");
        JSONObject data = new JSONObject();
        try {
            data.put("eventType", "Announcement");
            data.put("created_at", DateUtil.toISOString(new Date()));
            data.put("notes", error);
            data.put("isAnnouncement", true);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        bundle.putString("data", data.toString());
        Intent intent = new Intent(Intents.ACTION_DATABASE);
        intent.putExtras(bundle);
        intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        context.sendBroadcast(intent);
        DbLogger.dbAdd(intent, data.toString(), MsgError.class);
    }

    public void uploadAppStart() {
        Context context = MainApp.instance().getApplicationContext();
        Bundle bundle = new Bundle();
        bundle.putString("action", "dbAdd");
        bundle.putString("collection", "treatments");
        JSONObject data = new JSONObject();
        try {
            data.put("eventType", "Note");
            data.put("created_at", DateUtil.toISOString(new Date()));
            data.put("notes", MainApp.sResources.getString(R.string.androidaps_start));
        } catch (JSONException e) {
            e.printStackTrace();
        }
        bundle.putString("data", data.toString());
        Intent intent = new Intent(Intents.ACTION_DATABASE);
        intent.putExtras(bundle);
        intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        context.sendBroadcast(intent);
        DbLogger.dbAdd(intent, data.toString(), ConfigBuilderPlugin.class);
    }

}

package info.nightscout.androidaps.plugins.ConfigBuilder;

import android.content.Intent;
import android.support.annotation.Nullable;

import com.crashlytics.android.answers.CustomEvent;
import com.squareup.otto.Subscribe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;

import info.nightscout.androidaps.BuildConfig;
import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.DetailedBolusInfo;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.data.ProfileStore;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.db.CareportalEvent;
import info.nightscout.androidaps.db.ProfileSwitch;
import info.nightscout.androidaps.db.Source;
import info.nightscout.androidaps.db.TemporaryBasal;
import info.nightscout.androidaps.events.EventAppInitialized;
import info.nightscout.androidaps.events.EventNewBasalProfile;
import info.nightscout.androidaps.events.EventProfileSwitchChange;
import info.nightscout.androidaps.interfaces.APSInterface;
import info.nightscout.androidaps.interfaces.BgSourceInterface;
import info.nightscout.androidaps.interfaces.Constraint;
import info.nightscout.androidaps.interfaces.InsulinInterface;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.PluginDescription;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.interfaces.ProfileInterface;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.interfaces.SensitivityInterface;
import info.nightscout.androidaps.interfaces.TreatmentsInterface;
import info.nightscout.androidaps.plugins.Insulin.InsulinOrefRapidActingPlugin;
import info.nightscout.androidaps.plugins.Loop.APSResult;
import info.nightscout.androidaps.plugins.Loop.LoopPlugin;
import info.nightscout.androidaps.plugins.Overview.Dialogs.ErrorHelperActivity;
import info.nightscout.androidaps.plugins.PumpVirtual.VirtualPumpPlugin;
import info.nightscout.androidaps.plugins.SensitivityOref0.SensitivityOref0Plugin;
import info.nightscout.androidaps.queue.Callback;
import info.nightscout.androidaps.queue.CommandQueue;
import info.nightscout.utils.FabricPrivacy;
import info.nightscout.utils.NSUpload;
import info.nightscout.utils.SP;
import info.nightscout.utils.ToastUtils;

/**
 * Created by mike on 05.08.2016.
 */
public class ConfigBuilderPlugin extends PluginBase {
    private static Logger log = LoggerFactory.getLogger(ConfigBuilderPlugin.class);

    private static ConfigBuilderPlugin configBuilderPlugin;

    static public ConfigBuilderPlugin getPlugin() {
        if (configBuilderPlugin == null)
            configBuilderPlugin = new ConfigBuilderPlugin();
        return configBuilderPlugin;
    }

    private BgSourceInterface activeBgSource;
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

    private static CommandQueue commandQueue = new CommandQueue();

    public ConfigBuilderPlugin() {
        super(new PluginDescription()
                .mainType(PluginType.GENERAL)
                .fragmentClass(ConfigBuilderFragment.class.getName())
                .showInList(false)
                .alwaysEnabled(true)
                .alwayVisible(true)
                .pluginName(R.string.configbuilder)
                .shortName(R.string.configbuilder_shortname)
        );
    }

    @Override
    protected void onStart() {
        MainApp.bus().register(this);
        super.onStart();
    }

    @Override
    protected void onStop() {
        super.onStop();
        MainApp.bus().unregister(this);
    }


    public void initialize() {
        pluginList = MainApp.getPluginsList();
        upgradeSettings();
        loadSettings();
        MainApp.bus().post(new EventAppInitialized());
    }

    public void storeSettings(String from) {
        if (pluginList != null) {
            if (Config.logPrefsChange)
                log.debug("Storing settings from: " + from);

            for (PluginBase p : pluginList) {
                PluginType type = p.getType();
                if (p.pluginDescription.alwaysEnabled && p.pluginDescription.alwayVisible)
                    continue;
                if (p.pluginDescription.alwaysEnabled && p.pluginDescription.neverVisible)
                    continue;
                savePref(p, type, true);
                if (type == PluginType.PUMP) {
                    if (p instanceof ProfileInterface) { // Store state of optional Profile interface
                        savePref(p, PluginType.PROFILE, false);
                    }
                }
            }
            verifySelectionInCategories();
        }
    }

    private void savePref(PluginBase p, PluginType type, boolean storeVisible) {
        String settingEnabled = "ConfigBuilder_" + type.name() + "_" + p.getClass().getSimpleName() + "_Enabled";
        SP.putBoolean(settingEnabled, p.isEnabled(type));
        log.debug("Storing: " + settingEnabled + ":" + p.isEnabled(type));
        if (storeVisible) {
            String settingVisible = "ConfigBuilder_" + type.name() + "_" + p.getClass().getSimpleName() + "_Visible";
            SP.putBoolean(settingVisible, p.isFragmentVisible());
            log.debug("Storing: " + settingVisible + ":" + p.isFragmentVisible());
        }
    }

    private void loadSettings() {
        if (Config.logPrefsChange)
            log.debug("Loading stored settings");
        for (PluginBase p : pluginList) {
            PluginType type = p.getType();
            loadPref(p, type, true);
            if (p.getType() == PluginType.PUMP) {
                if (p instanceof ProfileInterface) {
                    loadPref(p, PluginType.PROFILE, false);
                }
            }
        }
        verifySelectionInCategories();
    }

    private void loadPref(PluginBase p, PluginType type, boolean loadVisible) {
        String settingEnabled = "ConfigBuilder_" + type.name() + "_" + p.getClass().getSimpleName() + "_Enabled";
        if (SP.contains(settingEnabled))
            p.setPluginEnabled(type, SP.getBoolean(settingEnabled, false));
        else if (p.getType() == type && (p.pluginDescription.enableByDefault || p.pluginDescription.alwaysEnabled)) {
            p.setPluginEnabled(type, true);
        }
        log.debug("Loaded: " + settingEnabled + ":" + p.isEnabled(type));
        if (loadVisible) {
            String settingVisible = "ConfigBuilder_" + type.name() + "_" + p.getClass().getSimpleName() + "_Visible";
            if (SP.contains(settingVisible))
                p.setFragmentVisible(type, SP.getBoolean(settingVisible, false) && SP.getBoolean(settingEnabled, false));
            else if (p.getType() == type && p.pluginDescription.visibleByDefault) {
                p.setFragmentVisible(type, true);
            }
            log.debug("Loaded: " + settingVisible + ":" + p.isFragmentVisible());
        }
    }

    // Detect settings prior 1.60
    private void upgradeSettings() {
        if (!SP.contains("ConfigBuilder_1_NSProfilePlugin_Enabled"))
            return;
        if (Config.logPrefsChange)
            log.debug("Upgrading stored settings");
        for (PluginBase p : pluginList) {
            log.debug("Processing " + p.getName());
            for (int type = 1; type < 11; type++) {
                PluginType newType;
                switch (type) {
                    case 1:
                        newType = PluginType.GENERAL;
                        break;
                    case 2:
                        newType = PluginType.TREATMENT;
                        break;
                    case 3:
                        newType = PluginType.SENSITIVITY;
                        break;
                    case 4:
                        newType = PluginType.PROFILE;
                        break;
                    case 5:
                        newType = PluginType.APS;
                        break;
                    case 6:
                        newType = PluginType.PUMP;
                        break;
                    case 7:
                        newType = PluginType.CONSTRAINTS;
                        break;
                    case 8:
                        newType = PluginType.LOOP;
                        break;
                    case 9:
                        newType = PluginType.BGSOURCE;
                        break;
                    case 10:
                        newType = PluginType.INSULIN;
                        break;
                    default:
                        newType = PluginType.GENERAL;
                        break;
                }
                String settingEnabled = "ConfigBuilder_" + type + "_" + p.getClass().getSimpleName() + "_Enabled";
                String settingVisible = "ConfigBuilder_" + type + "_" + p.getClass().getSimpleName() + "_Visible";
                if (SP.contains(settingEnabled))
                    p.setPluginEnabled(newType, SP.getBoolean(settingEnabled, false));
                if (SP.contains(settingVisible))
                    p.setFragmentVisible(newType, SP.getBoolean(settingVisible, false) && SP.getBoolean(settingEnabled, false));
                SP.remove(settingEnabled);
                SP.remove(settingVisible);
                if (newType == p.getType()) {
                    savePref(p, newType, true);
                } else if (p.getType() == PluginType.PUMP && p instanceof ProfileInterface) {
                    savePref(p, PluginType.PROFILE, false);
                }
            }
        }
    }

    public static CommandQueue getCommandQueue() {
        return commandQueue;
    }

    public BgSourceInterface getActiveBgSource() {
        return activeBgSource;
    }

    public ProfileInterface getActiveProfileInterface() {
        return activeProfile;
    }

    public static InsulinInterface getActiveInsulin() {
        return activeInsulin;
    }

    public static APSInterface getActiveAPS() {
        return activeAPS;
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
                    (p.isEnabled(PluginType.GENERAL) ? " GENERAL" : "") +
                    (p.isEnabled(PluginType.TREATMENT) ? " TREATMENT" : "") +
                    (p.isEnabled(PluginType.SENSITIVITY) ? " SENSITIVITY" : "") +
                    (p.isEnabled(PluginType.PROFILE) ? " PROFILE" : "") +
                    (p.isEnabled(PluginType.APS) ? " APS" : "") +
                    (p.isEnabled(PluginType.PUMP) ? " PUMP" : "") +
                    (p.isEnabled(PluginType.CONSTRAINTS) ? " CONSTRAINTS" : "") +
                    (p.isEnabled(PluginType.LOOP) ? " LOOP" : "") +
                    (p.isEnabled(PluginType.BGSOURCE) ? " BGSOURCE" : "") +
                    (p.isEnabled(PluginType.INSULIN) ? " INSULIN" : "")
            );
        }
    }

    private void verifySelectionInCategories() {
        ArrayList<PluginBase> pluginsInCategory;

        // PluginType.APS
        activeAPS = this.determineActivePlugin(APSInterface.class, PluginType.APS);

        // PluginType.INSULIN
        pluginsInCategory = MainApp.getSpecificPluginsList(PluginType.INSULIN);
        activeInsulin = (InsulinInterface) getTheOneEnabledInArray(pluginsInCategory, PluginType.INSULIN);
        if (activeInsulin == null) {
            activeInsulin = InsulinOrefRapidActingPlugin.getPlugin();
            InsulinOrefRapidActingPlugin.getPlugin().setPluginEnabled(PluginType.INSULIN, true);
        }
        this.setFragmentVisiblities(((PluginBase) activeInsulin).getName(), pluginsInCategory, PluginType.INSULIN);

        // PluginType.SENSITIVITY
        pluginsInCategory = MainApp.getSpecificPluginsList(PluginType.SENSITIVITY);
        activeSensitivity = (SensitivityInterface) getTheOneEnabledInArray(pluginsInCategory, PluginType.SENSITIVITY);
        if (activeSensitivity == null) {
            activeSensitivity = SensitivityOref0Plugin.getPlugin();
            SensitivityOref0Plugin.getPlugin().setPluginEnabled(PluginType.SENSITIVITY, true);
        }
        this.setFragmentVisiblities(((PluginBase) activeSensitivity).getName(), pluginsInCategory, PluginType.SENSITIVITY);

        // PluginType.PROFILE
        activeProfile = this.determineActivePlugin(ProfileInterface.class, PluginType.PROFILE);

        // PluginType.BGSOURCE
        activeBgSource = this.determineActivePlugin(BgSourceInterface.class, PluginType.BGSOURCE);

        // PluginType.PUMP
        pluginsInCategory = MainApp.getSpecificPluginsList(PluginType.PUMP);
        activePump = (PumpInterface) getTheOneEnabledInArray(pluginsInCategory, PluginType.PUMP);
        if (activePump == null) {
            activePump = VirtualPumpPlugin.getPlugin();
            VirtualPumpPlugin.getPlugin().setPluginEnabled(PluginType.PUMP, true);
        }
        this.setFragmentVisiblities(((PluginBase) activePump).getName(), pluginsInCategory, PluginType.PUMP);

        // PluginType.LOOP
        activeLoop = this.determineActivePlugin(PluginType.LOOP);

        // PluginType.TREATMENT
        activeTreatments = this.determineActivePlugin(PluginType.TREATMENT);
    }

    /**
     * disables the visibility for all fragments of Plugins with the given PluginType
     * which are not equally named to the Plugin implementing the given Plugin Interface.
     *
     * @param pluginInterface
     * @param pluginType
     * @param <T>
     * @return
     */
    private <T> T determineActivePlugin(Class<T> pluginInterface, PluginType pluginType) {
        ArrayList<PluginBase> pluginsInCategory;
        pluginsInCategory = MainApp.getSpecificPluginsListByInterface(pluginInterface);

        return this.determineActivePlugin(pluginsInCategory, pluginType);
    }

    private <T> T determineActivePlugin(PluginType pluginType) {
        ArrayList<PluginBase> pluginsInCategory;
        pluginsInCategory = MainApp.getSpecificPluginsList(pluginType);

        return this.determineActivePlugin(pluginsInCategory, pluginType);
    }

    /**
     * disables the visibility for all fragments of Plugins in the given pluginsInCategory
     * with the given PluginType which are not equally named to the Plugin implementing the
     * given Plugin Interface.
     * <p>
     * TODO we are casting an interface to PluginBase, which seems to be rather odd, since
     * TODO the interface is not implementing PluginBase (this is just avoiding errors through
     * TODO conventions.
     *
     * @param pluginsInCategory
     * @param pluginType
     * @param <T>
     * @return
     */
    private <T> T determineActivePlugin(ArrayList<PluginBase> pluginsInCategory,
                                        PluginType pluginType) {
        T activePlugin = (T) getTheOneEnabledInArray(pluginsInCategory, pluginType);

        if (activePlugin != null) {
            this.setFragmentVisiblities(((PluginBase) activePlugin).getName(),
                    pluginsInCategory, pluginType);
        }

        return activePlugin;
    }

    private void setFragmentVisiblities(String activePluginName, ArrayList<PluginBase> pluginsInCategory,
                                        PluginType pluginType) {
        if (Config.logConfigBuilder)
            log.debug("Selected interface: " + activePluginName);
        for (PluginBase p : pluginsInCategory) {
            if (!p.getName().equals(activePluginName)) {
                p.setFragmentVisible(pluginType, false);
            }
        }
    }

    @Nullable
    private PluginBase getTheOneEnabledInArray(ArrayList<PluginBase> pluginsInCategory, PluginType type) {
        PluginBase found = null;
        for (PluginBase p : pluginsInCategory) {
            if (p.isEnabled(type) && found == null) {
                found = p;
            } else if (p.isEnabled(type)) {
                // set others disabled
                p.setPluginEnabled(type, false);
            }
        }
        // If none enabled, enable first one
        //if (found == null && pluginsInCategory.size() > 0)
        //    found = pluginsInCategory.get(0);
        return found;
    }

    /**
     * expect absolute request and allow both absolute and percent response based on pump capabilities
     */
    public void applyTBRRequest(APSResult request, Profile profile, Callback callback) {
        if (!request.tempBasalRequested) {
            if (callback != null) {
                callback.result(new PumpEnactResult().enacted(false).success(true).comment(MainApp.gs(R.string.nochangerequested))).run();
            }
            return;
        }

        PumpInterface pump = getActivePump();

        request.rateConstraint = new Constraint<>(request.rate);
        request.rate = MainApp.getConstraintChecker().applyBasalConstraints(request.rateConstraint, profile).value();

        if (!pump.isInitialized()) {
            log.debug("applyAPSRequest: " + MainApp.gs(R.string.pumpNotInitialized));
            if (callback != null) {
                callback.result(new PumpEnactResult().comment(MainApp.gs(R.string.pumpNotInitialized)).enacted(false).success(false)).run();
            }
            return;
        }

        if (pump.isSuspended()) {
            log.debug("applyAPSRequest: " + MainApp.gs(R.string.pumpsuspended));
            if (callback != null) {
                callback.result(new PumpEnactResult().comment(MainApp.gs(R.string.pumpsuspended)).enacted(false).success(false)).run();
            }
            return;
        }

        if (Config.logCongigBuilderActions)
            log.debug("applyAPSRequest: " + request.toString());

        long now = System.currentTimeMillis();
        TemporaryBasal activeTemp = activeTreatments.getTempBasalFromHistory(now);
        if ((request.rate == 0 && request.duration == 0) || Math.abs(request.rate - pump.getBaseBasalRate()) < pump.getPumpDescription().basalStep) {
            if (activeTemp != null) {
                if (Config.logCongigBuilderActions)
                    log.debug("applyAPSRequest: cancelTempBasal()");
                getCommandQueue().cancelTempBasal(false, callback);
            } else {
                if (Config.logCongigBuilderActions)
                    log.debug("applyAPSRequest: Basal set correctly");
                if (callback != null) {
                    callback.result(new PumpEnactResult().absolute(request.rate).duration(0)
                            .enacted(false).success(true).comment(MainApp.gs(R.string.basal_set_correctly))).run();
                }
            }
        } else if (activeTemp != null
                && activeTemp.getPlannedRemainingMinutes() > 5
                && request.duration - activeTemp.getPlannedRemainingMinutes() < 30
                && Math.abs(request.rate - activeTemp.tempBasalConvertedToAbsolute(now, profile)) < pump.getPumpDescription().basalStep) {
            if (Config.logCongigBuilderActions)
                log.debug("applyAPSRequest: Temp basal set correctly");
            if (callback != null) {
                callback.result(new PumpEnactResult().absolute(activeTemp.tempBasalConvertedToAbsolute(now, profile))
                        .enacted(false).success(true).duration(activeTemp.getPlannedRemainingMinutes())
                        .comment(MainApp.gs(R.string.let_temp_basal_run))).run();
            }
        } else {
            if (Config.logCongigBuilderActions)
                log.debug("applyAPSRequest: setTempBasalAbsolute()");
            getCommandQueue().tempBasalAbsolute(request.rate, request.duration, false, profile, callback);
        }
    }

    public void applySMBRequest(APSResult request, Callback callback) {
        if (!request.bolusRequested) {
            return;
        }

        long lastBolusTime = activeTreatments.getLastBolusTime();
        if (lastBolusTime != 0 && lastBolusTime + 3 * 60 * 1000 > System.currentTimeMillis()) {
            log.debug("SMB requested but still in 3 min interval");
            if (callback != null) {
                callback.result(new PumpEnactResult()
                        .comment(MainApp.gs(R.string.smb_frequency_exceeded))
                        .enacted(false).success(false)).run();
            }
            return;
        }

        PumpInterface pump = getActivePump();

        if (!pump.isInitialized()) {
            log.debug("applySMBRequest: " + MainApp.gs(R.string.pumpNotInitialized));
            if (callback != null) {
                callback.result(new PumpEnactResult().comment(MainApp.gs(R.string.pumpNotInitialized)).enacted(false).success(false)).run();
            }
            return;
        }

        if (pump.isSuspended()) {
            log.debug("applySMBRequest: " + MainApp.gs(R.string.pumpsuspended));
            if (callback != null) {
                callback.result(new PumpEnactResult().comment(MainApp.gs(R.string.pumpsuspended)).enacted(false).success(false)).run();
            }
            return;
        }

        if (Config.logCongigBuilderActions)
            log.debug("applySMBRequest: " + request.toString());

        // deliver SMB
        DetailedBolusInfo detailedBolusInfo = new DetailedBolusInfo();
        detailedBolusInfo.eventType = CareportalEvent.CORRECTIONBOLUS;
        detailedBolusInfo.insulin = request.smb;
        detailedBolusInfo.isSMB = true;
        detailedBolusInfo.source = Source.USER;
        detailedBolusInfo.deliverAt = request.deliverAt;
        if (Config.logCongigBuilderActions)
            log.debug("applyAPSRequest: bolus()");
        getCommandQueue().bolus(detailedBolusInfo, callback);
    }

    @Subscribe
    public void onProfileSwitch(EventProfileSwitchChange ignored) {
        getCommandQueue().setProfile(getProfile(), new Callback() {
            @Override
            public void run() {
                if (!result.success) {
                    Intent i = new Intent(MainApp.instance(), ErrorHelperActivity.class);
                    i.putExtra("soundid", R.raw.boluserror);
                    i.putExtra("status", result.comment);
                    i.putExtra("title", MainApp.gs(R.string.failedupdatebasalprofile));
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    MainApp.instance().startActivity(i);
                }
                MainApp.bus().post(new EventNewBasalProfile());
            }
        });
    }

    public String getProfileName() {
        return getProfileName(System.currentTimeMillis());
    }

    public String getProfileName(boolean customized) {
        return getProfileName(System.currentTimeMillis(), customized);
    }

    public String getProfileName(long time) {
        return getProfileName(time, true);
    }

    public String getProfileName(long time, boolean customized) {
        ProfileSwitch profileSwitch = activeTreatments.getProfileSwitchFromHistory(time);
        if (profileSwitch != null) {
            if (profileSwitch.profileJson != null) {
                return customized ? profileSwitch.getCustomizedName() : profileSwitch.profileName;
            } else {
                ProfileStore profileStore = activeProfile.getProfile();
                if (profileStore != null) {
                    Profile profile = profileStore.getSpecificProfile(profileSwitch.profileName);
                    if (profile != null)
                        return profileSwitch.profileName;
                }
            }
        }
        return MainApp.gs(R.string.noprofileselected);
    }

    public boolean isProfileValid(String from) {
        return getProfile() != null && getProfile().isValid(from);
    }

    @Nullable
    public Profile getProfile() {
        return getProfile(System.currentTimeMillis());
    }

    public String getProfileUnits() {
        Profile profile = getProfile();
        return profile != null ? profile.getUnits() : Constants.MGDL;
    }

    @Nullable
    public Profile getProfile(long time) {
        if (activeTreatments == null) {
            log.debug("getProfile activeTreatments == null: returning null");
            return null; //app not initialized
        }
        //log.debug("Profile for: " + new Date(time).toLocaleString() + " : " + getProfileName(time));
        ProfileSwitch profileSwitch = activeTreatments.getProfileSwitchFromHistory(time);
        if (profileSwitch != null) {
            if (profileSwitch.profileJson != null) {
                return profileSwitch.getProfileObject();
            } else if (activeProfile.getProfile() != null) {
                Profile profile = activeProfile.getProfile().getSpecificProfile(profileSwitch.profileName);
                if (profile != null)
                    return profile;
            }
        }
        if (activeTreatments.getProfileSwitchesFromHistory().size() > 0) {
            FabricPrivacy.getInstance().logCustom(new CustomEvent("CatchedError")
                    .putCustomAttribute("buildversion", BuildConfig.BUILDVERSION)
                    .putCustomAttribute("version", BuildConfig.VERSION)
                    .putCustomAttribute("time", time)
                    .putCustomAttribute("getProfileSwitchesFromHistory", activeTreatments.getProfileSwitchesFromHistory().toString())
            );
        }
        log.debug("getProfile at the end: returning null");
        return null;
    }

    public void disconnectPump(int durationInMinutes, Profile profile) {
        LoopPlugin.getPlugin().disconnectTo(System.currentTimeMillis() + durationInMinutes * 60 * 1000L);
        getCommandQueue().tempBasalPercent(0, durationInMinutes, true, profile, new Callback() {
            @Override
            public void run() {
                if (!result.success) {
                    ToastUtils.showToastInUiThread(MainApp.instance().getApplicationContext(), MainApp.gs(R.string.tempbasaldeliveryerror));
                }
            }
        });
        if (getActivePump().getPumpDescription().isExtendedBolusCapable && activeTreatments.isInHistoryExtendedBoluslInProgress()) {
            getCommandQueue().cancelExtended(new Callback() {
                @Override
                public void run() {
                    if (!result.success) {
                        ToastUtils.showToastInUiThread(MainApp.instance().getApplicationContext(), MainApp.gs(R.string.extendedbolusdeliveryerror));
                    }
                }
            });
        }
        NSUpload.uploadOpenAPSOffline(durationInMinutes);
    }

    public void suspendLoop(int durationInMinutes) {
        LoopPlugin.getPlugin().suspendTo(System.currentTimeMillis() + durationInMinutes * 60 * 1000);
        getCommandQueue().cancelTempBasal(true, new Callback() {
            @Override
            public void run() {
                if (!result.success) {
                    ToastUtils.showToastInUiThread(MainApp.instance().getApplicationContext(), MainApp.gs(R.string.tempbasaldeliveryerror));
                }
            }
        });
        NSUpload.uploadOpenAPSOffline(durationInMinutes);
    }
}

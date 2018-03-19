package info.nightscout.androidaps.plugins.ConfigBuilder;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.DetailedBolusInfo;
import info.nightscout.androidaps.data.Intervals;
import info.nightscout.androidaps.data.IobTotal;
import info.nightscout.androidaps.data.MealData;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.data.ProfileIntervals;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.db.CareportalEvent;
import info.nightscout.androidaps.db.ExtendedBolus;
import info.nightscout.androidaps.db.ProfileSwitch;
import info.nightscout.androidaps.db.Source;
import info.nightscout.androidaps.db.TempTarget;
import info.nightscout.androidaps.db.TemporaryBasal;
import info.nightscout.androidaps.db.Treatment;
import info.nightscout.androidaps.events.EventAppInitialized;
import info.nightscout.androidaps.interfaces.APSInterface;
import info.nightscout.androidaps.interfaces.BgSourceInterface;
import info.nightscout.androidaps.interfaces.constrains.BooleanConstraint;
import info.nightscout.androidaps.interfaces.ConstraintsInterface;
import info.nightscout.androidaps.interfaces.InsulinInterface;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.ProfileInterface;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.interfaces.SensitivityInterface;
import info.nightscout.androidaps.interfaces.TreatmentsInterface;
import info.nightscout.androidaps.plugins.Loop.APSResult;
import info.nightscout.androidaps.plugins.Loop.LoopPlugin;
import info.nightscout.androidaps.plugins.Overview.events.EventDismissNotification;
import info.nightscout.androidaps.plugins.Overview.notifications.Notification;
import info.nightscout.androidaps.plugins.PumpVirtual.VirtualPumpPlugin;
import info.nightscout.androidaps.queue.Callback;
import info.nightscout.androidaps.queue.CommandQueue;
import info.nightscout.utils.NSUpload;
import info.nightscout.utils.SP;
import info.nightscout.utils.ToastUtils;

/**
 * Created by mike on 05.08.2016.
 */
public class ConfigBuilderPlugin implements PluginBase, ConstraintsInterface, TreatmentsInterface {
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

    private static CommandQueue commandQueue = new CommandQueue();

    public ConfigBuilderPlugin() {
        MainApp.bus().register(this);
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

    @Override
    public int getPreferencesId() {
        return -1;
    }

    public void initialize() {
        pluginList = MainApp.getPluginsList();
        loadSettings();
        MainApp.bus().post(new EventAppInitialized());
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

    public static CommandQueue getCommandQueue() {
        return commandQueue;
    }

    public static BgSourceInterface getActiveBgSource() {
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
        activeAPS = this.determineActivePlugin(APSInterface.class, PluginBase.APS);

        // PluginBase.INSULIN
        activeInsulin = this.determineActivePlugin(InsulinInterface.class, PluginBase.INSULIN);

        // PluginBase.SENSITIVITY
        activeSensitivity = this.determineActivePlugin(SensitivityInterface.class, PluginBase.SENSITIVITY);

        // PluginBase.PROFILE
        activeProfile = this.determineActivePlugin(ProfileInterface.class, PluginBase.PROFILE);

        // PluginBase.BGSOURCE
        activeBgSource = this.determineActivePlugin(BgSourceInterface.class, PluginBase.BGSOURCE);

        // PluginBase.PUMP
        pluginsInCategory = MainApp.getSpecificPluginsList(PluginBase.PUMP);
        activePump = (PumpInterface) getTheOneEnabledInArray(pluginsInCategory, PluginBase.PUMP);
        if (activePump == null)
            activePump = VirtualPumpPlugin.getPlugin(); // for NSClient build
        this.setFragmentVisiblities(((PluginBase) activePump).getName(), pluginsInCategory, PluginBase.PUMP);

        // PluginBase.LOOP
        activeLoop = this.determineActivePlugin(PluginBase.LOOP);

        // PluginBase.TREATMENT
        activeTreatments = this.determineActivePlugin(PluginBase.TREATMENT);
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
    private <T> T determineActivePlugin(Class<T> pluginInterface, int pluginType) {
        ArrayList<PluginBase> pluginsInCategory;
        pluginsInCategory = MainApp.getSpecificPluginsListByInterface(pluginInterface);

        return this.determineActivePlugin(pluginsInCategory, pluginType);
    }

    private <T> T determineActivePlugin(int pluginType) {
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
                                        int pluginType) {
        T activePlugin = (T) getTheOneEnabledInArray(pluginsInCategory, pluginType);

        if (activePlugin != null) {
            this.setFragmentVisiblities(((PluginBase) activePlugin).getName(),
                    pluginsInCategory, pluginType);
        }

        return activePlugin;
    }

    private void setFragmentVisiblities(String activePluginName, ArrayList<PluginBase> pluginsInCategory,
                                        int pluginType) {
        if (Config.logConfigBuilder)
            log.debug("Selected interface: " + activePluginName);
        for (PluginBase p : pluginsInCategory) {
            if (!p.getName().equals(activePluginName)) {
                p.setFragmentVisible(pluginType, false);
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

    /**
     * expect absolute request and allow both absolute and percent response based on pump capabilities
     */
    public void applyTBRRequest(APSResult request, Profile profile, Callback callback) {
        PumpInterface pump = getActivePump();
        request.rate = applyBasalConstraints(request.rate);

        long now = System.currentTimeMillis();

        if (!pump.isInitialized()) {
            log.debug("applyAPSRequest: " + MainApp.sResources.getString(R.string.pumpNotInitialized));
            if (callback != null) {
                callback.result(new PumpEnactResult().comment(MainApp.sResources.getString(R.string.pumpNotInitialized)).enacted(false).success(false)).run();
            }
            return;
        }

        if (pump.isSuspended()) {
            log.debug("applyAPSRequest: " + MainApp.sResources.getString(R.string.pumpsuspended));
            if (callback != null) {
                callback.result(new PumpEnactResult().comment(MainApp.sResources.getString(R.string.pumpsuspended)).enacted(false).success(false)).run();
            }
            return;
        }

        if (Config.logCongigBuilderActions)
            log.debug("applyAPSRequest: " + request.toString());

        if (request.tempBasalReqested) {
            TemporaryBasal activeTemp = getTempBasalFromHistory(now);
            if ((request.rate == 0 && request.duration == 0) || Math.abs(request.rate - pump.getBaseBasalRate()) < pump.getPumpDescription().basalStep) {
                if (activeTemp != null) {
                    if (Config.logCongigBuilderActions)
                        log.debug("applyAPSRequest: cancelTempBasal()");
                    getCommandQueue().cancelTempBasal(false, callback);
                } else {
                    if (Config.logCongigBuilderActions)
                        log.debug("applyAPSRequest: Basal set correctly");
                    if (callback != null) {
                        callback.result(new PumpEnactResult().absolute(request.rate).duration(0).enacted(false).success(true).comment("Basal set correctly")).run();
                    }
                }
            } else if (activeTemp != null
                    && activeTemp.getPlannedRemainingMinutes() > 5
                    && Math.abs(request.rate - activeTemp.tempBasalConvertedToAbsolute(now, profile)) < pump.getPumpDescription().basalStep) {
                if (Config.logCongigBuilderActions)
                    log.debug("applyAPSRequest: Temp basal set correctly");
                if (callback != null) {
                    callback.result(new PumpEnactResult().absolute(activeTemp.tempBasalConvertedToAbsolute(now, profile)).duration(activeTemp.getPlannedRemainingMinutes()).enacted(false).success(true).comment("Temp basal set correctly")).run();
                }
            } else {
                if (Config.logCongigBuilderActions)
                    log.debug("applyAPSRequest: setTempBasalAbsolute()");
                getCommandQueue().tempBasalAbsolute(request.rate, request.duration, false, callback);
            }
        }
    }

    public void applySMBRequest(APSResult request, Callback callback) {
        PumpInterface pump = getActivePump();

        if (!pump.isInitialized()) {
            log.debug("applySMBRequest: " + MainApp.sResources.getString(R.string.pumpNotInitialized));
            if (callback != null) {
                callback.result(new PumpEnactResult().comment(MainApp.sResources.getString(R.string.pumpNotInitialized)).enacted(false).success(false)).run();
            }
            return;
        }

        if (pump.isSuspended()) {
            log.debug("applySMBRequest: " + MainApp.sResources.getString(R.string.pumpsuspended));
            if (callback != null) {
                callback.result(new PumpEnactResult().comment(MainApp.sResources.getString(R.string.pumpsuspended)).enacted(false).success(false)).run();
            }
            return;
        }

        if (Config.logCongigBuilderActions)
            log.debug("applySMBRequest: " + request.toString());

        if (request.bolusRequested) {
            long lastBolusTime = getLastBolusTime();
            if (lastBolusTime != 0 && lastBolusTime + 3 * 60 * 1000 > System.currentTimeMillis()) {
                log.debug("SMB requsted but still in 3 min interval");
            } else {
                DetailedBolusInfo detailedBolusInfo = new DetailedBolusInfo();
                detailedBolusInfo.eventType = CareportalEvent.CORRECTIONBOLUS;
                detailedBolusInfo.insulin = request.smb;
                detailedBolusInfo.isSMB = true;
                detailedBolusInfo.source = Source.USER;
                detailedBolusInfo.deliverAt = request.deliverAt;
                getCommandQueue().bolus(detailedBolusInfo, callback);
            }
        }
    }

    /**
     * Constraints interface
     **/
    @Override
    public void limitRunningLoop(BooleanConstraint value) {

        ArrayList<PluginBase> constraintsPlugins = MainApp.getSpecificPluginsListByInterface(ConstraintsInterface.class);
        for (PluginBase p : constraintsPlugins) {
            ConstraintsInterface constraint = (ConstraintsInterface) p;
            if (!p.isEnabled(PluginBase.CONSTRAINTS)) continue;
            constraint.limitRunningLoop(value);
        }
    }

    @Override
    public void limitClosedLoop(BooleanConstraint value) {

        ArrayList<PluginBase> constraintsPlugins = MainApp.getSpecificPluginsListByInterface(ConstraintsInterface.class);
        for (PluginBase p : constraintsPlugins) {
            ConstraintsInterface constraint = (ConstraintsInterface) p;
            if (!p.isEnabled(PluginBase.CONSTRAINTS)) continue;
            constraint.limitClosedLoop(value);
        }
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
        boolean result = SP.getBoolean("openapsama_useautosens", false);

        ArrayList<PluginBase> constraintsPlugins = MainApp.getSpecificPluginsListByInterface(ConstraintsInterface.class);
        for (PluginBase p : constraintsPlugins) {
            ConstraintsInterface constrain = (ConstraintsInterface) p;
            if (!p.isEnabled(PluginBase.CONSTRAINTS)) continue;
            result = result && constrain.isAMAModeEnabled();
        }
        return result;
    }

    @Override
    public boolean isSMBModeEnabled() {
        boolean result = true; // TODO update for SMB // SP.getBoolean("openapsama_useautosens", false);

        ArrayList<PluginBase> constraintsPlugins = MainApp.getSpecificPluginsListByInterface(ConstraintsInterface.class);
        for (PluginBase p : constraintsPlugins) {
            ConstraintsInterface constrain = (ConstraintsInterface) p;
            if (!p.isEnabled(PluginBase.CONSTRAINTS)) continue;
            result = result && constrain.isSMBModeEnabled();
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
    public long getLastBolusTime() {
        return activeTreatments.getLastBolusTime();
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
        return activeTreatments != null && activeTreatments.isTempBasalInProgress();
    }

    @Override
    @Nullable
    public TemporaryBasal getTempBasalFromHistory(long time) {
        return activeTreatments != null ? activeTreatments.getTempBasalFromHistory(time) : null;
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
        MainApp.bus().post(new EventDismissNotification(Notification.PROFILE_SWITCH_MISSING));
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

    public String getProfileName(boolean customized) {
        return getProfileName(System.currentTimeMillis(), customized);
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
        log.debug("getProfile at the end: returning null");
        return null;
    }

    public void disconnectPump(int durationInMinutes) {
        getActiveLoop().disconnectTo(System.currentTimeMillis() + durationInMinutes * 60 * 1000L);
        getCommandQueue().tempBasalPercent(0, durationInMinutes, true, new Callback() {
            @Override
            public void run() {
                if (!result.success) {
                    ToastUtils.showToastInUiThread(MainApp.instance().getApplicationContext(), MainApp.gs(R.string.tempbasaldeliveryerror));
                }
            }
        });
        if (getActivePump().getPumpDescription().isExtendedBolusCapable && isInHistoryExtendedBoluslInProgress()) {
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
        getActiveLoop().suspendTo(System.currentTimeMillis() + durationInMinutes * 60 * 1000);
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

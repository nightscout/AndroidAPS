package info.nightscout.androidaps.plugins.configBuilder;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.Lazy;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.events.EventAppInitialized;
import info.nightscout.androidaps.events.EventConfigBuilderChange;
import info.nightscout.androidaps.events.EventRebuildTabs;
import info.nightscout.androidaps.interfaces.APSInterface;
import info.nightscout.androidaps.interfaces.ActivePluginProvider;
import info.nightscout.androidaps.interfaces.BgSourceInterface;
import info.nightscout.androidaps.interfaces.CommandQueueProvider;
import info.nightscout.androidaps.interfaces.InsulinInterface;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.PluginDescription;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.interfaces.ProfileInterface;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.interfaces.SensitivityInterface;
import info.nightscout.androidaps.interfaces.TreatmentsInterface;
import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.bus.RxBusWrapper;
import info.nightscout.androidaps.plugins.insulin.InsulinOrefRapidActingPlugin;
import info.nightscout.androidaps.plugins.profile.local.LocalProfilePlugin;
import info.nightscout.androidaps.plugins.profile.ns.NSProfilePlugin;
import info.nightscout.androidaps.plugins.pump.virtual.VirtualPumpPlugin;
import info.nightscout.androidaps.plugins.sensitivity.SensitivityOref0Plugin;
import info.nightscout.androidaps.plugins.sensitivity.SensitivityOref1Plugin;
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin;
import info.nightscout.androidaps.utils.OKDialog;
import info.nightscout.androidaps.utils.resources.ResourceHelper;
import info.nightscout.androidaps.utils.sharedPreferences.SP;

/**
 * Created by mike on 05.08.2016.
 */
@Singleton
public class ConfigBuilderPlugin extends PluginBase implements ActivePluginProvider {
    private static ConfigBuilderPlugin configBuilderPlugin;
    private final SP sp;
    private final AAPSLogger aapsLogger;
    private final RxBusWrapper rxBus;
    private final ResourceHelper resourceHelper;
    private final CommandQueueProvider commandQueue;
    private final NSProfilePlugin nsProfilePlugin;

    /**
     * @deprecated Use dagger to get an instance
     */

    @Deprecated
    public CommandQueueProvider getCommandQueue() {
        if (commandQueue == null)
            throw new IllegalStateException("Accessing commandQueue before first instantiation");
        return commandQueue;
    }

    @Deprecated
    static public ConfigBuilderPlugin getPlugin() {
        if (configBuilderPlugin == null)
            throw new IllegalStateException("Accessing ConfigBuilder before first instantiation");
        return configBuilderPlugin;
    }

    private BgSourceInterface activeBgSource;
    private PumpInterface activePump;
    private ProfileInterface activeProfile;
    private APSInterface activeAPS;
    private InsulinInterface activeInsulin;
    private SensitivityInterface activeSensitivity;
    private Lazy<TreatmentsPlugin> treatmentsPlugin;
    private Lazy<SensitivityOref0Plugin> sensitivityOref0Plugin;
    private Lazy<SensitivityOref1Plugin> sensitivityOref1Plugin;

    private ArrayList<PluginBase> pluginList;

    private final Lazy<InsulinOrefRapidActingPlugin> insulinOrefRapidActingPlugin;
    private final Lazy<LocalProfilePlugin> localProfilePlugin;
    private final Lazy<VirtualPumpPlugin> virtualPumpPlugin;

    /*
     * Written by Adrian:
     * The ConfigBuilderPlugin.getPlugin() method is used at 333 places throughout the app.
     * In order to make the transition to DI, while legacy code is still calling `getPlugin()`,
     * I'd instantiate this plugin very very early on (first injected dependency in MainApp) and use
     * Lazy dependencies in this constructor.
     * */
    @Inject
    public ConfigBuilderPlugin(
            Lazy<InsulinOrefRapidActingPlugin> insulinOrefRapidActingPlugin,
            Lazy<LocalProfilePlugin> localProfilePlugin,
            Lazy<VirtualPumpPlugin> virtualPumpPlugin,
            Lazy<TreatmentsPlugin> treatmentsPlugin,
            Lazy<SensitivityOref0Plugin> sensitivityOref0Plugin,
            Lazy<SensitivityOref1Plugin> sensitivityOref1Plugin,
            SP sp,
            RxBusWrapper rxBus,
            AAPSLogger aapsLogger,
            ResourceHelper resourceHelper,
            CommandQueueProvider commandQueue,
            NSProfilePlugin nsProfilePlugin
    ) {
        super(new PluginDescription()
                        .mainType(PluginType.GENERAL)
                        .fragmentClass(ConfigBuilderFragment.class.getName())
                        .showInList(true)
                        .alwaysEnabled(true)
                        .alwaysVisible(false)
                        .pluginName(R.string.configbuilder)
                        .shortName(R.string.configbuilder_shortname)
                        .description(R.string.description_config_builder),
                aapsLogger, resourceHelper
        );
        this.insulinOrefRapidActingPlugin = insulinOrefRapidActingPlugin;
        this.localProfilePlugin = localProfilePlugin;
        this.virtualPumpPlugin = virtualPumpPlugin;
        this.treatmentsPlugin = treatmentsPlugin;
        this.sensitivityOref0Plugin = sensitivityOref0Plugin;
        this.sensitivityOref1Plugin = sensitivityOref1Plugin;
        this.sp = sp;
        this.rxBus = rxBus;
        this.aapsLogger = aapsLogger;
        this.resourceHelper = resourceHelper;
        this.commandQueue = commandQueue;
        this.nsProfilePlugin = nsProfilePlugin;
        configBuilderPlugin = this;  // TODO: only while transitioning to Dagger
    }

    public void initialize() {
        pluginList = MainApp.getPluginsList();
        upgradeSettings();
        loadSettings();
        setAlwaysEnabledPluginsEnabled();
        rxBus.send(new EventAppInitialized());
    }

    private void setAlwaysEnabledPluginsEnabled() {
        for (PluginBase plugin : pluginList) {
            if (plugin.getPluginDescription().alwaysEnabled)
                plugin.setPluginEnabled(plugin.getType(), true);
        }
        storeSettings("setAlwaysEnabledPluginsEnabled");
    }

    public void storeSettings(String from) {
        if (pluginList != null) {
            getAapsLogger().debug(LTag.CONFIGBUILDER, "Storing settings from: " + from);

            verifySelectionInCategories();

            for (PluginBase p : pluginList) {
                PluginType type = p.getType();
                if (p.getPluginDescription().alwaysEnabled && p.getPluginDescription().alwaysVisible)
                    continue;
                if (p.getPluginDescription().alwaysEnabled && p.getPluginDescription().neverVisible)
                    continue;
                savePref(p, type, true);
                if (type == PluginType.PUMP) {
                    if (p instanceof ProfileInterface) { // Store state of optional Profile interface
                        savePref(p, PluginType.PROFILE, false);
                    }
                }
            }
        }
    }

    private void savePref(PluginBase p, PluginType type, boolean storeVisible) {
        String settingEnabled = "ConfigBuilder_" + type.name() + "_" + p.getClass().getSimpleName() + "_Enabled";
        sp.putBoolean(settingEnabled, p.isEnabled(type));
        getAapsLogger().debug(LTag.CONFIGBUILDER, "Storing: " + settingEnabled + ":" + p.isEnabled(type));
        if (storeVisible) {
            String settingVisible = "ConfigBuilder_" + type.name() + "_" + p.getClass().getSimpleName() + "_Visible";
            sp.putBoolean(settingVisible, p.isFragmentVisible());
            getAapsLogger().debug(LTag.CONFIGBUILDER, "Storing: " + settingVisible + ":" + p.isFragmentVisible());
        }
    }

    private void loadSettings() {
        getAapsLogger().debug(LTag.CONFIGBUILDER, "Loading stored settings");
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
        if (sp.contains(settingEnabled))
            p.setPluginEnabled(type, sp.getBoolean(settingEnabled, false));
        else if (p.getType() == type && (p.getPluginDescription().enableByDefault || p.getPluginDescription().alwaysEnabled)) {
            p.setPluginEnabled(type, true);
        }
        getAapsLogger().debug(LTag.CONFIGBUILDER, "Loaded: " + settingEnabled + ":" + p.isEnabled(type));
        if (loadVisible) {
            String settingVisible = "ConfigBuilder_" + type.name() + "_" + p.getClass().getSimpleName() + "_Visible";
            if (sp.contains(settingVisible))
                p.setFragmentVisible(type, sp.getBoolean(settingVisible, false) && sp.getBoolean(settingEnabled, false));
            else if (p.getType() == type && p.getPluginDescription().visibleByDefault) {
                p.setFragmentVisible(type, true);
            }
            getAapsLogger().debug(LTag.CONFIGBUILDER, "Loaded: " + settingVisible + ":" + p.isFragmentVisible());
        }
    }

    // Detect settings prior 1.60
    private void upgradeSettings() {
        if (!sp.contains("ConfigBuilder_1_NSProfilePlugin_Enabled"))
            return;
        getAapsLogger().debug(LTag.CONFIGBUILDER, "Upgrading stored settings");
        for (PluginBase p : pluginList) {
            getAapsLogger().debug(LTag.CONFIGBUILDER, "Processing " + p.getName());
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
                if (sp.contains(settingEnabled))
                    p.setPluginEnabled(newType, sp.getBoolean(settingEnabled, false));
                if (sp.contains(settingVisible))
                    p.setFragmentVisible(newType, sp.getBoolean(settingVisible, false) && sp.getBoolean(settingEnabled, false));
                sp.remove(settingEnabled);
                sp.remove(settingVisible);
                if (newType == p.getType()) {
                    savePref(p, newType, true);
                } else if (p.getType() == PluginType.PUMP && p instanceof ProfileInterface) {
                    savePref(p, PluginType.PROFILE, false);
                }
            }
        }
    }

    @Override
    @Nullable
    public BgSourceInterface getActiveBgSource() {
        return activeBgSource;
    }

    @Override
    @NotNull
    public ProfileInterface getActiveProfileInterface() {
        if (activeProfile != null) return activeProfile;
        else return localProfilePlugin.get();
    }

    @Override
    @NotNull
    public InsulinInterface getActiveInsulin() {
        if (activeInsulin == null)
            return insulinOrefRapidActingPlugin.get();
        return activeInsulin;
    }

    @Override
    @Nullable
    public APSInterface getActiveAPS() {
        return activeAPS;
    }

    @Override
    @NotNull
    public PumpInterface getActivePump() {
        if (activePump == null)
            throw new IllegalStateException("No pump selected");
        return activePump;
    }

    @Override
    @Nullable
    public PumpInterface getActivePumpPlugin() {
        return activePump;
    }

    @Override
    @NotNull
    public SensitivityInterface getActiveSensitivity() {
        if (activeSensitivity == null)
            return sensitivityOref1Plugin.get();
        else
            return activeSensitivity;
    }

    @NonNull @Override public TreatmentsInterface getActiveTreatments() {
        return treatmentsPlugin.get();
    }

    public void logPluginStatus() {
        for (PluginBase p : pluginList) {
            getAapsLogger().debug(LTag.CONFIGBUILDER, p.getName() + ":" +
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
            activeInsulin = insulinOrefRapidActingPlugin.get();
            insulinOrefRapidActingPlugin.get().setPluginEnabled(PluginType.INSULIN, true);
            getAapsLogger().debug(LTag.CONFIGBUILDER, "Defaulting InsulinOrefRapidActingPlugin");
        }
        this.setFragmentVisiblities(((PluginBase) activeInsulin).getName(), pluginsInCategory, PluginType.INSULIN);

        // PluginType.SENSITIVITY
        pluginsInCategory = MainApp.getSpecificPluginsList(PluginType.SENSITIVITY);
        activeSensitivity = (SensitivityInterface) getTheOneEnabledInArray(pluginsInCategory, PluginType.SENSITIVITY);
        if (activeSensitivity == null) {
            activeSensitivity = sensitivityOref0Plugin.get();
            sensitivityOref0Plugin.get().setPluginEnabled(PluginType.SENSITIVITY, true);
            getAapsLogger().debug(LTag.CONFIGBUILDER, "Defaulting SensitivityOref0Plugin");
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
            activePump = virtualPumpPlugin.get();
            virtualPumpPlugin.get().setPluginEnabled(PluginType.PUMP, true);
            getAapsLogger().debug(LTag.CONFIGBUILDER, "Defaulting VirtualPumpPlugin");
        }
        this.setFragmentVisiblities(((PluginBase) activePump).getName(), pluginsInCategory, PluginType.PUMP);

        // PluginType.TREATMENT
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
        pluginsInCategory = MainApp.instance().getSpecificPluginsListByInterface(pluginInterface);

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
        getAapsLogger().debug(LTag.CONFIGBUILDER, "Selected interface: " + activePluginName);
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

    // Ask when switching to physical pump plugin
    public void switchAllowed(@NonNull PluginBase changedPlugin, boolean newState, @Nullable FragmentActivity activity, @NonNull PluginType type) {
        if (changedPlugin.getType() == PluginType.PUMP && !changedPlugin.getName().equals(resourceHelper.gs(R.string.virtualpump)))
            confirmPumpPluginActivation(changedPlugin, newState, activity, type);
        else
            performPluginSwitch(changedPlugin, newState, type);
    }

    private void confirmPumpPluginActivation(@NonNull PluginBase changedPlugin, boolean newState, @Nullable FragmentActivity activity, @NonNull PluginType type) {
        if (type == PluginType.PUMP) {
            boolean allowHardwarePump = sp.getBoolean("allow_hardware_pump", false);
            if (allowHardwarePump || activity == null) {
                performPluginSwitch(changedPlugin, newState, type);
            } else {
                OKDialog.showConfirmation(activity, resourceHelper.gs(R.string.allow_hardware_pump_text), () -> {
                    performPluginSwitch(changedPlugin, newState, type);
                    sp.putBoolean("allow_hardware_pump", true);
                    aapsLogger.debug(LTag.PUMP, "First time HW pump allowed!");
                }, () -> {
                    rxBus.send(new EventConfigBuilderUpdateGui());
                    aapsLogger.debug(LTag.PUMP, "User does not allow switching to HW pump!");
                });
            }
        } else {
            performPluginSwitch(changedPlugin, newState, type);
        }
    }

    private void performPluginSwitch(PluginBase changedPlugin, boolean enabled, @NonNull PluginType type) {
        setPluginEnabled(type, enabled);
        setFragmentVisible(type, enabled);
        processOnEnabledCategoryChanged(changedPlugin, type);
        storeSettings("CheckedCheckboxEnabled");
        rxBus.send(new EventRebuildTabs());
        rxBus.send(new EventConfigBuilderChange());
        rxBus.send(new EventConfigBuilderUpdateGui());
        logPluginStatus();
    }

    public void processOnEnabledCategoryChanged(PluginBase changedPlugin, PluginType type) {
        ArrayList<PluginBase> pluginsInCategory = null;
        switch (type) {
            // Multiple selection allowed
            case GENERAL:
            case CONSTRAINTS:
            case LOOP:
                break;
            // Single selection allowed
            case INSULIN:
                pluginsInCategory = MainApp.instance().getSpecificPluginsListByInterface(InsulinInterface.class);
                break;
            case SENSITIVITY:
                pluginsInCategory = MainApp.instance().getSpecificPluginsListByInterface(SensitivityInterface.class);
                break;
            case APS:
                pluginsInCategory = MainApp.instance().getSpecificPluginsListByInterface(APSInterface.class);
                break;
            case PROFILE:
                pluginsInCategory = MainApp.instance().getSpecificPluginsListByInterface(ProfileInterface.class);
                break;
            case BGSOURCE:
                pluginsInCategory = MainApp.instance().getSpecificPluginsListByInterface(BgSourceInterface.class);
                break;
            case TREATMENT:
            case PUMP:
                pluginsInCategory = MainApp.instance().getSpecificPluginsListByInterface(PumpInterface.class);
                break;
        }
        if (pluginsInCategory != null) {
            boolean newSelection = changedPlugin.isEnabled(type);
            if (newSelection) { // new plugin selected -> disable others
                for (PluginBase p : pluginsInCategory) {
                    if (p.getName().equals(changedPlugin.getName())) {
                        // this is new selected
                    } else {
                        p.setPluginEnabled(type, false);
                        p.setFragmentVisible(type, false);
                    }
                }
            } else { // enable first plugin in list
                if (type == PluginType.PUMP)
                    virtualPumpPlugin.get().setPluginEnabled(type, true);
                else if (type == PluginType.INSULIN)
                    insulinOrefRapidActingPlugin.get().setPluginEnabled(type, true);
                else if (type == PluginType.SENSITIVITY)
                    sensitivityOref0Plugin.get().setPluginEnabled(type, true);
                else if (type == PluginType.PROFILE)
                    nsProfilePlugin.setPluginEnabled(type, true);
                else
                    pluginsInCategory.get(0).setPluginEnabled(type, true);
            }
        }
    }
}

package info.nightscout.androidaps.plugins.configBuilder;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;

import java.util.ArrayList;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.android.HasAndroidInjector;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.events.EventAppInitialized;
import info.nightscout.androidaps.events.EventConfigBuilderChange;
import info.nightscout.androidaps.events.EventRebuildTabs;
import info.nightscout.androidaps.interfaces.APSInterface;
import info.nightscout.androidaps.interfaces.ActivePluginProvider;
import info.nightscout.androidaps.interfaces.BgSourceInterface;
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
import info.nightscout.androidaps.plugins.configBuilder.events.EventConfigBuilderUpdateGui;
import info.nightscout.androidaps.utils.OKDialog;
import info.nightscout.androidaps.utils.resources.ResourceHelper;
import info.nightscout.androidaps.utils.sharedPreferences.SP;

/**
 * Created by mike on 05.08.2016.
 */
@Singleton
public class ConfigBuilderPlugin extends PluginBase {
    private static ConfigBuilderPlugin configBuilderPlugin;

    private final ActivePluginProvider activePlugin;
    private final SP sp;
    private final AAPSLogger aapsLogger;
    private final RxBusWrapper rxBus;
    private final ResourceHelper resourceHelper;
    private final ProfileFunction profileFunction;

    /**
     * @deprecated Use dagger to get an instance
     */

    @Deprecated
    public ProfileFunction getProfileFunction() {
        if (profileFunction == null)
            throw new IllegalStateException("Accessing profileFunction before first instantiation");
        return profileFunction;
    }

    @Deprecated
    static public ConfigBuilderPlugin getPlugin() {
        if (configBuilderPlugin == null)
            throw new IllegalStateException("Accessing ConfigBuilder before first instantiation");
        return configBuilderPlugin;
    }

    /*
     * Written by Adrian:
     * The ConfigBuilderPlugin.getPlugin() method is used at 333 places throughout the app.
     * In order to make the transition to DI, while legacy code is still calling `getPlugin()`,
     * I'd instantiate this plugin very very early on (first injected dependency in MainApp) and use
     * Lazy dependencies in this constructor.
     * */
    @Inject
    public ConfigBuilderPlugin(
            ActivePluginProvider activePlugin,
            HasAndroidInjector injector,
            SP sp,
            RxBusWrapper rxBus,
            AAPSLogger aapsLogger,
            ResourceHelper resourceHelper,
            ProfileFunction profileFunction
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
                aapsLogger, resourceHelper, injector
        );
        this.activePlugin = activePlugin;
        this.sp = sp;
        this.rxBus = rxBus;
        this.aapsLogger = aapsLogger;
        this.resourceHelper = resourceHelper;
        this.profileFunction = profileFunction;
        configBuilderPlugin = this;  // TODO: only while transitioning to Dagger
    }

    public void initialize() {
        upgradeSettings();
        ((PluginStore) activePlugin).loadDefaults();
        loadSettings();
        setAlwaysEnabledPluginsEnabled();
        rxBus.send(new EventAppInitialized());
    }

    private void setAlwaysEnabledPluginsEnabled() {
        for (PluginBase plugin : activePlugin.getPluginsList()) {
            if (plugin.getPluginDescription().alwaysEnabled)
                plugin.setPluginEnabled(plugin.getType(), true);
        }
        storeSettings("setAlwaysEnabledPluginsEnabled");
    }

    public void storeSettings(String from) {
        activePlugin.getPluginsList();
        getAapsLogger().debug(LTag.CONFIGBUILDER, "Storing settings from: " + from);

        activePlugin.verifySelectionInCategories();

        for (PluginBase p : activePlugin.getPluginsList()) {
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
        for (PluginBase p : activePlugin.getPluginsList()) {
            PluginType type = p.getType();
            loadPref(p, type, true);
            if (p.getType() == PluginType.PUMP) {
                if (p instanceof ProfileInterface) {
                    loadPref(p, PluginType.PROFILE, false);
                }
            }
        }
        activePlugin.verifySelectionInCategories();
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
        for (PluginBase p : activePlugin.getPluginsList()) {
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
                }
            }
        }
    }

    public void logPluginStatus() {
        for (PluginBase p : activePlugin.getPluginsList()) {
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

    // Ask when switching to physical pump plugin
    public void switchAllowed(@NonNull PluginBase changedPlugin, boolean newState, @Nullable FragmentActivity activity, @NonNull PluginType type) {
        if (changedPlugin.getType() == PluginType.PUMP && !changedPlugin.getName().equals(resourceHelper.gs(R.string.virtualpump)))
            confirmPumpPluginActivation(changedPlugin, newState, activity, type);
        else
            performPluginSwitch(changedPlugin, newState, type);
    }

    private void confirmPumpPluginActivation(@NonNull PluginBase changedPlugin, boolean newState, @Nullable FragmentActivity activity, @NonNull PluginType type) {
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
    }

    public void performPluginSwitch(PluginBase changedPlugin, boolean enabled, @NonNull PluginType type) {
        changedPlugin.setPluginEnabled(type, enabled);
        changedPlugin.setFragmentVisible(type, enabled);
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
                pluginsInCategory = activePlugin.getSpecificPluginsListByInterface(InsulinInterface.class);
                break;
            case SENSITIVITY:
                pluginsInCategory = activePlugin.getSpecificPluginsListByInterface(SensitivityInterface.class);
                break;
            case APS:
                pluginsInCategory = activePlugin.getSpecificPluginsListByInterface(APSInterface.class);
                break;
            case PROFILE:
                pluginsInCategory = activePlugin.getSpecificPluginsListByInterface(ProfileInterface.class);
                break;
            case BGSOURCE:
                pluginsInCategory = activePlugin.getSpecificPluginsListByInterface(BgSourceInterface.class);
                break;
            case TREATMENT:
                pluginsInCategory = activePlugin.getSpecificPluginsListByInterface(TreatmentsInterface.class);
                break;
            case PUMP:
                pluginsInCategory = activePlugin.getSpecificPluginsListByInterface(PumpInterface.class);
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
                pluginsInCategory.get(0).setPluginEnabled(type, true);
            }
        }
    }
}

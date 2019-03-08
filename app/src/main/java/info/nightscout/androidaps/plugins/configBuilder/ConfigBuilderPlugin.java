package info.nightscout.androidaps.plugins.configBuilder;

import android.support.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.events.EventAppInitialized;
import info.nightscout.androidaps.interfaces.APSInterface;
import info.nightscout.androidaps.interfaces.BgSourceInterface;
import info.nightscout.androidaps.interfaces.InsulinInterface;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.PluginDescription;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.interfaces.ProfileInterface;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.interfaces.SensitivityInterface;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.insulin.InsulinOrefRapidActingPlugin;
import info.nightscout.androidaps.plugins.pump.virtual.VirtualPumpPlugin;
import info.nightscout.androidaps.plugins.sensitivity.SensitivityOref0Plugin;
import info.nightscout.androidaps.queue.CommandQueue;
import info.nightscout.androidaps.utils.SP;

/**
 * Created by mike on 05.08.2016.
 */
public class ConfigBuilderPlugin extends PluginBase {
    private Logger log = LoggerFactory.getLogger(L.CONFIGBUILDER);

    private static ConfigBuilderPlugin configBuilderPlugin;

    static public ConfigBuilderPlugin getPlugin() {
        if (configBuilderPlugin == null)
            configBuilderPlugin = new ConfigBuilderPlugin();
        return configBuilderPlugin;
    }

    private BgSourceInterface activeBgSource;
    private PumpInterface activePump;
    private ProfileInterface activeProfile;
    private APSInterface activeAPS;
    private InsulinInterface activeInsulin;
    private SensitivityInterface activeSensitivity;

    private ArrayList<PluginBase> pluginList;

    private CommandQueue commandQueue = new CommandQueue();

    public ConfigBuilderPlugin() {
        super(new PluginDescription()
                .mainType(PluginType.GENERAL)
                .fragmentClass(ConfigBuilderFragment.class.getName())
                .showInList(true)
                .alwaysEnabled(true)
                .alwayVisible(false)
                .pluginName(R.string.configbuilder)
                .shortName(R.string.configbuilder_shortname)
                .description(R.string.description_config_builder)
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
        setAlwaysEnabledPluginsEnabled();
        MainApp.bus().post(new EventAppInitialized());
    }

    private void setAlwaysEnabledPluginsEnabled() {
        for (PluginBase plugin : pluginList) {
            if (plugin.pluginDescription.alwaysEnabled)
                plugin.setPluginEnabled(plugin.getType(), true);
        }
        storeSettings("setAlwaysEnabledPluginsEnabled");
    }

    public void storeSettings(String from) {
        if (pluginList != null) {
            if (L.isEnabled(L.CONFIGBUILDER))
                log.debug("Storing settings from: " + from);

            verifySelectionInCategories();

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
        }
    }

    private void savePref(PluginBase p, PluginType type, boolean storeVisible) {
        String settingEnabled = "ConfigBuilder_" + type.name() + "_" + p.getClass().getSimpleName() + "_Enabled";
        SP.putBoolean(settingEnabled, p.isEnabled(type));
        if (L.isEnabled(L.CONFIGBUILDER))
            log.debug("Storing: " + settingEnabled + ":" + p.isEnabled(type));
        if (storeVisible) {
            String settingVisible = "ConfigBuilder_" + type.name() + "_" + p.getClass().getSimpleName() + "_Visible";
            SP.putBoolean(settingVisible, p.isFragmentVisible());
            if (L.isEnabled(L.CONFIGBUILDER))
                log.debug("Storing: " + settingVisible + ":" + p.isFragmentVisible());
        }
    }

    private void loadSettings() {
        if (L.isEnabled(L.CONFIGBUILDER))
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
        if (L.isEnabled(L.CONFIGBUILDER))
            log.debug("Loaded: " + settingEnabled + ":" + p.isEnabled(type));
        if (loadVisible) {
            String settingVisible = "ConfigBuilder_" + type.name() + "_" + p.getClass().getSimpleName() + "_Visible";
            if (SP.contains(settingVisible))
                p.setFragmentVisible(type, SP.getBoolean(settingVisible, false) && SP.getBoolean(settingEnabled, false));
            else if (p.getType() == type && p.pluginDescription.visibleByDefault) {
                p.setFragmentVisible(type, true);
            }
            if (L.isEnabled(L.CONFIGBUILDER))
                log.debug("Loaded: " + settingVisible + ":" + p.isFragmentVisible());
        }
    }

    // Detect settings prior 1.60
    private void upgradeSettings() {
        if (!SP.contains("ConfigBuilder_1_NSProfilePlugin_Enabled"))
            return;
        if (L.isEnabled(L.CONFIGBUILDER))
            log.debug("Upgrading stored settings");
        for (PluginBase p : pluginList) {
            if (L.isEnabled(L.CONFIGBUILDER))
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

    public CommandQueue getCommandQueue() {
        return commandQueue;
    }

    public BgSourceInterface getActiveBgSource() {
        return activeBgSource;
    }

    public ProfileInterface getActiveProfileInterface() {
        return activeProfile;
    }

    public InsulinInterface getActiveInsulin() {
        return activeInsulin;
    }

    public APSInterface getActiveAPS() {
        return activeAPS;
    }

    public PumpInterface getActivePump() {
        return activePump;
    }

    public SensitivityInterface getActiveSensitivity() {
        return activeSensitivity;
    }

    void logPluginStatus() {
        if (L.isEnabled(L.CONFIGBUILDER))
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
            if (L.isEnabled(L.CONFIGBUILDER))
                log.debug("Defaulting InsulinOrefRapidActingPlugin");
        }
        this.setFragmentVisiblities(((PluginBase) activeInsulin).getName(), pluginsInCategory, PluginType.INSULIN);

        // PluginType.SENSITIVITY
        pluginsInCategory = MainApp.getSpecificPluginsList(PluginType.SENSITIVITY);
        activeSensitivity = (SensitivityInterface) getTheOneEnabledInArray(pluginsInCategory, PluginType.SENSITIVITY);
        if (activeSensitivity == null) {
            activeSensitivity = SensitivityOref0Plugin.getPlugin();
            SensitivityOref0Plugin.getPlugin().setPluginEnabled(PluginType.SENSITIVITY, true);
            if (L.isEnabled(L.CONFIGBUILDER))
                log.debug("Defaulting SensitivityOref0Plugin");
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
            if (L.isEnabled(L.CONFIGBUILDER))
                log.debug("Defaulting VirtualPumpPlugin");
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
        if (L.isEnabled(L.CONFIGBUILDER))
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

}

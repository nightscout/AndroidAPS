package info.nightscout.androidaps.plugins.configBuilder

import androidx.fragment.app.FragmentActivity
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.R
import info.nightscout.androidaps.events.EventAppInitialized
import info.nightscout.androidaps.events.EventConfigBuilderChange
import info.nightscout.androidaps.events.EventRebuildTabs
import info.nightscout.androidaps.interfaces.*
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.configBuilder.events.EventConfigBuilderUpdateGui
import info.nightscout.androidaps.utils.alertDialogs.OKDialog.showConfirmation
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConfigBuilderPlugin @Inject constructor(
    injector: HasAndroidInjector,
    aapsLogger: AAPSLogger,
    resourceHelper: ResourceHelper,
    private val sp: SP,
    private val rxBus: RxBusWrapper,
    private val activePlugin: ActivePluginProvider
) : PluginBase(PluginDescription()
    .mainType(PluginType.GENERAL)
    .fragmentClass(ConfigBuilderFragment::class.java.name)
    .showInList(true)
    .alwaysEnabled(true)
    .alwaysVisible(false)
    .pluginIcon(R.drawable.ic_cogs)
    .pluginName(R.string.configbuilder)
    .shortName(R.string.configbuilder_shortname)
    .description(R.string.description_config_builder),
    aapsLogger, resourceHelper, injector
), ConfigBuilderInterface {

    fun initialize() {
        (activePlugin as PluginStore).loadDefaults()
        loadSettings()
        setAlwaysEnabledPluginsEnabled()
        rxBus.send(EventAppInitialized())
    }

    private fun setAlwaysEnabledPluginsEnabled() {
        for (plugin in activePlugin.getPluginsList()) {
            if (plugin.pluginDescription.alwaysEnabled) plugin.setPluginEnabled(plugin.getType(), true)
        }
        storeSettings("setAlwaysEnabledPluginsEnabled")
    }

    override fun storeSettings(from: String) {
        activePlugin.getPluginsList()
        aapsLogger.debug(LTag.CONFIGBUILDER, "Storing settings from: $from")
        activePlugin.verifySelectionInCategories()
        for (p in activePlugin.getPluginsList()) {
            val type = p.getType()
            if (p.pluginDescription.alwaysEnabled && p.pluginDescription.alwaysVisible) continue
            if (p.pluginDescription.alwaysEnabled && p.pluginDescription.neverVisible) continue
            savePref(p, type, true)
            if (type == PluginType.PUMP) {
                if (p is ProfileInterface) { // Store state of optional Profile interface
                    savePref(p, PluginType.PROFILE, false)
                }
            }
        }
    }

    private fun savePref(p: PluginBase, type: PluginType, storeVisible: Boolean) {
        val settingEnabled = "ConfigBuilder_" + type.name + "_" + p.javaClass.simpleName + "_Enabled"
        sp.putBoolean(settingEnabled, p.isEnabled(type))
        aapsLogger.debug(LTag.CONFIGBUILDER, "Storing: " + settingEnabled + ":" + p.isEnabled(type))
        if (storeVisible) {
            val settingVisible = "ConfigBuilder_" + type.name + "_" + p.javaClass.simpleName + "_Visible"
            sp.putBoolean(settingVisible, p.isFragmentVisible())
            aapsLogger.debug(LTag.CONFIGBUILDER, "Storing: " + settingVisible + ":" + p.isFragmentVisible())
        }
    }

    private fun loadSettings() {
        aapsLogger.debug(LTag.CONFIGBUILDER, "Loading stored settings")
        for (p in activePlugin.getPluginsList()) {
            val type = p.getType()
            loadPref(p, type, true)
            if (p.getType() == PluginType.PUMP) {
                if (p is ProfileInterface) {
                    loadPref(p, PluginType.PROFILE, false)
                }
            }
        }
        activePlugin.verifySelectionInCategories()
    }

    private fun loadPref(p: PluginBase, type: PluginType, loadVisible: Boolean) {
        val settingEnabled = "ConfigBuilder_" + type.name + "_" + p.javaClass.simpleName + "_Enabled"
        if (sp.contains(settingEnabled)) p.setPluginEnabled(type, sp.getBoolean(settingEnabled, false)) else if (p.getType() == type && (p.pluginDescription.enableByDefault || p.pluginDescription.alwaysEnabled)) {
            p.setPluginEnabled(type, true)
        }
        aapsLogger.debug(LTag.CONFIGBUILDER, "Loaded: " + settingEnabled + ":" + p.isEnabled(type))
        if (loadVisible) {
            val settingVisible = "ConfigBuilder_" + type.name + "_" + p.javaClass.simpleName + "_Visible"
            if (sp.contains(settingVisible)) p.setFragmentVisible(type, sp.getBoolean(settingVisible, false) && sp.getBoolean(settingEnabled, false)) else if (p.getType() == type && p.pluginDescription.visibleByDefault) {
                p.setFragmentVisible(type, true)
            }
            aapsLogger.debug(LTag.CONFIGBUILDER, "Loaded: " + settingVisible + ":" + p.isFragmentVisible())
        }
    }

    fun logPluginStatus() {
        for (p in activePlugin.getPluginsList()) {
            aapsLogger.debug(LTag.CONFIGBUILDER, p.name + ":" +
                (if (p.isEnabled(PluginType.GENERAL)) " GENERAL" else "") +
                (if (p.isEnabled(PluginType.TREATMENT)) " TREATMENT" else "") +
                (if (p.isEnabled(PluginType.SENSITIVITY)) " SENSITIVITY" else "") +
                (if (p.isEnabled(PluginType.PROFILE)) " PROFILE" else "") +
                (if (p.isEnabled(PluginType.APS)) " APS" else "") +
                (if (p.isEnabled(PluginType.PUMP)) " PUMP" else "") +
                (if (p.isEnabled(PluginType.CONSTRAINTS)) " CONSTRAINTS" else "") +
                (if (p.isEnabled(PluginType.LOOP)) " LOOP" else "") +
                (if (p.isEnabled(PluginType.BGSOURCE)) " BGSOURCE" else "") +
                if (p.isEnabled(PluginType.INSULIN)) " INSULIN" else ""
            )
        }
    }

    // Ask when switching to physical pump plugin
    fun switchAllowed(changedPlugin: PluginBase, newState: Boolean, activity: FragmentActivity?, type: PluginType) {
        if (changedPlugin.getType() == PluginType.PUMP && changedPlugin.name != resourceHelper.gs(R.string.virtualpump)) confirmPumpPluginActivation(changedPlugin, newState, activity, type) else performPluginSwitch(changedPlugin, newState, type)
    }

    private fun confirmPumpPluginActivation(changedPlugin: PluginBase, newState: Boolean, activity: FragmentActivity?, type: PluginType) {
        val allowHardwarePump = sp.getBoolean("allow_hardware_pump", false)
        if (allowHardwarePump || activity == null) {
            performPluginSwitch(changedPlugin, newState, type)
        } else {
            showConfirmation(activity, resourceHelper.gs(R.string.allow_hardware_pump_text), Runnable {
                performPluginSwitch(changedPlugin, newState, type)
                sp.putBoolean("allow_hardware_pump", true)
                aapsLogger.debug(LTag.PUMP, "First time HW pump allowed!")
            }, Runnable {
                rxBus.send(EventConfigBuilderUpdateGui())
                aapsLogger.debug(LTag.PUMP, "User does not allow switching to HW pump!")
            })
        }
    }

    override fun performPluginSwitch(changedPlugin: PluginBase, enabled: Boolean, type: PluginType) {
        changedPlugin.setPluginEnabled(type, enabled)
        changedPlugin.setFragmentVisible(type, enabled)
        processOnEnabledCategoryChanged(changedPlugin, type)
        storeSettings("CheckedCheckboxEnabled")
        rxBus.send(EventRebuildTabs())
        rxBus.send(EventConfigBuilderChange())
        rxBus.send(EventConfigBuilderUpdateGui())
        logPluginStatus()
    }

    fun processOnEnabledCategoryChanged(changedPlugin: PluginBase, type: PluginType?) {
        var pluginsInCategory: ArrayList<PluginBase>? = null
        when (type) {
            PluginType.INSULIN     -> pluginsInCategory = activePlugin.getSpecificPluginsListByInterface(InsulinInterface::class.java)
            PluginType.SENSITIVITY -> pluginsInCategory = activePlugin.getSpecificPluginsListByInterface(SensitivityInterface::class.java)
            PluginType.APS         -> pluginsInCategory = activePlugin.getSpecificPluginsListByInterface(APSInterface::class.java)
            PluginType.PROFILE     -> pluginsInCategory = activePlugin.getSpecificPluginsListByInterface(ProfileInterface::class.java)
            PluginType.BGSOURCE    -> pluginsInCategory = activePlugin.getSpecificPluginsListByInterface(BgSourceInterface::class.java)
            PluginType.TREATMENT   -> pluginsInCategory = activePlugin.getSpecificPluginsListByInterface(TreatmentsInterface::class.java)
            PluginType.PUMP        -> pluginsInCategory = activePlugin.getSpecificPluginsListByInterface(PumpInterface::class.java)

            else                   -> {
            }
        }
        if (pluginsInCategory != null) {
            val newSelection = changedPlugin.isEnabled(type!!)
            if (newSelection) { // new plugin selected -> disable others
                for (p in pluginsInCategory) {
                    if (p.name == changedPlugin.name) {
                        // this is new selected
                    } else {
                        p.setPluginEnabled(type, false)
                        p.setFragmentVisible(type, false)
                    }
                }
            } else { // enable first plugin in list
                pluginsInCategory[0].setPluginEnabled(type, true)
            }
        }
    }
}
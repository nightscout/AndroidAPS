package info.nightscout.configuration.configBuilder

import androidx.fragment.app.FragmentActivity
import dagger.android.HasAndroidInjector
import info.nightscout.configuration.R
import info.nightscout.configuration.configBuilder.events.EventConfigBuilderUpdateGui
import info.nightscout.core.ui.dialogs.OKDialog
import info.nightscout.database.entities.UserEntry.Action
import info.nightscout.database.entities.UserEntry.Sources
import info.nightscout.database.entities.ValueWithUnit
import info.nightscout.interfaces.ConfigBuilder
import info.nightscout.interfaces.aps.APS
import info.nightscout.interfaces.aps.Sensitivity
import info.nightscout.interfaces.insulin.Insulin
import info.nightscout.interfaces.logging.UserEntryLogger
import info.nightscout.interfaces.plugin.ActivePlugin
import info.nightscout.interfaces.plugin.PluginBase
import info.nightscout.interfaces.plugin.PluginDescription
import info.nightscout.interfaces.plugin.PluginType
import info.nightscout.interfaces.profile.ProfileSource
import info.nightscout.interfaces.pump.Pump
import info.nightscout.interfaces.pump.PumpSync
import info.nightscout.interfaces.smoothing.Smoothing
import info.nightscout.interfaces.source.BgSource
import info.nightscout.interfaces.sync.NsClient
import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.events.EventAppInitialized
import info.nightscout.rx.events.EventConfigBuilderChange
import info.nightscout.rx.events.EventRebuildTabs
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConfigBuilderPlugin @Inject constructor(
    injector: HasAndroidInjector,
    aapsLogger: AAPSLogger,
    rh: ResourceHelper,
    private val sp: SP,
    private val rxBus: RxBus,
    private val activePlugin: ActivePlugin,
    private val uel: UserEntryLogger,
    private val pumpSync: PumpSync
) : PluginBase(
    PluginDescription()
        .mainType(PluginType.GENERAL)
        .fragmentClass(ConfigBuilderFragment::class.java.name)
        .showInList(true)
        .alwaysEnabled(true)
        .alwaysVisible(false)
        .pluginIcon(info.nightscout.core.ui.R.drawable.ic_cogs)
        .pluginName(R.string.config_builder)
        .shortName(R.string.config_builder_shortname)
        .description(R.string.description_config_builder),
    aapsLogger, rh, injector
), ConfigBuilder {

    override fun initialize() {
        loadSettings()
        setAlwaysEnabledPluginsEnabled()
        rxBus.send(EventAppInitialized())
    }

    private fun setAlwaysEnabledPluginsEnabled() {
        for (plugin in activePlugin.getPluginsList()) {
            if (plugin.pluginDescription.alwaysEnabled) plugin.setPluginEnabled(plugin.getType(), true)
        }
    }

    override fun storeSettings(from: String) {
        activePlugin.getPluginsList()
        aapsLogger.debug(LTag.CONFIGBUILDER, "Storing settings from: $from")
        activePlugin.verifySelectionInCategories()
        for (p in activePlugin.getPluginsList()) {
            val type = p.getType()
            if (p.pluginDescription.alwaysEnabled && p.pluginDescription.alwaysVisible) continue
            if (p.pluginDescription.alwaysEnabled && p.pluginDescription.neverVisible) continue
            savePref(p, type)
        }
    }

    private fun savePref(p: PluginBase, type: PluginType) {
        val settingEnabled = "ConfigBuilder_" + type.name + "_" + p.javaClass.simpleName + "_Enabled"
        sp.putBoolean(settingEnabled, p.isEnabled())
        aapsLogger.debug(LTag.CONFIGBUILDER, "Storing: " + settingEnabled + ":" + p.isEnabled())
        val settingVisible = "ConfigBuilder_" + type.name + "_" + p.javaClass.simpleName + "_Visible"
        sp.putBoolean(settingVisible, p.isFragmentVisible())
        aapsLogger.debug(LTag.CONFIGBUILDER, "Storing: " + settingVisible + ":" + p.isFragmentVisible())
    }

    private fun loadSettings() {
        aapsLogger.debug(LTag.CONFIGBUILDER, "Loading stored settings")
        for (p in activePlugin.getPluginsList()) {
            val type = p.getType()
            loadPref(p, type)
        }
        activePlugin.verifySelectionInCategories()
    }

    private fun loadPref(p: PluginBase, type: PluginType) {
        val settingEnabled = "ConfigBuilder_" + type.name + "_" + p.javaClass.simpleName + "_Enabled"
        if (sp.contains(settingEnabled)) p.setPluginEnabled(type, sp.getBoolean(settingEnabled, false))
        else if (p.getType() == type && (p.pluginDescription.enableByDefault || p.pluginDescription.alwaysEnabled)) {
            p.setPluginEnabled(type, true)
        }
        aapsLogger.debug(LTag.CONFIGBUILDER, "Loaded: " + settingEnabled + ":" + p.isEnabled(type))
        val settingVisible = "ConfigBuilder_" + type.name + "_" + p.javaClass.simpleName + "_Visible"
        if (sp.contains(settingVisible)) p.setFragmentVisible(type, sp.getBoolean(settingVisible, false) && sp.getBoolean(settingEnabled, false))
        else if (p.getType() == type && p.pluginDescription.visibleByDefault) {
            p.setFragmentVisible(type, true)
        }
        aapsLogger.debug(LTag.CONFIGBUILDER, "Loaded: " + settingVisible + ":" + p.isFragmentVisible())
    }

    fun logPluginStatus() {
        for (p in activePlugin.getPluginsList()) {
            aapsLogger.debug(
                LTag.CONFIGBUILDER, p.name + ":" +
                    (if (p.isEnabled(PluginType.GENERAL)) " GENERAL" else "") +
                    (if (p.isEnabled(PluginType.SENSITIVITY)) " SENSITIVITY" else "") +
                    (if (p.isEnabled(PluginType.PROFILE)) " PROFILE" else "") +
                    (if (p.isEnabled(PluginType.APS)) " APS" else "") +
                    (if (p.isEnabled(PluginType.PUMP)) " PUMP" else "") +
                    (if (p.isEnabled(PluginType.CONSTRAINTS)) " CONSTRAINTS" else "") +
                    (if (p.isEnabled(PluginType.LOOP)) " LOOP" else "") +
                    (if (p.isEnabled(PluginType.BGSOURCE)) " BGSOURCE" else "") +
                    (if (p.isEnabled(PluginType.INSULIN)) " INSULIN" else "") +
                    (if (p.isEnabled(PluginType.SYNC)) " SYNC" else "") +
                    if (p.isEnabled(PluginType.SMOOTHING)) " SMOOTHING" else ""
            )
        }
    }

    // Ask when switching to physical pump plugin
    fun switchAllowed(changedPlugin: PluginBase, newState: Boolean, activity: FragmentActivity?, type: PluginType) {
        if (changedPlugin.getType() == PluginType.PUMP && changedPlugin.name != rh.gs(info.nightscout.core.ui.R.string.virtual_pump))
            confirmPumpPluginActivation(changedPlugin, newState, activity, type)
        else if (changedPlugin.getType() == PluginType.PUMP) {
            performPluginSwitch(changedPlugin, newState, type)
            pumpSync.connectNewPump()
        } else performPluginSwitch(changedPlugin, newState, type)
    }

    private fun confirmPumpPluginActivation(changedPlugin: PluginBase, newState: Boolean, activity: FragmentActivity?, type: PluginType) {
        val allowHardwarePump = sp.getBoolean("allow_hardware_pump", false)
        if (allowHardwarePump || activity == null) {
            performPluginSwitch(changedPlugin, newState, type)
            pumpSync.connectNewPump()
        } else {
            OKDialog.showConfirmation(activity, rh.gs(R.string.allow_hardware_pump_text), {
                performPluginSwitch(changedPlugin, newState, type)
                pumpSync.connectNewPump()
                sp.putBoolean("allow_hardware_pump", true)
                uel.log(
                    Action.HW_PUMP_ALLOWED, Sources.ConfigBuilder, rh.gs(changedPlugin.pluginDescription.pluginName),
                    ValueWithUnit.SimpleString(rh.gsNotLocalised(changedPlugin.pluginDescription.pluginName))
                )
                aapsLogger.debug(LTag.PUMP, "First time HW pump allowed!")
            }, {
                                          rxBus.send(EventConfigBuilderUpdateGui())
                                          aapsLogger.debug(LTag.PUMP, "User does not allow switching to HW pump!")
                                      })
        }
    }

    override fun performPluginSwitch(changedPlugin: PluginBase, enabled: Boolean, type: PluginType) {
        if (enabled && !changedPlugin.isEnabled()) {
            uel.log(
                Action.PLUGIN_ENABLED, Sources.ConfigBuilder, rh.gs(changedPlugin.pluginDescription.pluginName),
                ValueWithUnit.SimpleString(rh.gsNotLocalised(changedPlugin.pluginDescription.pluginName))
            )
        } else if (!enabled) {
            uel.log(
                Action.PLUGIN_DISABLED, Sources.ConfigBuilder, rh.gs(changedPlugin.pluginDescription.pluginName),
                ValueWithUnit.SimpleString(rh.gsNotLocalised(changedPlugin.pluginDescription.pluginName))
            )
        }
        changedPlugin.setPluginEnabled(type, enabled)
        changedPlugin.setFragmentVisible(type, enabled)
        processOnEnabledCategoryChanged(changedPlugin, type)
        storeSettings("RemoteConfiguration")
        rxBus.send(EventRebuildTabs())
        rxBus.send(EventConfigBuilderChange())
        rxBus.send(EventConfigBuilderUpdateGui())
        logPluginStatus()
    }

    override fun processOnEnabledCategoryChanged(changedPlugin: PluginBase, type: PluginType) {
        var pluginsInCategory: ArrayList<PluginBase>? = null
        when {
            type == PluginType.INSULIN     -> pluginsInCategory = activePlugin.getSpecificPluginsListByInterface(Insulin::class.java)
            type == PluginType.SENSITIVITY -> pluginsInCategory = activePlugin.getSpecificPluginsListByInterface(Sensitivity::class.java)
            type == PluginType.SMOOTHING   -> pluginsInCategory = activePlugin.getSpecificPluginsListByInterface(Smoothing::class.java)
            type == PluginType.APS         -> pluginsInCategory = activePlugin.getSpecificPluginsListByInterface(APS::class.java)
            type == PluginType.PROFILE     -> pluginsInCategory = activePlugin.getSpecificPluginsListByInterface(ProfileSource::class.java)
            type == PluginType.BGSOURCE    -> pluginsInCategory = activePlugin.getSpecificPluginsListByInterface(BgSource::class.java)
            type == PluginType.PUMP        -> pluginsInCategory = activePlugin.getSpecificPluginsListByInterface(Pump::class.java)
            // Process only NSClients
            changedPlugin is NsClient      -> pluginsInCategory = activePlugin.getSpecificPluginsListByInterface(NsClient::class.java)

            else                           -> {
            }
        }
        if (pluginsInCategory != null) {
            val newSelection = changedPlugin.isEnabled(type)
            if (newSelection) { // new plugin selected -> disable others
                for (p in pluginsInCategory) {
                    if (p.name == changedPlugin.name) {
                        // this is new selected
                    } else {
                        p.setPluginEnabled(type, false)
                        p.setFragmentVisible(type, false)
                    }
                }
            } else if (type != PluginType.SYNC) {
                // enable first plugin in list
                // NSC must not be selected
                pluginsInCategory[0].setPluginEnabled(type, true)
            }
        }
    }
}
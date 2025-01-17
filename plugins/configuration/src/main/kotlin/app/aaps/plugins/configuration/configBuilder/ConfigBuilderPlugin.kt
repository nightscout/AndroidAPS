package app.aaps.plugins.configuration.configBuilder

import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.aps.APS
import app.aaps.core.interfaces.aps.Sensitivity
import app.aaps.core.interfaces.configuration.ConfigBuilder
import app.aaps.core.interfaces.insulin.Insulin
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.profile.ProfileSource
import app.aaps.core.interfaces.protection.ProtectionCheck
import app.aaps.core.interfaces.pump.Pump
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventAppInitialized
import app.aaps.core.interfaces.rx.events.EventConfigBuilderChange
import app.aaps.core.interfaces.rx.events.EventRebuildTabs
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.interfaces.smoothing.Smoothing
import app.aaps.core.interfaces.source.BgSource
import app.aaps.core.interfaces.sync.NsClient
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.keys.Preferences
import app.aaps.core.ui.dialogs.OKDialog
import app.aaps.core.ui.extensions.toVisibility
import app.aaps.plugins.configuration.R
import app.aaps.plugins.configuration.configBuilder.events.EventConfigBuilderUpdateGui
import app.aaps.plugins.configuration.databinding.ConfigbuilderSingleCategoryBinding
import app.aaps.plugins.configuration.databinding.ConfigbuilderSinglePluginBinding
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConfigBuilderPlugin @Inject constructor(
    aapsLogger: AAPSLogger,
    rh: ResourceHelper,
    private val sp: SP,
    private val preferences: Preferences,
    private val rxBus: RxBus,
    private val activePlugin: ActivePlugin,
    private val uel: UserEntryLogger,
    private val pumpSync: PumpSync,
    private val protectionCheck: ProtectionCheck,
    private val uiInteraction: UiInteraction
) : PluginBase(
    PluginDescription()
        .mainType(PluginType.GENERAL)
        .fragmentClass(ConfigBuilderFragment::class.java.name)
        .alwaysEnabled(true)
        .pluginIcon(app.aaps.core.ui.R.drawable.ic_cogs)
        .pluginName(R.string.config_builder)
        .shortName(R.string.config_builder_shortname)
        .description(R.string.description_config_builder),
    aapsLogger, rh
), ConfigBuilder {

    override fun initialize() {
        loadSettings()
        setAlwaysEnabledPluginsEnabled()
        // Wait for MainActivity start
        Handler(Looper.getMainLooper()).postDelayed({ rxBus.send(EventAppInitialized()) }, 5000)
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
    fun switchAllowed(changedPlugin: PluginBase, newState: Boolean, activity: FragmentActivity, type: PluginType) {
        if (changedPlugin.getType() == PluginType.PUMP && changedPlugin.name != rh.gs(app.aaps.core.ui.R.string.virtual_pump))
            confirmPumpPluginActivation(changedPlugin, newState, activity, type)
        else if (changedPlugin.getType() == PluginType.PUMP) {
            performPluginSwitch(changedPlugin, newState, type)
            pumpSync.connectNewPump()
        } else performPluginSwitch(changedPlugin, newState, type)
    }

    private fun confirmPumpPluginActivation(changedPlugin: PluginBase, newState: Boolean, activity: FragmentActivity, type: PluginType) {
        val allowHardwarePump = sp.getBoolean("allow_hardware_pump", false)
        if (allowHardwarePump) {
            performPluginSwitch(changedPlugin, newState, type)
            pumpSync.connectNewPump()
        } else {
            OKDialog.showConfirmation(activity, rh.gs(R.string.allow_hardware_pump_text), {
                performPluginSwitch(changedPlugin, newState, type)
                pumpSync.connectNewPump()
                sp.putBoolean("allow_hardware_pump", true)
                uel.log(
                    action = Action.HW_PUMP_ALLOWED,
                    source = Sources.ConfigBuilder,
                    note = rh.gs(changedPlugin.pluginDescription.pluginName),
                    value = ValueWithUnit.SimpleString(rh.gsNotLocalised(changedPlugin.pluginDescription.pluginName))
                )
                aapsLogger.debug(LTag.PUMP, "First time HW pump allowed!")
            }, {
                                          rxBus.send(EventConfigBuilderUpdateGui())
                                          aapsLogger.debug(LTag.PUMP, "User does not allow switching to HW pump!")
                                      })
        }
    }

    override fun performPluginSwitch(changedPlugin: PluginBase, enabled: Boolean, type: PluginType) {
        Thread {
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
        }.start()
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

            else                           -> { // do nothing
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

    override fun createViewsForPlugins(
        @StringRes title: Int?,
        @StringRes description: Int,
        pluginType: PluginType,
        plugins: List<PluginBase>,
        pluginViewHolders: ArrayList<ConfigBuilder.PluginViewHolderInterface>,
        activity: FragmentActivity,
        parent: LinearLayout,
        showExpanded: Boolean
    ) {
        if (plugins.isEmpty()) return
        val layoutInflater = activity.layoutInflater

        val layout = ConfigbuilderSingleCategoryBinding.inflate(layoutInflater, parent, true)
        val pluginsAdded = ArrayList<PluginViewHolder>()

        if (title != null) layout.categoryTitle.text = rh.gs(title)
        else layout.categoryTitle.visibility = View.GONE
        layout.categoryVisibility.visibility = preferences.simpleMode.not().toVisibility()
        layout.categoryDescription.text = rh.gs(description)
        layout.categoryExpandLess.setOnClickListener {
            layout.categoryExpandLess.visibility = false.toVisibility()
            layout.categoryExpandMore.visibility = true.toVisibility()
            pluginsAdded.forEach { pluginViewHolder ->
                pluginViewHolder.layout.root.visibility = pluginViewHolder.plugin.isEnabled().toVisibility()
            }
        }
        layout.categoryExpandMore.setOnClickListener {
            layout.categoryExpandLess.visibility = true.toVisibility()
            layout.categoryExpandMore.visibility = false.toVisibility()
            pluginsAdded.forEach { pluginViewHolder ->
                pluginViewHolder.layout.root.visibility = true.toVisibility()
            }
        }
        for (plugin in plugins) {
            val pluginViewHolder = PluginViewHolder(layoutInflater, activity, pluginType, plugin)
            layout.categoryPlugins.addView(pluginViewHolder.layout.root)
            pluginViewHolders.add(pluginViewHolder)
            pluginsAdded.add(pluginViewHolder)
        }
        if (showExpanded) layout.categoryExpandMore.callOnClick()
        else layout.categoryExpandLess.callOnClick()
    }

    inner class PluginViewHolder internal constructor(
        layoutInflater: LayoutInflater,
        private val activity: FragmentActivity,
        private val pluginType: PluginType,
        val plugin: PluginBase
    ) : ConfigBuilder.PluginViewHolderInterface {

        val layout = ConfigbuilderSinglePluginBinding.inflate(layoutInflater, null, false).also { layout ->
            layout.pluginVisibility.setOnClickListener {
                plugin.setFragmentVisible(pluginType, layout.pluginVisibility.isChecked)
                storeSettings("CheckedCheckboxVisible")
                rxBus.send(EventRebuildTabs())
                logPluginStatus()
            }

            layout.pluginEnabledExclusive.setOnClickListener {
                switchAllowed(plugin, if (layout.pluginEnabledExclusive.visibility == View.VISIBLE) layout.pluginEnabledExclusive.isChecked else layout.pluginEnabledInclusive.isChecked, activity, pluginType)
            }
            layout.pluginEnabledInclusive.setOnClickListener {
                switchAllowed(plugin, if (layout.pluginEnabledExclusive.visibility == View.VISIBLE) layout.pluginEnabledExclusive.isChecked else layout.pluginEnabledInclusive.isChecked, activity, pluginType)
            }

            layout.pluginPreferences.setOnClickListener {
                protectionCheck.queryProtection(activity, ProtectionCheck.Protection.PREFERENCES, {
                    val i = Intent(activity, uiInteraction.preferencesActivity)
                    i.putExtra(UiInteraction.PLUGIN_NAME, plugin.javaClass.simpleName)
                    activity.startActivity(i)
                }, null)
            }
        }

        init {
            update()
        }

        override fun update() {
            layout.pluginEnabledExclusive.visibility = areMultipleSelectionsAllowed(pluginType).not().toVisibility()
            layout.pluginEnabledInclusive.visibility = areMultipleSelectionsAllowed(pluginType).toVisibility()
            layout.pluginEnabledExclusive.isChecked = plugin.isEnabled(pluginType)
            layout.pluginEnabledInclusive.isChecked = plugin.isEnabled(pluginType)
            layout.pluginEnabledInclusive.isEnabled = !plugin.pluginDescription.alwaysEnabled
            layout.pluginEnabledExclusive.isEnabled = !plugin.pluginDescription.alwaysEnabled
            if (plugin.menuIcon != -1) {
                layout.pluginIcon.visibility = View.VISIBLE
                layout.pluginIcon.setImageDrawable(ContextCompat.getDrawable(activity, plugin.menuIcon))
                if (plugin.menuIcon2 != -1) {
                    layout.pluginIcon2.visibility = View.VISIBLE
                    layout.pluginIcon2.setImageDrawable(ContextCompat.getDrawable(activity, plugin.menuIcon2))
                } else {
                    layout.pluginIcon2.visibility = View.GONE
                }
            } else {
                layout.pluginIcon.visibility = View.GONE
            }
            layout.pluginName.text = plugin.name
            if (plugin.description == null)
                layout.pluginDescription.visibility = View.GONE
            else {
                layout.pluginDescription.visibility = View.VISIBLE
                layout.pluginDescription.text = plugin.description
            }
            if (preferences.simpleMode) {
                layout.pluginPreferences.visibility =
                    if (plugin.preferencesId == PluginDescription.PREFERENCE_NONE || !plugin.isEnabled(pluginType) || !plugin.pluginDescription.preferencesVisibleInSimpleMode) View.INVISIBLE else View.VISIBLE
                layout.pluginVisibility.visibility = false.toVisibility()
            } else {
                layout.pluginPreferences.visibility = if (plugin.preferencesId == PluginDescription.PREFERENCE_NONE || !plugin.isEnabled(pluginType)) View.INVISIBLE else View.VISIBLE
                layout.pluginVisibility.visibility = plugin.hasFragment().toVisibility()
                layout.pluginVisibility.isEnabled = !(plugin.pluginDescription.neverVisible || plugin.pluginDescription.alwaysVisible) && plugin.isEnabled(pluginType)
                layout.pluginVisibility.isChecked = plugin.isFragmentVisible()
            }
        }

        private fun areMultipleSelectionsAllowed(type: PluginType): Boolean {
            return type == PluginType.GENERAL || type == PluginType.CONSTRAINTS || type == PluginType.LOOP || type == PluginType.SYNC
        }
    }
}
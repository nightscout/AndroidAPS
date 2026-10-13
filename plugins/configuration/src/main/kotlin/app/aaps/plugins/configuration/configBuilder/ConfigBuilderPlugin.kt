package app.aaps.plugins.configuration.configBuilder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.AnimationDrawable
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
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
import app.aaps.core.interfaces.plugin.PluginBaseWithPreferences
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.profile.ProfileSource
import app.aaps.core.interfaces.protection.ProtectionCheck
import app.aaps.core.interfaces.pump.Pump
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventAppExit
import app.aaps.core.interfaces.rx.events.EventAppInitialized
import app.aaps.core.interfaces.rx.events.EventConfigBuilderChange
import app.aaps.core.interfaces.rx.events.EventRebuildTabs
import app.aaps.core.interfaces.smoothing.Smoothing
import app.aaps.core.interfaces.source.BgSource
import app.aaps.core.interfaces.sync.NsClient
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.dialogs.OKDialog
import app.aaps.core.ui.extensions.runOnUiThreadDelayed
import app.aaps.core.ui.extensions.scanForActivity
import app.aaps.core.ui.extensions.toVisibility
import app.aaps.plugins.configuration.R
import app.aaps.plugins.configuration.configBuilder.events.EventConfigBuilderUpdateGui
import app.aaps.plugins.configuration.databinding.ConfigbuilderSingleCategoryBinding
import app.aaps.plugins.configuration.databinding.ConfigbuilderSinglePluginBinding
import app.aaps.plugins.configuration.keys.ConfigurationBooleanComposedKey
import app.aaps.plugins.configuration.keys.ConfigurationBooleanKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.system.exitProcess

@Singleton
class ConfigBuilderPlugin @Inject constructor(
    aapsLogger: AAPSLogger,
    rh: ResourceHelper,
    preferences: Preferences,
    private val rxBus: RxBus,
    private val activePlugin: ActivePlugin,
    private val uel: UserEntryLogger,
    private val pumpSync: PumpSync,
    private val protectionCheck: ProtectionCheck,
    private val uiInteraction: UiInteraction,
    private val context: Context
) : PluginBaseWithPreferences(
    pluginDescription = PluginDescription()
        .mainType(PluginType.GENERAL)
        .fragmentClass(ConfigBuilderFragment::class.java.name)
        .alwaysEnabled(true)
        .pluginIcon(app.aaps.core.ui.R.drawable.ic_cogs)
        .pluginName(R.string.config_builder)
        .shortName(R.string.config_builder_shortname)
        .description(R.string.description_config_builder),
    ownPreferences = listOf(ConfigurationBooleanKey::class.java, ConfigurationBooleanComposedKey::class.java),
    aapsLogger, rh, preferences
), ConfigBuilder {

    private val scope = CoroutineScope(Dispatchers.Default + Job())

    override fun initialize() {
        loadSettings()
        setAlwaysEnabledPluginsEnabled()
        // Wait for MainActivity start
        runOnUiThreadDelayed(5000) { rxBus.send(EventAppInitialized()) }
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
        // val settingEnabled = "ConfigBuilder_" + type.name + "_" + p.javaClass.simpleName + "_Enabled"
        // sp.putBoolean(settingEnabled, p.isEnabled())
        preferences.put(ConfigurationBooleanComposedKey.ConfigBuilderEnabled, type.name + "_" + p.javaClass.simpleName, value = p.isEnabled())
        aapsLogger.debug(LTag.CONFIGBUILDER, "Storing: " + ConfigurationBooleanComposedKey.ConfigBuilderEnabled.composeKey(type.name + "_" + p.javaClass.simpleName) + ":" + p.isEnabled())
        val settingVisible = "ConfigBuilder_" + type.name + "_" + p.javaClass.simpleName + "_Visible"
        //sp.putBoolean(settingVisible, p.isFragmentVisible())
        preferences.put(ConfigurationBooleanComposedKey.ConfigBuilderVisible, type.name + "_" + p.javaClass.simpleName, value = p.isFragmentVisible())
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
        // val settingEnabled = "ConfigBuilder_" + type.name + "_" + p.javaClass.simpleName + "_Enabled"
        // if (sp.contains(settingEnabled)) p.setPluginEnabled(type, sp.getBoolean(settingEnabled, false))
        val existing = preferences.getIfExists(ConfigurationBooleanComposedKey.ConfigBuilderEnabled, type.name + "_" + p.javaClass.simpleName)
        if (existing != null) p.setPluginEnabled(type, existing)
        else if (p.getType() == type && (p.pluginDescription.enableByDefault || p.pluginDescription.alwaysEnabled)) {
            p.setPluginEnabled(type, true)
        }
        aapsLogger.debug(LTag.CONFIGBUILDER, "Loaded: " + ConfigurationBooleanComposedKey.ConfigBuilderEnabled.composeKey(type.name + "_" + p.javaClass.simpleName) + ":" + p.isEnabled(type))
        //val settingVisible = "ConfigBuilder_" + type.name + "_" + p.javaClass.simpleName + "_Visible"
        //if (sp.contains(settingVisible)) p.setFragmentVisible(type, sp.getBoolean(settingVisible, false) && sp.getBoolean(settingEnabled, false))
        val existingVisible = preferences.getIfExists(ConfigurationBooleanComposedKey.ConfigBuilderVisible, type.name + "_" + p.javaClass.simpleName)
        if (existingVisible != null) p.setFragmentVisible(type, existingVisible)
        else if (p.getType() == type && p.pluginDescription.visibleByDefault) {
            p.setFragmentVisible(type, true)
        }
        aapsLogger.debug(LTag.CONFIGBUILDER, "Loaded: " + ConfigurationBooleanComposedKey.ConfigBuilderVisible.composeKey(type.name + "_" + p.javaClass.simpleName) + ":" + p.isFragmentVisible())
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
        val allowHardwarePump = preferences.get(ConfigurationBooleanKey.AllowHardwarePump)
        if (allowHardwarePump) {
            performPluginSwitch(changedPlugin, newState, type)
            pumpSync.connectNewPump()
        } else {
            OKDialog.showConfirmation(activity, rh.gs(R.string.allow_hardware_pump_text), {
                performPluginSwitch(changedPlugin, newState, type)
                pumpSync.connectNewPump()
                preferences.put(ConfigurationBooleanKey.AllowHardwarePump, true)
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
        if (enabled && !changedPlugin.isEnabled()) {
            scope.launch {
                uel.log(
                    Action.PLUGIN_ENABLED, Sources.ConfigBuilder, rh.gs(changedPlugin.pluginDescription.pluginName),
                    ValueWithUnit.SimpleString(rh.gsNotLocalised(changedPlugin.pluginDescription.pluginName))
                )
            }
        } else if (!enabled) {
            scope.launch {
                uel.log(
                    Action.PLUGIN_DISABLED, Sources.ConfigBuilder, rh.gs(changedPlugin.pluginDescription.pluginName),
                    ValueWithUnit.SimpleString(rh.gsNotLocalised(changedPlugin.pluginDescription.pluginName))
                )
            }
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
        pluginViewHolders: ArrayList<ConfigBuilder.PluginViewHolderInterface>?,
        activity: FragmentActivity,
        parent: LinearLayout,
        showExpanded: Boolean
    ) {
        if (plugins.isEmpty()) return
        val layoutInflater = activity.layoutInflater

        val layout = ConfigbuilderSingleCategoryBinding.inflate(layoutInflater, parent, true)
        val pluginsAdded = ArrayList<PluginViewHolder>()

        if (title != null) layout.categoryTitle.text = rh.gs(title)
        else {
            layout.categoryTitle.visibility = View.GONE
            layout.header.background = null
        }
        layout.categoryVisibility.visibility = preferences.simpleMode.not().toVisibility()
        layout.categoryDescription.text = rh.gs(description)
        (layout.categoryExpandMore.background as AnimationDrawable).let { expandAnimation ->
            expandAnimation.setEnterFadeDuration(200)
            expandAnimation.setExitFadeDuration(200)
            if (!expandAnimation.isRunning) expandAnimation.start()
        }
        layout.categoryExpandLess.setOnClickListener {
            layout.categoryExpandLess.visibility = false.toVisibility()
            layout.categoryExpandMore.visibility = (plugins.size > 1).toVisibility()
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
            val pluginViewHolder = PluginViewHolder(layoutInflater, pluginType, plugin)
            layout.categoryPlugins.addView(pluginViewHolder.layout.root)
            pluginViewHolders?.add(pluginViewHolder)
            pluginsAdded.add(pluginViewHolder)
        }
        if (showExpanded) layout.categoryExpandMore.callOnClick()
        else layout.categoryExpandLess.callOnClick()
    }

    inner class PluginViewHolder internal constructor(
        layoutInflater: LayoutInflater,
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
                it.context.scanForActivity()?.let { activity ->
                    switchAllowed(plugin, if (layout.pluginEnabledExclusive.isVisible) layout.pluginEnabledExclusive.isChecked else layout.pluginEnabledInclusive.isChecked, activity, pluginType)
                }
            }
            layout.pluginEnabledInclusive.setOnClickListener {
                it.context.scanForActivity()?.let { activity ->
                    switchAllowed(plugin, if (layout.pluginEnabledExclusive.isVisible) layout.pluginEnabledExclusive.isChecked else layout.pluginEnabledInclusive.isChecked, activity, pluginType)
                }
            }

            layout.pluginPreferences.setOnClickListener {
                it.context.scanForActivity()?.let { activity ->
                    protectionCheck.queryProtection(activity, ProtectionCheck.Protection.PREFERENCES, {
                        val i = Intent(activity, uiInteraction.preferencesActivity)
                        i.putExtra(UiInteraction.PLUGIN_NAME, plugin.javaClass.simpleName)
                        activity.startActivity(i)
                    }, null)
                }
            }
        }

        init {
            update(layoutInflater.context)
        }

        override fun update(context: Context) {
            layout.pluginEnabledExclusive.visibility = areMultipleSelectionsAllowed(pluginType).not().toVisibility()
            layout.pluginEnabledInclusive.visibility = areMultipleSelectionsAllowed(pluginType).toVisibility()
            layout.pluginEnabledExclusive.isChecked = plugin.isEnabled(pluginType)
            layout.pluginEnabledInclusive.isChecked = plugin.isEnabled(pluginType)
            layout.pluginEnabledInclusive.isEnabled = !plugin.pluginDescription.alwaysEnabled
            layout.pluginEnabledExclusive.isEnabled = !plugin.pluginDescription.alwaysEnabled
            if (plugin.menuIcon != -1) {
                layout.pluginIcon.visibility = View.VISIBLE
                layout.pluginIcon.setImageDrawable(ContextCompat.getDrawable(context, plugin.menuIcon))
                if (plugin.menuIcon2 != -1) {
                    layout.pluginIcon2.visibility = View.VISIBLE
                    layout.pluginIcon2.setImageDrawable(ContextCompat.getDrawable(context, plugin.menuIcon2))
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

    override fun exitApp(from: String, source: Sources, launchAgain: Boolean) {
        rxBus.send(EventAppExit())
        aapsLogger.debug(LTag.CORE, "Exiting ... Requester: $from")
        uel.log(Action.EXIT_AAPS, source)
        if (launchAgain) scheduleStart()
        System.runFinalization()
        exitProcess(0)
    }

    fun scheduleStart() {
        // fetch the packageManager so we can get the default launch activity
        context.packageManager?.let { pm ->
            //create the intent with the default start activity for your application
            pm.getLaunchIntentForPackage(context.packageName)?.let { startActivity ->
                startActivity.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                //create a pending intent so the application is restarted after System.exit(0) was called.
                // We use an AlarmManager to call this intent in 100ms
                val pendingIntentId = 2233445
                val pendingIntent = PendingIntent.getActivity(context, pendingIntentId, startActivity, PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager)
                    .set(AlarmManager.RTC, System.currentTimeMillis() + 100, pendingIntent)
            }
        }
    }
}
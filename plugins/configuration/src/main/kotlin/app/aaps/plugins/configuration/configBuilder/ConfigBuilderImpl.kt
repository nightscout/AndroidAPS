package app.aaps.plugins.configuration.configBuilder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.aps.APS
import app.aaps.core.interfaces.aps.Sensitivity
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.configuration.ConfigBuilder
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.pump.Pump
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventAppExit
import app.aaps.core.interfaces.rx.events.EventConfigBuilderChange
import app.aaps.core.interfaces.smoothing.Smoothing
import app.aaps.core.interfaces.source.BgSource
import app.aaps.core.interfaces.sync.NsClient
import app.aaps.core.keys.BooleanComposedKey
import app.aaps.core.keys.BooleanNonKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.plugins.configuration.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.system.exitProcess

@Singleton
class ConfigBuilderImpl @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val rh: ResourceHelper,
    private val preferences: Preferences,
    private val rxBus: RxBus,
    private val activePlugin: ActivePlugin,
    private val uel: UserEntryLogger,
    private val pumpSync: PumpSync,
    private val context: Context,
    private val config: Config
) : ConfigBuilder {

    private val scope = CoroutineScope(Dispatchers.Default + Job())

    override fun initialize() {
        loadSettings()
        setAlwaysEnabledPluginsEnabled()
    }

    private fun setAlwaysEnabledPluginsEnabled() {
        for (plugin in activePlugin.getPluginsList()) {
            if (plugin.pluginDescription.alwaysEnabled) plugin.setPluginEnabled(plugin.getType(), true)
        }
    }

    override fun storeSettings(from: String) {
        aapsLogger.debug(LTag.CONFIGBUILDER, "Storing settings from: $from")
        activePlugin.verifySelectionInCategories()
        for (p in activePlugin.getPluginsList()) {
            val type = p.getType()
            if (p.pluginDescription.alwaysEnabled) continue
            savePref(p, type)
        }
    }

    private fun savePref(p: PluginBase, type: PluginType) {
        val composed = type.name + "_" + p.javaClass.simpleName
        preferences.put(BooleanComposedKey.ConfigBuilderEnabled, composed, value = p.isEnabled())
        aapsLogger.debug(LTag.CONFIGBUILDER, "Storing: " + BooleanComposedKey.ConfigBuilderEnabled.composeKey(composed) + ":" + p.isEnabled())
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
        val composed = type.name + "_" + p.javaClass.simpleName
        val existing = preferences.getIfExists(BooleanComposedKey.ConfigBuilderEnabled, composed)
        if (existing != null) p.setPluginEnabled(type, existing)
        else if (p.getType() == type && (p.pluginDescription.enableByDefault || p.pluginDescription.alwaysEnabled)) {
            p.setPluginEnabled(type, true)
        }
        aapsLogger.debug(LTag.CONFIGBUILDER, "Loaded: " + BooleanComposedKey.ConfigBuilderEnabled.composeKey(composed) + ":" + p.isEnabled(type))
    }

    private fun logPluginStatus() {
        for (p in activePlugin.getPluginsList()) {
            aapsLogger.debug(
                LTag.CONFIGBUILDER, p.name + ":" +
                    (if (p.isEnabled(PluginType.GENERAL)) " GENERAL" else "") +
                    (if (p.isEnabled(PluginType.SENSITIVITY)) " SENSITIVITY" else "") +
                    (if (p.isEnabled(PluginType.APS)) " APS" else "") +
                    (if (p.isEnabled(PluginType.PUMP)) " PUMP" else "") +
                    (if (p.isEnabled(PluginType.CONSTRAINTS)) " CONSTRAINTS" else "") +
                    (if (p.isEnabled(PluginType.LOOP)) " LOOP" else "") +
                    (if (p.isEnabled(PluginType.BGSOURCE)) " BGSOURCE" else "") +
                    (if (p.isEnabled(PluginType.SYNC)) " SYNC" else "") +
                    if (p.isEnabled(PluginType.SMOOTHING)) " SMOOTHING" else ""
            )
        }
    }

    override fun requestPluginSwitch(plugin: PluginBase, enabled: Boolean, type: PluginType): String? {
        return when {
            plugin.getType() == PluginType.PUMP && plugin.name != rh.gs(app.aaps.core.ui.R.string.virtual_pump) -> {
                val allowHardwarePump = preferences.get(BooleanNonKey.AllowHardwarePump)
                if (allowHardwarePump) {
                    performPluginSwitch(plugin, enabled, type)
                    pumpSync.connectNewPump()
                    null
                } else {
                    rh.gs(R.string.allow_hardware_pump_text)
                }
            }

            plugin.getType() == PluginType.PUMP                                                                 -> {
                performPluginSwitch(plugin, enabled, type)
                pumpSync.connectNewPump()
                null
            }

            else                                                                                                -> {
                performPluginSwitch(plugin, enabled, type)
                null
            }
        }
    }

    override fun confirmPumpPluginSwitch(plugin: PluginBase, enabled: Boolean, type: PluginType) {
        performPluginSwitch(plugin, enabled, type)
        pumpSync.connectNewPump()
        preferences.put(BooleanNonKey.AllowHardwarePump, true)
        scope.launch {
            uel.log(
                action = Action.HW_PUMP_ALLOWED,
                source = Sources.ConfigBuilder,
                note = rh.gs(plugin.pluginDescription.pluginName),
                value = ValueWithUnit.SimpleString(rh.gsNotLocalised(plugin.pluginDescription.pluginName))
            )
        }
        aapsLogger.debug(LTag.PUMP, "First time HW pump allowed!")
    }

    override fun performPluginSwitch(changedPlugin: PluginBase, enabled: Boolean, type: PluginType) {
        if (!config.AAPSCLIENT) {
            if (enabled && !changedPlugin.isEnabled()) {
                scope.launch {
                    uel.log(
                        Action.PLUGIN_ENABLED, Sources.ConfigBuilder, null,
                        ValueWithUnit.SimpleString(rh.gsNotLocalised(changedPlugin.pluginDescription.pluginName))
                    )
                }
            } else if (!enabled) {
                scope.launch {
                    uel.log(
                        Action.PLUGIN_DISABLED, Sources.ConfigBuilder, null,
                        ValueWithUnit.SimpleString(rh.gsNotLocalised(changedPlugin.pluginDescription.pluginName))
                    )
                }
            }
        }
        changedPlugin.setPluginEnabled(type, enabled)
        processOnEnabledCategoryChanged(changedPlugin, type)
        storeSettings("RemoteConfiguration")
        rxBus.send(EventConfigBuilderChange())
        logPluginStatus()
    }

    override fun processOnEnabledCategoryChanged(changedPlugin: PluginBase, type: PluginType) {
        var pluginsInCategory: ArrayList<PluginBase>? = null
        when {
            type == PluginType.SENSITIVITY -> pluginsInCategory = activePlugin.getSpecificPluginsListByInterface(Sensitivity::class.java)
            type == PluginType.SMOOTHING   -> pluginsInCategory = activePlugin.getSpecificPluginsListByInterface(Smoothing::class.java)
            type == PluginType.APS         -> pluginsInCategory = activePlugin.getSpecificPluginsListByInterface(APS::class.java)
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
                    }
                }
            } else if (type != PluginType.SYNC) {
                // enable first plugin in list
                // NSC must not be selected
                pluginsInCategory[0].setPluginEnabled(type, true)
            }
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

    private fun scheduleStart() {
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

package app.aaps.plugins.configuration.configBuilder

import app.aaps.core.interfaces.aps.Sensitivity
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.configuration.ConfigBuilder
import app.aaps.core.interfaces.insulin.Insulin
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.Notification
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.plugin.PluginType
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.pump.defs.PumpType
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventNSClientNewLog
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.interfaces.smoothing.Smoothing
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.nssdk.interfaces.RunningConfiguration
import app.aaps.core.nssdk.localmodel.devicestatus.NSDeviceStatus
import app.aaps.plugins.configuration.R
import org.json.JSONException
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RunningConfigurationImpl @Inject constructor(
    private val activePlugin: ActivePlugin,
    private val configBuilder: ConfigBuilder,
    private val sp: SP,
    private val aapsLogger: AAPSLogger,
    private val config: Config,
    private val rh: ResourceHelper,
    private val rxBus: RxBus,
    private val pumpSync: PumpSync,
    private val uiInteraction: UiInteraction
) : RunningConfiguration {

    private var counter = 0
    private val every = 12 // Send only every 12 device status to save traffic

    // called in AAPS mode only
    override fun configuration(): JSONObject {
        val json = JSONObject()
        val pumpInterface = activePlugin.activePump

        if (!pumpInterface.isInitialized()) return json
        if (counter++ % every == 0)
            try {
                val insulinInterface = activePlugin.activeInsulin
                val sensitivityInterface = activePlugin.activeSensitivity
                val overviewInterface = activePlugin.activeOverview
                val safetyInterface = activePlugin.activeSafety
                val smoothingInterface = activePlugin.activeSmoothing

                json.put("insulin", insulinInterface.id.value)
                json.put("insulinConfiguration", insulinInterface.configuration())
                json.put("sensitivity", sensitivityInterface.id.value)
                json.put("sensitivityConfiguration", sensitivityInterface.configuration())
                json.put("smoothing", smoothingInterface.javaClass.simpleName)
                json.put("overviewConfiguration", overviewInterface.configuration())
                json.put("safetyConfiguration", safetyInterface.configuration())
                json.put("pump", pumpInterface.model().description)
                json.put("version", config.VERSION_NAME)
            } catch (e: JSONException) {
                aapsLogger.error("Unhandled exception", e)
            }
        return json
    }

    // called in NSClient mode only
    override fun apply(configuration: NSDeviceStatus.Configuration) {
        assert(config.NSCLIENT)

        configuration.version?.let {
            rxBus.send(EventNSClientNewLog("◄ VERSION", "Received AAPS version  $it"))
            if (config.VERSION_NAME.startsWith(it).not())
                uiInteraction.addNotification(Notification.NSCLIENT_VERSION_DOES_NOT_MATCH, rh.gs(R.string.nsclient_version_does_not_match), Notification.NORMAL)
        }
        configuration.insulin?.let {
            val insulin = Insulin.InsulinType.fromInt(it)
            for (p in activePlugin.getSpecificPluginsListByInterface(Insulin::class.java)) {
                val insulinPlugin = p as Insulin
                if (insulinPlugin.id == insulin) {
                    if (!p.isEnabled()) {
                        aapsLogger.debug(LTag.CORE, "Changing insulin plugin to ${insulin.name}")
                        configBuilder.performPluginSwitch(p, true, PluginType.INSULIN)
                    }
                    configuration.insulinConfiguration?.let { ic -> insulinPlugin.applyConfiguration(ic) }
                }
            }
        }

        configuration.sensitivity?.let {
            val sensitivity = Sensitivity.SensitivityType.fromInt(it)
            for (p in activePlugin.getSpecificPluginsListByInterface(Sensitivity::class.java)) {
                val sensitivityPlugin = p as Sensitivity
                if (sensitivityPlugin.id == sensitivity) {
                    if (!p.isEnabled()) {
                        aapsLogger.debug(LTag.CORE, "Changing sensitivity plugin to ${sensitivity.name}")
                        configBuilder.performPluginSwitch(p, true, PluginType.SENSITIVITY)
                    }
                    configuration.sensitivityConfiguration?.let { sc -> sensitivityPlugin.applyConfiguration(sc) }
                }
            }
        }

        configuration.smoothing?.let {
            for (p in activePlugin.getSpecificPluginsListByInterface(Smoothing::class.java)) {
                val smoothingPlugin = p as Smoothing
                if (smoothingPlugin.javaClass.simpleName == it) {
                    if (!p.isEnabled()) {
                        aapsLogger.debug(LTag.CORE, "Changing smoothing plugin to ${smoothingPlugin.javaClass.simpleName}")
                        configBuilder.performPluginSwitch(p, true, PluginType.SMOOTHING)
                    }
                }
            }
        }

        configuration.pump?.let {
            if (sp.getString(app.aaps.core.utils.R.string.key_virtualpump_type, "fake") != it) {
                sp.putString(app.aaps.core.utils.R.string.key_virtualpump_type, it)
                activePlugin.activePump.pumpDescription.fillFor(PumpType.getByDescription(it))
                pumpSync.connectNewPump(endRunning = false) // do not end running TBRs, we call this only to accept data properly
                aapsLogger.debug(LTag.CORE, "Changing pump type to $it")
            }
        }

        configuration.overviewConfiguration?.let {
            activePlugin.activeOverview.applyConfiguration(it)
        }

        configuration.safetyConfiguration?.let {
            activePlugin.activeSafety.applyConfiguration(it)
        }
    }
}
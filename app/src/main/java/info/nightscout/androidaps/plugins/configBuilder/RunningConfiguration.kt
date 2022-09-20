package info.nightscout.androidaps.plugins.configBuilder

import info.nightscout.androidaps.core.R
import info.nightscout.androidaps.interfaces.*
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.shared.logging.LTag
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.plugins.sync.nsclient.events.EventNSClientNewLog
import info.nightscout.androidaps.plugins.general.overview.events.EventNewNotification
import info.nightscout.androidaps.plugins.general.overview.notifications.Notification
import info.nightscout.androidaps.plugins.pump.common.defs.PumpType
import info.nightscout.androidaps.utils.JsonHelper
import info.nightscout.androidaps.interfaces.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import org.json.JSONException
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RunningConfiguration @Inject constructor(
    private val activePlugin: ActivePlugin,
    private val configBuilder: ConfigBuilder,
    private val sp: SP,
    private val aapsLogger: AAPSLogger,
    private val config: Config,
    private val rh: ResourceHelper,
    private val rxBus: RxBus,
    private val pumpSync: PumpSync
) {

    private var counter = 0
    private val every = 12 // Send only every 12 device status to save traffic

    // called in AAPS mode only
    fun configuration(): JSONObject {
        val json = JSONObject()
        val pumpInterface = activePlugin.activePump

        if (!pumpInterface.isInitialized()) return json
        if (counter++ % every == 0)
            try {
                val insulinInterface = activePlugin.activeInsulin
                val sensitivityInterface = activePlugin.activeSensitivity
                val overviewInterface = activePlugin.activeOverview
                val safetyInterface = activePlugin.activeSafety

                json.put("insulin", insulinInterface.id.value)
                json.put("insulinConfiguration", insulinInterface.configuration())
                json.put("sensitivity", sensitivityInterface.id.value)
                json.put("sensitivityConfiguration", sensitivityInterface.configuration())
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
    fun apply(configuration: JSONObject, version: NsClient.Version) {
        assert(config.NSCLIENT)

        if (configuration.has("version")) {
            rxBus.send(EventNSClientNewLog("VERSION", "Received AndroidAPS version  ${configuration.getString("version")}", version))
            if (config.VERSION_NAME.startsWith(configuration.getString("version")).not()) {
                rxBus.send(EventNewNotification(Notification(Notification.NSCLIENT_VERSION_DOES_NOT_MATCH, rh.gs(R.string.nsclient_version_does_not_match), Notification.NORMAL)))
            }
        }
        if (configuration.has("insulin")) {
            val insulin = Insulin.InsulinType.fromInt(JsonHelper.safeGetInt(configuration, "insulin", Insulin.InsulinType.UNKNOWN.value))
            for (p in activePlugin.getSpecificPluginsListByInterface(Insulin::class.java)) {
                val insulinPlugin = p as Insulin
                if (insulinPlugin.id == insulin) {
                    if (!p.isEnabled()) {
                        aapsLogger.debug(LTag.CORE, "Changing insulin plugin to ${insulin.name}")
                        configBuilder.performPluginSwitch(p, true, PluginType.INSULIN)
                    }
                    insulinPlugin.applyConfiguration(configuration.getJSONObject("insulinConfiguration"))
                }
            }
        }

        if (configuration.has("sensitivity")) {
            val sensitivity = Sensitivity.SensitivityType.fromInt(JsonHelper.safeGetInt(configuration, "sensitivity", Sensitivity.SensitivityType.UNKNOWN.value))
            for (p in activePlugin.getSpecificPluginsListByInterface(Sensitivity::class.java)) {
                val sensitivityPlugin = p as Sensitivity
                if (sensitivityPlugin.id == sensitivity) {
                    if (!p.isEnabled()) {
                        aapsLogger.debug(LTag.CORE, "Changing sensitivity plugin to ${sensitivity.name}")
                        configBuilder.performPluginSwitch(p, true, PluginType.SENSITIVITY)
                    }
                    sensitivityPlugin.applyConfiguration(configuration.getJSONObject("sensitivityConfiguration"))
                }
            }
        }

        if (configuration.has("pump")) {
            val pumpType = JsonHelper.safeGetString(configuration, "pump", PumpType.GENERIC_AAPS.description)
            if (sp.getString(R.string.key_virtualpump_type, "fake") != pumpType) {
                sp.putString(R.string.key_virtualpump_type, pumpType)
                activePlugin.activePump.pumpDescription.fillFor(PumpType.getByDescription(pumpType))
                pumpSync.connectNewPump(endRunning = false) // do not end running TBRs, we call this only to accept data properly
                aapsLogger.debug(LTag.CORE, "Changing pump type to $pumpType")
            }
        }

        if (configuration.has("overviewConfiguration"))
            activePlugin.activeOverview.applyConfiguration(configuration.getJSONObject("overviewConfiguration"))

        if (configuration.has("safetyConfiguration"))
            activePlugin.activeSafety.applyConfiguration(configuration.getJSONObject("safetyConfiguration"))
    }
}
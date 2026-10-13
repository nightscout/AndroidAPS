@file:Suppress("SpellCheckingInspection")

package app.aaps.plugins.sync.nsclient.data

import android.content.Context
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.notifications.Notification
import app.aaps.core.interfaces.nsclient.NSSettingsStatus
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventDismissNotification
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.dialogs.OKDialog
import app.aaps.core.utils.JsonHelper
import app.aaps.plugins.sync.R
import org.json.JSONException
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/*
 {
 "status": "ok",
 "name": "Nightscout",
 "version": "0.10.0-dev-20170423",
 "versionNum": 1000,
 "serverTime": "2017-06-12T07:46:56.006Z",
 "apiEnabled": true,
 "careportalEnabled": true,
 "boluscalcEnabled": true,
 "head": "96ee154",
 "settings": {
     "units": "mmol",
     "timeFormat": 24,
     "nightMode": false,
     "editMode": true,
     "showRawbg": "always",
     "customTitle": "Bara's CGM",
     "theme": "colors",
     "alarmUrgentHigh": true,
     "alarmUrgentHighMins": [30, 60, 90, 120],
     "alarmHigh": true,
     "alarmHighMins": [30, 60, 90, 120],
     "alarmLow": true,
     "alarmLowMins": [15, 30, 45, 60],
     "alarmUrgentLow": true,
     "alarmUrgentLowMins": [15, 30, 45],
     "alarmUrgentMins": [30, 60, 90, 120],
     "alarmWarnMins": [30, 60, 90, 120],
     "alarmTimeagoWarn": true,
     "alarmTimeagoWarnMins": 15,
     "alarmTimeagoUrgent": true,
     "alarmTimeagoUrgentMins": 30,
     "language": "cs",
     "scaleY": "linear",
     "showPlugins": "careportal boluscalc food bwp cage sage iage iob cob basal ar2 delta direction upbat rawbg",
     "showForecast": "ar2",
     "focusHours": 3,
     "heartbeat": 60,
     "baseURL": "http:\/\/xxxxxxxxxxxx",
     "authDefaultRoles": "readable",
     "thresholds": {
         "bgHigh": 252,
         "bgTargetTop": 180,
         "bgTargetBottom": 72,
         "bgLow": 71
     },
     "DEFAULT_FEATURES": ["bgnow", "delta", "direction", "timeago", "devicestatus", "upbat", "errorcodes", "profile"],
     "alarmTypes": ["predict"],
     "enable": ["careportal", "boluscalc", "food", "bwp", "cage", "sage", "iage", "iob", "cob", "basal", "ar2", "rawbg", "pushover", "bgi", "pump", "openaps", "pushover", "treatmentnotify", "bgnow", "delta", "direction", "timeago", "devicestatus", "upbat", "profile", "ar2"]
 },
 "extendedSettings": {
     "pump": {
         "fields": "reservoir battery clock",
         "urgentBattP": 26,
         "warnBattP": 51
     },
     "openaps": {
         "enableAlerts": true
     },
     "cage": {
         "alerts": true,
         "display": "days",
         "urgent": 96,
         "warn": 72
     },
     "sage": {
         "alerts": true,
         "urgent": 336,
         "warn": 168
     },
     "iage": {
         "alerts": true,
         "urgent": 150,
         "warn": 120
     },
     "basal": {
         "render": "default"
     },
     "profile": {
         "history": true,
         "multiple": true
     },
     "devicestatus": {
         "advanced": true
     }
 },
 "activeProfile": "2016 +30%"
 }
 */
@Singleton
class NSSettingsStatusImpl @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val rh: ResourceHelper,
    private val rxBus: RxBus,
    private val preferences: Preferences,
    private val config: Config,
    private val uel: UserEntryLogger,
    private val uiInteraction: UiInteraction
) : NSSettingsStatus {

    // ***** PUMP STATUS ******
    private var data: JSONObject? = null

    /*  Other received data to 2016/02/10
        {
          status: 'ok'
          , name: env.name
          , version: env.version
          , versionNum: versionNum (for ver 1.2.3 contains 10203)
          , serverTime: new Date().toISOString()
          , apiEnabled: apiEnabled
          , careportalEnabled: apiEnabled && env.settings.enable.indexOf('careportal') > -1
          , boluscalcEnabled: apiEnabled && env.settings.enable.indexOf('boluscalc') > -1
          , head: env.head
          , settings: env.settings
          , extendedSettings: ctx.plugins && ctx.plugins.extendedClientSettings ? ctx.plugins.extendedClientSettings(env.extendedSettings) : {}
          , activeProfile ..... calculated from treatments or missing
        }
     */

    override fun handleNewData(status: JSONObject) {
        data = status
        aapsLogger.debug(LTag.NSCLIENT, "Got versions: Nightscout: ${getVersion()}")
        if (getVersionNum() < config.SUPPORTED_NS_VERSION) {
            uiInteraction.addNotification(Notification.OLD_NS, rh.gs(R.string.unsupported_ns_version), Notification.NORMAL)
        } else {
            rxBus.send(EventDismissNotification(Notification.OLD_NS))
        }
        data = status
        aapsLogger.debug(LTag.NSCLIENT, "Received status: $status")
        if (config.AAPSCLIENT) copyStatusLightsNsSettings(null)
    }

    override fun getVersion(): String =
        JsonHelper.safeGetStringAllowNull(data, "version", null) ?: "UNKNOWN"

    private fun getVersionNum(): Int =
        JsonHelper.safeGetInt(data, "versionNum")

    private fun getSettings() =
        JsonHelper.safeGetJSONObject(data, "settings", null)

    private fun getExtendedSettings(): JSONObject? =
        JsonHelper.safeGetJSONObject(data, "extendedSettings", null)

    // valid property is "warn" or "urgent"
    // plugins "iage" "sage" "cage" "pbage"
    private fun getExtendedWarnValue(plugin: String, property: String): Double? {
        val extendedSettings = getExtendedSettings() ?: return null
        val pluginJson = extendedSettings.optJSONObject(plugin) ?: return null
        return try {
            pluginJson.getDouble(property)
        } catch (e: Exception) {
            null
        }
    }

    /*
      , warnClock: sbx.extendedSettings.warnClock || 30
      , urgentClock: sbx.extendedSettings.urgentClock || 60
      , warnRes: sbx.extendedSettings.warnRes || 10
      , urgentRes: sbx.extendedSettings.urgentRes || 5
      , warnBattV: sbx.extendedSettings.warnBattV || 1.35
      , urgentBattV: sbx.extendedSettings.urgentBattV || 1.3
      , warnBattP: sbx.extendedSettings.warnBattP || 30
      , urgentBattP: sbx.extendedSettings.urgentBattP || 20
      , enableAlerts: sbx.extendedSettings.enableAlerts || false
     */
    override fun extendedPumpSettings(setting: String?): Double {
        try {
            val pump = extendedPumpSettings()
            return when (setting) {
                "warnClock"   -> JsonHelper.safeGetDouble(pump, setting, 30.0)
                "urgentClock" -> JsonHelper.safeGetDouble(pump, setting, 60.0)
                "warnRes"     -> JsonHelper.safeGetDouble(pump, setting, 10.0)
                "urgentRes"   -> JsonHelper.safeGetDouble(pump, setting, 5.0)
                "warnBattV"   -> JsonHelper.safeGetDouble(pump, setting, 1.35)
                "urgentBattV" -> JsonHelper.safeGetDouble(pump, setting, 1.3)
                "warnBattP"   -> JsonHelper.safeGetDouble(pump, setting, 30.0)
                "urgentBattP" -> JsonHelper.safeGetDouble(pump, setting, 20.0)
                else          -> 0.0
            }
        } catch (e: JSONException) {
            aapsLogger.error("Unhandled exception", e)
        }
        return 0.0
    }

    private fun extendedPumpSettings(): JSONObject? =
        JsonHelper.safeGetJSONObject(getExtendedSettings(), "pump", null)

    override fun pumpExtendedSettingsFields(): String =
        JsonHelper.safeGetString(extendedPumpSettings(), "fields", "")

    override fun copyStatusLightsNsSettings(context: Context?) {
        val action = Runnable {
            getExtendedWarnValue("cage", "warn")?.let { preferences.put(IntKey.OverviewCageWarning, it.toInt()) }
            getExtendedWarnValue("cage", "urgent")?.let { preferences.put(IntKey.OverviewCageCritical, it.toInt()) }
            getExtendedWarnValue("iage", "warn")?.let { preferences.put(IntKey.OverviewIageWarning, it.toInt()) }
            getExtendedWarnValue("iage", "urgent")?.let { preferences.put(IntKey.OverviewIageCritical, it.toInt()) }
            getExtendedWarnValue("sage", "warn")?.let { preferences.put(IntKey.OverviewSageWarning, it.toInt()) }
            getExtendedWarnValue("sage", "urgent")?.let { preferences.put(IntKey.OverviewSageCritical, it.toInt()) }
            getExtendedWarnValue("bage", "warn")?.let { preferences.put(IntKey.OverviewBageWarning, it.toInt()) }
            getExtendedWarnValue("bage", "urgent")?.let { preferences.put(IntKey.OverviewBageCritical, it.toInt()) }
            uel.log(Action.NS_SETTINGS_COPIED, Sources.NSClient)
        }

        if (context != null) OKDialog.showConfirmation(context, rh.gs(app.aaps.core.ui.R.string.statuslights), rh.gs(R.string.copy_existing_values), action)
        else action.run()
    }
}
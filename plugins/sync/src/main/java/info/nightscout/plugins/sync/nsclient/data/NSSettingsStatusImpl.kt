@file:Suppress("SpellCheckingInspection")

package info.nightscout.plugins.sync.nsclient.data

import android.content.Context
import info.nightscout.annotations.OpenForTesting
import info.nightscout.core.ui.dialogs.OKDialog
import info.nightscout.database.entities.UserEntry
import info.nightscout.database.entities.UserEntry.Action
import info.nightscout.interfaces.Config
import info.nightscout.interfaces.logging.UserEntryLogger
import info.nightscout.interfaces.notifications.Notification
import info.nightscout.interfaces.nsclient.NSSettingsStatus
import info.nightscout.interfaces.profile.DefaultValueHelper
import info.nightscout.interfaces.ui.UiInteraction
import info.nightscout.interfaces.utils.JsonHelper
import info.nightscout.plugins.sync.R
import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.events.EventDismissNotification
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
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
@OpenForTesting
@Singleton
class NSSettingsStatusImpl @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val rh: ResourceHelper,
    private val rxBus: RxBus,
    private val defaultValueHelper: DefaultValueHelper,
    private val sp: SP,
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
        val targetHigh = getSettingsThreshold("bgTargetTop")
        val targetlow = getSettingsThreshold("bgTargetBottom")
        if (targetHigh != null) defaultValueHelper.bgTargetHigh = targetHigh
        if (targetlow != null) defaultValueHelper.bgTargetLow = targetlow
        if (config.NSCLIENT) copyStatusLightsNsSettings(null)
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

    // "bgHigh": 252,
    // "bgTargetTop": 180,
    // "bgTargetBottom": 72,
    // "bgLow": 71
    private fun getSettingsThreshold(what: String): Double? {
        val threshold = JsonHelper.safeGetJSONObject(getSettings(), "thresholds", null)
        return JsonHelper.safeGetDoubleAllowNull(threshold, what)
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
            getExtendedWarnValue("cage", "warn")?.let { sp.putDouble(info.nightscout.core.utils.R.string.key_statuslights_cage_warning, it) }
            getExtendedWarnValue("cage", "urgent")?.let { sp.putDouble(info.nightscout.core.utils.R.string.key_statuslights_cage_critical, it) }
            getExtendedWarnValue("iage", "warn")?.let { sp.putDouble(info.nightscout.core.utils.R.string.key_statuslights_iage_warning, it) }
            getExtendedWarnValue("iage", "urgent")?.let { sp.putDouble(info.nightscout.core.utils.R.string.key_statuslights_iage_critical, it) }
            getExtendedWarnValue("sage", "warn")?.let { sp.putDouble(info.nightscout.core.utils.R.string.key_statuslights_sage_warning, it) }
            getExtendedWarnValue("sage", "urgent")?.let { sp.putDouble(info.nightscout.core.utils.R.string.key_statuslights_sage_critical, it) }
            getExtendedWarnValue("bage", "warn")?.let { sp.putDouble(info.nightscout.core.utils.R.string.key_statuslights_bage_warning, it) }
            getExtendedWarnValue("bage", "urgent")?.let { sp.putDouble(info.nightscout.core.utils.R.string.key_statuslights_bage_critical, it) }
            uel.log(Action.NS_SETTINGS_COPIED, UserEntry.Sources.NSClient)
        }

        if (context != null) OKDialog.showConfirmation(context, rh.gs(info.nightscout.core.ui.R.string.statuslights), rh.gs(R.string.copy_existing_values), action)
        else action.run()
    }
}
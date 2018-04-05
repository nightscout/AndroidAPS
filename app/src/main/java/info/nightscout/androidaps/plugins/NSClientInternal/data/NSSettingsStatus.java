package info.nightscout.androidaps.plugins.NSClientInternal.data;

import android.support.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.Objects;

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
     "baseURL": "http:\/\/barascgm.sysop.cz:82",
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
public class NSSettingsStatus {
    private static Logger log = LoggerFactory.getLogger(NSSettingsStatus.class);

    private static NSSettingsStatus instance = null;

    public static NSSettingsStatus getInstance() {
        if (instance == null)
            instance = new NSSettingsStatus();
        return instance;
    }

    private JSONObject data = null;

    public NSSettingsStatus() {
    }

    public NSSettingsStatus setData(JSONObject obj) {
        this.data = obj;
        return this;
    }

    public String getName() {
        return getStringOrNull("name");
    }

    public String getVersion() {
        return getStringOrNull("version");
    }

    public Integer getVersionNum() {
        return getIntegerOrNull("versionNum");
    }

    public Date getServerTime() {
        return getDateOrNull("versionNum");
    }

    public boolean getApiEnabled() {
        return getBooleanOrNull("apiEnabled");
    }

    public boolean getCareportalEnabled() {
        return getBooleanOrNull("careportalEnabled");
    }

    public boolean getBoluscalcEnabled() {
        return getBooleanOrNull("boluscalcEnabled");
    }

    public String getHead() {
        return getStringOrNull("head");
    }

    public String getSettings() {
        return getStringOrNull("settings");
    }

    public JSONObject getExtendedSettings() {
        try {
            String extended = getStringOrNull("extendedSettings");
            if (extended != null)
                return new JSONObject(extended);

        } catch (JSONException e) {
            log.error("Unhandled exception", e);
        }
        return null;

    }

    // valid property is "warn" or "urgent"
    // plugings "iage" "sage" "cage" "pbage"

    public double getExtendedWarnValue(String plugin, String property, double defaultvalue) {
        JSONObject extendedSettings = this.getExtendedSettings();
        if (extendedSettings == null)
            return defaultvalue;
        JSONObject pluginJson = extendedSettings.optJSONObject(plugin);
        if (pluginJson == null)
            return defaultvalue;
        return pluginJson.optDouble(property, defaultvalue);
    }

    public String getActiveProfile() {
        return getStringOrNull("activeProfile");
    }

    // "bgHigh": 252,
    // "bgTargetTop": 180,
    // "bgTargetBottom": 72,
    // "bgLow": 71
    @Nullable
    public Double getThreshold(String what) {
        try {
            if (data == null)
                return null;
            String settings = getSettings();
            if (settings != null) {
                JSONObject settingsO = new JSONObject(settings);
                if (settingsO.has("thresholds")) {
                    JSONObject tObject = settingsO.getJSONObject("thresholds");
                    if (tObject.has(what)) {
                        Double result = tObject.getDouble(what);
                        return result;
                    }
                }
                if (settingsO.has("alarmTimeagoWarnMins") && Objects.equals(what, "alarmTimeagoWarnMins")) {
                    Double result = settingsO.getDouble(what);
                    return result;
                }
            }
        } catch (JSONException e) {
            log.error("Unhandled exception", e);
        }
        return null;
    }

    private String getStringOrNull(String key) {
        String ret = null;
        if (data == null) return null;
        if (data.has(key)) {
            try {
                ret = data.getString(key);
            } catch (JSONException e) {
                log.error("Unhandled exception", e);
            }
        }
        return ret;
    }

    private Integer getIntegerOrNull(String key) {
        Integer ret = null;
        if (data.has(key)) {
            try {
                ret = data.getInt(key);
            } catch (JSONException e) {
                log.error("Unhandled exception", e);
            }
        }
        return ret;
    }

    private Long getLongOrNull(String key) {
        Long ret = null;
        if (data.has(key)) {
            try {
                ret = data.getLong(key);
            } catch (JSONException e) {
                log.error("Unhandled exception", e);
            }
        }
        return ret;
    }

    private Date getDateOrNull(String key) {
        Date ret = null;
        if (data.has(key)) {
            try {
                ret = new Date(data.getString(key));
            } catch (JSONException e) {
                log.error("Unhandled exception", e);
            }
        }
        return ret;
    }

    private boolean getBooleanOrNull(String key) {
        boolean ret = false;
        if (data.has(key)) {
            try {
                ret = data.getBoolean(key);
            } catch (JSONException e) {
                log.error("Unhandled exception", e);
            }
        }
        return ret;
    }

    // ***** PUMP STATUS ******

    public JSONObject getData() {
        return data;
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

    public double extendedPumpSettings(String setting) {
        try {
            JSONObject pump = extentendedPumpSettings();
            switch (setting) {
                case "warnClock":
                    return pump != null && pump.has(setting) ? pump.getDouble(setting) : 30;
                case "urgentClock":
                    return pump != null && pump.has(setting) ? pump.getDouble(setting) : 30;
                case "warnRes":
                    return pump != null && pump.has(setting) ? pump.getDouble(setting) : 30;
                case "urgentRes":
                    return pump != null && pump.has(setting) ? pump.getDouble(setting) : 30;
                case "warnBattV":
                    return pump != null && pump.has(setting) ? pump.getDouble(setting) : 30;
                case "urgentBattV":
                    return pump != null && pump.has(setting) ? pump.getDouble(setting) : 30;
                case "warnBattP":
                    return pump != null && pump.has(setting) ? pump.getDouble(setting) : 30;
                case "urgentBattP":
                    return pump != null && pump.has(setting) ? pump.getDouble(setting) : 30;
            }
        } catch (JSONException e) {
            log.error("Unhandled exception", e);
        }
        return 0d;
    }


    @Nullable
    public JSONObject extentendedPumpSettings() {
        try {
            JSONObject extended = getExtendedSettings();
            if (extended == null) return null;
            if (extended.has("pump")) {
                JSONObject pump = extended.getJSONObject("pump");
                return pump;
            }
        } catch (JSONException e) {
            log.error("Unhandled exception", e);
        }
        return null;
    }

    public boolean pumpExtentendedSettingsEnabledAlerts() {
        try {
            JSONObject pump = extentendedPumpSettings();
            if (pump != null && pump.has("enableAlerts")) {
                return pump.getBoolean("enableAlerts");
            }
        } catch (JSONException e) {
            log.error("Unhandled exception", e);
        }
        return false;
    }

    public String pumpExtentendedSettingsFields() {
        try {
            JSONObject pump = extentendedPumpSettings();
            if (pump != null && pump.has("fields")) {
                return pump.getString("fields");
            }
        } catch (JSONException e) {
            log.error("Unhandled exception", e);
        }
        return "";
    }

    public boolean openAPSEnabledAlerts() {
        try {
            JSONObject pump = extentendedPumpSettings();
            if (pump != null && pump.has("openaps")) {
                return pump.getBoolean("enableAlerts");
            }
        } catch (JSONException e) {
            log.error("Unhandled exception", e);
        }
        return false;
    }

}

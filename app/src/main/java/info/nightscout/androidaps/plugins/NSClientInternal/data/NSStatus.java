package info.nightscout.androidaps.plugins.NSClientInternal.data;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Date;

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
public class NSStatus {
    private static NSStatus instance = null;

    public static NSStatus getInstance() {
        if (instance == null)
            instance = new NSStatus();
        return instance;
    }

    private JSONObject data;

    public NSStatus() {}

    public NSStatus setData(JSONObject obj) {
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

    public String getExtendedSettings() {
        return getStringOrNull("extendedSettings");
    }

    public String getActiveProfile() {
        return getStringOrNull("activeProfile");
    }

    private String getStringOrNull(String key) {
        String ret = null;
        if (data.has(key)) {
            try {
                ret = data.getString(key);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return ret;
    }

    ;

    private Integer getIntegerOrNull(String key) {
        Integer ret = null;
        if (data.has(key)) {
            try {
                ret = data.getInt(key);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return ret;
    }

    ;

    private Long getLongOrNull(String key) {
        Long ret = null;
        if (data.has(key)) {
            try {
                ret = data.getLong(key);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return ret;
    }

    ;

    private Date getDateOrNull(String key) {
        Date ret = null;
        if (data.has(key)) {
            try {
                ret = new Date(data.getString(key));
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return ret;
    }

    ;

    private boolean getBooleanOrNull(String key) {
        boolean ret = false;
        if (data.has(key)) {
            try {
                ret = data.getBoolean(key);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return ret;
    }

    ;

    public JSONObject getData() {
        return data;
    }

}

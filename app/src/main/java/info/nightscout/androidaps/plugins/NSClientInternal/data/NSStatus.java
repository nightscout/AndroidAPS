package info.nightscout.androidaps.plugins.NSClientInternal.data;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Date;

/**
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
public class NSStatus {
     private JSONObject data;

    public NSStatus(JSONObject obj) {
        this.data = obj;
    }

    public String getName () { return getStringOrNull("name"); }
    public String getVersion () { return getStringOrNull("version"); }
    public Integer getVersionNum () { return getIntegerOrNull("versionNum"); }
    public Date getServerTime () { return getDateOrNull("versionNum"); }
    public boolean getApiEnabled () { return getBooleanOrNull("apiEnabled"); }
    public boolean getCareportalEnabled () { return getBooleanOrNull("careportalEnabled"); }
    public boolean getBoluscalcEnabled () { return getBooleanOrNull("boluscalcEnabled"); }
    public String getHead () { return getStringOrNull("head"); }
    public String getSettings () { return getStringOrNull("settings"); }
    public String getExtendedSettings () { return getStringOrNull("extendedSettings"); }
    public String getActiveProfile () { return getStringOrNull("activeProfile"); }

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
    };

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
    };

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
    };

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
    };

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
    };

    public JSONObject getData () { return data; }

}

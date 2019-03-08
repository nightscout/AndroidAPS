package info.nightscout.androidaps.plugins.general.nsclient.data;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.logging.L;

/**
 * Created by mike on 11.06.2017.
 */

public class NSAlarm {
    private static Logger log = LoggerFactory.getLogger(L.NSCLIENT);

    JSONObject data;

    public NSAlarm(JSONObject data) {
        this.data = data;
    }

    public int getLevel() {
        int retval = 0;
        if (data.has("level")) {
            try {
                retval = data.getInt("level");
            } catch (JSONException e) {
                log.error("Unhandled exception", e);
            }
        }
         return retval;
    }

    public String getGroup() {
        String retval = "N/A";
        if (data.has("group")) {
            try {
                retval = data.getString("group");
            } catch (JSONException e) {
                log.error("Unhandled exception", e);
            }
        }
         return retval;
    }

    public String getTile() {
        String retval = "N/A";
        if (data.has("title")) {
            try {
                retval = data.getString("title");
            } catch (JSONException e) {
                log.error("Unhandled exception", e);
            }
        }
         return retval;
    }

    public String getMessage() {
        String retval = "N/A";
        if (data.has("message")) {
            try {
                retval = data.getString("message");
            } catch (JSONException e) {
                log.error("Unhandled exception", e);
            }
        }
         return retval;
    }
}

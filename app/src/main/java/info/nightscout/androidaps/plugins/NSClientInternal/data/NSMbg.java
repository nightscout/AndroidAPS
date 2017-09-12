package info.nightscout.androidaps.plugins.NSClientInternal.data;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NSMbg {
    private static Logger log = LoggerFactory.getLogger(NSMbg.class);
    public long date;
    public double mbg;
    public String json;

    public NSMbg(JSONObject json) {
        try {
            date = json.getLong("mills");
            mbg = json.getDouble("mgdl");
            this.json = json.toString();
        } catch (JSONException e) {
            log.error("Unhandled exception", e);
            log.debug("Data: " + json.toString());
        }
    }
}

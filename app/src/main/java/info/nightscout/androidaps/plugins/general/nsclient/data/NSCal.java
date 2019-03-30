package info.nightscout.androidaps.plugins.general.nsclient.data;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.logging.L;

public class NSCal {
    private static Logger log = LoggerFactory.getLogger(L.NSCLIENT);
    public long date;
    public double slope;
    public double intercept;
    public double scale = 1;

    public void set(JSONObject json) {
        try {
            date = json.getLong("date");
            slope = json.getDouble("slope");
            intercept = json.getDouble("intercept");
            scale = json.getDouble("scale");
        } catch (JSONException e) {
            log.error("Unhandled exception", e);
            log.error("Data: " + json.toString());
        }
    }
}

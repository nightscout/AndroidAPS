package info.nightscout.androidaps.plugins.general.nsclient.data;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;

import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.logging.StacktraceLoggerWrapper;

public class NSMbg {
    private static final Logger log = StacktraceLoggerWrapper.getLogger(LTag.NSCLIENT);
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
            log.error("Data: " + json.toString());
        }
    }
}

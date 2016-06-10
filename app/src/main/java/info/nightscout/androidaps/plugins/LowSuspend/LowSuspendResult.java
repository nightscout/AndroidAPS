package info.nightscout.androidaps.plugins.LowSuspend;

import org.json.JSONException;
import org.json.JSONObject;

public class LowSuspendResult {
    public boolean lowProjected;
    public boolean low;
    public String reason;

    public int percent;

    public JSONObject json() {
        JSONObject json = new JSONObject();
        try {
            json.put("low", low);
            json.put("lowProjected", lowProjected);
            json.put("reason", reason);
            json.put("percent", percent);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return json;
    }
}

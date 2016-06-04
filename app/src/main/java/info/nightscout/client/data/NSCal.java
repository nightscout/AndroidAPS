package info.nightscout.client.data;

import org.json.JSONException;
import org.json.JSONObject;

public class NSCal {
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
            e.printStackTrace();
        }
    }
}

package info.nightscout.androidaps.plugins.OpenAPSAMA;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Created by mike on 06.01.2017.
 */
public class AutosensResult {
    public double ratio;
    public double carbsAbsorbed;
    public String sensResult;
    public String pastSensitivity;
    public String ratioLimit;

    public JSONObject json() {
        JSONObject ret = new JSONObject();
        try {
            ret.put("ratio", ratio);
            ret.put("ratioLimit", ratioLimit);
            ret.put("pastSensitivity", pastSensitivity);
            ret.put("sensResult", sensResult);
            ret.put("ratio", ratio);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return ret;
    }

}

package info.nightscout.androidaps.plugins.iob.iobCobCalculator;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.logging.L;

/**
 * Created by mike on 06.01.2017.
 */
public class AutosensResult {
    private static Logger log = LoggerFactory.getLogger(L.AUTOSENS);

    //default values to show when autosens algorithm is not called
    public double ratio = 1d;
    public double carbsAbsorbed = 0d;
    public String sensResult = "autosens not available";
    public String pastSensitivity = "";
    public String ratioLimit = "";

    public JSONObject json() {
        JSONObject ret = new JSONObject();
        try {
            ret.put("ratio", ratio);
            ret.put("ratioLimit", ratioLimit);
            ret.put("pastSensitivity", pastSensitivity);
            ret.put("sensResult", sensResult);
            ret.put("ratio", ratio);
        } catch (JSONException e) {
            log.error("Unhandled exception", e);
        }
        return ret;
    }

    @Override
    public String toString() {
        return json().toString();
    }
}

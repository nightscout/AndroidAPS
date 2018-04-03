package info.nightscout.utils;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JSonHelper is a Helper class which contains several methods to safely get data from the ggiven JSONObject.
 *
 * Created by triplem on 04.01.18.
 */

public class JsonHelper {

    private static final Logger log = LoggerFactory.getLogger(JsonHelper.class);

    private JsonHelper() {};

    public static String safeGetString(JSONObject json, String fieldName) throws JSONException {
        String result = null;

        if (json.has(fieldName)) {
            result = json.getString(fieldName);
        }

        return result;
    }

    public static String safeGetString(JSONObject json, String fieldName, String defaultValue) throws JSONException {
        String result = defaultValue;

        if (json.has(fieldName)) {
            result = json.getString(fieldName);
        }

        return result;
    }

    public static double safeGetDouble(JSONObject json, String fieldName) throws JSONException {
        double result = 0d;

        if (json.has(fieldName)) {
            result = json.getDouble(fieldName);
        }

        return result;
    }

    public static int safeGetInt(JSONObject json, String fieldName) throws JSONException {
        int result = 0;

        if (json.has(fieldName)) {
            result = json.getInt(fieldName);
        }

        return result;
    }
}

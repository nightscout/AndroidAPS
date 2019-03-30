package info.nightscout.androidaps.utils;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Created by mike on 12.03.2018.
 */

public class JsonHelperTest {

    String jsonString = "{\"d\":\"3.0\",\"i\":\"4\",\"s\":\"5\"}";

    @Test
    public void runTest() throws JSONException {
        JSONObject object = new JSONObject(jsonString);
        assertEquals(null, JsonHelper.safeGetString(object, "notexisting"));
        assertEquals("5", JsonHelper.safeGetString(object, "s"));
        assertEquals("default", JsonHelper.safeGetString(object, "notexisting", "default"));
        assertEquals("5", JsonHelper.safeGetString(object, "s", "default"));

        assertEquals(0.0d, JsonHelper.safeGetDouble(object, "notexisting"), 0.0d);
        assertEquals(3.0d, JsonHelper.safeGetDouble(object, "d"), 0.000001d);

        assertEquals(0, JsonHelper.safeGetInt(object, "notexisting"));
        assertEquals(4, JsonHelper.safeGetInt(object, "i"));
    }
}

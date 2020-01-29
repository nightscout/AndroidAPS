package info.nightscout.androidaps.utils;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Created by mike on 12.03.2018.
 */

public class JsonHelperTest {

    private String jsonString = "{\"d\":\"3.0\",\"i\":\"4\",\"s\":\"5\",\"b\":\"true\",\"j\":{\"a\": \"1\"}}";

    @Test
    public void safeGetObjectTest() throws JSONException {
        JSONObject object = new JSONObject(jsonString);
        Object o = new Object();
        assertEquals(o, JsonHelper.safeGetObject(null, "x", o));
        assertEquals(o, JsonHelper.safeGetObject(object, "x", o));
        assertNotEquals(o, JsonHelper.safeGetObject(object, "d", o));
    }

    @Test
    public void safeGetJSONObjectTest() throws JSONException {
        JSONObject object = new JSONObject(jsonString);
        JSONObject o = new JSONObject();
        assertEquals(o, JsonHelper.safeGetJSONObject(null, "x", o));
        assertTrue(JsonHelper.safeGetJSONObject(object, "j", o).has("a"));
        assertEquals(o, JsonHelper.safeGetJSONObject(object, "d", o));
    }

    @Test
    public void safeGetStringTest() throws JSONException {
        JSONObject object = new JSONObject(jsonString);
        Object o = new Object();
        assertNull(JsonHelper.safeGetString(null, "s"));
        assertNull(JsonHelper.safeGetString(object, "notexisting"));
        assertEquals("5", JsonHelper.safeGetString(object, "s"));

        assertEquals("default", JsonHelper.safeGetString(null, "notexisting", "default"));
        assertEquals("default", JsonHelper.safeGetString(object, "notexisting", "default"));
        assertEquals("5", JsonHelper.safeGetString(object, "s", "default"));

        assertEquals("default", JsonHelper.safeGetStringAllowNull(null, "notexisting", "default"));
        assertEquals("default", JsonHelper.safeGetStringAllowNull(object, "notexisting", "default"));
        assertNull(JsonHelper.safeGetStringAllowNull(object, "notexisting", null));
        assertEquals("5", JsonHelper.safeGetStringAllowNull(object, "s", "default"));
    }

    @Test
    public void safeGetDoubleTest() throws JSONException {
        JSONObject object = new JSONObject(jsonString);

        assertEquals(0.0d, JsonHelper.safeGetDouble(object, "notexisting"), 0.0d);
        assertEquals(0.0d, JsonHelper.safeGetDouble(null, "notexisting"), 0.0d);
        assertEquals(3.0d, JsonHelper.safeGetDouble(object, "d"), 0.000001d);

        assertEquals(6d, JsonHelper.safeGetDouble(null, "notexisting", 6d), 0.0d);
        assertEquals(6d, JsonHelper.safeGetDouble(object, "notexisting", 6d), 0.0d);
        assertEquals(3d, JsonHelper.safeGetDouble(object, "d", 6d), 0.0d);
    }

    @Test
    public void safeGetLntTest() throws JSONException {
        JSONObject object = new JSONObject(jsonString);
        assertEquals(0, JsonHelper.safeGetInt(null, "notexisting"));
        assertEquals(0, JsonHelper.safeGetInt(object, "notexisting"));
        assertEquals(4, JsonHelper.safeGetInt(object, "i"));
    }

    @Test
    public void safeGetLongTest() throws JSONException {
        JSONObject object = new JSONObject(jsonString);
        assertEquals(0, JsonHelper.safeGetInt(null, "notexisting"));
        assertEquals(0, JsonHelper.safeGetInt(object, "notexisting"));
        assertEquals(4, JsonHelper.safeGetInt(object, "i"));
    }

    @Test
    public void safeGetBooleanTest() throws JSONException {
        JSONObject object = new JSONObject(jsonString);
        assertFalse(JsonHelper.safeGetBoolean(null, "notexisting"));
        assertFalse(JsonHelper.safeGetBoolean(object, "notexisting"));
        assertTrue(JsonHelper.safeGetBoolean(object, "b"));
    }
}

package info.nightscout.core.utils

import info.nightscout.interfaces.utils.JsonHelper
import org.json.JSONObject
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

@Suppress("SpellCheckingInspection")
class JsonHelperTest {

    private val jsonString = "{\"d\":\"3.0\",\"i\":\"4\",\"s\":\"5\",\"b\":\"true\",\"j\":{\"a\": \"1\"}}"

    @Test
    fun safeGetObjectTest() {
        val json = JSONObject(jsonString)
        val o = Any()
        Assertions.assertEquals(o, JsonHelper.safeGetObject(null, "x", o))
        Assertions.assertEquals(o, JsonHelper.safeGetObject(json, "x", o))
        Assertions.assertNotEquals(o, JsonHelper.safeGetObject(json, "d", o))
    }

    @Test
    fun safeGetJSONObjectTest() {
        val json = JSONObject(jsonString)
        val o = JSONObject()
        Assertions.assertEquals(o, JsonHelper.safeGetJSONObject(null, "x", o))
        Assertions.assertTrue(JsonHelper.safeGetJSONObject(json, "j", o)!!.has("a"))
        Assertions.assertEquals(o, JsonHelper.safeGetJSONObject(json, "d", o))
    }

    @Test
    fun safeGetStringTest() {
        val json = JSONObject(jsonString)
        Assertions.assertNull(JsonHelper.safeGetString(null, "s"))
        Assertions.assertNull(JsonHelper.safeGetString(json, "notexisting"))
        Assertions.assertEquals("5", JsonHelper.safeGetString(json, "s"))
        Assertions.assertEquals("default", JsonHelper.safeGetString(null, "notexisting", "default"))
        Assertions.assertEquals("default", JsonHelper.safeGetString(json, "notexisting", "default"))
        Assertions.assertEquals("5", JsonHelper.safeGetString(json, "s", "default"))
        Assertions.assertEquals("default", JsonHelper.safeGetStringAllowNull(null, "notexisting", "default"))
        Assertions.assertEquals("default", JsonHelper.safeGetStringAllowNull(json, "notexisting", "default"))
        Assertions.assertNull(JsonHelper.safeGetStringAllowNull(json, "notexisting", null))
        Assertions.assertEquals("5", JsonHelper.safeGetStringAllowNull(json, "s", "default"))
    }

    @Test
    fun safeGetDoubleTest() {
        val json = JSONObject(jsonString)
        Assertions.assertEquals(0.0, JsonHelper.safeGetDouble(json, "notexisting"), 0.0)
        Assertions.assertEquals(0.0, JsonHelper.safeGetDouble(null, "notexisting"), 0.0)
        Assertions.assertEquals(3.0, JsonHelper.safeGetDouble(json, "d"), 0.000001)
        Assertions.assertEquals(6.0, JsonHelper.safeGetDouble(null, "notexisting", 6.0), 0.0)
        Assertions.assertEquals(6.0, JsonHelper.safeGetDouble(json, "notexisting", 6.0), 0.0)
        Assertions.assertEquals(3.0, JsonHelper.safeGetDouble(json, "d", 6.0), 0.0)
    }

    @Test
    fun safeGetLntTest() {
        val json = JSONObject(jsonString)
        Assertions.assertEquals(0, JsonHelper.safeGetInt(null, "notexisting").toLong())
        Assertions.assertEquals(0, JsonHelper.safeGetInt(json, "notexisting").toLong())
        Assertions.assertEquals(4, JsonHelper.safeGetInt(json, "i").toLong())
    }

    @Test
    fun safeGetLongTest() {
        val json = JSONObject(jsonString)
        Assertions.assertEquals(0, JsonHelper.safeGetInt(null, "notexisting").toLong())
        Assertions.assertEquals(0, JsonHelper.safeGetInt(json, "notexisting").toLong())
        Assertions.assertEquals(4, JsonHelper.safeGetInt(json, "i").toLong())
    }

    @Test
    fun safeGetBooleanTest() {
        val json = JSONObject(jsonString)
        Assertions.assertFalse(JsonHelper.safeGetBoolean(null, "notexisting"))
        Assertions.assertFalse(JsonHelper.safeGetBoolean(json, "notexisting"))
        Assertions.assertTrue(JsonHelper.safeGetBoolean(json, "b"))
    }
}
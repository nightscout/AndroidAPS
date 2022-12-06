package info.nightscout.androidaps.utils

import info.nightscout.interfaces.utils.JsonHelper
import org.json.JSONObject
import org.junit.Assert
import org.junit.jupiter.api.Test

@Suppress("SpellCheckingInspection")
class JsonHelperTest {

    private val jsonString = "{\"d\":\"3.0\",\"i\":\"4\",\"s\":\"5\",\"b\":\"true\",\"j\":{\"a\": \"1\"}}"

    @Test
    fun safeGetObjectTest() {
        val json = JSONObject(jsonString)
        val o = Any()
        Assert.assertEquals(o, JsonHelper.safeGetObject(null, "x", o))
        Assert.assertEquals(o, JsonHelper.safeGetObject(json, "x", o))
        Assert.assertNotEquals(o, JsonHelper.safeGetObject(json, "d", o))
    }

    @Test
    fun safeGetJSONObjectTest() {
        val json = JSONObject(jsonString)
        val o = JSONObject()
        Assert.assertEquals(o, JsonHelper.safeGetJSONObject(null, "x", o))
        Assert.assertTrue(JsonHelper.safeGetJSONObject(json, "j", o)!!.has("a"))
        Assert.assertEquals(o, JsonHelper.safeGetJSONObject(json, "d", o))
    }

    @Test
    fun safeGetStringTest() {
        val json = JSONObject(jsonString)
        Assert.assertNull(JsonHelper.safeGetString(null, "s"))
        Assert.assertNull(JsonHelper.safeGetString(json, "notexisting"))
        Assert.assertEquals("5", JsonHelper.safeGetString(json, "s"))
        Assert.assertEquals("default", JsonHelper.safeGetString(null, "notexisting", "default"))
        Assert.assertEquals("default", JsonHelper.safeGetString(json, "notexisting", "default"))
        Assert.assertEquals("5", JsonHelper.safeGetString(json, "s", "default"))
        Assert.assertEquals("default", JsonHelper.safeGetStringAllowNull(null, "notexisting", "default"))
        Assert.assertEquals("default", JsonHelper.safeGetStringAllowNull(json, "notexisting", "default"))
        Assert.assertNull(JsonHelper.safeGetStringAllowNull(json, "notexisting", null))
        Assert.assertEquals("5", JsonHelper.safeGetStringAllowNull(json, "s", "default"))
    }

    @Test
    fun safeGetDoubleTest() {
        val json = JSONObject(jsonString)
        Assert.assertEquals(0.0, JsonHelper.safeGetDouble(json, "notexisting"), 0.0)
        Assert.assertEquals(0.0, JsonHelper.safeGetDouble(null, "notexisting"), 0.0)
        Assert.assertEquals(3.0, JsonHelper.safeGetDouble(json, "d"), 0.000001)
        Assert.assertEquals(6.0, JsonHelper.safeGetDouble(null, "notexisting", 6.0), 0.0)
        Assert.assertEquals(6.0, JsonHelper.safeGetDouble(json, "notexisting", 6.0), 0.0)
        Assert.assertEquals(3.0, JsonHelper.safeGetDouble(json, "d", 6.0), 0.0)
    }

    @Test
    fun safeGetLntTest() {
        val json = JSONObject(jsonString)
        Assert.assertEquals(0, JsonHelper.safeGetInt(null, "notexisting").toLong())
        Assert.assertEquals(0, JsonHelper.safeGetInt(json, "notexisting").toLong())
        Assert.assertEquals(4, JsonHelper.safeGetInt(json, "i").toLong())
    }

    @Test
    fun safeGetLongTest() {
        val json = JSONObject(jsonString)
        Assert.assertEquals(0, JsonHelper.safeGetInt(null, "notexisting").toLong())
        Assert.assertEquals(0, JsonHelper.safeGetInt(json, "notexisting").toLong())
        Assert.assertEquals(4, JsonHelper.safeGetInt(json, "i").toLong())
    }

    @Test
    fun safeGetBooleanTest() {
        val json = JSONObject(jsonString)
        Assert.assertFalse(JsonHelper.safeGetBoolean(null, "notexisting"))
        Assert.assertFalse(JsonHelper.safeGetBoolean(json, "notexisting"))
        Assert.assertTrue(JsonHelper.safeGetBoolean(json, "b"))
    }
}
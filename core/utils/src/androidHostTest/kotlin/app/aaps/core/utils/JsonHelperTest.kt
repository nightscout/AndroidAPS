package app.aaps.core.utils

import com.google.common.truth.Truth.assertThat
import org.json.JSONObject
import org.junit.jupiter.api.Test

@Suppress("SpellCheckingInspection")
class JsonHelperTest {

    private val jsonString = "{\"d\":\"3.0\",\"i\":\"4\",\"s\":\"5\",\"b\":\"true\",\"j\":{\"a\": \"1\"}}"

    @Test
    fun safeGetObjectTest() {
        val json = JSONObject(jsonString)
        val o = Any()
        assertThat(JsonHelper.safeGetObject(null, "x", o)).isEqualTo(o)
        assertThat(JsonHelper.safeGetObject(json, "x", o)).isEqualTo(o)
        assertThat(JsonHelper.safeGetObject(json, "d", o)).isNotEqualTo(o)
    }

    @Test
    fun safeGetJSONObjectTest() {
        val json = JSONObject(jsonString)
        val o = JSONObject()
        assertThat(JsonHelper.safeGetJSONObject(null, "x", o)).isEqualTo(o)
        assertThat(JsonHelper.safeGetJSONObject(json, "j", o)!!.has("a")).isTrue()
        assertThat(JsonHelper.safeGetJSONObject(json, "d", o)).isEqualTo(o)
    }

    @Test
    fun safeGetStringTest() {
        val json = JSONObject(jsonString)
        assertThat(JsonHelper.safeGetString(null, "s")).isNull()
        assertThat(JsonHelper.safeGetString(json, "notexisting")).isNull()
        assertThat(JsonHelper.safeGetString(json, "s")).isEqualTo("5")
        assertThat(JsonHelper.safeGetString(null, "notexisting", "default")).isEqualTo("default")
        assertThat(JsonHelper.safeGetString(json, "notexisting", "default")).isEqualTo("default")
        assertThat(JsonHelper.safeGetString(json, "s", "default")).isEqualTo("5")
        assertThat(JsonHelper.safeGetStringAllowNull(null, "notexisting", "default")).isEqualTo("default")
        assertThat(JsonHelper.safeGetStringAllowNull(json, "notexisting", "default")).isEqualTo("default")
        assertThat(JsonHelper.safeGetStringAllowNull(json, "notexisting", null)).isNull()
        assertThat(JsonHelper.safeGetStringAllowNull(json, "s", "default")).isEqualTo("5")
    }

    @Test
    fun safeGetDoubleTest() {
        val json = JSONObject(jsonString)
        assertThat(JsonHelper.safeGetDouble(json, "notexisting")).isWithin(0.0).of(0.0)
        assertThat(JsonHelper.safeGetDouble(null, "notexisting")).isWithin(0.0).of(0.0)
        assertThat(JsonHelper.safeGetDouble(json, "d")).isWithin(0.000001).of(3.0)
        assertThat(JsonHelper.safeGetDouble(null, "notexisting", 6.0)).isWithin(0.0).of(6.0)
        assertThat(JsonHelper.safeGetDouble(json, "notexisting", 6.0)).isWithin(0.0).of(6.0)
        assertThat(JsonHelper.safeGetDouble(json, "d", 6.0)).isWithin(0.0).of(3.0)
    }

    @Test
    fun safeGetLntTest() {
        val json = JSONObject(jsonString)
        assertThat(JsonHelper.safeGetInt(null, "notexisting").toLong()).isEqualTo(0)
        assertThat(JsonHelper.safeGetInt(json, "notexisting").toLong()).isEqualTo(0)
        assertThat(JsonHelper.safeGetInt(json, "i").toLong()).isEqualTo(4)
    }

    @Test
    fun safeGetLongTest() {
        val json = JSONObject(jsonString)
        assertThat(JsonHelper.safeGetInt(null, "notexisting").toLong()).isEqualTo(0)
        assertThat(JsonHelper.safeGetInt(json, "notexisting").toLong()).isEqualTo(0)
        assertThat(JsonHelper.safeGetInt(json, "i").toLong()).isEqualTo(4)
    }

    @Test
    fun safeGetBooleanTest() {
        val json = JSONObject(jsonString)
        assertThat(JsonHelper.safeGetBoolean(null, "notexisting")).isFalse()
        assertThat(JsonHelper.safeGetBoolean(json, "notexisting")).isFalse()
        assertThat(JsonHelper.safeGetBoolean(json, "b")).isTrue()
    }

    @Test
    fun mergeTest() {
        val json1 = JSONObject().also {
            it.put("a", 1)
            it.put("b", 2)
        }
        val json2 = JSONObject().also {
            it.put("b", 3)
        }
        val merged = JsonHelper.merge(json1, json2)
        assertThat(merged.getInt("a")).isEqualTo(1)
        assertThat(merged.getInt("b")).isEqualTo(3)
    }
}

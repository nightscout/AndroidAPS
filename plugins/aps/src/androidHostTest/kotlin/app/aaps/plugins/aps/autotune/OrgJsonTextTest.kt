package app.aaps.plugins.aps.autotune

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.json.JSONArray
import org.json.JSONObject
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * [OrgJsonText] against the `org.json` it replaces, which is the Android one here.
 *
 * Objects have one key each: the `org.json` of the JVM tests keeps keys in a hash map, so the order
 * of two keys would say nothing about the phone.
 */
class OrgJsonTextTest {

    private val doubles = listOf(
        0.0, -0.0, 1.0, -1.0, 8.0, 10.0, 120.0, 0.5, 0.725, -0.25, 1.005, 0.1 + 0.2, 1.0E-7, 1.0E-4, 0.001, 123456.789,
        1.0E7, 1.0E15, 1.0E16, 1.0E19, 1.0E20, 9.223372036854775807E18, 1653099300000.0, 2.0 / 3.0, -1.25 * 3, Double.MIN_VALUE, Double.MAX_VALUE
    )
    private val longs = listOf(0L, -1L, 42L, 1653099300000L, Long.MAX_VALUE, Long.MIN_VALUE)
    private val strings = listOf("", "plain", "a/b", "openaps://AndroidAPS", "quote\"back\\slash", "tab\tnew\nline\rret", "\b\u000C", "\u0001\u001F\u007F", "ěščř ✓ 漢", " ")

    @Test
    fun `numbers are written as org json writes them`() {
        for (value in doubles) assertThat(OrgJsonText.render(JsonArray(listOf(JsonPrimitive(value))))).isEqualTo(JSONArray().put(value).toString())
        for (value in longs) assertThat(OrgJsonText.render(JsonArray(listOf(JsonPrimitive(value))))).isEqualTo(JSONArray().put(value).toString())
        for (value in listOf(0, 7, -3, Int.MAX_VALUE)) assertThat(OrgJsonText.render(JsonArray(listOf(JsonPrimitive(value))))).isEqualTo(JSONArray().put(value).toString())
    }

    @Test
    fun `text and other values are written as org json writes them`() {
        for (value in strings) assertThat(OrgJsonText.render(JsonArray(listOf(JsonPrimitive(value))))).isEqualTo(JSONArray().put(value).toString())
        assertThat(OrgJsonText.render(buildJsonArray { add(JsonPrimitive(true)); add(JsonPrimitive(false)); add(JsonNull) }))
            .isEqualTo(JSONArray().put(true).put(false).put(JSONObject.NULL).toString())
        assertThat(OrgJsonText.render(buildJsonObject { put("a/b", "c/d") })).isEqualTo(JSONObject().put("a/b", "c/d").toString())
    }

    @Test
    fun `the layout matches at each indent`() {
        val kotlinx = buildJsonObject {
            put("list", buildJsonArray {
                add(buildJsonObject { put("rate", 0.725) })
                add(buildJsonObject { put("empty", JsonArray(emptyList())) })
                add(buildJsonObject { put("none", JsonObject(emptyMap())) })
                add(buildJsonArray { add(JsonPrimitive(1)); add(JsonPrimitive(2.0)) })
                add(JsonPrimitive("x"))
            })
        }
        val org = JSONObject().put(
            "list", JSONArray()
                .put(JSONObject().put("rate", 0.725))
                .put(JSONObject().put("empty", JSONArray()))
                .put(JSONObject().put("none", JSONObject()))
                .put(JSONArray().put(1).put(2.0))
                .put("x")
        )
        assertThat(OrgJsonText.render(kotlinx)).isEqualTo(org.toString())
        for (indent in listOf(1, 2, 4)) assertThat(OrgJsonText.render(kotlinx, indent)).isEqualTo(org.toString(indent))
        assertThat(OrgJsonText.render(JsonArray(emptyList()), 2)).isEqualTo(JSONArray().toString(2))
        assertThat(OrgJsonText.render(JsonObject(emptyMap()), 2)).isEqualTo(JSONObject().toString(2))
    }

    @Test
    fun `the keys keep the order they were put in`() {
        assertThat(OrgJsonText.render(buildJsonObject { put("z", 1); put("a", 2); put("m", 3) })).isEqualTo("""{"z":1,"a":2,"m":3}""")
    }

    @Test
    fun `a number JSON cannot hold is refused`() {
        for (value in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            assertThrows<NonFiniteNumberException> { OrgJsonText.render(JsonArray(listOf(JsonPrimitive(value)))) }
            assertThrows<NonFiniteNumberException> { finite(value) }
        }
        assertThat(finite(1.5)).isEqualTo(1.5)
    }
}

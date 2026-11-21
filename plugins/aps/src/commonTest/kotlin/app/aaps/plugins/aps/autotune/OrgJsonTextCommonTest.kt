package app.aaps.plugins.aps.autotune

import app.aaps.plugins.aps.autotune.compose.roundHalfUpAsText
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * The text of [OrgJsonText] and the rounding of [roundHalfUpAsText] on every target.
 *
 * `OrgJsonTextTest` and `RoundHalfUpAsTextTest` compare them with the JVM originals, but only on the
 * JVM. These fixed answers, taken from those originals, also run on iOS, where a double is turned
 * into text by Kotlin/Native and not by the JVM.
 */
class OrgJsonTextCommonTest {

    private fun one(value: Double) = OrgJsonText.render(JsonArray(listOf(JsonPrimitive(value))))

    @Test
    fun numbers() {
        assertEquals("[8]", one(8.0))
        assertEquals("[-0]", one(-0.0))
        assertEquals("[0.725]", one(0.725))
        assertEquals("[-0.25]", one(-0.25))
        assertEquals("[0.30000000000000004]", one(0.1 + 0.2))
        assertEquals("[1.0E-7]", one(1.0E-7))
        assertEquals("[10000000]", one(1.0E7))
        assertEquals("[1.0E20]", one(1.0E20))
        assertEquals("[1653099300000]", one(1653099300000.0))
        assertEquals("[0.6666666666666666]", one(2.0 / 3.0))
        assertEquals("[1653099300000]", OrgJsonText.render(JsonArray(listOf(JsonPrimitive(1653099300000L)))))
    }

    @Test
    fun text() {
        assertEquals("""["a\/b"]""", OrgJsonText.render(JsonArray(listOf(JsonPrimitive("a/b")))))
        assertEquals("""["q\"b\\s\t\n\u0001"]""", OrgJsonText.render(JsonArray(listOf(JsonPrimitive("q\"b\\s\t\n\u0001")))))
    }

    @Test
    fun layout() {
        val json = buildJsonObject {
            put("z", 1)
            put("list", buildJsonArray {
                add(buildJsonObject { put("rate", 1.0) })
                add(JsonArray(emptyList()))
                add(JsonObject(emptyMap()))
            })
        }
        assertEquals("""{"z":1,"list":[{"rate":1},[],{}]}""", OrgJsonText.render(json))
        assertEquals("{\n  \"z\": 1,\n  \"list\": [\n    {\n      \"rate\": 1\n    },\n    [],\n    {}\n  ]\n}", OrgJsonText.render(json, 2))
    }

    @Test
    fun nonFiniteNumbersAreRefused() {
        assertFailsWith<NonFiniteNumberException> { one(Double.NaN) }
        assertFailsWith<NonFiniteNumberException> { finite(Double.POSITIVE_INFINITY) }
    }

    @Test
    fun roundingTheShortestText() {
        assertEquals(1.01, roundHalfUpAsText(1.005, 2))
        assertEquals(0.2, roundHalfUpAsText(0.15, 1))
        assertEquals(2.68, roundHalfUpAsText(2.675, 2))
        assertEquals(10.0, roundHalfUpAsText(9.995, 2))
        assertEquals(0.001, roundHalfUpAsText(0.0005, 3))
        assertEquals(0.0, roundHalfUpAsText(0.00049, 3))
        assertEquals(1.0, roundHalfUpAsText(0.6, 0))
        assertEquals(16.951, roundHalfUpAsText(16.951, 3))
    }
}

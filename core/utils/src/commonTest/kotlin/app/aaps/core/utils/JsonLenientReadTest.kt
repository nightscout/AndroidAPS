package app.aaps.core.utils

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins the lenient readers on every target, including Kotlin/Native.
 *
 * These exist because `org.json` coerces on read and kotlinx does not, and the difference is not
 * cosmetic: a stored `"36"` - a quoted number, which real Nightscout documents contain - read back as
 * the default instead of 36 is what turns `ICfg.insulinEndTime` into 0, i.e. DIA 0. So the quoted
 * cases below are the point of the whole file, not edge cases.
 */
class JsonLenientReadTest {

    private fun json(text: String): JsonObject = Json.parseToJsonElement(text) as JsonObject

    // ---- the quoted-number hazard -------------------------------------------------------------

    @Test
    fun `a quoted number reads as a number`() {
        val o = json("""{"i":"36","l":"1800000","d":"3.5"}""")
        assertEquals(36, o.lenientInt("i"))
        assertEquals(1800000L, o.lenientLong("l"))
        assertEquals(3.5, o.lenientDouble("d"))
    }

    @Test
    fun `an unquoted number reads as a number`() {
        val o = json("""{"i":36,"l":1800000,"d":3.5}""")
        assertEquals(36, o.lenientInt("i"))
        assertEquals(1800000L, o.lenientLong("l"))
        assertEquals(3.5, o.lenientDouble("d"))
    }

    @Test
    fun `a fractional or exponent form still reads as a whole number`() {
        // The DIA field has been seen in both of these shapes.
        val o = json("""{"a":"1.8E7","b":18000000.5,"c":"18000000.5"}""")
        assertEquals(18000000L, o.lenientLong("a"))
        assertEquals(18000000L, o.lenientLong("b"))
        assertEquals(18000000L, o.lenientLong("c"))
    }

    // ---- absent and null ------------------------------------------------------------------------

    @Test
    fun `a missing key gives the default`() {
        val o = json("""{"other":1}""")
        assertEquals(7, o.lenientInt("missing", 7))
        assertEquals(7L, o.lenientLong("missing", 7L))
        assertEquals(7.5, o.lenientDouble("missing", 7.5))
        assertEquals("d", o.lenientString("missing", "d"))
        assertTrue(o.lenientBoolean("missing", true))
    }

    @Test
    fun `an explicit JSON null gives the default rather than the text null`() {
        val o = json("""{"x":null}""")
        assertEquals(7, o.lenientInt("x", 7))
        assertEquals("d", o.lenientString("x", "d"))
        assertNull(o.lenientStringOrNull("x"))
        assertNull(o.lenientLongOrNull("x"))
    }

    @Test
    fun `a null receiver gives the default`() {
        val o: JsonObject? = null
        assertEquals(7, o.lenientInt("x", 7))
        assertEquals("d", o.lenientString("x", "d"))
        assertNull(o.lenientStringOrNull("x"))
    }

    // ---- unreadable values ----------------------------------------------------------------------

    @Test
    fun `text that is not a number gives the default`() {
        val o = json("""{"x":"abc"}""")
        assertEquals(7, o.lenientInt("x", 7))
        assertEquals(7L, o.lenientLong("x", 7L))
        assertEquals(7.5, o.lenientDouble("x", 7.5))
        assertNull(o.lenientIntOrNull("x"))
    }

    @Test
    fun `a boolean is not a number`() {
        // org.json throws for this, so the default is the matching answer.
        val o = json("""{"x":true}""")
        assertEquals(7, o.lenientInt("x", 7))
        assertEquals(7.5, o.lenientDouble("x", 7.5))
    }

    // ---- booleans and strings -------------------------------------------------------------------

    @Test
    fun `a boolean reads quoted or unquoted and in any case`() {
        val o = json("""{"a":true,"b":"true","c":"TRUE","d":false,"e":"False"}""")
        assertTrue(o.lenientBoolean("a", false))
        assertTrue(o.lenientBoolean("b", false))
        assertTrue(o.lenientBoolean("c", false))
        assertTrue(!o.lenientBoolean("d", true))
        assertTrue(!o.lenientBoolean("e", true))
    }

    @Test
    fun `a number is not a boolean`() {
        val o = json("""{"x":1}""")
        assertTrue(o.lenientBoolean("x", true))
        assertTrue(!o.lenientBoolean("x", false))
    }

    @Test
    fun `any value reads back as its text`() {
        val o = json("""{"n":12,"b":true,"s":"already"}""")
        assertEquals("12", o.lenientString("n"))
        assertEquals("true", o.lenientString("b"))
        assertEquals("already", o.lenientString("s"))
    }

    @Test
    fun `holdsQuotedString separates a quoted number from a real one`() {
        val o = json("""{"quoted":"36","plain":36,"missing2":null}""")
        assertTrue(o.holdsQuotedString("quoted"))
        assertTrue(!o.holdsQuotedString("plain"))
        assertTrue(!o.holdsQuotedString("missing2"))
        assertTrue(!o.holdsQuotedString("absent"))
    }

    // ---- nullable variants ----------------------------------------------------------------------

    @Test
    fun `the nullable variants tell absent from present and zero`() {
        val o = json("""{"zero":0}""")
        assertEquals(0, o.lenientIntOrNull("zero"))
        assertEquals(0L, o.lenientLongOrNull("zero"))
        assertEquals(0.0, o.lenientDoubleOrNull("zero"))
        assertNull(o.lenientIntOrNull("absent"))
        assertNull(o.lenientLongOrNull("absent"))
        assertNull(o.lenientDoubleOrNull("absent"))
    }
}

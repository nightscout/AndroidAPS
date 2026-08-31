package app.aaps.plugins.sync.nsclientV3.ws

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Reading the Nightscout websocket frames.
 *
 * This is the half of the iOS connection that has to agree with the wire, and where a mistake is
 * silent: a field read as null looks exactly like a field the server never sent. The frames below
 * are the shapes the Android handlers document.
 */
class NsWsPayloadTest {

    private fun doc(json: String) = Json.parseToJsonElement(json) as JsonObject

    // ---- frames ---------------------------------------------------------------------------------

    @Test
    fun `a create frame yields its collection and document`() {
        val frame = doc("""{"colName":"entries","doc":{"srvModified":1700000000000,"sgv":120}}""")

        assertEquals("entries", NsWsPayload.string(frame, "colName"))
        assertEquals(1_700_000_000_000L, NsWsPayload.long(NsWsPayload.document(frame)!!, "srvModified"))
    }

    @Test
    fun `a delete frame yields its identifier`() {
        val frame = doc("""{"colName":"treatments","identifier":"abc123"}""")

        assertEquals("treatments", NsWsPayload.string(frame, "colName"))
        assertEquals("abc123", NsWsPayload.string(frame, "identifier"))
    }

    @Test
    fun `a frame with no document reads as none`() {
        assertNull(NsWsPayload.document(doc("""{"colName":"entries"}""")))
    }

    // ---- malformed input ------------------------------------------------------------------------

    /** A bad frame is dropped, not thrown: one unreadable message must not cost every later one. */
    @Test
    fun `text that is not json is dropped`() {
        assertNull(NsWsPayload.parse("not json at all"))
        assertNull(NsWsPayload.parse(""))
        assertNull(NsWsPayload.parse("{unclosed"))
    }

    @Test
    fun `json that is not an object is dropped`() {
        assertNull(NsWsPayload.parse("[1,2,3]"))
        assertNull(NsWsPayload.parse("\"just a string\""))
    }

    @Test
    fun `a missing field reads as null rather than throwing`() {
        val frame = doc("{}")

        assertNull(NsWsPayload.string(frame, "colName"))
        assertNull(NsWsPayload.long(frame, "srvModified"))
        assertNull(NsWsPayload.boolean(frame, "success"))
    }

    // ---- srvModified, which decides what the next load asks for ---------------------------------

    /**
     * The reason [NsWsPayload.long] accepts a decimal.
     *
     * `srvModified` moves the high-water mark. Read as null it stays put, and the next load asks for
     * the same window again — forever. A server or proxy that renders the timestamp as a double must
     * not cause that.
     */
    @Test
    fun `a timestamp written as a decimal still reads`() {
        assertEquals(1_700_000_000_000L, NsWsPayload.long(doc("""{"srvModified":1700000000000.0}"""), "srvModified"))
    }

    @Test
    fun `a timestamp written as a string still reads`() {
        assertEquals(1_700_000_000_000L, NsWsPayload.long(doc("""{"srvModified":"1700000000000"}"""), "srvModified"))
    }

    @Test
    fun `a timestamp that is not a number reads as null`() {
        assertNull(NsWsPayload.long(doc("""{"srvModified":"whenever"}"""), "srvModified"))
    }

    // ---- the subscribe acknowledgement ----------------------------------------------------------

    @Test
    fun `a successful subscribe is recognised`() {
        val ack = doc("""{"success":true,"collections":"entries treatments"}""")

        assertTrue(NsWsPayload.boolean(ack, "success") == true)
        assertEquals("entries treatments", NsWsPayload.string(ack, "collections"))
    }

    @Test
    fun `a failed subscribe is not mistaken for success`() {
        assertEquals(false, NsWsPayload.boolean(doc("""{"success":false}"""), "success"))
        // Absent is not false, and the caller treats only an explicit true as connected.
        assertNull(NsWsPayload.boolean(doc("""{"message":"nope"}"""), "success"))
    }
}

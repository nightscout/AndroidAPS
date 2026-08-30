package app.aaps.plugins.sync.nsclientV3.ws

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Reads real Nightscout alarm documents.
 *
 * The payloads below are the ones documented in the Android handler, kept verbatim so the two
 * readers are checked against the same wire format rather than against each other.
 */
class KotlinxNsAlarmTest {

    private fun alarm(json: String) = KotlinxNsAlarm(Json.parseToJsonElement(json) as JsonObject)

    @Test
    fun `reads an urgent high alarm`() {
        val a = alarm(
            """{"level":2,"title":"Urgent HIGH","message":"BG Now: 5.2","eventName":"high",
               "group":"default","key":"simplealarms_2"}"""
        )

        assertEquals(2, a.level)
        assertEquals("Urgent HIGH", a.title)
        assertEquals("BG Now: 5.2", a.message)
        assertEquals("default", a.group)
        assertTrue(a.high)
        assertFalse(a.low)
        assertFalse(a.timeAgo)
    }

    @Test
    fun `reads a low alarm`() {
        val a = alarm("""{"level":1,"title":"Warning LOW","eventName":"low","group":"default"}""")

        assertTrue(a.low)
        assertFalse(a.high)
    }

    @Test
    fun `reads a time ago alarm`() {
        assertTrue(alarm("""{"level":1,"eventName":"timeago"}""").timeAgo)
    }

    @Test
    fun `reads an announcement`() {
        val a = alarm("""{"level":0,"title":"Announcement","message":"test","group":"Announcement","isAnnouncement":true}""")

        assertEquals(0, a.level)
        assertEquals("Announcement", a.group)
    }

    /**
     * Missing fields fall back rather than throwing.
     *
     * An alarm document that failed to parse would be an alarm that never sounds, which is worse
     * than one shown with a placeholder.
     */
    @Test
    fun `a document with nothing in it still yields an alarm`() {
        val a = alarm("{}")

        assertEquals(0, a.level)
        assertEquals("N/A", a.title)
        assertEquals("N/A", a.message)
        assertEquals("N/A", a.group)
        assertFalse(a.high)
        assertFalse(a.low)
    }
}

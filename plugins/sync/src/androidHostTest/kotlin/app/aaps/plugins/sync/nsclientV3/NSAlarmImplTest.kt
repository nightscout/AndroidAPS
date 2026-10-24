package app.aaps.plugins.sync.nsclientV3

import app.aaps.plugins.sync.nsclientV3.json.JsonBridge.toKotlinxJson
import app.aaps.shared.tests.TestBase
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NSAlarmImplTest : TestBase() {

    @Test
    fun `parses high alarm correctly`() {
        // Arrange
        val json = JSONObject().apply {
            put("level", 2)
            put("title", "Urgent HIGH")
            put("message", "BG Now: 15.2 mmol/L")
            put("eventName", "high")
            put("group", "default")
        }

        // Act
        val alarm = NSAlarmObject(json.toKotlinxJson())

        // Assert
        assertEquals(2, alarm.level)
        assertEquals("Urgent HIGH", alarm.title)
        assertEquals("BG Now: 15.2 mmol/L", alarm.message)
        assertEquals("default", alarm.group)
        assertTrue(alarm.high)
        assertFalse(alarm.low)
        assertFalse(alarm.timeAgo)
    }

    @Test
    fun `parses low alarm correctly`() {
        // Arrange
        val json = JSONObject().apply {
            put("level", 1)
            put("title", "Urgent LOW")
            put("eventName", "low")
            put("group", "custom_group")
        }

        // Act
        val alarm = NSAlarmObject(json.toKotlinxJson())

        // Assert
        assertEquals(1, alarm.level)
        assertEquals("Urgent LOW", alarm.title)
        assertEquals("custom_group", alarm.group)
        assertTrue(alarm.low)
        assertFalse(alarm.high)
        assertFalse(alarm.timeAgo)
    }

    @Test
    fun `parses timeago alarm correctly`() {
        // Arrange
        val json = JSONObject().apply {
            put("level", 0)
            put("title", "Data Missing")
            put("eventName", "timeago")
        }

        // Act
        val alarm = NSAlarmObject(json.toKotlinxJson())

        // Assert
        assertEquals(0, alarm.level)
        assertEquals("Data Missing", alarm.title)
        assertTrue(alarm.timeAgo)
        assertFalse(alarm.low)
        assertFalse(alarm.high)
    }

    @Test
    fun `returns default values for missing or malformed fields`() {
        // Arrange
        val json = JSONObject().apply {
            // "level" is a number, provide string to test safety
            put("level", "not-a-number")
            // "eventName" is missing
            // "group" is missing
            // "title" is missing
            put("message", JSONObject.NULL) // Test handling of JSON null
        }

        // Act
        val alarm = NSAlarmObject(json.toKotlinxJson())

        // Assert
        assertEquals(0, alarm.level, "Level should default to 0 for non-integer value")
        assertEquals("N/A", alarm.group, "Group should default to N/A")
        assertEquals("N/A", alarm.title, "Title should default to N/A")
        assertEquals("N/A", alarm.message, "Message should default to N/A for null value")
        assertFalse(alarm.high, "High should be false when eventName is missing")
        assertFalse(alarm.low, "Low should be false when eventName is missing")
        assertFalse(alarm.timeAgo, "TimeAgo should be false when eventName is missing")
    }

    // The four below pin coercion rather than defaults. Nothing asserted it before, and it is exactly
    // what a stricter parser would change: these inputs would throw instead of being read or defaulted.
    // A Nightscout server is not under our control, so an alarm must never fail to fire over a type.

    @Test
    fun `reads a numeric level sent as a string`() {
        val json = JSONObject().apply { put("level", "2") }

        assertEquals(2, NSAlarmObject(json.toKotlinxJson()).level)
    }

    @Test
    fun `truncates a level sent as a decimal`() {
        val json = JSONObject().apply { put("level", 2.9) }

        assertEquals(2, NSAlarmObject(json.toKotlinxJson()).level)
    }

    @Test
    fun `reads a title sent as a number`() {
        val json = JSONObject().apply { put("title", 5) }

        assertEquals("5", NSAlarmObject(json.toKotlinxJson()).title)
    }

    @Test
    fun `renders an object valued string field as its json text and defaults a numeric one`() {
        // Not what you would guess: a string field holding an object is NOT defaulted, it is rendered
        // as the object's json text. A numeric field holding one does fall back. Documented because it
        // is current behaviour that a port must keep, not because either is desirable on screen.
        val json = JSONObject().apply {
            put("title", JSONObject().apply { put("nested", "value") })
            put("level", JSONObject())
        }

        assertEquals("{\"nested\":\"value\"}", NSAlarmObject(json.toKotlinxJson()).title)
        assertEquals(0, NSAlarmObject(json.toKotlinxJson()).level)
    }

    @Test
    fun `handles completely empty JSON object`() {
        // Arrange
        val emptyJson = JSONObject()

        // Act
        val alarm = NSAlarmObject(emptyJson.toKotlinxJson())

        // Assert
        assertEquals(0, alarm.level)
        assertEquals("N/A", alarm.group)
        assertEquals("N/A", alarm.title)
        assertEquals("N/A", alarm.message)
        assertFalse(alarm.high)
        assertFalse(alarm.low)
        assertFalse(alarm.timeAgo)
    }
}
package app.aaps.plugins.sync.nsShared

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
        val alarm = NSAlarmObject(json)

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
        val alarm = NSAlarmObject(json)

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
        val alarm = NSAlarmObject(json)

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
        val alarm = NSAlarmObject(json)

        // Assert
        assertEquals(0, alarm.level, "Level should default to 0 for non-integer value")
        assertEquals("N/A", alarm.group, "Group should default to N/A")
        assertEquals("N/A", alarm.title, "Title should default to N/A")
        assertEquals("N/A", alarm.message, "Message should default to N/A for null value")
        assertFalse(alarm.high, "High should be false when eventName is missing")
        assertFalse(alarm.low, "Low should be false when eventName is missing")
        assertFalse(alarm.timeAgo, "TimeAgo should be false when eventName is missing")
    }

    @Test
    fun `handles completely empty JSON object`() {
        // Arrange
        val emptyJson = JSONObject()

        // Act
        val alarm = NSAlarmObject(emptyJson)

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

package app.aaps.pump.equil.events

import app.aaps.shared.tests.TestBase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class EventEquilAlarmTest : TestBase() {

    @Test
    fun `constructor should set tips`() {
        val event = EventEquilAlarm("Low battery")
        assertEquals("Low battery", event.tips)
    }

    @Test
    fun `tips should be settable`() {
        val event = EventEquilAlarm("Initial tip")
        event.tips = "Updated tip"
        assertEquals("Updated tip", event.tips)
    }

    @Test
    fun `tips should handle empty string`() {
        val event = EventEquilAlarm("")
        assertEquals("", event.tips)
    }

    @Test
    fun `tips should handle various alarm messages`() {
        val messages = listOf(
            "Low insulin",
            "Occlusion detected",
            "Battery low",
            "Pump error",
            "Replace cartridge",
            "Communication error"
        )

        messages.forEach { message ->
            val event = EventEquilAlarm(message)
            assertEquals(message, event.tips)
        }
    }

    @Test
    fun `tips should handle long messages`() {
        val longMessage = "This is a very long alarm message that contains detailed information about the pump status"
        val event = EventEquilAlarm(longMessage)
        assertEquals(longMessage, event.tips)
    }

    @Test
    fun `tips should handle special characters`() {
        val specialMessage = "Alarm! @#\$%^&*() Error: 123"
        val event = EventEquilAlarm(specialMessage)
        assertEquals(specialMessage, event.tips)
    }

    @Test
    fun `multiple instances should be independent`() {
        val event1 = EventEquilAlarm("Alarm 1")
        val event2 = EventEquilAlarm("Alarm 2")

        assertEquals("Alarm 1", event1.tips)
        assertEquals("Alarm 2", event2.tips)

        event1.tips = "Modified 1"
        assertEquals("Modified 1", event1.tips)
        assertEquals("Alarm 2", event2.tips)
    }

    @Test
    fun `tips should be mutable`() {
        val event = EventEquilAlarm("Initial")
        assertEquals("Initial", event.tips)

        event.tips = "First update"
        assertEquals("First update", event.tips)

        event.tips = "Second update"
        assertEquals("Second update", event.tips)
    }
}

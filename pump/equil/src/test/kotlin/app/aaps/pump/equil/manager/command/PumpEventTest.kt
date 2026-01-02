package app.aaps.pump.equil.manager.command

import app.aaps.pump.equil.R
import app.aaps.shared.tests.TestBaseWithProfile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever

class PumpEventTest : TestBaseWithProfile() {

    @BeforeEach
    fun setUp() {

        // Mock all resource strings
        whenever(rh.gs(R.string.equil_history_item1)).thenReturn("Item 1")
        whenever(rh.gs(R.string.equil_history_item2)).thenReturn("Item 2")
        whenever(rh.gs(R.string.equil_history_item3)).thenReturn("Item 3")
        whenever(rh.gs(R.string.equil_history_item4)).thenReturn("Item 4")
        whenever(rh.gs(R.string.equil_history_item5)).thenReturn("Item 5")
        whenever(rh.gs(R.string.equil_history_item6)).thenReturn("Item 6")
        whenever(rh.gs(R.string.equil_history_item7)).thenReturn("Item 7")
        whenever(rh.gs(R.string.equil_history_item8)).thenReturn("Item 8")
        whenever(rh.gs(R.string.equil_history_item9)).thenReturn("Item 9")
        whenever(rh.gs(R.string.equil_history_item10)).thenReturn("Item 10")
        whenever(rh.gs(R.string.equil_history_item11)).thenReturn("Item 11")
        whenever(rh.gs(R.string.equil_history_item12)).thenReturn("Item 12")
        whenever(rh.gs(R.string.equil_history_item13)).thenReturn("Item 13")
        whenever(rh.gs(R.string.equil_history_item14)).thenReturn("Item 14")
        whenever(rh.gs(R.string.equil_history_item15)).thenReturn("Item 15")
        whenever(rh.gs(R.string.equil_history_item16)).thenReturn("Item 16")
        whenever(rh.gs(R.string.equil_history_item17)).thenReturn("Item 17")
        whenever(rh.gs(R.string.equil_history_item18)).thenReturn("Item 18")
    }

    @Test
    fun `constructor should set all properties`() {
        val event = PumpEvent(4, 2, 1, "Test comment")
        assertEquals("Test comment", event.comment)
    }

    @Test
    fun `equals should return true for events with same port type level`() {
        val event1 = PumpEvent(4, 2, 1, "Comment 1")
        val event2 = PumpEvent(4, 2, 1, "Comment 2")

        assertEquals(event1, event2)
    }

    @Test
    fun `equals should ignore comment in comparison`() {
        val event1 = PumpEvent(4, 2, 1, "Different comment")
        val event2 = PumpEvent(4, 2, 1, "Another comment")

        assertEquals(event1, event2)
    }

    @Test
    fun `equals should return false for different port`() {
        val event1 = PumpEvent(4, 2, 1, "Comment")
        val event2 = PumpEvent(5, 2, 1, "Comment")

        assertNotEquals(event1, event2)
    }

    @Test
    fun `equals should return false for different type`() {
        val event1 = PumpEvent(4, 2, 1, "Comment")
        val event2 = PumpEvent(4, 3, 1, "Comment")

        assertNotEquals(event1, event2)
    }

    @Test
    fun `equals should return false for different level`() {
        val event1 = PumpEvent(4, 2, 1, "Comment")
        val event2 = PumpEvent(4, 2, 2, "Comment")

        assertNotEquals(event1, event2)
    }

    @Test
    fun `hashCode should be consistent with equals`() {
        val event1 = PumpEvent(4, 2, 1, "Comment 1")
        val event2 = PumpEvent(4, 2, 1, "Comment 2")

        assertEquals(event1.hashCode(), event2.hashCode())
    }

    @Test
    fun `hashCode should differ for different events`() {
        val event1 = PumpEvent(4, 2, 1, "Comment")
        val event2 = PumpEvent(4, 2, 2, "Comment")

        assertNotEquals(event1.hashCode(), event2.hashCode())
    }

    @Test
    fun `init should populate lists with correct number of events`() {
        PumpEvent.init(rh)

        // Should have 19 events (1 default event + 18 resource string events)
        assertEquals(19, PumpEvent.lists.size)
    }

    @Test
    fun `init should create events with correct port values`() {
        PumpEvent.init(rh)

        // Count port 4 events
        val port4Count = PumpEvent.lists.count {
            it.equals(PumpEvent(4, 0, 0, "")) ||
                it.equals(PumpEvent(4, 1, 0, "")) ||
                it.equals(PumpEvent(4, 2, 0, ""))
        }
        // Most events should be port 4
        assert(port4Count > 0)
    }

    @Test
    fun `getTips should return correct tip for known event`() {
        PumpEvent.init(rh)

        val tip1 = PumpEvent.getTips(4, 1, 1)
        assertEquals("Item 1", tip1)

        val tip2 = PumpEvent.getTips(4, 1, 2)
        assertEquals("Item 2", tip2)

        val tip3 = PumpEvent.getTips(4, 2, 2)
        assertEquals("Item 3", tip3)
    }

    @Test
    fun `getTips should return empty string for unknown event`() {
        PumpEvent.init(rh)

        val tip = PumpEvent.getTips(99, 99, 99)
        assertEquals("", tip)
    }

    @Test
    fun `getTips should handle various port type level combinations`() {
        PumpEvent.init(rh)

        // Test a few specific combinations
        assertEquals("Item 4", PumpEvent.getTips(4, 3, 0))
        assertEquals("Item 5", PumpEvent.getTips(4, 3, 2))
        assertEquals("Item 6", PumpEvent.getTips(4, 5, 0))
        assertEquals("Item 7", PumpEvent.getTips(4, 5, 1))
    }

    @Test
    fun `getTips should handle port 5 events`() {
        PumpEvent.init(rh)

        assertEquals("Item 15", PumpEvent.getTips(5, 0, 1))
        assertEquals("Item 16", PumpEvent.getTips(5, 0, 2))
        assertEquals("Item 17", PumpEvent.getTips(5, 1, 0))
        assertEquals("Item 18", PumpEvent.getTips(5, 1, 2))
    }

    @Test
    fun `init should handle special placeholder event`() {
        PumpEvent.init(rh)

        // First event is a placeholder with "--"
        val tip = PumpEvent.getTips(4, 0, 0)
        assertEquals("--", tip)
    }

    @Test
    fun `comment should be mutable`() {
        val event = PumpEvent(4, 2, 1, "Initial")
        assertEquals("Initial", event.comment)

        event.comment = "Modified"
        assertEquals("Modified", event.comment)
    }

    @Test
    fun `lists should be cleared on re-init`() {
        PumpEvent.init(rh)
        val size1 = PumpEvent.lists.size

        // Re-init should reset the list
        PumpEvent.init(rh)
        val size2 = PumpEvent.lists.size

        assertEquals(size1, size2)
        assertEquals(19, size2)
    }

    @Test
    fun `equals should be reflexive`() {
        val event = PumpEvent(4, 2, 1, "Comment")
        assertEquals(event, event)
    }

    @Test
    fun `equals should be symmetric`() {
        val event1 = PumpEvent(4, 2, 1, "Comment 1")
        val event2 = PumpEvent(4, 2, 1, "Comment 2")

        assertEquals(event1, event2)
        assertEquals(event2, event1)
    }

    @Test
    fun `equals should handle null`() {
        val event = PumpEvent(4, 2, 1, "Comment")
        assertNotEquals(event, null)
    }

    @Test
    fun `multiple events with same coordinates should be equal`() {
        val events = listOf(
            PumpEvent(4, 2, 1, "A"),
            PumpEvent(4, 2, 1, "B"),
            PumpEvent(4, 2, 1, "C")
        )

        // All should be equal to each other
        for (i in events.indices) {
            for (j in events.indices) {
                assertEquals(events[i], events[j])
            }
        }
    }
}

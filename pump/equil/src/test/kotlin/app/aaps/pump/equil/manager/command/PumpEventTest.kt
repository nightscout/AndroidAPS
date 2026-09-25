package app.aaps.pump.equil.manager.command

import app.aaps.pump.equil.R
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PumpEventTest {

    @Test
    fun `getEventStringRes should return correct resource for known events`() {
        assertEquals(R.string.equil_history_item1, PumpEvent.getEventStringRes(4, 1, 1))
        assertEquals(R.string.equil_history_item2, PumpEvent.getEventStringRes(4, 1, 2))
        assertEquals(R.string.equil_history_item3, PumpEvent.getEventStringRes(4, 2, 2))
    }

    @Test
    fun `getEventStringRes should return null for unknown event`() {
        assertNull(PumpEvent.getEventStringRes(99, 99, 99))
    }

    @Test
    fun `getEventStringRes should handle all port 4 events`() {
        assertEquals(R.string.equil_history_item4, PumpEvent.getEventStringRes(4, 3, 0))
        assertEquals(R.string.equil_history_item5, PumpEvent.getEventStringRes(4, 3, 2))
        assertEquals(R.string.equil_history_item6, PumpEvent.getEventStringRes(4, 5, 0))
        assertEquals(R.string.equil_history_item7, PumpEvent.getEventStringRes(4, 5, 1))
        assertEquals(R.string.equil_history_item8, PumpEvent.getEventStringRes(4, 6, 1))
        assertEquals(R.string.equil_history_item9, PumpEvent.getEventStringRes(4, 6, 2))
        assertEquals(R.string.equil_history_item10, PumpEvent.getEventStringRes(4, 7, 0))
        assertEquals(R.string.equil_history_item11, PumpEvent.getEventStringRes(4, 8, 0))
        assertEquals(R.string.equil_history_item12, PumpEvent.getEventStringRes(4, 9, 0))
        assertEquals(R.string.equil_history_item13, PumpEvent.getEventStringRes(4, 10, 0))
        assertEquals(R.string.equil_history_item14, PumpEvent.getEventStringRes(4, 11, 0))
    }

    @Test
    fun `getEventStringRes should handle all port 5 events`() {
        assertEquals(R.string.equil_history_item15, PumpEvent.getEventStringRes(5, 0, 1))
        assertEquals(R.string.equil_history_item16, PumpEvent.getEventStringRes(5, 0, 2))
        assertEquals(R.string.equil_history_item17, PumpEvent.getEventStringRes(5, 1, 0))
        assertEquals(R.string.equil_history_item18, PumpEvent.getEventStringRes(5, 1, 2))
    }

    @Test
    fun `getEventStringRes should return null for placeholder event`() {
        assertNull(PumpEvent.getEventStringRes(4, 0, 0))
    }
}

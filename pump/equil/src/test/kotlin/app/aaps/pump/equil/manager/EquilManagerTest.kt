package app.aaps.pump.equil.manager

import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.pump.equil.R
import app.aaps.pump.equil.ble.EquilBLE
import app.aaps.pump.equil.database.EquilHistoryPumpDao
import app.aaps.pump.equil.database.EquilHistoryRecordDao
import app.aaps.shared.tests.TestBaseWithProfile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.whenever

class EquilManagerTest : TestBaseWithProfile() {

    @Mock
    private lateinit var pumpSync: PumpSync

    @Mock
    private lateinit var equilBLE: EquilBLE

    @Mock
    private lateinit var equilHistoryRecordDao: EquilHistoryRecordDao

    @Mock
    private lateinit var equilHistoryPumpDao: EquilHistoryPumpDao

    private lateinit var equilManager: EquilManager

    @BeforeEach
    fun setUp() {

        // Mock resource strings
        whenever(rh.gs(R.string.equil_history_item3)).thenReturn("Error 1")
        whenever(rh.gs(R.string.equil_history_item4)).thenReturn("Error 2")
        whenever(rh.gs(R.string.equil_history_item5)).thenReturn("Error 3")
        whenever(rh.gs(R.string.equil_shutdown_be)).thenReturn("Shutdown BE")
        whenever(rh.gs(R.string.equil_shutdown)).thenReturn("Shutdown")
        whenever(rh.gs(R.string.equil_history_item18)).thenReturn("Error 6")

        equilManager = EquilManager(
            aapsLogger,
            rxBus,
            preferences,
            rh,
            pumpSync,
            equilBLE,
            equilHistoryRecordDao,
            equilHistoryPumpDao,
            pumpEnactResultProvider,
            dateUtil
        )
    }

    @Test
    fun `getEquilError should return correct error for valid event`() {
        // Initialize error list
        equilManager.init()

        // Test known error codes from initEquilError
        val error1 = equilManager.getEquilError(4, 2, 2)
        assertEquals("Error 1", error1)

        val error2 = equilManager.getEquilError(4, 3, 0)
        assertEquals("Error 2", error2)

        val error3 = equilManager.getEquilError(4, 3, 2)
        assertEquals("Error 3", error3)
    }

    @Test
    fun `getEquilError should return empty string for unknown event`() {
        equilManager.init()

        // Test unknown error code
        val error = equilManager.getEquilError(99, 99, 99)
        assertEquals("", error)
    }

    @Test
    fun `getEquilError should handle shutdown events`() {
        equilManager.init()

        val shutdownBe = equilManager.getEquilError(4, 6, 1)
        assertEquals("Shutdown BE", shutdownBe)

        val shutdown1 = equilManager.getEquilError(4, 6, 2)
        assertEquals("Shutdown", shutdown1)

        val shutdown2 = equilManager.getEquilError(4, 8, 0)
        assertEquals("Shutdown", shutdown2)
    }

    @Test
    fun `getEquilError should handle various event types`() {
        equilManager.init()

        val event = equilManager.getEquilError(5, 1, 2)
        assertEquals("Error 6", event)
    }

    @Test
    fun `listEvent should be initialized after init`() {
        equilManager.init()

        // List should have 7 events based on initEquilError
        assertEquals(7, equilManager.listEvent.size)
    }

    @Test
    fun `listEvent should have events with correct structure`() {
        equilManager.init()

        val events = equilManager.listEvent

        // Verify first event
        assertEquals(4, events[0].port)
        assertEquals(2, events[0].type)
        assertEquals(2, events[0].level)
        assertEquals("Error 1", events[0].comment)

        // Verify last event
        assertEquals(5, events[6].port)
        assertEquals(1, events[6].type)
        assertEquals(2, events[6].level)
        assertEquals("Error 6", events[6].comment)
    }

    @Test
    fun `getEquilError should be case sensitive for port type level combination`() {
        equilManager.init()

        // These should be different
        val error1 = equilManager.getEquilError(4, 2, 2) // Valid
        val error2 = equilManager.getEquilError(4, 2, 1) // Invalid (wrong level)
        val error3 = equilManager.getEquilError(4, 1, 2) // Invalid (wrong type)
        val error4 = equilManager.getEquilError(3, 2, 2) // Invalid (wrong port)

        assertEquals("Error 1", error1)
        assertEquals("", error2)
        assertEquals("", error3)
        assertEquals("", error4)
    }

    @Test
    fun `equilState should be null initially`() {
        assertEquals(null, equilManager.equilState)
    }

    @Test
    fun `getEquilError with multiple matching criteria`() {
        equilManager.init()

        // Port 4, level 2 appears in multiple events
        val shutdown = equilManager.getEquilError(4, 6, 2)
        assertEquals("Shutdown", shutdown)

        val error = equilManager.getEquilError(4, 3, 2)
        assertEquals("Error 3", error)
    }

    @Test
    fun `listEvent should handle empty list before init`() {
        // Before init, list should be empty or uninitialized
        // After construction, the list exists but may be empty
        val manager = EquilManager(
            aapsLogger,
            rxBus,
            preferences,
            rh,
            pumpSync,
            equilBLE,
            equilHistoryRecordDao,
            equilHistoryPumpDao,
            pumpEnactResultProvider,
            dateUtil
        )

        // List should be initialized but empty before init()
        assertNotNull(manager.listEvent)
    }

    @Test
    fun `init should be idempotent for error list`() {
        equilManager.init()
        val size1 = equilManager.listEvent.size

        // Calling init again should reset the list
        equilManager.init()
        val size2 = equilManager.listEvent.size

        assertEquals(size1, size2)
        assertEquals(7, size2)
    }

    private fun assertNotNull(value: Any?) {
        assert(value != null) { "Value should not be null" }
    }
}

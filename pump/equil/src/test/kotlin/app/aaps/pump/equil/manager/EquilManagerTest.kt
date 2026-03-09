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
            dateUtil,
            notificationManager,
            ch
        )
    }

    @Test
    fun `getEquilError should return correct error for valid event`() {
        assertEquals("Error 1", equilManager.getEquilError(4, 2, 2))
        assertEquals("Error 2", equilManager.getEquilError(4, 3, 0))
        assertEquals("Error 3", equilManager.getEquilError(4, 3, 2))
    }

    @Test
    fun `getEquilError should return empty string for unknown event`() {
        assertEquals("", equilManager.getEquilError(99, 99, 99))
    }

    @Test
    fun `getEquilError should handle shutdown events`() {
        assertEquals("Shutdown BE", equilManager.getEquilError(4, 6, 1))
        assertEquals("Shutdown", equilManager.getEquilError(4, 6, 2))
        assertEquals("Shutdown", equilManager.getEquilError(4, 8, 0))
    }

    @Test
    fun `getEquilError should handle port 5 events`() {
        assertEquals("Error 6", equilManager.getEquilError(5, 1, 2))
    }

    @Test
    fun `getEquilError should distinguish port type level combinations`() {
        assertEquals("Error 1", equilManager.getEquilError(4, 2, 2))
        assertEquals("", equilManager.getEquilError(4, 2, 1))
        assertEquals("", equilManager.getEquilError(4, 1, 2))
        assertEquals("", equilManager.getEquilError(3, 2, 2))
    }

    @Test
    fun `equilState should be null initially`() {
        assertEquals(null, equilManager.equilState)
    }


}

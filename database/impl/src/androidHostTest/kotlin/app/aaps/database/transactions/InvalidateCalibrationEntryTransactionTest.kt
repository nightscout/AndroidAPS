package app.aaps.database.transactions

import app.aaps.database.DelegatedAppDatabase
import app.aaps.database.daos.CalibrationEntryDao
import app.aaps.database.entities.CalibrationEntry
import app.aaps.database.entities.embedments.InterfaceIDs
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class InvalidateCalibrationEntryTransactionTest {

    private lateinit var database: DelegatedAppDatabase
    private lateinit var dao: CalibrationEntryDao

    @BeforeEach
    fun setup() {
        dao = mock()
        database = mock()
        whenever(database.calibrationEntryDao).thenReturn(dao)
    }

    @Test
    fun `invalidates valid calibration entry`() = runTest {
        val entry = calibrationEntry(id = 1, isValid = true)
        whenever(dao.findById(1)).thenReturn(entry)

        val transaction = InvalidateCalibrationEntryTransaction(id = 1).also { it.database = database }
        val result = transaction.run()

        assertThat(entry.isValid).isFalse()
        assertThat(result.invalidated).hasSize(1)
        verify(dao).updateExistingEntry(entry)
    }

    @Test
    fun `does not update already invalid entry`() = runTest {
        val entry = calibrationEntry(id = 1, isValid = false)
        whenever(dao.findById(1)).thenReturn(entry)

        val result = InvalidateCalibrationEntryTransaction(id = 1).also { it.database = database }.run()

        assertThat(result.invalidated).isEmpty()
        verify(dao, never()).updateExistingEntry(any())
    }

    @Test
    fun `throws when entry not found`() = runTest {
        whenever(dao.findById(999)).thenReturn(null)
        try {
            InvalidateCalibrationEntryTransaction(id = 999).also { it.database = database }.run()
            throw AssertionError("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("There is no such CalibrationEntry")
        }
    }

    private fun calibrationEntry(id: Long, isValid: Boolean): CalibrationEntry =
        CalibrationEntry(
            timestamp = 1_700_000_000_000L,
            fingerstickMgdl = 120.0,
            sensorMgdlAtPairing = 110.0,
            isValid = isValid,
            interfaceIDs_backing = InterfaceIDs()
        ).also { it.id = id }
}

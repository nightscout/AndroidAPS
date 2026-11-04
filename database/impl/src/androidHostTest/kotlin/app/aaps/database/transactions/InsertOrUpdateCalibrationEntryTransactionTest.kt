package app.aaps.database.transactions

import app.aaps.database.DelegatedAppDatabase
import app.aaps.database.daos.CalibrationEntryDao
import app.aaps.database.entities.CalibrationEntry
import app.aaps.database.entities.embedments.InterfaceIDs
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.verify
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class InsertOrUpdateCalibrationEntryTransactionTest {

    private lateinit var database: DelegatedAppDatabase
    private lateinit var dao: CalibrationEntryDao

    @BeforeEach
    fun setup() {
        dao = mock()
        database = mock()
        whenever(database.calibrationEntryDao).thenReturn(dao)
    }

    @Test
    fun `inserts when not present`() = runTest {
        val entry = calibrationEntry(id = 0)
        whenever(dao.findById(0)).thenReturn(null)

        val result = InsertOrUpdateCalibrationEntryTransaction(entry).also { it.database = database }.run()

        assertThat(result.inserted).hasSize(1)
        assertThat(result.updated).isEmpty()
        verify(dao).insertNewEntry(entry)
    }

    @Test
    fun `updates when present`() = runTest {
        val entry = calibrationEntry(id = 5)
        whenever(dao.findById(5)).thenReturn(calibrationEntry(id = 5))

        val result = InsertOrUpdateCalibrationEntryTransaction(entry).also { it.database = database }.run()

        assertThat(result.updated).hasSize(1)
        assertThat(result.inserted).isEmpty()
        verify(dao).updateExistingEntry(entry)
    }

    private fun calibrationEntry(id: Long): CalibrationEntry =
        CalibrationEntry(
            timestamp = 1_700_000_000_000L,
            fingerstickMgdl = 120.0,
            sensorMgdlAtPairing = 110.0,
            interfaceIDs_backing = InterfaceIDs()
        ).also { it.id = id }
}

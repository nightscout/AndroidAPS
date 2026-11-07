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

class UpdateNsIdCalibrationEntryTransactionTest {

    private lateinit var database: DelegatedAppDatabase
    private lateinit var dao: CalibrationEntryDao

    @BeforeEach
    fun setup() {
        dao = mock()
        database = mock()
        whenever(database.calibrationEntryDao).thenReturn(dao)
    }

    @Test
    fun `updates nsId when changed`() = runTest {
        val current = calibrationEntry(id = 1, nsId = null)
        val incoming = calibrationEntry(id = 1, nsId = "abc")
        whenever(dao.findById(1)).thenReturn(current)

        val result = UpdateNsIdCalibrationEntryTransaction(listOf(incoming)).also { it.database = database }.run()

        assertThat(result.updatedNsId).hasSize(1)
        assertThat(current.interfaceIDs.nightscoutId).isEqualTo("abc")
        verify(dao).updateExistingEntry(current)
    }

    @Test
    fun `no update when nsId unchanged`() = runTest {
        val current = calibrationEntry(id = 1, nsId = "abc")
        val incoming = calibrationEntry(id = 1, nsId = "abc")
        whenever(dao.findById(1)).thenReturn(current)

        val result = UpdateNsIdCalibrationEntryTransaction(listOf(incoming)).also { it.database = database }.run()

        assertThat(result.updatedNsId).isEmpty()
        verify(dao, never()).updateExistingEntry(any())
    }

    private fun calibrationEntry(id: Long, nsId: String?): CalibrationEntry =
        CalibrationEntry(
            timestamp = 1_700_000_000_000L,
            fingerstickMgdl = 120.0,
            sensorMgdlAtPairing = 110.0,
            interfaceIDs_backing = InterfaceIDs(nightscoutId = nsId)
        ).also { it.id = id }
}

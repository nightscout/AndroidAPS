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

class SyncNsCalibrationEntryTransactionTest {

    private lateinit var database: DelegatedAppDatabase
    private lateinit var dao: CalibrationEntryDao

    @BeforeEach
    fun setup() {
        dao = mock()
        database = mock()
        whenever(database.calibrationEntryDao).thenReturn(dao)
    }

    @Test
    fun `inserts unknown nsId`() = runTest {
        val incoming = calibrationEntry(nsId = "new", fingerstick = 120.0)
        whenever(dao.findByNSId("new")).thenReturn(null)

        val result = SyncNsCalibrationEntryTransaction(listOf(incoming)).also { it.database = database }.run()

        assertThat(result.inserted).hasSize(1)
        verify(dao).insertNewEntry(incoming)
    }

    @Test
    fun `attaches nsId to local row matched by timestamp when nsId unknown`() = runTest {
        // Our own just-uploaded entry round-tripping back before its nsId was written locally.
        val local = calibrationEntry(id = 7, nsId = null, fingerstick = 120.0).also { it.timestamp = 1_700_000_000_000L }
        val incoming = calibrationEntry(nsId = "fresh", fingerstick = 120.0).also { it.timestamp = 1_700_000_000_000L }
        whenever(dao.findByNSId("fresh")).thenReturn(null)
        whenever(dao.findByTimestamp(1_700_000_000_000L)).thenReturn(local)

        val result = SyncNsCalibrationEntryTransaction(listOf(incoming)).also { it.database = database }.run()

        assertThat(result.inserted).isEmpty()
        assertThat(result.updatedNsId).hasSize(1)
        assertThat(local.interfaceIDs.nightscoutId).isEqualTo("fresh")
        verify(dao).updateExistingEntry(local)
    }

    @Test
    fun `updates existing nsId when content changed`() = runTest {
        val current = calibrationEntry(id = 3, nsId = "x", fingerstick = 120.0)
        val incoming = calibrationEntry(nsId = "x", fingerstick = 130.0)
        whenever(dao.findByNSId("x")).thenReturn(current)

        val result = SyncNsCalibrationEntryTransaction(listOf(incoming)).also { it.database = database }.run()

        assertThat(result.updated).hasSize(1)
        assertThat(current.fingerstickMgdl).isEqualTo(130.0) // copyFrom applied
        verify(dao).updateExistingEntry(current)
    }

    @Test
    fun `invalidates existing when incoming is invalid`() = runTest {
        val current = calibrationEntry(id = 3, nsId = "x", fingerstick = 120.0, isValid = true)
        val incoming = calibrationEntry(nsId = "x", fingerstick = 120.0, isValid = false)
        whenever(dao.findByNSId("x")).thenReturn(current)

        val result = SyncNsCalibrationEntryTransaction(listOf(incoming)).also { it.database = database }.run()

        assertThat(result.invalidated).hasSize(1)
        assertThat(current.isValid).isFalse()
        verify(dao).updateExistingEntry(current)
    }

    @Test
    fun `no-op when nsId known and content identical`() = runTest {
        val current = calibrationEntry(id = 3, nsId = "x", fingerstick = 120.0)
        val incoming = calibrationEntry(nsId = "x", fingerstick = 120.0)
        whenever(dao.findByNSId("x")).thenReturn(current)

        val result = SyncNsCalibrationEntryTransaction(listOf(incoming)).also { it.database = database }.run()

        assertThat(result.inserted).isEmpty()
        assertThat(result.updated).isEmpty()
        assertThat(result.invalidated).isEmpty()
    }

    private fun calibrationEntry(
        id: Long = 0,
        nsId: String?,
        fingerstick: Double,
        isValid: Boolean = true
    ): CalibrationEntry =
        CalibrationEntry(
            timestamp = 1_700_000_000_000L,
            fingerstickMgdl = fingerstick,
            sensorMgdlAtPairing = 110.0,
            isValid = isValid,
            interfaceIDs_backing = InterfaceIDs(nightscoutId = nsId)
        ).also { it.id = id }
}

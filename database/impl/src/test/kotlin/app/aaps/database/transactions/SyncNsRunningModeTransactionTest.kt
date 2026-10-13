package app.aaps.database.transactions

import app.aaps.database.DelegatedAppDatabase
import app.aaps.database.daos.RunningModeDao
import app.aaps.database.entities.RunningMode
import app.aaps.database.entities.embedments.InterfaceIDs
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class SyncNsRunningModeTransactionTest {

    private lateinit var database: DelegatedAppDatabase
    private lateinit var runningModeDao: RunningModeDao

    @BeforeEach
    fun setup() {
        runningModeDao = mock()
        database = mock()
        whenever(database.runningModeDao).thenReturn(runningModeDao)
    }

    @Test
    fun `inserts new when nsId not found and no timestamp match`() {
        val runningMode = createRunningMode(id = 0, nsId = "ns-123", timestamp = 1000L)

        whenever(runningModeDao.findByNSId("ns-123")).thenReturn(null)
        whenever(runningModeDao.findByTimestamp(1000L)).thenReturn(null)

        val transaction = SyncNsRunningModeTransaction(listOf(runningMode))
        transaction.database = database
        val result = transaction.run()

        assertThat(result.inserted).hasSize(1)
        assertThat(result.updatedNsId).isEmpty()
        assertThat(result.invalidated).isEmpty()

        verify(runningModeDao).insertNewEntry(runningMode)
    }

    @Test
    fun `updates nsId when timestamp matches but nsId is null`() {
        val runningMode = createRunningMode(id = 0, nsId = "ns-123", timestamp = 1000L)
        val existing = createRunningMode(id = 1, nsId = null, timestamp = 1000L)

        whenever(runningModeDao.findByNSId("ns-123")).thenReturn(null)
        whenever(runningModeDao.findByTimestamp(1000L)).thenReturn(existing)

        val transaction = SyncNsRunningModeTransaction(listOf(runningMode))
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updatedNsId).hasSize(1)
        assertThat(existing.interfaceIDs.nightscoutId).isEqualTo("ns-123")

        verify(runningModeDao).updateExistingEntry(existing)
    }

    @Test
    fun `invalidates when valid becomes invalid`() {
        val runningMode = createRunningMode(id = 0, nsId = "ns-123", isValid = false)
        val existing = createRunningMode(id = 1, nsId = "ns-123", isValid = true)

        whenever(runningModeDao.findByNSId("ns-123")).thenReturn(existing)

        val transaction = SyncNsRunningModeTransaction(listOf(runningMode))
        transaction.database = database
        val result = transaction.run()

        assertThat(result.invalidated).hasSize(1)
        assertThat(existing.isValid).isFalse()

        verify(runningModeDao).updateExistingEntry(existing)
    }

    @Test
    fun `updates duration to shorter when duration changes`() {
        val runningMode = createRunningMode(id = 0, nsId = "ns-123", duration = 30_000L)
        val existing = createRunningMode(id = 1, nsId = "ns-123", duration = 60_000L)

        whenever(runningModeDao.findByNSId("ns-123")).thenReturn(existing)

        val transaction = SyncNsRunningModeTransaction(listOf(runningMode))
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updatedDuration).hasSize(1)
        assertThat(existing.duration).isEqualTo(30_000L)

        verify(runningModeDao).updateExistingEntry(existing)
    }

    @Test
    fun `does not update duration to longer`() {
        val runningMode = createRunningMode(id = 0, nsId = "ns-123", duration = 120_000L)
        val existing = createRunningMode(id = 1, nsId = "ns-123", duration = 60_000L)

        whenever(runningModeDao.findByNSId("ns-123")).thenReturn(existing)

        val transaction = SyncNsRunningModeTransaction(listOf(runningMode))
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updatedDuration).isEmpty()
        assertThat(existing.duration).isEqualTo(60_000L)

        verify(runningModeDao, never()).updateExistingEntry(any())
    }

    private fun createRunningMode(
        id: Long,
        nsId: String?,
        timestamp: Long = System.currentTimeMillis(),
        isValid: Boolean = true,
        duration: Long = 0L
    ): RunningMode = RunningMode(
        timestamp = timestamp,
        mode = RunningMode.Mode.OPEN_LOOP,
        interfaceIDs_backing = InterfaceIDs(nightscoutId = nsId),
        duration = duration,
        isValid = isValid
    ).also { it.id = id }
}

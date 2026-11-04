package app.aaps.database.transactions

import app.aaps.database.DelegatedAppDatabase
import app.aaps.database.daos.TherapyEventDao
import app.aaps.database.entities.TherapyEvent
import app.aaps.database.entities.data.GlucoseUnit
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

class SyncNsTherapyEventTransactionTest {

    private lateinit var database: DelegatedAppDatabase
    private lateinit var therapyEventDao: TherapyEventDao

    @BeforeEach
    fun setup() {
        therapyEventDao = mock()
        database = mock()
        whenever(database.therapyEventDao).thenReturn(therapyEventDao)
    }

    @Test
    fun `inserts new therapy event when nsId not found and no timestamp match`() = runTest {
        val therapyEvent = createTherapyEvent(id = 0, nsId = "ns-123", timestamp = 1000L)

        whenever(therapyEventDao.findByNSId("ns-123")).thenReturn(null)
        whenever(therapyEventDao.findByTimestamp(TherapyEvent.Type.NOTE, 1000L)).thenReturn(null)

        val transaction = SyncNsTherapyEventTransaction(listOf(therapyEvent), nsClientMode = false)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.inserted).hasSize(1)
        assertThat(result.inserted[0]).isEqualTo(therapyEvent)
        assertThat(result.updatedNsId).isEmpty()
        assertThat(result.invalidated).isEmpty()
        assertThat(result.updatedDuration).isEmpty()

        verify(therapyEventDao).insertNewEntry(therapyEvent)
    }

    @Test
    fun `updates nsId when timestamp matches but nsId is null`() = runTest {
        val therapyEvent = createTherapyEvent(id = 0, nsId = "ns-123", timestamp = 1000L)
        val existing = createTherapyEvent(id = 1, nsId = null, timestamp = 1000L)

        whenever(therapyEventDao.findByNSId("ns-123")).thenReturn(null)
        whenever(therapyEventDao.findByTimestamp(TherapyEvent.Type.NOTE, 1000L)).thenReturn(existing)

        val transaction = SyncNsTherapyEventTransaction(listOf(therapyEvent), nsClientMode = false)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updatedNsId).hasSize(1)
        assertThat(result.updatedNsId[0]).isEqualTo(existing)
        assertThat(existing.interfaceIDs.nightscoutId).isEqualTo("ns-123")
        assertThat(result.inserted).isEmpty()
        assertThat(result.invalidated).isEmpty()

        verify(therapyEventDao).updateExistingEntry(existing)
        verify(therapyEventDao, never()).insertNewEntry(any())
    }

    @Test
    fun `inserts new when timestamp matches but existing has different nsId`() = runTest {
        val therapyEvent = createTherapyEvent(id = 0, nsId = "ns-123", timestamp = 1000L)
        val existing = createTherapyEvent(id = 1, nsId = "other-ns", timestamp = 1000L)

        whenever(therapyEventDao.findByNSId("ns-123")).thenReturn(null)
        whenever(therapyEventDao.findByTimestamp(TherapyEvent.Type.NOTE, 1000L)).thenReturn(existing)

        val transaction = SyncNsTherapyEventTransaction(listOf(therapyEvent), nsClientMode = false)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.inserted).hasSize(1)
        assertThat(result.inserted[0]).isEqualTo(therapyEvent)
        assertThat(result.updatedNsId).isEmpty()

        verify(therapyEventDao).insertNewEntry(therapyEvent)
    }

    @Test
    fun `invalidates therapy event when valid becomes invalid`() = runTest {
        val therapyEvent = createTherapyEvent(id = 0, nsId = "ns-123", isValid = false)
        val existing = createTherapyEvent(id = 1, nsId = "ns-123", isValid = true)

        whenever(therapyEventDao.findByNSId("ns-123")).thenReturn(existing)

        val transaction = SyncNsTherapyEventTransaction(listOf(therapyEvent), nsClientMode = false)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.invalidated).hasSize(1)
        assertThat(result.invalidated[0]).isEqualTo(existing)
        assertThat(existing.isValid).isFalse()
        assertThat(result.inserted).isEmpty()
        assertThat(result.updatedNsId).isEmpty()

        verify(therapyEventDao).updateExistingEntry(existing)
        verify(therapyEventDao, never()).insertNewEntry(any())
    }

    @Test
    fun `does not invalidate already invalid therapy event`() = runTest {
        val therapyEvent = createTherapyEvent(id = 0, nsId = "ns-123", isValid = false)
        val existing = createTherapyEvent(id = 1, nsId = "ns-123", isValid = false)

        whenever(therapyEventDao.findByNSId("ns-123")).thenReturn(existing)

        val transaction = SyncNsTherapyEventTransaction(listOf(therapyEvent), nsClientMode = false)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.invalidated).isEmpty()
        assertThat(result.inserted).isEmpty()
        assertThat(result.updatedNsId).isEmpty()
        assertThat(result.updatedDuration).isEmpty()

        verify(therapyEventDao, never()).updateExistingEntry(any())
        verify(therapyEventDao, never()).insertNewEntry(any())
    }

    @Test
    fun `updates duration to shorter in NS client mode when duration changes`() = runTest {
        val therapyEvent = createTherapyEvent(id = 0, nsId = "ns-123", duration = 30_000L)
        val existing = createTherapyEvent(id = 1, nsId = "ns-123", duration = 60_000L)

        whenever(therapyEventDao.findByNSId("ns-123")).thenReturn(existing)

        val transaction = SyncNsTherapyEventTransaction(listOf(therapyEvent), nsClientMode = true)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updatedDuration).hasSize(1)
        assertThat(result.updatedDuration[0]).isEqualTo(existing)
        assertThat(existing.duration).isEqualTo(30_000L)
        assertThat(result.inserted).isEmpty()
        assertThat(result.invalidated).isEmpty()

        verify(therapyEventDao).updateExistingEntry(existing)
    }

    @Test
    fun `does not update duration to longer in NS client mode`() = runTest {
        val therapyEvent = createTherapyEvent(id = 0, nsId = "ns-123", duration = 120_000L)
        val existing = createTherapyEvent(id = 1, nsId = "ns-123", duration = 60_000L)

        whenever(therapyEventDao.findByNSId("ns-123")).thenReturn(existing)

        val transaction = SyncNsTherapyEventTransaction(listOf(therapyEvent), nsClientMode = true)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updatedDuration).isEmpty()
        assertThat(existing.duration).isEqualTo(60_000L)
        assertThat(result.inserted).isEmpty()
        assertThat(result.invalidated).isEmpty()

        verify(therapyEventDao, never()).updateExistingEntry(any())
    }

    @Test
    fun `does not update duration in non-NS client mode`() = runTest {
        val therapyEvent = createTherapyEvent(id = 0, nsId = "ns-123", duration = 120_000L)
        val existing = createTherapyEvent(id = 1, nsId = "ns-123", duration = 60_000L)

        whenever(therapyEventDao.findByNSId("ns-123")).thenReturn(existing)

        val transaction = SyncNsTherapyEventTransaction(listOf(therapyEvent), nsClientMode = false)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updatedDuration).isEmpty()
        assertThat(result.inserted).isEmpty()
        assertThat(result.invalidated).isEmpty()

        verify(therapyEventDao, never()).updateExistingEntry(any())
    }

    @Test
    fun `handles multiple therapy events`() = runTest {
        val therapyEvent1 = createTherapyEvent(id = 0, nsId = "ns-1", timestamp = 1000L)
        val therapyEvent2 = createTherapyEvent(id = 0, nsId = "ns-2", timestamp = 2000L)

        whenever(therapyEventDao.findByNSId("ns-1")).thenReturn(null)
        whenever(therapyEventDao.findByNSId("ns-2")).thenReturn(null)
        whenever(therapyEventDao.findByTimestamp(TherapyEvent.Type.NOTE, 1000L)).thenReturn(null)
        whenever(therapyEventDao.findByTimestamp(TherapyEvent.Type.NOTE, 2000L)).thenReturn(null)

        val transaction = SyncNsTherapyEventTransaction(listOf(therapyEvent1, therapyEvent2), nsClientMode = false)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.inserted).hasSize(2)
    }

    @Test
    fun `updates valid flag when updating nsId`() = runTest {
        val therapyEvent = createTherapyEvent(id = 0, nsId = "ns-123", timestamp = 1000L, isValid = false)
        val existing = createTherapyEvent(id = 1, nsId = null, timestamp = 1000L, isValid = true)

        whenever(therapyEventDao.findByNSId("ns-123")).thenReturn(null)
        whenever(therapyEventDao.findByTimestamp(TherapyEvent.Type.NOTE, 1000L)).thenReturn(existing)

        val transaction = SyncNsTherapyEventTransaction(listOf(therapyEvent), nsClientMode = false)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updatedNsId).hasSize(1)
        assertThat(existing.interfaceIDs.nightscoutId).isEqualTo("ns-123")
        assertThat(existing.isValid).isFalse()
    }

    private fun createTherapyEvent(
        id: Long,
        nsId: String?,
        timestamp: Long = System.currentTimeMillis(),
        isValid: Boolean = true,
        duration: Long = 0L
    ): TherapyEvent = TherapyEvent(
        timestamp = timestamp,
        type = TherapyEvent.Type.NOTE,
        note = "Test",
        isValid = isValid,
        duration = duration,
        glucoseUnit = GlucoseUnit.MGDL,
        interfaceIDs_backing = InterfaceIDs(nightscoutId = nsId)
    ).also { it.id = id }
}

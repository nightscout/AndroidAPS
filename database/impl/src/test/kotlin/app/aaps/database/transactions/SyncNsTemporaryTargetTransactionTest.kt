package app.aaps.database.transactions

import app.aaps.database.DelegatedAppDatabase
import app.aaps.database.daos.TemporaryTargetDao
import app.aaps.database.entities.TemporaryTarget
import app.aaps.database.entities.embedments.InterfaceIDs
import app.aaps.database.entities.interfaces.end
import com.google.common.truth.Truth.assertThat
import io.reactivex.rxjava3.core.Maybe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class SyncNsTemporaryTargetTransactionTest {

    private lateinit var database: DelegatedAppDatabase
    private lateinit var temporaryTargetDao: TemporaryTargetDao

    @BeforeEach
    fun setup() {
        temporaryTargetDao = mock()
        database = mock()
        whenever(database.temporaryTargetDao).thenReturn(temporaryTargetDao)
    }

    @Test
    fun `inserts new temporary target when nsId not found and no active target`() {
        val tempTarget = createTemporaryTarget(id = 0, nsId = "ns-123", timestamp = 1000L, duration = 60_000L)

        whenever(temporaryTargetDao.findByNSId("ns-123")).thenReturn(null)
        whenever(temporaryTargetDao.getTemporaryTargetActiveAt(1000L)).thenReturn(Maybe.empty())

        val transaction = SyncNsTemporaryTargetTransaction(listOf(tempTarget))
        transaction.database = database
        val result = transaction.run()

        assertThat(result.inserted).hasSize(1)
        assertThat(result.inserted[0]).isEqualTo(tempTarget)
        assertThat(result.updatedNsId).isEmpty()
        assertThat(result.invalidated).isEmpty()
        assertThat(result.ended).isEmpty()

        verify(temporaryTargetDao).insertNewEntry(tempTarget)
    }

    @Test
    fun `updates nsId when active target at same timestamp`() {
        val tempTarget = createTemporaryTarget(id = 0, nsId = "ns-123", timestamp = 1000L, duration = 60_000L)
        val existing = createTemporaryTarget(id = 1, nsId = null, timestamp = 999L, duration = 60_000L)

        whenever(temporaryTargetDao.findByNSId("ns-123")).thenReturn(null)
        whenever(temporaryTargetDao.getTemporaryTargetActiveAt(1000L)).thenReturn(Maybe.just(existing))

        val transaction = SyncNsTemporaryTargetTransaction(listOf(tempTarget))
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updatedNsId).hasSize(1)
        assertThat(result.updatedNsId[0]).isEqualTo(existing)
        assertThat(existing.interfaceIDs.nightscoutId).isEqualTo("ns-123")
        assertThat(result.inserted).isEmpty()
        assertThat(result.ended).isEmpty()

        verify(temporaryTargetDao).updateExistingEntry(existing)
        verify(temporaryTargetDao, never()).insertNewEntry(any())
    }

    @Test
    fun `ends running target and inserts new when timestamps differ significantly`() {
        val tempTarget = createTemporaryTarget(id = 0, nsId = "ns-123", timestamp = 5000L, duration = 60_000L)
        val existing = createTemporaryTarget(id = 1, nsId = null, timestamp = 1000L, duration = 60_000L)

        whenever(temporaryTargetDao.findByNSId("ns-123")).thenReturn(null)
        whenever(temporaryTargetDao.getTemporaryTargetActiveAt(5000L)).thenReturn(Maybe.just(existing))

        val transaction = SyncNsTemporaryTargetTransaction(listOf(tempTarget))
        transaction.database = database
        val result = transaction.run()

        assertThat(result.ended).hasSize(1)
        assertThat(result.ended[0]).isEqualTo(existing)
        assertThat(existing.end).isEqualTo(5000L)
        assertThat(result.inserted).hasSize(1)
        assertThat(result.inserted[0]).isEqualTo(tempTarget)

        verify(temporaryTargetDao).updateExistingEntry(existing)
        verify(temporaryTargetDao).insertNewEntry(tempTarget)
    }

    @Test
    fun `invalidates temporary target when valid becomes invalid`() {
        val tempTarget = createTemporaryTarget(id = 0, nsId = "ns-123", duration = 60_000L, isValid = false)
        val existing = createTemporaryTarget(id = 1, nsId = "ns-123", duration = 60_000L, isValid = true)

        whenever(temporaryTargetDao.findByNSId("ns-123")).thenReturn(existing)

        val transaction = SyncNsTemporaryTargetTransaction(listOf(tempTarget))
        transaction.database = database
        val result = transaction.run()

        assertThat(result.invalidated).hasSize(1)
        assertThat(result.invalidated[0]).isEqualTo(existing)
        assertThat(existing.isValid).isFalse()
        assertThat(result.inserted).isEmpty()

        verify(temporaryTargetDao).updateExistingEntry(existing)
        verify(temporaryTargetDao, never()).insertNewEntry(any())
    }

    @Test
    fun `updates duration to shorter when duration changes`() {
        val tempTarget = createTemporaryTarget(id = 0, nsId = "ns-123", duration = 30_000L)
        val existing = createTemporaryTarget(id = 1, nsId = "ns-123", duration = 60_000L)

        whenever(temporaryTargetDao.findByNSId("ns-123")).thenReturn(existing)

        val transaction = SyncNsTemporaryTargetTransaction(listOf(tempTarget))
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updatedDuration).hasSize(1)
        assertThat(result.updatedDuration[0]).isEqualTo(existing)
        assertThat(existing.duration).isEqualTo(30_000L)
        assertThat(result.inserted).isEmpty()

        verify(temporaryTargetDao).updateExistingEntry(existing)
    }

    @Test
    fun `does not update duration to longer`() {
        val tempTarget = createTemporaryTarget(id = 0, nsId = "ns-123", duration = 120_000L)
        val existing = createTemporaryTarget(id = 1, nsId = "ns-123", duration = 60_000L)

        whenever(temporaryTargetDao.findByNSId("ns-123")).thenReturn(existing)

        val transaction = SyncNsTemporaryTargetTransaction(listOf(tempTarget))
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updatedDuration).isEmpty()
        assertThat(existing.duration).isEqualTo(60_000L)
        assertThat(result.inserted).isEmpty()

        verify(temporaryTargetDao, never()).updateExistingEntry(any())
    }

    @Test
    fun `does not update when duration is same`() {
        val tempTarget = createTemporaryTarget(id = 0, nsId = "ns-123", duration = 60_000L)
        val existing = createTemporaryTarget(id = 1, nsId = "ns-123", duration = 60_000L)

        whenever(temporaryTargetDao.findByNSId("ns-123")).thenReturn(existing)

        val transaction = SyncNsTemporaryTargetTransaction(listOf(tempTarget))
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updatedDuration).isEmpty()
        assertThat(result.invalidated).isEmpty()
        assertThat(result.inserted).isEmpty()

        verify(temporaryTargetDao, never()).updateExistingEntry(any())
    }

    @Test
    fun `ends running target when receiving ending event`() {
        val tempTarget = createTemporaryTarget(id = 0, nsId = "ns-123", timestamp = 61_000L, duration = 0L)
        val existing = createTemporaryTarget(id = 1, nsId = null, timestamp = 1000L, duration = 60_000L)

        whenever(temporaryTargetDao.getTemporaryTargetActiveAt(61_000L)).thenReturn(Maybe.just(existing))

        val transaction = SyncNsTemporaryTargetTransaction(listOf(tempTarget))
        transaction.database = database
        val result = transaction.run()

        assertThat(result.ended).hasSize(1)
        assertThat(result.ended[0]).isEqualTo(existing)
        assertThat(existing.end).isEqualTo(61_000L)
        assertThat(result.inserted).isEmpty()

        verify(temporaryTargetDao).updateExistingEntry(existing)
        verify(temporaryTargetDao, never()).insertNewEntry(any())
    }

    @Test
    fun `does nothing when ending event but no running target`() {
        val tempTarget = createTemporaryTarget(id = 0, nsId = "ns-123", timestamp = 61_000L, duration = 0L)

        whenever(temporaryTargetDao.getTemporaryTargetActiveAt(61_000L)).thenReturn(Maybe.empty())

        val transaction = SyncNsTemporaryTargetTransaction(listOf(tempTarget))
        transaction.database = database
        val result = transaction.run()

        assertThat(result.ended).isEmpty()
        assertThat(result.inserted).isEmpty()

        verify(temporaryTargetDao, never()).updateExistingEntry(any())
        verify(temporaryTargetDao, never()).insertNewEntry(any())
    }

    @Test
    fun `handles multiple temporary targets`() {
        val tempTarget1 = createTemporaryTarget(id = 0, nsId = "ns-1", timestamp = 1000L, duration = 60_000L)
        val tempTarget2 = createTemporaryTarget(id = 0, nsId = "ns-2", timestamp = 2000L, duration = 60_000L)

        whenever(temporaryTargetDao.findByNSId("ns-1")).thenReturn(null)
        whenever(temporaryTargetDao.findByNSId("ns-2")).thenReturn(null)
        whenever(temporaryTargetDao.getTemporaryTargetActiveAt(1000L)).thenReturn(Maybe.empty())
        whenever(temporaryTargetDao.getTemporaryTargetActiveAt(2000L)).thenReturn(Maybe.empty())

        val transaction = SyncNsTemporaryTargetTransaction(listOf(tempTarget1, tempTarget2))
        transaction.database = database
        val result = transaction.run()

        assertThat(result.inserted).hasSize(2)
    }

    private fun createTemporaryTarget(
        id: Long,
        nsId: String?,
        timestamp: Long = System.currentTimeMillis(),
        duration: Long,
        isValid: Boolean = true
    ): TemporaryTarget = TemporaryTarget(
        timestamp = timestamp,
        duration = duration,
        reason = TemporaryTarget.Reason.CUSTOM,
        lowTarget = 80.0,
        highTarget = 120.0,
        isValid = isValid,
        interfaceIDs_backing = InterfaceIDs(nightscoutId = nsId)
    ).also { it.id = id }
}

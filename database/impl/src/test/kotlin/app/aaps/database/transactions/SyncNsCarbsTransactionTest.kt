package app.aaps.database.transactions

import app.aaps.database.DelegatedAppDatabase
import app.aaps.database.daos.CarbsDao
import app.aaps.database.entities.Carbs
import app.aaps.database.entities.embedments.InterfaceIDs
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class SyncNsCarbsTransactionTest {

    private lateinit var database: DelegatedAppDatabase
    private lateinit var carbsDao: CarbsDao

    @BeforeEach
    fun setup() {
        carbsDao = mock()
        database = mock()
        whenever(database.carbsDao).thenReturn(carbsDao)
    }

    @Test
    fun `inserts new carbs when nsId not found and no timestamp match`() {
        val carbs = createCarbs(id = 0, nsId = "ns-123", amount = 50.0, timestamp = 1000L)

        whenever(carbsDao.getByNSId("ns-123")).thenReturn(null)
        whenever(carbsDao.findByTimestamp(1000L)).thenReturn(null)

        val transaction = SyncNsCarbsTransaction(listOf(carbs), nsClientMode = false)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.inserted).hasSize(1)
        assertThat(result.inserted[0]).isEqualTo(carbs)
        assertThat(result.updated).isEmpty()
        assertThat(result.invalidated).isEmpty()
        assertThat(result.updatedNsId).isEmpty()

        verify(carbsDao).insertNewEntry(carbs)
    }

    @Test
    fun `updates nsId when timestamp matches but nsId is null`() {
        val nsId = "ns-123"
        val timestamp = 1000L
        val existing = createCarbs(id = 1, nsId = null, amount = 50.0, timestamp = timestamp)
        val incoming = createCarbs(id = 0, nsId = nsId, amount = 50.0, timestamp = timestamp)

        whenever(carbsDao.getByNSId(nsId)).thenReturn(null)
        whenever(carbsDao.findByTimestamp(timestamp)).thenReturn(existing)

        val transaction = SyncNsCarbsTransaction(listOf(incoming), nsClientMode = false)
        transaction.database = database
        val result = transaction.run()

        assertThat(existing.interfaceIDs.nightscoutId).isEqualTo(nsId)
        assertThat(result.updatedNsId).hasSize(1)
        assertThat(result.updatedNsId[0]).isEqualTo(existing)
        assertThat(result.inserted).isEmpty()

        verify(carbsDao).updateExistingEntry(existing)
        verify(carbsDao, never()).insertNewEntry(any())
    }

    @Test
    fun `invalidates carbs when nsId exists and incoming is invalid`() {
        val nsId = "ns-123"
        val existing = createCarbs(id = 1, nsId = nsId, amount = 50.0, isValid = true)
        val incoming = createCarbs(id = 0, nsId = nsId, amount = 50.0, isValid = false)

        whenever(carbsDao.getByNSId(nsId)).thenReturn(existing)

        val transaction = SyncNsCarbsTransaction(listOf(incoming), nsClientMode = false)
        transaction.database = database
        val result = transaction.run()

        assertThat(existing.isValid).isFalse()
        assertThat(result.invalidated).hasSize(1)
        assertThat(result.invalidated[0]).isEqualTo(existing)

        verify(carbsDao).updateExistingEntry(existing)
    }

    @Test
    fun `does not invalidate already invalid carbs`() {
        val nsId = "ns-123"
        val existing = createCarbs(id = 1, nsId = nsId, amount = 50.0, isValid = false)
        val incoming = createCarbs(id = 0, nsId = nsId, amount = 50.0, isValid = false)

        whenever(carbsDao.getByNSId(nsId)).thenReturn(existing)

        val transaction = SyncNsCarbsTransaction(listOf(incoming), nsClientMode = false)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.invalidated).isEmpty()
        verify(carbsDao, never()).updateExistingEntry(any())
    }

    @Test
    fun `updates duration to shorter in nsClientMode when duration differs`() {
        val nsId = "ns-123"
        val existing = createCarbs(id = 1, nsId = nsId, amount = 50.0, duration = 60_000L)
        val incoming = createCarbs(id = 0, nsId = nsId, amount = 75.0, duration = 30_000L)

        whenever(carbsDao.getByNSId(nsId)).thenReturn(existing)

        val transaction = SyncNsCarbsTransaction(listOf(incoming), nsClientMode = true)
        transaction.database = database
        val result = transaction.run()

        assertThat(existing.duration).isEqualTo(30_000L)
        assertThat(existing.amount).isEqualTo(75.0)
        assertThat(result.updated).hasSize(1)
        assertThat(result.updated[0]).isEqualTo(existing)

        verify(carbsDao).updateExistingEntry(existing)
    }

    @Test
    fun `does not update duration to longer in nsClientMode`() {
        val nsId = "ns-123"
        val existing = createCarbs(id = 1, nsId = nsId, amount = 50.0, duration = 60_000L)
        val incoming = createCarbs(id = 0, nsId = nsId, amount = 75.0, duration = 120_000L)

        whenever(carbsDao.getByNSId(nsId)).thenReturn(existing)

        val transaction = SyncNsCarbsTransaction(listOf(incoming), nsClientMode = true)
        transaction.database = database
        val result = transaction.run()

        assertThat(existing.duration).isEqualTo(60_000L)
        assertThat(existing.amount).isEqualTo(50.0)
        assertThat(result.updated).isEmpty()

        verify(carbsDao, never()).updateExistingEntry(any())
    }

    @Test
    fun `does not update duration when not in nsClientMode`() {
        val nsId = "ns-123"
        val existing = createCarbs(id = 1, nsId = nsId, amount = 50.0, duration = 60_000L)
        val incoming = createCarbs(id = 0, nsId = nsId, amount = 75.0, duration = 120_000L)

        whenever(carbsDao.getByNSId(nsId)).thenReturn(existing)

        val transaction = SyncNsCarbsTransaction(listOf(incoming), nsClientMode = false)
        transaction.database = database
        val result = transaction.run()

        assertThat(existing.duration).isEqualTo(60_000L)
        assertThat(existing.amount).isEqualTo(50.0)
        assertThat(result.updated).isEmpty()

        verify(carbsDao, never()).updateExistingEntry(any())
    }

    @Test
    fun `does not update when duration is same in nsClientMode`() {
        val nsId = "ns-123"
        val duration = 60_000L
        val existing = createCarbs(id = 1, nsId = nsId, amount = 50.0, duration = duration)
        val incoming = createCarbs(id = 0, nsId = nsId, amount = 50.0, duration = duration)

        whenever(carbsDao.getByNSId(nsId)).thenReturn(existing)

        val transaction = SyncNsCarbsTransaction(listOf(incoming), nsClientMode = true)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updated).isEmpty()
        verify(carbsDao, never()).updateExistingEntry(any())
    }

    @Test
    fun `handles both invalidation and duration update to shorter`() {
        val nsId = "ns-123"
        val existing = createCarbs(id = 1, nsId = nsId, amount = 50.0, duration = 60_000L, isValid = true)
        val incoming = createCarbs(id = 0, nsId = nsId, amount = 75.0, duration = 30_000L, isValid = false)

        whenever(carbsDao.getByNSId(nsId)).thenReturn(existing)

        val transaction = SyncNsCarbsTransaction(listOf(incoming), nsClientMode = true)
        transaction.database = database
        val result = transaction.run()

        assertThat(existing.isValid).isFalse()
        assertThat(existing.duration).isEqualTo(30_000L)
        assertThat(existing.amount).isEqualTo(75.0)
        assertThat(result.invalidated).hasSize(1)
        assertThat(result.updated).hasSize(1)

        verify(carbsDao, times(2)).updateExistingEntry(existing)
    }

    @Test
    fun `syncs multiple carbs`() {
        val carbs1 = createCarbs(id = 0, nsId = "ns-1", amount = 50.0, timestamp = 1000L)
        val carbs2 = createCarbs(id = 0, nsId = "ns-2", amount = 30.0, timestamp = 2000L)

        whenever(carbsDao.getByNSId("ns-1")).thenReturn(null)
        whenever(carbsDao.getByNSId("ns-2")).thenReturn(null)
        whenever(carbsDao.findByTimestamp(1000L)).thenReturn(null)
        whenever(carbsDao.findByTimestamp(2000L)).thenReturn(null)

        val transaction = SyncNsCarbsTransaction(listOf(carbs1, carbs2), nsClientMode = false)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.inserted).hasSize(2)

        verify(carbsDao).insertNewEntry(carbs1)
        verify(carbsDao).insertNewEntry(carbs2)
    }

    @Test
    fun `handles empty carbs list`() {
        val transaction = SyncNsCarbsTransaction(emptyList(), nsClientMode = false)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.inserted).isEmpty()
        assertThat(result.updated).isEmpty()
        assertThat(result.invalidated).isEmpty()
        assertThat(result.updatedNsId).isEmpty()

        verify(carbsDao, never()).insertNewEntry(any())
        verify(carbsDao, never()).updateExistingEntry(any())
    }

    @Test
    fun `updates both validity and nsId when timestamp matches`() {
        val nsId = "ns-123"
        val timestamp = 1000L
        val existing = createCarbs(id = 1, nsId = null, amount = 50.0, timestamp = timestamp, isValid = true)
        val incoming = createCarbs(id = 0, nsId = nsId, amount = 50.0, timestamp = timestamp, isValid = false)

        whenever(carbsDao.getByNSId(nsId)).thenReturn(null)
        whenever(carbsDao.findByTimestamp(timestamp)).thenReturn(existing)

        val transaction = SyncNsCarbsTransaction(listOf(incoming), nsClientMode = false)
        transaction.database = database
        val result = transaction.run()

        assertThat(existing.interfaceIDs.nightscoutId).isEqualTo(nsId)
        assertThat(existing.isValid).isFalse()
        assertThat(result.updatedNsId).hasSize(1)

        verify(carbsDao).updateExistingEntry(existing)
    }

    @Test
    fun `transaction result has correct structure`() {
        val result = SyncNsCarbsTransaction.TransactionResult()

        assertThat(result.updated).isEmpty()
        assertThat(result.updatedNsId).isEmpty()
        assertThat(result.inserted).isEmpty()
        assertThat(result.invalidated).isEmpty()
    }

    @Test
    fun `updates nsId when composite key matches but nsId not in DB`() {
        val pumpId = 12345L
        val pumpType = InterfaceIDs.PumpType.DANA_I
        val pumpSerial = "ABC123"
        val nsId = "ns-123"

        val existing = createCarbs(
            id = 1,
            nsId = null,
            amount = 50.0,
            timestamp = 1000L,
            pumpId = pumpId,
            pumpType = pumpType,
            pumpSerial = pumpSerial
        )

        val incoming = createCarbs(
            id = 0,
            nsId = nsId,
            amount = 50.0,
            timestamp = 1000L,
            pumpId = pumpId,
            pumpType = pumpType,
            pumpSerial = pumpSerial
        )

        whenever(carbsDao.getByNSId(nsId)).thenReturn(null)
        whenever(carbsDao.findByPumpIds(pumpId, pumpType, pumpSerial)).thenReturn(existing)

        val transaction = SyncNsCarbsTransaction(listOf(incoming), nsClientMode = false)
        transaction.database = database
        val result = transaction.run()

        assertThat(existing.interfaceIDs.nightscoutId).isEqualTo(nsId)
        assertThat(result.updatedNsId).hasSize(1)
        assertThat(result.updatedNsId[0]).isEqualTo(existing)
        assertThat(result.inserted).isEmpty()

        verify(carbsDao).updateExistingEntry(existing)
        verify(carbsDao, never()).insertNewEntry(any())
    }

    @Test
    fun `inserts both records when same pumpId but different pumpType`() {
        val pumpId = 12345L

        val carbs1 = createCarbs(
            id = 0,
            nsId = "ns-1",
            amount = 50.0,
            timestamp = 1000L,
            pumpId = pumpId,
            pumpType = InterfaceIDs.PumpType.DANA_I,
            pumpSerial = "DANA-ABC"
        )

        val carbs2 = createCarbs(
            id = 0,
            nsId = "ns-2",
            amount = 30.0,
            timestamp = 2000L,
            pumpId = pumpId,  // SAME pumpId
            pumpType = InterfaceIDs.PumpType.MEDTRONIC_522_722,  // DIFFERENT type
            pumpSerial = "MEDTRONIC-XYZ"
        )

        whenever(carbsDao.getByNSId("ns-1")).thenReturn(null)
        whenever(carbsDao.getByNSId("ns-2")).thenReturn(null)
        whenever(carbsDao.findByPumpIds(pumpId, InterfaceIDs.PumpType.DANA_I, "DANA-ABC")).thenReturn(null)
        whenever(carbsDao.findByPumpIds(pumpId, InterfaceIDs.PumpType.MEDTRONIC_522_722, "MEDTRONIC-XYZ")).thenReturn(null)
        whenever(carbsDao.findByTimestamp(1000L)).thenReturn(null)
        whenever(carbsDao.findByTimestamp(2000L)).thenReturn(null)

        val transaction = SyncNsCarbsTransaction(listOf(carbs1, carbs2), nsClientMode = false)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.inserted).hasSize(2)  // Both inserted, NO false deduplication

        verify(carbsDao).insertNewEntry(carbs1)
        verify(carbsDao).insertNewEntry(carbs2)
    }

    @Test
    fun `ignores duplicate NS record when composite key has different nsId`() {
        val pumpId = 12345L
        val pumpType = InterfaceIDs.PumpType.DANA_I
        val pumpSerial = "ABC123"

        val existing = createCarbs(
            id = 1,
            nsId = "ns-OLD",  // Already has DIFFERENT nsId
            amount = 50.0,
            timestamp = 1000L,
            pumpId = pumpId,
            pumpType = pumpType,
            pumpSerial = pumpSerial
        )

        val incoming = createCarbs(
            id = 0,
            nsId = "ns-NEW",  // MongoDB created duplicate with new _id
            amount = 50.0,
            timestamp = 1000L,
            pumpId = pumpId,
            pumpType = pumpType,
            pumpSerial = pumpSerial
        )

        whenever(carbsDao.getByNSId("ns-NEW")).thenReturn(null)
        whenever(carbsDao.findByPumpIds(pumpId, pumpType, pumpSerial)).thenReturn(existing)

        val transaction = SyncNsCarbsTransaction(listOf(incoming), nsClientMode = false)
        transaction.database = database
        val result = transaction.run()

        // Should IGNORE duplicate NS record
        assertThat(existing.interfaceIDs.nightscoutId).isEqualTo("ns-OLD")  // NOT overwritten
        assertThat(result.updatedNsId).isEmpty()  // NOT added to updated list
        assertThat(result.inserted).isEmpty()  // NOT inserted as new

        verify(carbsDao, never()).updateExistingEntry(any())
        verify(carbsDao, never()).insertNewEntry(any())
    }

    @Test
    fun `falls back to timestamp when partial pump data is null`() {
        val nsId = "ns-123"
        val timestamp = 1000L

        val existing = createCarbs(
            id = 1,
            nsId = null,
            amount = 50.0,
            timestamp = timestamp,
            pumpId = null,  // Partial/missing pump data
            pumpType = null,
            pumpSerial = null
        )

        val incoming = createCarbs(
            id = 0,
            nsId = nsId,
            amount = 50.0,
            timestamp = timestamp,
            pumpId = 12345L,  // Has pumpId
            pumpType = null,  // But pumpType is null
            pumpSerial = null  // And pumpSerial is null
        )

        whenever(carbsDao.getByNSId(nsId)).thenReturn(null)
        // Composite key check should NOT be called (null check fails)
        whenever(carbsDao.findByTimestamp(timestamp)).thenReturn(existing)

        val transaction = SyncNsCarbsTransaction(listOf(incoming), nsClientMode = false)
        transaction.database = database
        val result = transaction.run()

        // Should fall back to timestamp matching
        assertThat(existing.interfaceIDs.nightscoutId).isEqualTo(nsId)
        assertThat(result.updatedNsId).hasSize(1)

        verify(carbsDao, never()).findByPumpIds(any(), any(), any())  // NOT called
        verify(carbsDao).findByTimestamp(timestamp)  // Fallback used
        verify(carbsDao).updateExistingEntry(existing)
    }

    private fun createCarbs(
        id: Long,
        nsId: String?,
        amount: Double,
        timestamp: Long = System.currentTimeMillis(),
        duration: Long = 0L,
        isValid: Boolean = true,
        pumpId: Long? = null,
        pumpType: InterfaceIDs.PumpType? = null,
        pumpSerial: String? = null
    ): Carbs = Carbs(
        timestamp = timestamp,
        amount = amount,
        duration = duration,
        isValid = isValid,
        interfaceIDs_backing = InterfaceIDs(
            nightscoutId = nsId,
            pumpId = pumpId,
            pumpType = pumpType,
            pumpSerial = pumpSerial
        )
    ).also { it.id = id }
}

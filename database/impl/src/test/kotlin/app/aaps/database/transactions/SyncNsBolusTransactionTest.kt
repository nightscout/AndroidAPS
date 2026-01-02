package app.aaps.database.transactions

import app.aaps.database.DelegatedAppDatabase
import app.aaps.database.daos.BolusDao
import app.aaps.database.entities.Bolus
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

class SyncNsBolusTransactionTest {

    private lateinit var database: DelegatedAppDatabase
    private lateinit var bolusDao: BolusDao

    @BeforeEach
    fun setup() {
        bolusDao = mock()
        database = mock()
        whenever(database.bolusDao).thenReturn(bolusDao)
    }

    @Test
    fun `inserts new bolus when nsId not found and no timestamp match`() {
        val bolus = createBolus(id = 0, nsId = "ns-123", amount = 5.0, timestamp = 1000L)

        whenever(bolusDao.getByNSId("ns-123")).thenReturn(null)
        whenever(bolusDao.findByTimestamp(1000L)).thenReturn(null)

        val transaction = SyncNsBolusTransaction(listOf(bolus))
        transaction.database = database
        val result = transaction.run()

        assertThat(result.inserted).hasSize(1)
        assertThat(result.inserted[0]).isEqualTo(bolus)
        assertThat(result.updated).isEmpty()
        assertThat(result.invalidated).isEmpty()
        assertThat(result.updatedNsId).isEmpty()

        verify(bolusDao).insertNewEntry(bolus)
    }

    @Test
    fun `updates nsId when timestamp matches but nsId is null`() {
        val nsId = "ns-123"
        val timestamp = 1000L
        val existing = createBolus(id = 1, nsId = null, amount = 5.0, timestamp = timestamp)
        val incoming = createBolus(id = 0, nsId = nsId, amount = 5.0, timestamp = timestamp)

        whenever(bolusDao.getByNSId(nsId)).thenReturn(null)
        whenever(bolusDao.findByTimestamp(timestamp)).thenReturn(existing)

        val transaction = SyncNsBolusTransaction(listOf(incoming))
        transaction.database = database
        val result = transaction.run()

        assertThat(existing.interfaceIDs.nightscoutId).isEqualTo(nsId)
        assertThat(result.updatedNsId).hasSize(1)
        assertThat(result.updatedNsId[0]).isEqualTo(existing)
        assertThat(result.inserted).isEmpty()

        verify(bolusDao).updateExistingEntry(existing)
        verify(bolusDao, never()).insertNewEntry(any())
    }

    @Test
    fun `invalidates bolus when nsId exists and incoming is invalid`() {
        val nsId = "ns-123"
        val existing = createBolus(id = 1, nsId = nsId, amount = 5.0, isValid = true)
        val incoming = createBolus(id = 0, nsId = nsId, amount = 5.0, isValid = false)

        whenever(bolusDao.getByNSId(nsId)).thenReturn(existing)

        val transaction = SyncNsBolusTransaction(listOf(incoming))
        transaction.database = database
        val result = transaction.run()

        assertThat(existing.isValid).isFalse()
        assertThat(result.invalidated).hasSize(1)
        assertThat(result.invalidated[0]).isEqualTo(existing)

        verify(bolusDao).updateExistingEntry(existing)
    }

    @Test
    fun `does not invalidate already invalid bolus`() {
        val nsId = "ns-123"
        val existing = createBolus(id = 1, nsId = nsId, amount = 5.0, isValid = false)
        val incoming = createBolus(id = 0, nsId = nsId, amount = 5.0, isValid = false)

        whenever(bolusDao.getByNSId(nsId)).thenReturn(existing)

        val transaction = SyncNsBolusTransaction(listOf(incoming))
        transaction.database = database
        val result = transaction.run()

        assertThat(result.invalidated).isEmpty()
        verify(bolusDao, never()).updateExistingEntry(any())
    }

    @Test
    fun `updates amount when nsId exists and amount differs`() {
        val nsId = "ns-123"
        val existing = createBolus(id = 1, nsId = nsId, amount = 5.0)
        val incoming = createBolus(id = 0, nsId = nsId, amount = 7.5)

        whenever(bolusDao.getByNSId(nsId)).thenReturn(existing)

        val transaction = SyncNsBolusTransaction(listOf(incoming))
        transaction.database = database
        val result = transaction.run()

        assertThat(existing.amount).isEqualTo(7.5)
        assertThat(result.updated).hasSize(1)
        assertThat(result.updated[0]).isEqualTo(existing)

        verify(bolusDao).updateExistingEntry(existing)
    }

    @Test
    fun `does not update when nsId exists and amount is same`() {
        val nsId = "ns-123"
        val existing = createBolus(id = 1, nsId = nsId, amount = 5.0)
        val incoming = createBolus(id = 0, nsId = nsId, amount = 5.0)

        whenever(bolusDao.getByNSId(nsId)).thenReturn(existing)

        val transaction = SyncNsBolusTransaction(listOf(incoming))
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updated).isEmpty()
        verify(bolusDao, never()).updateExistingEntry(any())
    }

    @Test
    fun `handles both invalidation and amount update`() {
        val nsId = "ns-123"
        val existing = createBolus(id = 1, nsId = nsId, amount = 5.0, isValid = true)
        val incoming = createBolus(id = 0, nsId = nsId, amount = 7.5, isValid = false)

        whenever(bolusDao.getByNSId(nsId)).thenReturn(existing)

        val transaction = SyncNsBolusTransaction(listOf(incoming))
        transaction.database = database
        val result = transaction.run()

        assertThat(existing.isValid).isFalse()
        assertThat(existing.amount).isEqualTo(7.5)
        assertThat(result.invalidated).hasSize(1)
        assertThat(result.updated).hasSize(1)

        verify(bolusDao, times(2)).updateExistingEntry(existing)
    }

    @Test
    fun `syncs multiple boluses`() {
        val bolus1 = createBolus(id = 0, nsId = "ns-1", amount = 5.0, timestamp = 1000L)
        val bolus2 = createBolus(id = 0, nsId = "ns-2", amount = 3.0, timestamp = 2000L)

        whenever(bolusDao.getByNSId("ns-1")).thenReturn(null)
        whenever(bolusDao.getByNSId("ns-2")).thenReturn(null)
        whenever(bolusDao.findByTimestamp(1000L)).thenReturn(null)
        whenever(bolusDao.findByTimestamp(2000L)).thenReturn(null)

        val transaction = SyncNsBolusTransaction(listOf(bolus1, bolus2))
        transaction.database = database
        val result = transaction.run()

        assertThat(result.inserted).hasSize(2)

        verify(bolusDao).insertNewEntry(bolus1)
        verify(bolusDao).insertNewEntry(bolus2)
    }

    @Test
    fun `handles empty bolus list`() {
        val transaction = SyncNsBolusTransaction(emptyList())
        transaction.database = database
        val result = transaction.run()

        assertThat(result.inserted).isEmpty()
        assertThat(result.updated).isEmpty()
        assertThat(result.invalidated).isEmpty()
        assertThat(result.updatedNsId).isEmpty()

        verify(bolusDao, never()).insertNewEntry(any())
        verify(bolusDao, never()).updateExistingEntry(any())
    }

    @Test
    fun `updates both amount and validity when timestamp matches`() {
        val nsId = "ns-123"
        val timestamp = 1000L
        val existing = createBolus(id = 1, nsId = null, amount = 5.0, timestamp = timestamp, isValid = true)
        val incoming = createBolus(id = 0, nsId = nsId, amount = 7.5, timestamp = timestamp, isValid = false)

        whenever(bolusDao.getByNSId(nsId)).thenReturn(null)
        whenever(bolusDao.findByTimestamp(timestamp)).thenReturn(existing)

        val transaction = SyncNsBolusTransaction(listOf(incoming))
        transaction.database = database
        val result = transaction.run()

        assertThat(existing.interfaceIDs.nightscoutId).isEqualTo(nsId)
        assertThat(existing.amount).isEqualTo(7.5)
        assertThat(existing.isValid).isFalse()
        assertThat(result.updatedNsId).hasSize(1)

        verify(bolusDao).updateExistingEntry(existing)
    }

    @Test
    fun `skips bolus with null nsId when no timestamp match`() {
        val bolus = createBolus(id = 0, nsId = null, amount = 5.0, timestamp = 1000L)

        whenever(bolusDao.findByTimestamp(1000L)).thenReturn(null)

        val transaction = SyncNsBolusTransaction(listOf(bolus))
        transaction.database = database
        val result = transaction.run()

        assertThat(result.inserted).hasSize(1)

        verify(bolusDao).insertNewEntry(bolus)
    }

    @Test
    fun `transaction result has correct structure`() {
        val result = SyncNsBolusTransaction.TransactionResult()

        assertThat(result.updatedNsId).isEmpty()
        assertThat(result.inserted).isEmpty()
        assertThat(result.invalidated).isEmpty()
        assertThat(result.updated).isEmpty()
    }

    @Test
    fun `updates nsId when composite key matches but nsId not in DB`() {
        val pumpId = 12345L
        val pumpType = InterfaceIDs.PumpType.DANA_I
        val pumpSerial = "ABC123"
        val nsId = "ns-123"

        val existing = createBolus(
            id = 1,
            nsId = null,
            amount = 5.0,
            timestamp = 1000L,
            pumpId = pumpId,
            pumpType = pumpType,
            pumpSerial = pumpSerial
        )

        val incoming = createBolus(
            id = 0,
            nsId = nsId,
            amount = 5.0,
            timestamp = 1000L,
            pumpId = pumpId,
            pumpType = pumpType,
            pumpSerial = pumpSerial
        )

        whenever(bolusDao.getByNSId(nsId)).thenReturn(null)
        whenever(bolusDao.findByPumpIds(pumpId, pumpType, pumpSerial)).thenReturn(existing)

        val transaction = SyncNsBolusTransaction(listOf(incoming))
        transaction.database = database
        val result = transaction.run()

        assertThat(existing.interfaceIDs.nightscoutId).isEqualTo(nsId)
        assertThat(result.updatedNsId).hasSize(1)
        assertThat(result.updatedNsId[0]).isEqualTo(existing)
        assertThat(result.inserted).isEmpty()

        verify(bolusDao).updateExistingEntry(existing)
        verify(bolusDao, never()).insertNewEntry(any())
    }

    @Test
    fun `inserts both records when same pumpId but different pumpType`() {
        val pumpId = 12345L

        val bolus1 = createBolus(
            id = 0,
            nsId = "ns-1",
            amount = 5.0,
            timestamp = 1000L,
            pumpId = pumpId,
            pumpType = InterfaceIDs.PumpType.DANA_I,
            pumpSerial = "DANA-ABC"
        )

        val bolus2 = createBolus(
            id = 0,
            nsId = "ns-2",
            amount = 3.0,
            timestamp = 2000L,
            pumpId = pumpId,  // SAME pumpId
            pumpType = InterfaceIDs.PumpType.MEDTRONIC_522_722,  // DIFFERENT type
            pumpSerial = "MEDTRONIC-XYZ"
        )

        whenever(bolusDao.getByNSId("ns-1")).thenReturn(null)
        whenever(bolusDao.getByNSId("ns-2")).thenReturn(null)
        whenever(bolusDao.findByPumpIds(pumpId, InterfaceIDs.PumpType.DANA_I, "DANA-ABC")).thenReturn(null)
        whenever(bolusDao.findByPumpIds(pumpId, InterfaceIDs.PumpType.MEDTRONIC_522_722, "MEDTRONIC-XYZ")).thenReturn(null)
        whenever(bolusDao.findByTimestamp(1000L)).thenReturn(null)
        whenever(bolusDao.findByTimestamp(2000L)).thenReturn(null)

        val transaction = SyncNsBolusTransaction(listOf(bolus1, bolus2))
        transaction.database = database
        val result = transaction.run()

        assertThat(result.inserted).hasSize(2)  // Both inserted, NO false deduplication

        verify(bolusDao).insertNewEntry(bolus1)
        verify(bolusDao).insertNewEntry(bolus2)
    }

    @Test
    fun `ignores duplicate NS record when composite key has different nsId`() {
        val pumpId = 12345L
        val pumpType = InterfaceIDs.PumpType.DANA_I
        val pumpSerial = "ABC123"

        val existing = createBolus(
            id = 1,
            nsId = "ns-OLD",  // Already has DIFFERENT nsId
            amount = 5.0,
            timestamp = 1000L,
            pumpId = pumpId,
            pumpType = pumpType,
            pumpSerial = pumpSerial
        )

        val incoming = createBolus(
            id = 0,
            nsId = "ns-NEW",  // MongoDB created duplicate with new _id
            amount = 5.0,
            timestamp = 1000L,
            pumpId = pumpId,
            pumpType = pumpType,
            pumpSerial = pumpSerial
        )

        whenever(bolusDao.getByNSId("ns-NEW")).thenReturn(null)
        whenever(bolusDao.findByPumpIds(pumpId, pumpType, pumpSerial)).thenReturn(existing)

        val transaction = SyncNsBolusTransaction(listOf(incoming))
        transaction.database = database
        val result = transaction.run()

        // Should IGNORE duplicate NS record
        assertThat(existing.interfaceIDs.nightscoutId).isEqualTo("ns-OLD")  // NOT overwritten
        assertThat(result.updatedNsId).isEmpty()  // NOT added to updated list
        assertThat(result.inserted).isEmpty()  // NOT inserted as new

        verify(bolusDao, never()).updateExistingEntry(any())
        verify(bolusDao, never()).insertNewEntry(any())
    }

    @Test
    fun `falls back to timestamp when partial pump data is null`() {
        val nsId = "ns-123"
        val timestamp = 1000L

        val existing = createBolus(
            id = 1,
            nsId = null,
            amount = 5.0,
            timestamp = timestamp,
            pumpId = null,  // Partial/missing pump data
            pumpType = null,
            pumpSerial = null
        )

        val incoming = createBolus(
            id = 0,
            nsId = nsId,
            amount = 5.0,
            timestamp = timestamp,
            pumpId = 12345L,  // Has pumpId
            pumpType = null,  // But pumpType is null
            pumpSerial = null  // And pumpSerial is null
        )

        whenever(bolusDao.getByNSId(nsId)).thenReturn(null)
        // Composite key check should NOT be called (null check fails)
        whenever(bolusDao.findByTimestamp(timestamp)).thenReturn(existing)

        val transaction = SyncNsBolusTransaction(listOf(incoming))
        transaction.database = database
        val result = transaction.run()

        // Should fall back to timestamp matching
        assertThat(existing.interfaceIDs.nightscoutId).isEqualTo(nsId)
        assertThat(result.updatedNsId).hasSize(1)

        verify(bolusDao, never()).findByPumpIds(any(), any(), any())  // NOT called
        verify(bolusDao).findByTimestamp(timestamp)  // Fallback used
        verify(bolusDao).updateExistingEntry(existing)
    }

    private fun createBolus(
        id: Long,
        nsId: String?,
        amount: Double,
        timestamp: Long = System.currentTimeMillis(),
        type: Bolus.Type = Bolus.Type.NORMAL,
        isValid: Boolean = true,
        pumpId: Long? = null,
        pumpType: InterfaceIDs.PumpType? = null,
        pumpSerial: String? = null
    ): Bolus = Bolus(
        timestamp = timestamp,
        amount = amount,
        type = type,
        isValid = isValid,
        interfaceIDs_backing = InterfaceIDs(
            nightscoutId = nsId,
            pumpId = pumpId,
            pumpType = pumpType,
            pumpSerial = pumpSerial
        )
    ).also { it.id = id }
}

package app.aaps.database.transactions

import app.aaps.database.DelegatedAppDatabase
import app.aaps.database.daos.BolusDao
import app.aaps.database.entities.Bolus
import app.aaps.database.entities.embedments.InterfaceIDs
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class UpdateNsIdBolusTransactionTest {

    private lateinit var database: DelegatedAppDatabase
    private lateinit var bolusDao: BolusDao

    @BeforeEach
    fun setup() {
        bolusDao = mock()
        database = mock()
        whenever(database.bolusDao).thenReturn(bolusDao)
    }

    @Test
    fun `updates NS ID when different`() {
        val newNsId = "new-ns-id-123"
        val currentBolus = createBolus(id = 1, nsId = "old-ns-id")
        val updateBolus = createBolus(id = 1, nsId = newNsId)

        whenever(bolusDao.findById(1)).thenReturn(currentBolus)

        val transaction = UpdateNsIdBolusTransaction(listOf(updateBolus))
        transaction.database = database
        val result = transaction.run()

        assertThat(currentBolus.interfaceIDs.nightscoutId).isEqualTo(newNsId)
        assertThat(result.updatedNsId).hasSize(1)
        assertThat(result.updatedNsId[0]).isEqualTo(currentBolus)

        verify(bolusDao).updateExistingEntry(currentBolus)
    }

    @Test
    fun `does not update when NS ID is the same`() {
        val sameNsId = "same-ns-id"
        val currentBolus = createBolus(id = 1, nsId = sameNsId)
        val updateBolus = createBolus(id = 1, nsId = sameNsId)

        whenever(bolusDao.findById(1)).thenReturn(currentBolus)

        val transaction = UpdateNsIdBolusTransaction(listOf(updateBolus))
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updatedNsId).isEmpty()

        verify(bolusDao, never()).updateExistingEntry(any())
    }

    @Test
    fun `skips when bolus not found`() {
        val updateBolus = createBolus(id = 999, nsId = "new-ns-id")

        whenever(bolusDao.findById(999)).thenReturn(null)

        val transaction = UpdateNsIdBolusTransaction(listOf(updateBolus))
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updatedNsId).isEmpty()

        verify(bolusDao, never()).updateExistingEntry(any())
    }

    @Test
    fun `updates multiple boluses`() {
        val bolus1 = createBolus(id = 1, nsId = "old-1")
        val bolus2 = createBolus(id = 2, nsId = "old-2")
        val update1 = createBolus(id = 1, nsId = "new-1")
        val update2 = createBolus(id = 2, nsId = "new-2")

        whenever(bolusDao.findById(1)).thenReturn(bolus1)
        whenever(bolusDao.findById(2)).thenReturn(bolus2)

        val transaction = UpdateNsIdBolusTransaction(listOf(update1, update2))
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updatedNsId).hasSize(2)
        assertThat(bolus1.interfaceIDs.nightscoutId).isEqualTo("new-1")
        assertThat(bolus2.interfaceIDs.nightscoutId).isEqualTo("new-2")

        verify(bolusDao).updateExistingEntry(bolus1)
        verify(bolusDao).updateExistingEntry(bolus2)
    }

    @Test
    fun `handles mix of found and not found boluses`() {
        val currentBolus = createBolus(id = 1, nsId = "old")
        val update1 = createBolus(id = 1, nsId = "new")
        val update2 = createBolus(id = 999, nsId = "new-missing")

        whenever(bolusDao.findById(1)).thenReturn(currentBolus)
        whenever(bolusDao.findById(999)).thenReturn(null)

        val transaction = UpdateNsIdBolusTransaction(listOf(update1, update2))
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updatedNsId).hasSize(1)
        assertThat(result.updatedNsId[0]).isEqualTo(currentBolus)
    }

    @Test
    fun `handles empty bolus list`() {
        val transaction = UpdateNsIdBolusTransaction(emptyList())
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updatedNsId).isEmpty()

        verify(bolusDao, never()).updateExistingEntry(any())
    }

    @Test
    fun `preserves other bolus fields when updating NS ID`() {
        val amount = 5.5
        val timestamp = 123456789L
        val type = Bolus.Type.NORMAL
        val currentBolus = createBolus(
            id = 1,
            nsId = "old",
            amount = amount,
            timestamp = timestamp,
            type = type
        )
        val updateBolus = createBolus(id = 1, nsId = "new")

        whenever(bolusDao.findById(1)).thenReturn(currentBolus)

        val transaction = UpdateNsIdBolusTransaction(listOf(updateBolus))
        transaction.database = database
        transaction.run()

        assertThat(currentBolus.amount).isEqualTo(amount)
        assertThat(currentBolus.timestamp).isEqualTo(timestamp)
        assertThat(currentBolus.type).isEqualTo(type)
    }

    @Test
    fun `updates from null NS ID to actual value`() {
        val currentBolus = createBolus(id = 1, nsId = null)
        val updateBolus = createBolus(id = 1, nsId = "new-ns-id")

        whenever(bolusDao.findById(1)).thenReturn(currentBolus)

        val transaction = UpdateNsIdBolusTransaction(listOf(updateBolus))
        transaction.database = database
        val result = transaction.run()

        assertThat(currentBolus.interfaceIDs.nightscoutId).isEqualTo("new-ns-id")
        assertThat(result.updatedNsId).hasSize(1)

        verify(bolusDao).updateExistingEntry(currentBolus)
    }

    @Test
    fun `updates from actual NS ID to null`() {
        val currentBolus = createBolus(id = 1, nsId = "existing-id")
        val updateBolus = createBolus(id = 1, nsId = null)

        whenever(bolusDao.findById(1)).thenReturn(currentBolus)

        val transaction = UpdateNsIdBolusTransaction(listOf(updateBolus))
        transaction.database = database
        val result = transaction.run()

        assertThat(currentBolus.interfaceIDs.nightscoutId).isNull()
        assertThat(result.updatedNsId).hasSize(1)

        verify(bolusDao).updateExistingEntry(currentBolus)
    }

    @Test
    fun `transaction result has correct structure`() {
        val result = UpdateNsIdBolusTransaction.TransactionResult()

        assertThat(result.updatedNsId).isEmpty()
        assertThat(result.updatedNsId).isInstanceOf(MutableList::class.java)
    }

    private fun createBolus(
        id: Long,
        nsId: String?,
        amount: Double = 5.0,
        timestamp: Long = System.currentTimeMillis(),
        type: Bolus.Type = Bolus.Type.NORMAL
    ): Bolus = Bolus(
        timestamp = timestamp,
        amount = amount,
        type = type,
        interfaceIDs_backing = InterfaceIDs(nightscoutId = nsId)
    ).also { it.id = id }
}

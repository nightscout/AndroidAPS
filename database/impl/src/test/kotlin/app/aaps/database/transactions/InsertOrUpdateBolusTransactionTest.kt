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

class InsertOrUpdateBolusTransactionTest {

    private lateinit var database: DelegatedAppDatabase
    private lateinit var bolusDao: BolusDao

    @BeforeEach
    fun setup() {
        bolusDao = mock()
        database = mock()
        whenever(database.bolusDao).thenReturn(bolusDao)
    }

    @Test
    fun `inserts new bolus when id not found`() {
        val bolus = createBolus(id = 1, amount = 5.0)

        whenever(bolusDao.findById(1)).thenReturn(null)

        val transaction = InsertOrUpdateBolusTransaction(bolus)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.inserted).hasSize(1)
        assertThat(result.inserted[0]).isEqualTo(bolus)
        assertThat(result.updated).isEmpty()

        verify(bolusDao).insertNewEntry(bolus)
        verify(bolusDao, never()).updateExistingEntry(any())
    }

    @Test
    fun `updates existing bolus when id found`() {
        val bolus = createBolus(id = 1, amount = 5.0)
        val existing = createBolus(id = 1, amount = 3.0)

        whenever(bolusDao.findById(1)).thenReturn(existing)

        val transaction = InsertOrUpdateBolusTransaction(bolus)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updated).hasSize(1)
        assertThat(result.updated[0]).isEqualTo(bolus)
        assertThat(result.inserted).isEmpty()

        verify(bolusDao).updateExistingEntry(bolus)
        verify(bolusDao, never()).insertNewEntry(any())
    }

    @Test
    fun `inserts SMB bolus`() {
        val bolus = createBolus(id = 1, amount = 0.5, type = Bolus.Type.SMB)

        whenever(bolusDao.findById(1)).thenReturn(null)

        val transaction = InsertOrUpdateBolusTransaction(bolus)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.inserted).hasSize(1)
        assertThat(result.inserted[0].type).isEqualTo(Bolus.Type.SMB)
    }

    @Test
    fun `updates bolus amount`() {
        val existing = createBolus(id = 1, amount = 3.0)
        val updated = createBolus(id = 1, amount = 7.5)

        whenever(bolusDao.findById(1)).thenReturn(existing)

        val transaction = InsertOrUpdateBolusTransaction(updated)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updated).hasSize(1)
        assertThat(result.updated[0].amount).isEqualTo(7.5)
    }

    @Test
    fun `inserts invalid bolus`() {
        val bolus = createBolus(id = 1, amount = 5.0, isValid = false)

        whenever(bolusDao.findById(1)).thenReturn(null)

        val transaction = InsertOrUpdateBolusTransaction(bolus)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.inserted).hasSize(1)
        assertThat(result.inserted[0].isValid).isFalse()
    }

    @Test
    fun `transaction result has correct structure`() {
        val result = InsertOrUpdateBolusTransaction.TransactionResult()

        assertThat(result.inserted).isEmpty()
        assertThat(result.updated).isEmpty()
    }

    private fun createBolus(
        id: Long,
        amount: Double,
        type: Bolus.Type = Bolus.Type.NORMAL,
        isValid: Boolean = true
    ): Bolus = Bolus(
        timestamp = System.currentTimeMillis(),
        amount = amount,
        type = type,
        isValid = isValid,
        interfaceIDs_backing = InterfaceIDs()
    ).also { it.id = id }
}

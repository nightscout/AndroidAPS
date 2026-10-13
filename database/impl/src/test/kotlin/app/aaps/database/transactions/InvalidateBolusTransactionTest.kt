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

class InvalidateBolusTransactionTest {

    private lateinit var database: DelegatedAppDatabase
    private lateinit var bolusDao: BolusDao

    @BeforeEach
    fun setup() {
        bolusDao = mock()
        database = mock()
        whenever(database.bolusDao).thenReturn(bolusDao)
    }

    @Test
    fun `invalidates valid bolus`() {
        val bolus = createBolus(id = 1, isValid = true)

        whenever(bolusDao.findById(1)).thenReturn(bolus)

        val transaction = InvalidateBolusTransaction(id = 1)
        transaction.database = database
        val result = transaction.run()

        assertThat(bolus.isValid).isFalse()
        assertThat(result.invalidated).hasSize(1)
        assertThat(result.invalidated[0]).isEqualTo(bolus)

        verify(bolusDao).updateExistingEntry(bolus)
    }

    @Test
    fun `does not update already invalid bolus`() {
        val bolus = createBolus(id = 1, isValid = false)

        whenever(bolusDao.findById(1)).thenReturn(bolus)

        val transaction = InvalidateBolusTransaction(id = 1)
        transaction.database = database
        val result = transaction.run()

        assertThat(bolus.isValid).isFalse()
        assertThat(result.invalidated).isEmpty()

        verify(bolusDao, never()).updateExistingEntry(any())
    }

    @Test
    fun `throws exception when bolus not found`() {
        whenever(bolusDao.findById(999)).thenReturn(null)

        val transaction = InvalidateBolusTransaction(id = 999)
        transaction.database = database

        try {
            transaction.run()
            throw AssertionError("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("There is no such Bolus with the specified ID")
        }
    }

    @Test
    fun `preserves bolus amount when invalidating`() {
        val amount = 5.5
        val bolus = createBolus(id = 1, isValid = true, amount = amount)

        whenever(bolusDao.findById(1)).thenReturn(bolus)

        val transaction = InvalidateBolusTransaction(id = 1)
        transaction.database = database
        transaction.run()

        assertThat(bolus.amount).isEqualTo(amount)
        assertThat(bolus.isValid).isFalse()
    }

    @Test
    fun `invalidates normal bolus`() {
        val bolus = createBolus(id = 1, type = Bolus.Type.NORMAL, isValid = true)

        whenever(bolusDao.findById(1)).thenReturn(bolus)

        val transaction = InvalidateBolusTransaction(id = 1)
        transaction.database = database
        val result = transaction.run()

        assertThat(bolus.isValid).isFalse()
        assertThat(result.invalidated).hasSize(1)
    }

    @Test
    fun `invalidates SMB bolus`() {
        val bolus = createBolus(id = 2, type = Bolus.Type.SMB, isValid = true)

        whenever(bolusDao.findById(2)).thenReturn(bolus)

        val transaction = InvalidateBolusTransaction(id = 2)
        transaction.database = database
        val result = transaction.run()

        assertThat(bolus.isValid).isFalse()
        assertThat(result.invalidated).hasSize(1)
    }

    @Test
    fun `transaction result has correct structure`() {
        val result = InvalidateBolusTransaction.TransactionResult()

        assertThat(result.invalidated).isEmpty()
        assertThat(result.invalidated).isInstanceOf(MutableList::class.java)
    }

    private fun createBolus(
        id: Long,
        type: Bolus.Type = Bolus.Type.NORMAL,
        amount: Double = 5.0,
        isValid: Boolean = true
    ): Bolus = Bolus(
        timestamp = System.currentTimeMillis(),
        amount = amount,
        type = type,
        isValid = isValid,
        interfaceIDs_backing = InterfaceIDs()
    ).also { it.id = id }
}

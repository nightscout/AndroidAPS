package app.aaps.database.transactions

import app.aaps.database.DelegatedAppDatabase
import app.aaps.database.daos.CarbsDao
import app.aaps.database.entities.Carbs
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

class InvalidateCarbsTransactionTest {

    private lateinit var database: DelegatedAppDatabase
    private lateinit var carbsDao: CarbsDao

    @BeforeEach
    fun setup() {
        carbsDao = mock()
        database = mock()
        whenever(database.carbsDao).thenReturn(carbsDao)
    }

    @Test
    fun `invalidates valid carbs`() = runTest {
        val carbs = createCarbs(id = 1, isValid = true, amount = 50.0)

        whenever(carbsDao.findById(1)).thenReturn(carbs)

        val transaction = InvalidateCarbsTransaction(id = 1)
        transaction.database = database
        val result = transaction.run()

        assertThat(carbs.isValid).isFalse()
        assertThat(result.invalidated).hasSize(1)
        assertThat(result.invalidated[0]).isEqualTo(carbs)

        verify(carbsDao).updateExistingEntry(carbs)
    }

    @Test
    fun `does not update already invalid carbs`() = runTest {
        val carbs = createCarbs(id = 1, isValid = false, amount = 50.0)

        whenever(carbsDao.findById(1)).thenReturn(carbs)

        val transaction = InvalidateCarbsTransaction(id = 1)
        transaction.database = database
        val result = transaction.run()

        assertThat(carbs.isValid).isFalse()
        assertThat(result.invalidated).isEmpty()

        verify(carbsDao, never()).updateExistingEntry(any())
    }

    @Test
    fun `throws exception when carbs not found`() = runTest {
        whenever(carbsDao.findById(999)).thenReturn(null)

        val transaction = InvalidateCarbsTransaction(id = 999)
        transaction.database = database

        try {
            transaction.run()
            throw AssertionError("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("There is no such Carbs with the specified ID")
        }
    }

    @Test
    fun `transaction result has correct structure`() = runTest {
        val result = InvalidateCarbsTransaction.TransactionResult()

        assertThat(result.invalidated).isEmpty()
        assertThat(result.invalidated).isInstanceOf(MutableList::class.java)
    }

    @Test
    fun `preserves carbs amount and timestamp when invalidating`() = runTest {
        val amount = 75.0
        val timestamp = 123456789L
        val carbs = createCarbs(id = 1, isValid = true, amount = amount, timestamp = timestamp)

        whenever(carbsDao.findById(1)).thenReturn(carbs)

        val transaction = InvalidateCarbsTransaction(id = 1)
        transaction.database = database
        transaction.run()

        assertThat(carbs.amount).isEqualTo(amount)
        assertThat(carbs.timestamp).isEqualTo(timestamp)
        assertThat(carbs.isValid).isFalse()
    }

    @Test
    fun `invalidates extended carbs with duration`() = runTest {
        val carbs = createCarbs(id = 1, isValid = true, amount = 50.0, duration = 120_000L)

        whenever(carbsDao.findById(1)).thenReturn(carbs)

        val transaction = InvalidateCarbsTransaction(id = 1)
        transaction.database = database
        val result = transaction.run()

        assertThat(carbs.isValid).isFalse()
        assertThat(carbs.duration).isEqualTo(120_000L)
        assertThat(result.invalidated).hasSize(1)
    }

    private fun createCarbs(
        id: Long,
        isValid: Boolean,
        amount: Double,
        timestamp: Long = System.currentTimeMillis(),
        duration: Long = 0L
    ): Carbs = Carbs(
        timestamp = timestamp,
        amount = amount,
        duration = duration,
        isValid = isValid,
        interfaceIDs_backing = InterfaceIDs()
    ).also { it.id = id }
}

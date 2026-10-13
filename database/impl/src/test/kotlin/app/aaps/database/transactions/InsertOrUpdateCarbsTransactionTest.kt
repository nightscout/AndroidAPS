package app.aaps.database.transactions

import app.aaps.database.DelegatedAppDatabase
import app.aaps.database.daos.CarbsDao
import app.aaps.database.entities.Carbs
import app.aaps.database.entities.embedments.InterfaceIDs
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class InsertOrUpdateCarbsTransactionTest {

    private lateinit var database: DelegatedAppDatabase
    private lateinit var carbsDao: CarbsDao

    @BeforeEach
    fun setup() {
        carbsDao = mock()
        database = mock()
        whenever(database.carbsDao).thenReturn(carbsDao)
    }

    @Test
    fun `inserts new carbs when id not found`() {
        val carbs = createCarbs(id = 1, amount = 50.0)

        whenever(carbsDao.findById(1)).thenReturn(null)

        val transaction = InsertOrUpdateCarbsTransaction(carbs)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.inserted).hasSize(1)
        assertThat(result.inserted[0]).isEqualTo(carbs)
        assertThat(result.updated).isEmpty()

        verify(carbsDao).insertNewEntry(carbs)
        verify(carbsDao, never()).updateExistingEntry(any())
    }

    @Test
    fun `updates existing carbs when id found`() {
        val carbs = createCarbs(id = 1, amount = 50.0)
        val existing = createCarbs(id = 1, amount = 30.0)

        whenever(carbsDao.findById(1)).thenReturn(existing)

        val transaction = InsertOrUpdateCarbsTransaction(carbs)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updated).hasSize(1)
        assertThat(result.updated[0]).isEqualTo(carbs)
        assertThat(result.inserted).isEmpty()

        verify(carbsDao).updateExistingEntry(carbs)
        verify(carbsDao, never()).insertNewEntry(any())
    }

    @Test
    fun `handles carbs with zero amount`() {
        val carbs = createCarbs(id = 1, amount = 0.0)

        whenever(carbsDao.findById(1)).thenReturn(null)

        val transaction = InsertOrUpdateCarbsTransaction(carbs)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.inserted).hasSize(1)
        assertThat(result.inserted[0].amount).isEqualTo(0.0)
    }

    @Test
    fun `handles carbs with extended duration`() {
        val carbs = createCarbs(id = 1, amount = 50.0, duration = 120_000L)

        whenever(carbsDao.findById(1)).thenReturn(null)

        val transaction = InsertOrUpdateCarbsTransaction(carbs)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.inserted).hasSize(1)
        assertThat(result.inserted[0].duration).isEqualTo(120_000L)
    }

    @Test
    fun `handles carbs without duration`() {
        val carbs = createCarbs(id = 1, amount = 50.0, duration = 0L)

        whenever(carbsDao.findById(1)).thenReturn(null)

        val transaction = InsertOrUpdateCarbsTransaction(carbs)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.inserted).hasSize(1)
        assertThat(result.inserted[0].duration).isEqualTo(0L)
    }

    @Test
    fun `updates invalid carbs`() {
        val carbs = createCarbs(id = 1, amount = 50.0, isValid = false)
        val existing = createCarbs(id = 1, amount = 30.0, isValid = true)

        whenever(carbsDao.findById(1)).thenReturn(existing)

        val transaction = InsertOrUpdateCarbsTransaction(carbs)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updated).hasSize(1)
        verify(carbsDao).updateExistingEntry(carbs)
    }

    @Test
    fun `transaction result has correct structure`() {
        val result = InsertOrUpdateCarbsTransaction.TransactionResult()

        assertThat(result.inserted).isEmpty()
        assertThat(result.updated).isEmpty()
        assertThat(result.inserted).isInstanceOf(MutableList::class.java)
        assertThat(result.updated).isInstanceOf(MutableList::class.java)
    }

    @Test
    fun `preserves carbs timestamp on update`() {
        val timestamp = 123456789L
        val carbs = createCarbs(id = 1, amount = 50.0, timestamp = timestamp)
        val existing = createCarbs(id = 1, amount = 30.0, timestamp = timestamp)

        whenever(carbsDao.findById(1)).thenReturn(existing)

        val transaction = InsertOrUpdateCarbsTransaction(carbs)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updated[0].timestamp).isEqualTo(timestamp)
    }

    @Test
    fun `inserts carbs with notes`() {
        val carbs = createCarbs(id = 1, amount = 50.0, notes = "Test meal")

        whenever(carbsDao.findById(1)).thenReturn(null)

        val transaction = InsertOrUpdateCarbsTransaction(carbs)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.inserted).hasSize(1)
        assertThat(result.inserted[0].notes).isEqualTo("Test meal")
    }

    @Test
    fun `updates carbs with different amounts`() {
        val existing = createCarbs(id = 1, amount = 30.0)
        val updated = createCarbs(id = 1, amount = 75.0)

        whenever(carbsDao.findById(1)).thenReturn(existing)

        val transaction = InsertOrUpdateCarbsTransaction(updated)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updated).hasSize(1)
        assertThat(result.updated[0].amount).isEqualTo(75.0)
    }

    private fun createCarbs(
        id: Long,
        amount: Double,
        timestamp: Long = System.currentTimeMillis(),
        duration: Long = 0L,
        isValid: Boolean = true,
        notes: String? = null
    ): Carbs = Carbs(
        timestamp = timestamp,
        amount = amount,
        duration = duration,
        isValid = isValid,
        notes = notes,
        interfaceIDs_backing = InterfaceIDs()
    ).also { it.id = id }
}

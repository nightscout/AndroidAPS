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

class InsertIfNewByTimestampCarbsTransactionTest {

    private lateinit var database: DelegatedAppDatabase
    private lateinit var carbsDao: CarbsDao

    @BeforeEach
    fun setup() {
        carbsDao = mock()
        database = mock()
        whenever(database.carbsDao).thenReturn(carbsDao)
    }

    @Test
    fun `inserts carbs when timestamp not found`() {
        val timestamp = 123456789L
        val carbs = createCarbs(timestamp = timestamp, amount = 50.0)

        whenever(carbsDao.findByTimestamp(timestamp)).thenReturn(null)

        val transaction = InsertIfNewByTimestampCarbsTransaction(carbs)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.inserted).hasSize(1)
        assertThat(result.inserted[0]).isEqualTo(carbs)
        assertThat(result.existing).isEmpty()

        verify(carbsDao).insertNewEntry(carbs)
    }

    @Test
    fun `does not insert when timestamp exists`() {
        val timestamp = 123456789L
        val existing = createCarbs(timestamp = timestamp, amount = 30.0)
        val incoming = createCarbs(timestamp = timestamp, amount = 50.0)

        whenever(carbsDao.findByTimestamp(timestamp)).thenReturn(existing)

        val transaction = InsertIfNewByTimestampCarbsTransaction(incoming)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.inserted).isEmpty()
        assertThat(result.existing).hasSize(1)
        assertThat(result.existing[0]).isEqualTo(incoming)

        verify(carbsDao, never()).insertNewEntry(any())
    }

    @Test
    fun `handles different amounts at same timestamp`() {
        val timestamp = 123456789L
        val existing = createCarbs(timestamp = timestamp, amount = 30.0)
        val incoming = createCarbs(timestamp = timestamp, amount = 50.0)

        whenever(carbsDao.findByTimestamp(timestamp)).thenReturn(existing)

        val transaction = InsertIfNewByTimestampCarbsTransaction(incoming)
        transaction.database = database
        val result = transaction.run()

        // Should not insert because timestamp exists, even though amounts differ
        assertThat(result.inserted).isEmpty()
        assertThat(result.existing).hasSize(1)
    }

    @Test
    fun `inserts carbs with extended duration`() {
        val timestamp = 123456789L
        val carbs = createCarbs(timestamp = timestamp, amount = 50.0, duration = 120_000L)

        whenever(carbsDao.findByTimestamp(timestamp)).thenReturn(null)

        val transaction = InsertIfNewByTimestampCarbsTransaction(carbs)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.inserted).hasSize(1)
        assertThat(result.inserted[0].duration).isEqualTo(120_000L)
    }

    @Test
    fun `inserts carbs with zero amount`() {
        val timestamp = 123456789L
        val carbs = createCarbs(timestamp = timestamp, amount = 0.0)

        whenever(carbsDao.findByTimestamp(timestamp)).thenReturn(null)

        val transaction = InsertIfNewByTimestampCarbsTransaction(carbs)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.inserted).hasSize(1)
        assertThat(result.inserted[0].amount).isEqualTo(0.0)
    }

    @Test
    fun `does not insert invalid carbs when timestamp exists`() {
        val timestamp = 123456789L
        val existing = createCarbs(timestamp = timestamp, amount = 30.0, isValid = true)
        val incoming = createCarbs(timestamp = timestamp, amount = 50.0, isValid = false)

        whenever(carbsDao.findByTimestamp(timestamp)).thenReturn(existing)

        val transaction = InsertIfNewByTimestampCarbsTransaction(incoming)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.inserted).isEmpty()
        assertThat(result.existing).hasSize(1)
    }

    @Test
    fun `inserts invalid carbs when timestamp not found`() {
        val timestamp = 123456789L
        val carbs = createCarbs(timestamp = timestamp, amount = 50.0, isValid = false)

        whenever(carbsDao.findByTimestamp(timestamp)).thenReturn(null)

        val transaction = InsertIfNewByTimestampCarbsTransaction(carbs)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.inserted).hasSize(1)
        assertThat(result.inserted[0].isValid).isFalse()
    }

    @Test
    fun `preserves all carbs fields when inserting`() {
        val timestamp = 123456789L
        val amount = 75.0
        val duration = 90_000L
        val notes = "Lunch"
        val carbs = createCarbs(
            timestamp = timestamp,
            amount = amount,
            duration = duration,
            notes = notes
        )

        whenever(carbsDao.findByTimestamp(timestamp)).thenReturn(null)

        val transaction = InsertIfNewByTimestampCarbsTransaction(carbs)
        transaction.database = database
        val result = transaction.run()

        val inserted = result.inserted[0]
        assertThat(inserted.timestamp).isEqualTo(timestamp)
        assertThat(inserted.amount).isEqualTo(amount)
        assertThat(inserted.duration).isEqualTo(duration)
        assertThat(inserted.notes).isEqualTo(notes)
    }

    @Test
    fun `transaction result has correct structure`() {
        val result = InsertIfNewByTimestampCarbsTransaction.TransactionResult()

        assertThat(result.inserted).isEmpty()
        assertThat(result.existing).isEmpty()
        assertThat(result.inserted).isInstanceOf(MutableList::class.java)
        assertThat(result.existing).isInstanceOf(MutableList::class.java)
    }

    @Test
    fun `handles carbs with notes when timestamp not found`() {
        val timestamp = 123456789L
        val carbs = createCarbs(timestamp = timestamp, amount = 50.0, notes = "Dinner")

        whenever(carbsDao.findByTimestamp(timestamp)).thenReturn(null)

        val transaction = InsertIfNewByTimestampCarbsTransaction(carbs)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.inserted).hasSize(1)
        assertThat(result.inserted[0].notes).isEqualTo("Dinner")
    }

    @Test
    fun `checks timestamp equality exactly`() {
        val timestamp1 = 123456789L
        val timestamp2 = 123456790L // One millisecond difference
        val existing = createCarbs(timestamp = timestamp1, amount = 30.0)
        val incoming = createCarbs(timestamp = timestamp2, amount = 50.0)

        whenever(carbsDao.findByTimestamp(timestamp1)).thenReturn(existing)
        whenever(carbsDao.findByTimestamp(timestamp2)).thenReturn(null)

        val transaction = InsertIfNewByTimestampCarbsTransaction(incoming)
        transaction.database = database
        val result = transaction.run()

        // Should insert because timestamps are different
        assertThat(result.inserted).hasSize(1)
        assertThat(result.existing).isEmpty()

        verify(carbsDao).insertNewEntry(incoming)
    }

    private fun createCarbs(
        timestamp: Long,
        amount: Double,
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
    )
}

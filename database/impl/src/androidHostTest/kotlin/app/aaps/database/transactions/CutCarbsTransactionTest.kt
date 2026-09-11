package app.aaps.database.transactions

import app.aaps.database.DelegatedAppDatabase
import app.aaps.database.daos.CarbsDao
import app.aaps.database.entities.Carbs
import app.aaps.database.entities.embedments.InterfaceIDs
import app.aaps.database.entities.interfaces.end
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.verify
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class CutCarbsTransactionTest {

    private lateinit var database: DelegatedAppDatabase
    private lateinit var carbsDao: CarbsDao

    @BeforeEach
    fun setup() {
        carbsDao = mock()
        database = mock()
        whenever(database.carbsDao).thenReturn(carbsDao)
    }

    @Test
    fun `invalidates carbs when end time equals start time`() = runTest {
        val timestamp = 1000L
        val carbs = createCarbs(
            id = 1,
            timestamp = timestamp,
            duration = 60_000L,
            amount = 50.0
        )

        whenever(carbsDao.findById(1)).thenReturn(carbs)

        val transaction = CutCarbsTransaction(id = 1, end = timestamp)
        transaction.database = database
        val result = transaction.run()

        assertThat(carbs.isValid).isFalse()
        assertThat(result.invalidated).hasSize(1)
        assertThat(result.invalidated[0]).isEqualTo(carbs)
        assertThat(result.updated).isEmpty()

        verify(carbsDao).updateExistingEntry(carbs)
    }

    @Test
    fun `cuts carbs proportionally when end is in the middle`() = runTest {
        val timestamp = 1000L
        val duration = 60_000L // 60 seconds
        val amount = 100.0
        val carbs = createCarbs(
            id = 1,
            timestamp = timestamp,
            duration = duration,
            amount = amount
        )

        whenever(carbsDao.findById(1)).thenReturn(carbs)

        val halfwayPoint = timestamp + (duration / 2)
        val transaction = CutCarbsTransaction(id = 1, end = halfwayPoint)
        transaction.database = database
        val result = transaction.run()

        // Should be cut to 50% of original amount
        assertThat(carbs.amount).isEqualTo(50.0)
        assertThat(carbs.end).isEqualTo(halfwayPoint)
        assertThat(result.updated).hasSize(1)
        assertThat(result.updated[0]).isEqualTo(carbs)
        assertThat(result.invalidated).isEmpty()

        verify(carbsDao).updateExistingEntry(carbs)
    }

    @Test
    fun `cuts carbs proportionally at 25 percent`() = runTest {
        val timestamp = 1000L
        val duration = 60_000L
        val amount = 100.0
        val carbs = createCarbs(
            id = 1,
            timestamp = timestamp,
            duration = duration,
            amount = amount
        )

        whenever(carbsDao.findById(1)).thenReturn(carbs)

        val quarterPoint = timestamp + (duration / 4)
        val transaction = CutCarbsTransaction(id = 1, end = quarterPoint)
        transaction.database = database
        val result = transaction.run()

        // Should be cut to 25% of original amount
        assertThat(carbs.amount).isEqualTo(25.0)
        assertThat(carbs.end).isEqualTo(quarterPoint)
        assertThat(result.updated).hasSize(1)
    }

    @Test
    fun `cuts carbs proportionally at 75 percent`() = runTest {
        val timestamp = 1000L
        val duration = 60_000L
        val amount = 100.0
        val carbs = createCarbs(
            id = 1,
            timestamp = timestamp,
            duration = duration,
            amount = amount
        )

        whenever(carbsDao.findById(1)).thenReturn(carbs)

        val threeQuarterPoint = timestamp + (duration * 3 / 4)
        val transaction = CutCarbsTransaction(id = 1, end = threeQuarterPoint)
        transaction.database = database
        val result = transaction.run()

        // Should be cut to 75% of original amount
        assertThat(carbs.amount).isEqualTo(75.0)
        assertThat(carbs.end).isEqualTo(threeQuarterPoint)
        assertThat(result.updated).hasSize(1)
    }

    @Test
    fun `rounds carbs amount to nearest integer`() = runTest {
        val timestamp = 1000L
        val duration = 60_000L
        val amount = 100.0
        val carbs = createCarbs(
            id = 1,
            timestamp = timestamp,
            duration = duration,
            amount = amount
        )

        whenever(carbsDao.findById(1)).thenReturn(carbs)

        // 33.33% should round to 33
        val oneThirdPoint = timestamp + (duration / 3)
        val transaction = CutCarbsTransaction(id = 1, end = oneThirdPoint)
        transaction.database = database
        transaction.run()

        assertThat(carbs.amount).isEqualTo(33.0)
    }

    @Test
    fun `does nothing when end time is before start time`() = runTest {
        val timestamp = 1000L
        val carbs = createCarbs(
            id = 1,
            timestamp = timestamp,
            duration = 60_000L,
            amount = 50.0
        )
        val originalAmount = carbs.amount
        val originalEnd = carbs.end

        whenever(carbsDao.findById(1)).thenReturn(carbs)

        val beforeStart = timestamp - 1000L
        val transaction = CutCarbsTransaction(id = 1, end = beforeStart)
        transaction.database = database
        val result = transaction.run()

        // Nothing should change
        assertThat(carbs.amount).isEqualTo(originalAmount)
        assertThat(carbs.end).isEqualTo(originalEnd)
        assertThat(carbs.isValid).isTrue()
        assertThat(result.invalidated).isEmpty()
        assertThat(result.updated).isEmpty()
    }

    @Test
    fun `does nothing when end time is after carbs end time`() = runTest {
        val timestamp = 1000L
        val duration = 60_000L
        val carbs = createCarbs(
            id = 1,
            timestamp = timestamp,
            duration = duration,
            amount = 50.0
        )
        val originalAmount = carbs.amount
        val originalEnd = carbs.end

        whenever(carbsDao.findById(1)).thenReturn(carbs)

        val afterEnd = timestamp + duration + 1000L
        val transaction = CutCarbsTransaction(id = 1, end = afterEnd)
        transaction.database = database
        val result = transaction.run()

        // Nothing should change
        assertThat(carbs.amount).isEqualTo(originalAmount)
        assertThat(carbs.end).isEqualTo(originalEnd)
        assertThat(carbs.isValid).isTrue()
        assertThat(result.invalidated).isEmpty()
        assertThat(result.updated).isEmpty()
    }

    @Test
    fun `throws exception when carbs not found`() = runTest {
        whenever(carbsDao.findById(999)).thenReturn(null)

        val transaction = CutCarbsTransaction(id = 999, end = 2000L)
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
        val result = CutCarbsTransaction.TransactionResult()

        assertThat(result.invalidated).isEmpty()
        assertThat(result.updated).isEmpty()
    }

    private fun createCarbs(
        id: Long,
        timestamp: Long,
        duration: Long,
        amount: Double
    ): Carbs = Carbs(
        timestamp = timestamp,
        duration = duration,
        amount = amount,
        isValid = true,
        interfaceIDs_backing = InterfaceIDs()
    ).also { it.id = id }
}

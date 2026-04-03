package app.aaps.database.transactions

import app.aaps.database.DelegatedAppDatabase
import app.aaps.database.daos.StepsCountDao
import app.aaps.database.entities.StepsCount
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class InsertOrUpdateStepsCountTransactionTest {

    private lateinit var database: DelegatedAppDatabase
    private lateinit var stepsCountDao: StepsCountDao

    @BeforeEach
    fun setup() {
        stepsCountDao = mock()
        database = mock()
        whenever(database.stepsCountDao).thenReturn(stepsCountDao)
    }

    @Test
    fun `inserts new steps count when id is 0`() = runTest {
        val stepsCount = createStepsCount(id = 0, steps5min = 100, steps10min = 200)

        val transaction = InsertOrUpdateStepsCountTransaction(stepsCount)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.inserted).hasSize(1)
        assertThat(result.inserted[0]).isEqualTo(stepsCount)
        assertThat(result.updated).isEmpty()

        verify(stepsCountDao).insertNewEntry(stepsCount)
        verify(stepsCountDao, never()).updateExistingEntry(any())
    }

    @Test
    fun `inserts new steps count when id not found`() = runTest {
        val stepsCount = createStepsCount(id = 1, steps5min = 100, steps10min = 200)

        whenever(stepsCountDao.findById(1)).thenReturn(null)

        val transaction = InsertOrUpdateStepsCountTransaction(stepsCount)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.inserted).hasSize(1)
        assertThat(result.inserted[0]).isEqualTo(stepsCount)
        assertThat(result.updated).isEmpty()

        verify(stepsCountDao).insertNewEntry(stepsCount)
        verify(stepsCountDao, never()).updateExistingEntry(any())
    }

    @Test
    fun `updates existing steps count when id found`() = runTest {
        val stepsCount = createStepsCount(id = 1, steps5min = 150, steps10min = 300)
        val existing = createStepsCount(id = 1, steps5min = 100, steps10min = 200)

        whenever(stepsCountDao.findById(1)).thenReturn(existing)

        val transaction = InsertOrUpdateStepsCountTransaction(stepsCount)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updated).hasSize(1)
        assertThat(result.updated[0]).isEqualTo(stepsCount)
        assertThat(result.inserted).isEmpty()

        verify(stepsCountDao).updateExistingEntry(stepsCount)
        verify(stepsCountDao, never()).insertNewEntry(any())
    }

    @Test
    fun `updates steps count values`() = runTest {
        val existing = createStepsCount(id = 1, steps5min = 50, steps10min = 100)
        val updated = createStepsCount(id = 1, steps5min = 200, steps10min = 400)

        whenever(stepsCountDao.findById(1)).thenReturn(existing)

        val transaction = InsertOrUpdateStepsCountTransaction(updated)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updated).hasSize(1)
        assertThat(result.updated[0].steps5min).isEqualTo(200)
        assertThat(result.updated[0].steps10min).isEqualTo(400)
    }

    private fun createStepsCount(
        id: Long,
        steps5min: Int,
        steps10min: Int
    ): StepsCount = StepsCount(
        timestamp = System.currentTimeMillis(),
        steps5min = steps5min,
        steps10min = steps10min,
        steps15min = steps10min + 100,
        steps30min = steps10min + 200,
        steps60min = steps10min + 500,
        steps180min = steps10min + 1000,
        device = "TestDevice",
        duration = 60000L
    ).also { it.id = id }
}

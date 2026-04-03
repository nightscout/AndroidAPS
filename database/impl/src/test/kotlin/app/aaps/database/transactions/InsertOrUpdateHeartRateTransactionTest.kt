package app.aaps.database.transactions

import app.aaps.database.DelegatedAppDatabase
import app.aaps.database.daos.HeartRateDao
import app.aaps.database.entities.HeartRate
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class InsertOrUpdateHeartRateTransactionTest {

    private lateinit var database: DelegatedAppDatabase
    private lateinit var heartRateDao: HeartRateDao

    @BeforeEach
    fun setup() {
        heartRateDao = mock()
        database = mock()
        whenever(database.heartRateDao).thenReturn(heartRateDao)
    }

    @Test
    fun `inserts new heart rate when id is 0`() = runTest {
        val heartRate = createHeartRate(id = 0, beatsPerMinute = 75.0)

        val transaction = InsertOrUpdateHeartRateTransaction(heartRate)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.inserted).hasSize(1)
        assertThat(result.inserted[0]).isEqualTo(heartRate)
        assertThat(result.updated).isEmpty()

        verify(heartRateDao).insertNewEntry(heartRate)
        verify(heartRateDao, never()).updateExistingEntry(any())
    }

    @Test
    fun `inserts new heart rate when id not found`() = runTest {
        val heartRate = createHeartRate(id = 1, beatsPerMinute = 75.0)

        whenever(heartRateDao.findById(1)).thenReturn(null)

        val transaction = InsertOrUpdateHeartRateTransaction(heartRate)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.inserted).hasSize(1)
        assertThat(result.inserted[0]).isEqualTo(heartRate)
        assertThat(result.updated).isEmpty()

        verify(heartRateDao).insertNewEntry(heartRate)
        verify(heartRateDao, never()).updateExistingEntry(any())
    }

    @Test
    fun `updates existing heart rate when id found`() = runTest {
        val heartRate = createHeartRate(id = 1, beatsPerMinute = 85.0)
        val existing = createHeartRate(id = 1, beatsPerMinute = 75.0)

        whenever(heartRateDao.findById(1)).thenReturn(existing)

        val transaction = InsertOrUpdateHeartRateTransaction(heartRate)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updated).hasSize(1)
        assertThat(result.updated[0]).isEqualTo(heartRate)
        assertThat(result.inserted).isEmpty()

        verify(heartRateDao).updateExistingEntry(heartRate)
        verify(heartRateDao, never()).insertNewEntry(any())
    }

    @Test
    fun `updates heart rate value`() = runTest {
        val existing = createHeartRate(id = 1, beatsPerMinute = 70.0)
        val updated = createHeartRate(id = 1, beatsPerMinute = 90.0)

        whenever(heartRateDao.findById(1)).thenReturn(existing)

        val transaction = InsertOrUpdateHeartRateTransaction(updated)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updated).hasSize(1)
        assertThat(result.updated[0].beatsPerMinute).isEqualTo(90.0)
    }

    private fun createHeartRate(
        id: Long,
        beatsPerMinute: Double
    ): HeartRate = HeartRate(
        timestamp = System.currentTimeMillis(),
        beatsPerMinute = beatsPerMinute,
        device = "TestDevice",
        duration = 60_000L
    ).also { it.id = id }
}

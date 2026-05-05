package app.aaps.database.transactions

import app.aaps.database.DelegatedAppDatabase
import app.aaps.database.daos.RunningModeDao
import app.aaps.database.entities.RunningMode
import app.aaps.database.entities.embedments.InterfaceIDs
import app.aaps.database.entities.interfaces.end
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class CancelRunningModeTransactionTest {

    private lateinit var database: DelegatedAppDatabase
    private lateinit var runningModeDao: RunningModeDao

    @BeforeEach
    fun setup() {
        runningModeDao = mock()
        database = mock()
        whenever(database.runningModeDao).thenReturn(runningModeDao)
    }

    @Test
    fun `cuts active temporary running mode`() = runTest {
        val timestamp = 31_000L
        val current = createRunningMode(id = 7, timestamp = 1_000L, duration = 60_000L)
        whenever(runningModeDao.findById(7)).thenReturn(current)

        val transaction = CancelRunningModeTransaction(id = 7, timestamp = timestamp)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updated).hasSize(1)
        assertThat(current.end).isEqualTo(timestamp)
        verify(runningModeDao).updateExistingEntry(current)
    }

    @Test
    fun `cuts permanent running mode (duration=0)`() = runTest {
        val timestamp = 31_000L
        val current = createRunningMode(id = 7, timestamp = 1_000L, duration = 0L)
        whenever(runningModeDao.findById(7)).thenReturn(current)

        val transaction = CancelRunningModeTransaction(id = 7, timestamp = timestamp)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updated).hasSize(1)
        assertThat(current.end).isEqualTo(timestamp)
        assertThat(current.duration).isEqualTo(timestamp - 1_000L)
        verify(runningModeDao).updateExistingEntry(current)
    }

    @Test
    fun `cuts indefinite running mode (duration=Long MAX_VALUE) to finite`() = runTest {
        val timestamp = 31_000L
        val current = createRunningMode(id = 7, timestamp = 1_000L, duration = Long.MAX_VALUE)
        whenever(runningModeDao.findById(7)).thenReturn(current)

        val transaction = CancelRunningModeTransaction(id = 7, timestamp = timestamp)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updated).hasSize(1)
        assertThat(current.end).isEqualTo(timestamp)
        assertThat(current.duration).isEqualTo(timestamp - 1_000L)
        verify(runningModeDao).updateExistingEntry(current)
    }

    @Test
    fun `does nothing when id is missing`() = runTest {
        whenever(runningModeDao.findById(99)).thenReturn(null)

        val transaction = CancelRunningModeTransaction(id = 99, timestamp = 31_000L)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updated).isEmpty()
        verify(runningModeDao, never()).updateExistingEntry(any())
    }

    @Test
    fun `does nothing when row is invalid`() = runTest {
        val current = createRunningMode(id = 7, timestamp = 1_000L, duration = 60_000L, isValid = false)
        whenever(runningModeDao.findById(7)).thenReturn(current)

        val transaction = CancelRunningModeTransaction(id = 7, timestamp = 31_000L)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updated).isEmpty()
        verify(runningModeDao, never()).updateExistingEntry(any())
    }

    @Test
    fun `does nothing when row started at or after timestamp`() = runTest {
        val current = createRunningMode(id = 7, timestamp = 31_000L, duration = 60_000L)
        whenever(runningModeDao.findById(7)).thenReturn(current)

        val transaction = CancelRunningModeTransaction(id = 7, timestamp = 31_000L)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updated).isEmpty()
        verify(runningModeDao, never()).updateExistingEntry(any())
    }

    @Test
    fun `does nothing when row already finished`() = runTest {
        val current = createRunningMode(id = 7, timestamp = 1_000L, duration = 5_000L)
        whenever(runningModeDao.findById(7)).thenReturn(current)

        val transaction = CancelRunningModeTransaction(id = 7, timestamp = 31_000L)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updated).isEmpty()
        verify(runningModeDao, never()).updateExistingEntry(any())
    }

    private fun createRunningMode(
        id: Long,
        timestamp: Long,
        duration: Long,
        isValid: Boolean = true
    ): RunningMode = RunningMode(
        timestamp = timestamp,
        mode = RunningMode.Mode.OPEN_LOOP,
        duration = duration,
        interfaceIDs_backing = InterfaceIDs(),
        isValid = isValid
    ).also { it.id = id }
}

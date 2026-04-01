package app.aaps.database.transactions

import app.aaps.database.DelegatedAppDatabase
import app.aaps.database.daos.RunningModeDao
import app.aaps.database.entities.RunningMode
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

class InsertOrUpdateRunningModeTransactionTest {

    private lateinit var database: DelegatedAppDatabase
    private lateinit var runningModeDao: RunningModeDao

    @BeforeEach
    fun setup() {
        runningModeDao = mock()
        database = mock()
        whenever(database.runningModeDao).thenReturn(runningModeDao)
    }

    @Test
    fun `inserts new running mode when id not found`() = runTest {
        val runningMode = createRunningMode(id = 1, mode = RunningMode.Mode.OPEN_LOOP)

        whenever(runningModeDao.findById(1)).thenReturn(null)

        val transaction = InsertOrUpdateRunningModeTransaction(runningMode)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.inserted).hasSize(1)
        assertThat(result.inserted[0]).isEqualTo(runningMode)
        assertThat(result.updated).isEmpty()

        verify(runningModeDao).insertNewEntry(runningMode)
        verify(runningModeDao, never()).updateExistingEntry(any())
    }

    @Test
    fun `updates existing running mode when id found`() = runTest {
        val runningMode = createRunningMode(id = 1, mode = RunningMode.Mode.CLOSED_LOOP)
        val existing = createRunningMode(id = 1, mode = RunningMode.Mode.OPEN_LOOP)

        whenever(runningModeDao.findById(1)).thenReturn(existing)

        val transaction = InsertOrUpdateRunningModeTransaction(runningMode)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updated).hasSize(1)
        assertThat(result.updated[0]).isEqualTo(runningMode)
        assertThat(result.inserted).isEmpty()

        verify(runningModeDao).updateExistingEntry(runningMode)
        verify(runningModeDao, never()).insertNewEntry(any())
    }

    @Test
    fun `updates running mode type`() = runTest {
        val existing = createRunningMode(id = 1, mode = RunningMode.Mode.OPEN_LOOP)
        val updated = createRunningMode(id = 1, mode = RunningMode.Mode.CLOSED_LOOP)

        whenever(runningModeDao.findById(1)).thenReturn(existing)

        val transaction = InsertOrUpdateRunningModeTransaction(updated)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updated).hasSize(1)
        assertThat(result.updated[0].mode).isEqualTo(RunningMode.Mode.CLOSED_LOOP)
    }

    @Test
    fun `inserts running mode with duration`() = runTest {
        val runningMode = createRunningMode(id = 1, mode = RunningMode.Mode.OPEN_LOOP, duration = 60_000L)

        whenever(runningModeDao.findById(1)).thenReturn(null)

        val transaction = InsertOrUpdateRunningModeTransaction(runningMode)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.inserted).hasSize(1)
        assertThat(result.inserted[0].duration).isEqualTo(60_000L)
    }

    private fun createRunningMode(
        id: Long,
        mode: RunningMode.Mode,
        duration: Long = 0L
    ): RunningMode = RunningMode(
        timestamp = System.currentTimeMillis(),
        mode = mode,
        duration = duration,
        interfaceIDs_backing = InterfaceIDs()
    ).also { it.id = id }
}

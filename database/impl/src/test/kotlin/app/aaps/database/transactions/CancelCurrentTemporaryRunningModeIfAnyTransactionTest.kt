package app.aaps.database.transactions

import app.aaps.database.DelegatedAppDatabase
import app.aaps.database.daos.RunningModeDao
import app.aaps.database.entities.RunningMode
import app.aaps.database.entities.embedments.InterfaceIDs
import app.aaps.database.entities.interfaces.end
import com.google.common.truth.Truth.assertThat
import io.reactivex.rxjava3.core.Maybe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class CancelCurrentTemporaryRunningModeIfAnyTransactionTest {

    private lateinit var database: DelegatedAppDatabase
    private lateinit var runningModeDao: RunningModeDao

    @BeforeEach
    fun setup() {
        runningModeDao = mock()
        database = mock()
        whenever(database.runningModeDao).thenReturn(runningModeDao)
    }

    @Test
    fun `cancels running temporary running mode`() {
        val timestamp = 31_000L
        val running = createRunningMode(timestamp = 1000L, duration = 60_000L)

        whenever(runningModeDao.getTemporaryRunningModeActiveAt(31_000L)).thenReturn(Maybe.just(running))

        val transaction = CancelCurrentTemporaryRunningModeIfAnyTransaction(timestamp)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updated).hasSize(1)
        assertThat(running.end).isEqualTo(31_000L)

        verify(runningModeDao).updateExistingEntry(running)
    }

    @Test
    fun `does not cancel when no running temporary running mode`() {
        val timestamp = 31_000L

        whenever(runningModeDao.getTemporaryRunningModeActiveAt(31_000L)).thenReturn(Maybe.empty())

        val transaction = CancelCurrentTemporaryRunningModeIfAnyTransaction(timestamp)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updated).isEmpty()

        verify(runningModeDao, never()).updateExistingEntry(any())
    }

    private fun createRunningMode(
        timestamp: Long,
        duration: Long
    ): RunningMode = RunningMode(
        timestamp = timestamp,
        mode = RunningMode.Mode.OPEN_LOOP,
        duration = duration,
        interfaceIDs_backing = InterfaceIDs()
    )
}

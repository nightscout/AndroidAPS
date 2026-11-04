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

class InvalidateRunningModeTransactionTest {

    private lateinit var database: DelegatedAppDatabase
    private lateinit var runningModeDao: RunningModeDao

    @BeforeEach
    fun setup() {
        runningModeDao = mock()
        database = mock()
        whenever(database.runningModeDao).thenReturn(runningModeDao)
    }

    @Test
    fun `invalidates valid running mode`() = runTest {
        val rm = createRunningMode(id = 1, isValid = true)

        whenever(runningModeDao.findById(1)).thenReturn(rm)

        val transaction = InvalidateRunningModeTransaction(id = 1)
        transaction.database = database
        val result = transaction.run()

        assertThat(rm.isValid).isFalse()
        assertThat(result.invalidated).hasSize(1)

        verify(runningModeDao).updateExistingEntry(rm)
    }

    @Test
    fun `does not update already invalid running mode`() = runTest {
        val rm = createRunningMode(id = 1, isValid = false)

        whenever(runningModeDao.findById(1)).thenReturn(rm)

        val transaction = InvalidateRunningModeTransaction(id = 1)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.invalidated).isEmpty()

        verify(runningModeDao, never()).updateExistingEntry(any())
    }

    @Test
    fun `throws exception when running mode not found`() = runTest {
        whenever(runningModeDao.findById(999)).thenReturn(null)

        val transaction = InvalidateRunningModeTransaction(id = 999)
        transaction.database = database

        try {
            transaction.run()
            throw AssertionError("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("There is no such RunningMode")
        }
    }

    private fun createRunningMode(
        id: Long,
        isValid: Boolean
    ): RunningMode = RunningMode(
        timestamp = System.currentTimeMillis(),
        mode = RunningMode.Mode.CLOSED_LOOP,
        duration = 0,
        isValid = isValid,
        interfaceIDs_backing = InterfaceIDs()
    ).also { it.id = id }
}

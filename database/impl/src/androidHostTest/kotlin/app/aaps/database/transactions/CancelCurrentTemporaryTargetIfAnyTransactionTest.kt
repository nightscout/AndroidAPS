package app.aaps.database.transactions

import app.aaps.database.DelegatedAppDatabase
import app.aaps.database.daos.TemporaryTargetDao
import app.aaps.database.entities.TemporaryTarget
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

class CancelCurrentTemporaryTargetIfAnyTransactionTest {

    private lateinit var database: DelegatedAppDatabase
    private lateinit var temporaryTargetDao: TemporaryTargetDao

    @BeforeEach
    fun setup() {
        temporaryTargetDao = mock()
        database = mock()
        whenever(database.temporaryTargetDao).thenReturn(temporaryTargetDao)
    }

    @Test
    fun `cancels running temporary target`() = runTest {
        val timestamp = 31_000L
        val running = createTemporaryTarget(timestamp = 1000L, duration = 60_000L)

        whenever(temporaryTargetDao.getTemporaryTargetActiveAt(31_000L)).thenReturn(running)

        val transaction = CancelCurrentTemporaryTargetIfAnyTransaction(timestamp)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updated).hasSize(1)
        assertThat(running.end).isEqualTo(31_000L)

        verify(temporaryTargetDao).updateExistingEntry(running)
    }

    @Test
    fun `does not cancel when no running temporary target`() = runTest {
        val timestamp = 31_000L

        whenever(temporaryTargetDao.getTemporaryTargetActiveAt(31_000L)).thenReturn(null)

        val transaction = CancelCurrentTemporaryTargetIfAnyTransaction(timestamp)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updated).isEmpty()

        verify(temporaryTargetDao, never()).updateExistingEntry(any())
    }

    private fun createTemporaryTarget(
        timestamp: Long,
        duration: Long
    ): TemporaryTarget = TemporaryTarget(
        timestamp = timestamp,
        duration = duration,
        reason = TemporaryTarget.Reason.CUSTOM,
        lowTarget = 80.0,
        highTarget = 120.0,
        interfaceIDs_backing = InterfaceIDs()
    )
}

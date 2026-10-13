package app.aaps.database.transactions

import app.aaps.database.DelegatedAppDatabase
import app.aaps.database.daos.TemporaryTargetDao
import app.aaps.database.entities.TemporaryTarget
import app.aaps.database.entities.embedments.InterfaceIDs
import app.aaps.database.entities.interfaces.end
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class InsertAndCancelCurrentTemporaryTargetTransactionTest {

    private lateinit var database: DelegatedAppDatabase
    private lateinit var temporaryTargetDao: TemporaryTargetDao

    @BeforeEach
    fun setup() {
        temporaryTargetDao = mock()
        database = mock()
        whenever(database.temporaryTargetDao).thenReturn(temporaryTargetDao)
    }

    @Test
    fun `inserts new target and cancels active target`() {
        val timestamp = 10000L
        val activeTarget = createTemporaryTarget(id = 1, timestamp = 5000L, duration = 30_000L)
        val newTarget = createTemporaryTarget(id = 0, timestamp = timestamp, duration = 60_000L)

        whenever(temporaryTargetDao.getTemporaryTargetActiveAtLegacy(timestamp)).thenReturn(activeTarget)

        val transaction = InsertAndCancelCurrentTemporaryTargetTransaction(newTarget)
        transaction.database = database
        val result = transaction.run()

        assertThat(activeTarget.end).isEqualTo(timestamp)
        assertThat(result.updated).hasSize(1)
        assertThat(result.updated[0]).isEqualTo(activeTarget)
        assertThat(result.inserted).hasSize(1)
        assertThat(result.inserted[0]).isEqualTo(newTarget)

        verify(temporaryTargetDao).updateExistingEntry(activeTarget)
        verify(temporaryTargetDao).insertNewEntry(newTarget)
    }

    @Test
    fun `inserts new target when no active target`() {
        val timestamp = 10000L
        val newTarget = createTemporaryTarget(id = 0, timestamp = timestamp, duration = 60_000L)

        whenever(temporaryTargetDao.getTemporaryTargetActiveAtLegacy(timestamp)).thenReturn(null)

        val transaction = InsertAndCancelCurrentTemporaryTargetTransaction(newTarget)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updated).isEmpty()
        assertThat(result.inserted).hasSize(1)
        assertThat(result.inserted[0]).isEqualTo(newTarget)

        verify(temporaryTargetDao, never()).updateExistingEntry(any())
        verify(temporaryTargetDao).insertNewEntry(newTarget)
    }

    @Test
    fun `sets end time exactly to new target timestamp`() {
        val timestamp = 15000L
        val activeTarget = createTemporaryTarget(id = 1, timestamp = 10000L, duration = 20_000L)
        val newTarget = createTemporaryTarget(id = 0, timestamp = timestamp, duration = 30_000L)

        whenever(temporaryTargetDao.getTemporaryTargetActiveAtLegacy(timestamp)).thenReturn(activeTarget)

        val transaction = InsertAndCancelCurrentTemporaryTargetTransaction(newTarget)
        transaction.database = database
        transaction.run()

        assertThat(activeTarget.end).isEqualTo(timestamp)
    }

    @Test
    fun `preserves new target properties`() {
        val timestamp = 10000L
        val lowTarget = 80.0
        val highTarget = 120.0
        val duration = 60_000L
        val reason = TemporaryTarget.Reason.ACTIVITY
        val newTarget = createTemporaryTarget(
            id = 0,
            timestamp = timestamp,
            duration = duration,
            lowTarget = lowTarget,
            highTarget = highTarget,
            reason = reason
        )

        whenever(temporaryTargetDao.getTemporaryTargetActiveAtLegacy(timestamp)).thenReturn(null)

        val transaction = InsertAndCancelCurrentTemporaryTargetTransaction(newTarget)
        transaction.database = database
        val result = transaction.run()

        val inserted = result.inserted[0]
        assertThat(inserted.timestamp).isEqualTo(timestamp)
        assertThat(inserted.duration).isEqualTo(duration)
        assertThat(inserted.lowTarget).isEqualTo(lowTarget)
        assertThat(inserted.highTarget).isEqualTo(highTarget)
        assertThat(inserted.reason).isEqualTo(reason)
    }

    @Test
    fun `transaction result has correct structure`() {
        val result = InsertAndCancelCurrentTemporaryTargetTransaction.TransactionResult()

        assertThat(result.inserted).isEmpty()
        assertThat(result.updated).isEmpty()
        assertThat(result.inserted).isInstanceOf(MutableList::class.java)
        assertThat(result.updated).isInstanceOf(MutableList::class.java)
    }

    @Test
    fun `handles overlapping targets correctly`() {
        val timestamp = 10000L
        val activeTarget = createTemporaryTarget(
            id = 1,
            timestamp = 5000L,
            duration = 15_000L, // ends at 20000
            reason = TemporaryTarget.Reason.EATING_SOON
        )
        val newTarget = createTemporaryTarget(
            id = 0,
            timestamp = timestamp, // starts at 10000
            duration = 30_000L,
            reason = TemporaryTarget.Reason.ACTIVITY
        )

        whenever(temporaryTargetDao.getTemporaryTargetActiveAtLegacy(timestamp)).thenReturn(activeTarget)

        val transaction = InsertAndCancelCurrentTemporaryTargetTransaction(newTarget)
        transaction.database = database
        val result = transaction.run()

        // Active target should be cut short
        assertThat(activeTarget.end).isEqualTo(10000L)
        assertThat(result.updated).hasSize(1)
        assertThat(result.inserted).hasSize(1)
    }

    private fun createTemporaryTarget(
        id: Long,
        timestamp: Long,
        duration: Long,
        lowTarget: Double = 80.0,
        highTarget: Double = 120.0,
        reason: TemporaryTarget.Reason = TemporaryTarget.Reason.CUSTOM
    ): TemporaryTarget = TemporaryTarget(
        timestamp = timestamp,
        duration = duration,
        reason = reason,
        lowTarget = lowTarget,
        highTarget = highTarget,
        interfaceIDs_backing = InterfaceIDs()
    ).also { it.id = id }
}

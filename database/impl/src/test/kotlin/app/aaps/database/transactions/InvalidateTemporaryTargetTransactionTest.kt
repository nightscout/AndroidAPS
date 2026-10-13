package app.aaps.database.transactions

import app.aaps.database.DelegatedAppDatabase
import app.aaps.database.daos.TemporaryTargetDao
import app.aaps.database.entities.TemporaryTarget
import app.aaps.database.entities.embedments.InterfaceIDs
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class InvalidateTemporaryTargetTransactionTest {

    private lateinit var database: DelegatedAppDatabase
    private lateinit var temporaryTargetDao: TemporaryTargetDao

    @BeforeEach
    fun setup() {
        temporaryTargetDao = mock()
        database = mock()
        whenever(database.temporaryTargetDao).thenReturn(temporaryTargetDao)
    }

    @Test
    fun `invalidates valid temporary target`() {
        val target = createTemporaryTarget(id = 1, isValid = true)

        whenever(temporaryTargetDao.findById(1)).thenReturn(target)

        val transaction = InvalidateTemporaryTargetTransaction(id = 1)
        transaction.database = database
        val result = transaction.run()

        assertThat(target.isValid).isFalse()
        assertThat(result.invalidated).hasSize(1)
        assertThat(result.invalidated[0]).isEqualTo(target)

        verify(temporaryTargetDao).updateExistingEntry(target)
    }

    @Test
    fun `does not update already invalid temporary target`() {
        val target = createTemporaryTarget(id = 1, isValid = false)

        whenever(temporaryTargetDao.findById(1)).thenReturn(target)

        val transaction = InvalidateTemporaryTargetTransaction(id = 1)
        transaction.database = database
        val result = transaction.run()

        assertThat(target.isValid).isFalse()
        assertThat(result.invalidated).isEmpty()

        verify(temporaryTargetDao, never()).updateExistingEntry(any())
    }

    @Test
    fun `throws exception when temporary target not found`() {
        whenever(temporaryTargetDao.findById(999)).thenReturn(null)

        val transaction = InvalidateTemporaryTargetTransaction(id = 999)
        transaction.database = database

        try {
            transaction.run()
            throw AssertionError("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("There is no such TemporaryTarget")
        }
    }

    @Test
    fun `preserves target values when invalidating`() {
        val lowTarget = 80.0
        val highTarget = 120.0
        val target = createTemporaryTarget(id = 1, isValid = true, lowTarget = lowTarget, highTarget = highTarget)

        whenever(temporaryTargetDao.findById(1)).thenReturn(target)

        val transaction = InvalidateTemporaryTargetTransaction(id = 1)
        transaction.database = database
        transaction.run()

        assertThat(target.lowTarget).isEqualTo(lowTarget)
        assertThat(target.highTarget).isEqualTo(highTarget)
        assertThat(target.isValid).isFalse()
    }

    private fun createTemporaryTarget(
        id: Long,
        isValid: Boolean,
        lowTarget: Double = 80.0,
        highTarget: Double = 120.0
    ): TemporaryTarget = TemporaryTarget(
        timestamp = System.currentTimeMillis(),
        duration = 60_000L,
        reason = TemporaryTarget.Reason.CUSTOM,
        lowTarget = lowTarget,
        highTarget = highTarget,
        isValid = isValid,
        interfaceIDs_backing = InterfaceIDs()
    ).also { it.id = id }
}

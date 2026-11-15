package app.aaps.database.transactions

import app.aaps.database.DelegatedAppDatabase
import app.aaps.database.daos.TemporaryBasalDao
import app.aaps.database.entities.TemporaryBasal
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

class InvalidateTemporaryBasalTransactionTest {

    private lateinit var database: DelegatedAppDatabase
    private lateinit var temporaryBasalDao: TemporaryBasalDao

    @BeforeEach
    fun setup() {
        temporaryBasalDao = mock()
        database = mock()
        whenever(database.temporaryBasalDao).thenReturn(temporaryBasalDao)
    }

    @Test
    fun `invalidates valid temporary basal`() = runTest {
        val tb = createTemporaryBasal(id = 1, isValid = true)

        whenever(temporaryBasalDao.findById(1)).thenReturn(tb)

        val transaction = InvalidateTemporaryBasalTransaction(id = 1)
        transaction.database = database
        val result = transaction.run()

        assertThat(tb.isValid).isFalse()
        assertThat(result.invalidated).hasSize(1)

        verify(temporaryBasalDao).updateExistingEntry(tb)
    }

    @Test
    fun `does not update already invalid temporary basal`() = runTest {
        val tb = createTemporaryBasal(id = 1, isValid = false)

        whenever(temporaryBasalDao.findById(1)).thenReturn(tb)

        val transaction = InvalidateTemporaryBasalTransaction(id = 1)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.invalidated).isEmpty()

        verify(temporaryBasalDao, never()).updateExistingEntry(any())
    }

    @Test
    fun `throws exception when temporary basal not found`() = runTest {
        whenever(temporaryBasalDao.findById(999)).thenReturn(null)

        val transaction = InvalidateTemporaryBasalTransaction(id = 999)
        transaction.database = database

        try {
            transaction.run()
            throw AssertionError("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("There is no such Temporary Basal with the specified ID.")
        }
    }

    private fun createTemporaryBasal(
        id: Long,
        isValid: Boolean
    ): TemporaryBasal = TemporaryBasal(
        timestamp = System.currentTimeMillis(),
        rate = 1.5,
        duration = 60_000L,
        type = TemporaryBasal.Type.NORMAL,
        isAbsolute = true,
        isValid = isValid,
        interfaceIDs_backing = InterfaceIDs()
    ).also { it.id = id }
}

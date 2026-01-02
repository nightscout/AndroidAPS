package app.aaps.database.transactions

import app.aaps.database.DelegatedAppDatabase
import app.aaps.database.daos.TemporaryBasalDao
import app.aaps.database.entities.TemporaryBasal
import app.aaps.database.entities.embedments.InterfaceIDs
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class InvalidateTemporaryBasalTransactionWithPumpIdTest {

    private lateinit var database: DelegatedAppDatabase
    private lateinit var temporaryBasalDao: TemporaryBasalDao

    @BeforeEach
    fun setup() {
        temporaryBasalDao = mock()
        database = mock()
        whenever(database.temporaryBasalDao).thenReturn(temporaryBasalDao)
    }

    @Test
    fun `invalidates valid temporary basal by pump ids`() {
        val pumpId = 12345L
        val pumpType = InterfaceIDs.PumpType.DANA_I
        val pumpSerial = "ABC123"
        val tb = createTemporaryBasal(id = 1, isValid = true)

        whenever(temporaryBasalDao.findByPumpIds(pumpId, pumpType, pumpSerial)).thenReturn(tb)

        val transaction = InvalidateTemporaryBasalTransactionWithPumpId(pumpId, pumpType, pumpSerial)
        transaction.database = database
        val result = transaction.run()

        assertThat(tb.isValid).isFalse()
        assertThat(result.invalidated).hasSize(1)

        verify(temporaryBasalDao).updateExistingEntry(tb)
    }

    @Test
    fun `does not update already invalid temporary basal`() {
        val pumpId = 12345L
        val pumpType = InterfaceIDs.PumpType.DANA_I
        val pumpSerial = "ABC123"
        val tb = createTemporaryBasal(id = 1, isValid = false)

        whenever(temporaryBasalDao.findByPumpIds(pumpId, pumpType, pumpSerial)).thenReturn(tb)

        val transaction = InvalidateTemporaryBasalTransactionWithPumpId(pumpId, pumpType, pumpSerial)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.invalidated).isEmpty()

        verify(temporaryBasalDao, never()).updateExistingEntry(any())
    }

    @Test
    fun `throws exception when temporary basal not found`() {
        val pumpId = 999L
        val pumpType = InterfaceIDs.PumpType.DANA_I
        val pumpSerial = "ABC123"

        whenever(temporaryBasalDao.findByPumpIds(pumpId, pumpType, pumpSerial)).thenReturn(null)

        val transaction = InvalidateTemporaryBasalTransactionWithPumpId(pumpId, pumpType, pumpSerial)
        transaction.database = database

        try {
            transaction.run()
            throw AssertionError("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("There is no such Temporary Basal")
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

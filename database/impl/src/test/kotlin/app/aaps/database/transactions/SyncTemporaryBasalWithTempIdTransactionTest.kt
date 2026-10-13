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

class SyncTemporaryBasalWithTempIdTransactionTest {

    private lateinit var database: DelegatedAppDatabase
    private lateinit var temporaryBasalDao: TemporaryBasalDao

    @BeforeEach
    fun setup() {
        temporaryBasalDao = mock()
        database = mock()
        whenever(database.temporaryBasalDao).thenReturn(temporaryBasalDao)
    }

    @Test
    fun `updates existing temporary basal when found by temp id`() {
        val tb = createTemporaryBasal(tempId = 500L, pumpId = 100L, rate = 2.0, duration = 30_000L, timestamp = 2000L)
        val existing = createTemporaryBasal(tempId = 500L, pumpId = null, rate = 1.5, duration = 60_000L, timestamp = 1000L)

        whenever(temporaryBasalDao.findByPumpTempIds(500L, InterfaceIDs.PumpType.DANA_I, "ABC123")).thenReturn(existing)

        val transaction = SyncTemporaryBasalWithTempIdTransaction(tb, null)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updated).hasSize(1)
        val (old, updated) = result.updated[0]
        assertThat(updated.timestamp).isEqualTo(2000L)
        assertThat(updated.rate).isEqualTo(2.0)
        assertThat(updated.duration).isEqualTo(30_000L)
        assertThat(updated.interfaceIDs.pumpId).isEqualTo(100L)

        verify(temporaryBasalDao).updateExistingEntry(existing)
    }

    @Test
    fun `does not update when not found by temp id`() {
        val tb = createTemporaryBasal(tempId = 500L, pumpId = 100L, rate = 2.0, duration = 30_000L, timestamp = 2000L)

        whenever(temporaryBasalDao.findByPumpTempIds(500L, InterfaceIDs.PumpType.DANA_I, "ABC123")).thenReturn(null)

        val transaction = SyncTemporaryBasalWithTempIdTransaction(tb, null)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updated).isEmpty()

        verify(temporaryBasalDao, never()).updateExistingEntry(any())
    }

    @Test
    fun `updates type when provided`() {
        val tb = createTemporaryBasal(tempId = 500L, pumpId = 100L, rate = 1.5, duration = 60_000L, timestamp = 1000L, type = TemporaryBasal.Type.NORMAL)
        val existing = createTemporaryBasal(tempId = 500L, pumpId = null, rate = 1.5, duration = 60_000L, timestamp = 1000L, type = TemporaryBasal.Type.NORMAL)

        whenever(temporaryBasalDao.findByPumpTempIds(500L, InterfaceIDs.PumpType.DANA_I, "ABC123")).thenReturn(existing)

        val transaction = SyncTemporaryBasalWithTempIdTransaction(tb, TemporaryBasal.Type.EMULATED_PUMP_SUSPEND)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updated).hasSize(1)
        assertThat(existing.type).isEqualTo(TemporaryBasal.Type.EMULATED_PUMP_SUSPEND)
    }

    private fun createTemporaryBasal(
        tempId: Long,
        pumpId: Long?,
        rate: Double,
        duration: Long,
        timestamp: Long,
        type: TemporaryBasal.Type = TemporaryBasal.Type.NORMAL
    ): TemporaryBasal = TemporaryBasal(
        timestamp = timestamp,
        rate = rate,
        duration = duration,
        type = type,
        isAbsolute = true,
        interfaceIDs_backing = InterfaceIDs(
            temporaryId = tempId,
            pumpId = pumpId,
            pumpType = InterfaceIDs.PumpType.DANA_I,
            pumpSerial = "ABC123"
        )
    )
}

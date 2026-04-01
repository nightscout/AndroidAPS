package app.aaps.database.transactions

import app.aaps.database.DelegatedAppDatabase
import app.aaps.database.daos.TemporaryBasalDao
import app.aaps.database.entities.TemporaryBasal
import app.aaps.database.entities.embedments.InterfaceIDs
import app.aaps.database.entities.interfaces.end
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.verify
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class SyncPumpTemporaryBasalTransactionTest {

    private lateinit var database: DelegatedAppDatabase
    private lateinit var temporaryBasalDao: TemporaryBasalDao

    @BeforeEach
    fun setup() {
        temporaryBasalDao = mock()
        database = mock()
        whenever(database.temporaryBasalDao).thenReturn(temporaryBasalDao)
    }

    @Test
    fun `inserts new temporary basal when not found by pump ids`() = runTest {
        val tb = createTemporaryBasal(pumpId = 100L, timestamp = 1000L, rate = 1.5, duration = 60_000L)

        whenever(temporaryBasalDao.findByPumpIds(100L, InterfaceIDs.PumpType.DANA_I, "ABC123")).thenReturn(null)
        whenever(temporaryBasalDao.getTemporaryBasalActiveAtLegacy(1000L)).thenReturn(null)

        val transaction = SyncPumpTemporaryBasalTransaction(tb, null)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.inserted).hasSize(1)

        verify(temporaryBasalDao).insertNewEntry(tb)
    }

    @Test
    fun `updates existing temporary basal when found by pump ids`() = runTest {
        val tb = createTemporaryBasal(pumpId = 100L, timestamp = 2000L, rate = 2.0, duration = 30_000L)
        val existing = createTemporaryBasal(pumpId = 100L, timestamp = 1000L, rate = 1.5, duration = 60_000L)

        whenever(temporaryBasalDao.findByPumpIds(100L, InterfaceIDs.PumpType.DANA_I, "ABC123")).thenReturn(existing)

        val transaction = SyncPumpTemporaryBasalTransaction(tb, null)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updated).hasSize(1)
        assertThat(existing.timestamp).isEqualTo(2000L)
        assertThat(existing.rate).isEqualTo(2.0)
        assertThat(existing.duration).isEqualTo(30_000L)

        verify(temporaryBasalDao).updateExistingEntry(existing)
    }

    @Test
    fun `does not update when values are same`() = runTest {
        val tb = createTemporaryBasal(pumpId = 100L, timestamp = 1000L, rate = 1.5, duration = 60_000L)
        val existing = createTemporaryBasal(pumpId = 100L, timestamp = 1000L, rate = 1.5, duration = 60_000L)

        whenever(temporaryBasalDao.findByPumpIds(100L, InterfaceIDs.PumpType.DANA_I, "ABC123")).thenReturn(existing)

        val transaction = SyncPumpTemporaryBasalTransaction(tb, null)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updated).isEmpty()
        assertThat(result.inserted).isEmpty()
    }

    @Test
    fun `ends running temporary basal and inserts new when not found by pump id`() = runTest {
        val tb = createTemporaryBasal(pumpId = 100L, timestamp = 5000L, rate = 2.0, duration = 30_000L)
        val running = createTemporaryBasal(pumpId = 50L, timestamp = 1000L, rate = 1.5, duration = 60_000L)

        whenever(temporaryBasalDao.findByPumpIds(100L, InterfaceIDs.PumpType.DANA_I, "ABC123")).thenReturn(null)
        whenever(temporaryBasalDao.getTemporaryBasalActiveAtLegacy(5000L)).thenReturn(running)

        val transaction = SyncPumpTemporaryBasalTransaction(tb, null)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updated).hasSize(1)
        assertThat(result.inserted).hasSize(1)
        assertThat(running.end).isEqualTo(5000L)
        assertThat(running.interfaceIDs.endId).isEqualTo(100L)
    }

    @Test
    fun `updates type when provided`() = runTest {
        val tb = createTemporaryBasal(pumpId = 100L, timestamp = 1000L, rate = 1.5, duration = 60_000L, type = TemporaryBasal.Type.NORMAL)
        val existing = createTemporaryBasal(pumpId = 100L, timestamp = 1000L, rate = 1.5, duration = 60_000L, type = TemporaryBasal.Type.NORMAL)

        whenever(temporaryBasalDao.findByPumpIds(100L, InterfaceIDs.PumpType.DANA_I, "ABC123")).thenReturn(existing)

        val transaction = SyncPumpTemporaryBasalTransaction(tb, TemporaryBasal.Type.EMULATED_PUMP_SUSPEND)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updated).hasSize(1)
        assertThat(existing.type).isEqualTo(TemporaryBasal.Type.EMULATED_PUMP_SUSPEND)
    }

    private fun createTemporaryBasal(
        pumpId: Long,
        timestamp: Long,
        rate: Double,
        duration: Long,
        type: TemporaryBasal.Type = TemporaryBasal.Type.NORMAL
    ): TemporaryBasal = TemporaryBasal(
        timestamp = timestamp,
        rate = rate,
        duration = duration,
        type = type,
        isAbsolute = true,
        interfaceIDs_backing = InterfaceIDs(
            pumpId = pumpId,
            pumpType = InterfaceIDs.PumpType.DANA_I,
            pumpSerial = "ABC123"
        )
    )
}

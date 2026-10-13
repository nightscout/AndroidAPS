package app.aaps.database.transactions

import app.aaps.database.DelegatedAppDatabase
import app.aaps.database.daos.ExtendedBolusDao
import app.aaps.database.entities.ExtendedBolus
import app.aaps.database.entities.embedments.InterfaceIDs
import app.aaps.database.entities.interfaces.end
import com.google.common.truth.Truth.assertThat
import io.reactivex.rxjava3.core.Maybe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.verify
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class SyncPumpExtendedBolusTransactionTest {

    private lateinit var database: DelegatedAppDatabase
    private lateinit var extendedBolusDao: ExtendedBolusDao

    @BeforeEach
    fun setup() {
        extendedBolusDao = mock()
        database = mock()
        whenever(database.extendedBolusDao).thenReturn(extendedBolusDao)
    }

    @Test
    fun `inserts new extended bolus when not found by pump ids`() {
        val eb = createExtendedBolus(pumpId = 100L, timestamp = 1000L, amount = 5.0, duration = 60_000L)

        whenever(extendedBolusDao.findByPumpIds(100L, InterfaceIDs.PumpType.DANA_I, "ABC123")).thenReturn(null)
        whenever(extendedBolusDao.getExtendedBolusActiveAtLegacy(1000L)).thenReturn(null)

        val transaction = SyncPumpExtendedBolusTransaction(eb)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.inserted).hasSize(1)

        verify(extendedBolusDao).insertNewEntry(eb)
    }

    @Test
    fun `updates existing extended bolus when found by pump ids`() {
        val eb = createExtendedBolus(pumpId = 100L, timestamp = 2000L, amount = 7.0, duration = 30_000L)
        val existing = createExtendedBolus(pumpId = 100L, timestamp = 1000L, amount = 5.0, duration = 60_000L)

        whenever(extendedBolusDao.findByPumpIds(100L, InterfaceIDs.PumpType.DANA_I, "ABC123")).thenReturn(existing)

        val transaction = SyncPumpExtendedBolusTransaction(eb)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updated).hasSize(1)
        assertThat(existing.timestamp).isEqualTo(2000L)
        assertThat(existing.amount).isEqualTo(7.0)
        assertThat(existing.duration).isEqualTo(30_000L)

        verify(extendedBolusDao).updateExistingEntry(existing)
    }

    @Test
    fun `does not update when values are same`() {
        val eb = createExtendedBolus(pumpId = 100L, timestamp = 1000L, amount = 5.0, duration = 60_000L)
        val existing = createExtendedBolus(pumpId = 100L, timestamp = 1000L, amount = 5.0, duration = 60_000L)

        whenever(extendedBolusDao.findByPumpIds(100L, InterfaceIDs.PumpType.DANA_I, "ABC123")).thenReturn(existing)

        val transaction = SyncPumpExtendedBolusTransaction(eb)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updated).isEmpty()
        assertThat(result.inserted).isEmpty()
    }

    @Test
    fun `does not update when existing has end id`() {
        val eb = createExtendedBolus(pumpId = 100L, timestamp = 2000L, amount = 7.0, duration = 30_000L)
        val existing = createExtendedBolus(pumpId = 100L, timestamp = 1000L, amount = 5.0, duration = 60_000L, endId = 200L)

        whenever(extendedBolusDao.findByPumpIds(100L, InterfaceIDs.PumpType.DANA_I, "ABC123")).thenReturn(existing)

        val transaction = SyncPumpExtendedBolusTransaction(eb)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updated).isEmpty()
        assertThat(result.inserted).isEmpty()
    }

    @Test
    fun `ends running extended bolus and inserts new when not found by pump id`() {
        val eb = createExtendedBolus(pumpId = 100L, timestamp = 31_000L, amount = 5.0, duration = 60_000L)
        val running = createExtendedBolus(pumpId = 50L, timestamp = 1000L, amount = 6.0, duration = 60_000L)

        whenever(extendedBolusDao.findByPumpIds(100L, InterfaceIDs.PumpType.DANA_I, "ABC123")).thenReturn(null)
        whenever(extendedBolusDao.getExtendedBolusActiveAtLegacy(31_000L)).thenReturn(running)

        val transaction = SyncPumpExtendedBolusTransaction(eb)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updated).hasSize(1)
        assertThat(result.inserted).hasSize(1)
        assertThat(running.end).isEqualTo(31_000L)
        assertThat(running.interfaceIDs.endId).isEqualTo(100L)
        // Amount should be proportionally reduced: (31000-1000)/60000 = 0.5, so 6.0 * 0.5 = 3.0
        assertThat(running.amount).isWithin(0.1).of(3.0)
    }

    private fun createExtendedBolus(
        pumpId: Long,
        timestamp: Long,
        amount: Double,
        duration: Long,
        endId: Long? = null
    ): ExtendedBolus = ExtendedBolus(
        timestamp = timestamp,
        amount = amount,
        duration = duration,
        isEmulatingTempBasal = false,
        interfaceIDs_backing = InterfaceIDs(
            pumpId = pumpId,
            pumpType = InterfaceIDs.PumpType.DANA_I,
            pumpSerial = "ABC123",
            endId = endId
        )
    )
}

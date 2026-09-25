package app.aaps.database.transactions

import app.aaps.database.DelegatedAppDatabase
import app.aaps.database.daos.ExtendedBolusDao
import app.aaps.database.entities.ExtendedBolus
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

class SyncPumpCancelExtendedBolusIfAnyTransactionTest {

    private lateinit var database: DelegatedAppDatabase
    private lateinit var extendedBolusDao: ExtendedBolusDao

    @BeforeEach
    fun setup() {
        extendedBolusDao = mock()
        database = mock()
        whenever(database.extendedBolusDao).thenReturn(extendedBolusDao)
    }

    @Test
    fun `cancels running extended bolus with proportional amount`() = runTest {
        val timestamp = 31_000L
        val endPumpId = 200L
        val running = createExtendedBolus(timestamp = 1000L, duration = 60_000L, amount = 6.0, endId = null)

        whenever(extendedBolusDao.findByPumpEndIds(200L, InterfaceIDs.PumpType.DANA_I, "ABC123")).thenReturn(null)
        whenever(extendedBolusDao.getExtendedBolusActiveAt(31_000L)).thenReturn(running)

        val transaction = SyncPumpCancelExtendedBolusIfAnyTransaction(
            timestamp, endPumpId, InterfaceIDs.PumpType.DANA_I, "ABC123"
        )
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updated).hasSize(1)
        assertThat(running.end).isEqualTo(31_000L)
        assertThat(running.interfaceIDs.endId).isEqualTo(200L)
        // Amount should be proportionally reduced: (31000-1000)/60000 = 0.5, so 6.0 * 0.5 = 3.0
        assertThat(running.amount).isWithin(0.1).of(3.0)

        verify(extendedBolusDao).updateExistingEntry(running)
    }

    @Test
    fun `does not cancel if already cancelled by end id`() = runTest {
        val timestamp = 31_000L
        val endPumpId = 200L

        whenever(extendedBolusDao.findByPumpEndIds(200L, InterfaceIDs.PumpType.DANA_I, "ABC123")).thenReturn(
            createExtendedBolus(timestamp = 1000L, duration = 60_000L, amount = 6.0, endId = 200L)
        )

        val transaction = SyncPumpCancelExtendedBolusIfAnyTransaction(
            timestamp, endPumpId, InterfaceIDs.PumpType.DANA_I, "ABC123"
        )
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updated).isEmpty()

        verify(extendedBolusDao, never()).updateExistingEntry(any())
    }

    @Test
    fun `does not cancel if no running extended bolus`() = runTest {
        val timestamp = 31_000L
        val endPumpId = 200L

        whenever(extendedBolusDao.findByPumpEndIds(200L, InterfaceIDs.PumpType.DANA_I, "ABC123")).thenReturn(null)
        whenever(extendedBolusDao.getExtendedBolusActiveAt(31_000L)).thenReturn(null)

        val transaction = SyncPumpCancelExtendedBolusIfAnyTransaction(
            timestamp, endPumpId, InterfaceIDs.PumpType.DANA_I, "ABC123"
        )
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updated).isEmpty()

        verify(extendedBolusDao, never()).updateExistingEntry(any())
    }

    @Test
    fun `does not cancel if running already has end id`() = runTest {
        val timestamp = 31_000L
        val endPumpId = 200L
        val running = createExtendedBolus(timestamp = 1000L, duration = 60_000L, amount = 6.0, endId = 150L)

        whenever(extendedBolusDao.findByPumpEndIds(200L, InterfaceIDs.PumpType.DANA_I, "ABC123")).thenReturn(null)
        whenever(extendedBolusDao.getExtendedBolusActiveAt(31_000L)).thenReturn(running)

        val transaction = SyncPumpCancelExtendedBolusIfAnyTransaction(
            timestamp, endPumpId, InterfaceIDs.PumpType.DANA_I, "ABC123"
        )
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updated).isEmpty()

        verify(extendedBolusDao, never()).updateExistingEntry(any())
    }

    private fun createExtendedBolus(
        timestamp: Long,
        duration: Long,
        amount: Double,
        endId: Long?
    ): ExtendedBolus = ExtendedBolus(
        timestamp = timestamp,
        amount = amount,
        duration = duration,
        isEmulatingTempBasal = false,
        interfaceIDs_backing = InterfaceIDs(
            endId = endId,
            pumpId = 100L,
            pumpType = InterfaceIDs.PumpType.DANA_I,
            pumpSerial = "ABC123"
        )
    )
}

package app.aaps.database.transactions

import app.aaps.database.DelegatedAppDatabase
import app.aaps.database.daos.TemporaryBasalDao
import app.aaps.database.entities.TemporaryBasal
import app.aaps.database.entities.embedments.InterfaceIDs
import app.aaps.database.entities.interfaces.end
import com.google.common.truth.Truth.assertThat
import io.reactivex.rxjava3.core.Maybe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class SyncPumpCancelTemporaryBasalIfAnyTransactionTest {

    private lateinit var database: DelegatedAppDatabase
    private lateinit var temporaryBasalDao: TemporaryBasalDao

    @BeforeEach
    fun setup() {
        temporaryBasalDao = mock()
        database = mock()
        whenever(database.temporaryBasalDao).thenReturn(temporaryBasalDao)
    }

    @Test
    fun `cancels running temporary basal`() {
        val timestamp = 31_000L
        val endPumpId = 200L
        val running = createTemporaryBasal(timestamp = 1000L, duration = 60_000L, endId = null)

        whenever(temporaryBasalDao.findByPumpEndIds(200L, InterfaceIDs.PumpType.DANA_I, "ABC123")).thenReturn(null)
        whenever(temporaryBasalDao.getTemporaryBasalActiveAt(31_000L)).thenReturn(Maybe.just(running))

        val transaction = SyncPumpCancelTemporaryBasalIfAnyTransaction(
            timestamp, endPumpId, InterfaceIDs.PumpType.DANA_I, "ABC123"
        )
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updated).hasSize(1)
        assertThat(running.end).isEqualTo(31_000L)
        assertThat(running.interfaceIDs.endId).isEqualTo(200L)

        verify(temporaryBasalDao).updateExistingEntry(running)
    }

    @Test
    fun `does not cancel if already cancelled by end id`() {
        val timestamp = 31_000L
        val endPumpId = 200L

        whenever(temporaryBasalDao.findByPumpEndIds(200L, InterfaceIDs.PumpType.DANA_I, "ABC123")).thenReturn(
            createTemporaryBasal(timestamp = 1000L, duration = 60_000L, endId = 200L)
        )

        val transaction = SyncPumpCancelTemporaryBasalIfAnyTransaction(
            timestamp, endPumpId, InterfaceIDs.PumpType.DANA_I, "ABC123"
        )
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updated).isEmpty()

        verify(temporaryBasalDao, never()).updateExistingEntry(any())
    }

    @Test
    fun `does not cancel if no running temporary basal`() {
        val timestamp = 31_000L
        val endPumpId = 200L

        whenever(temporaryBasalDao.findByPumpEndIds(200L, InterfaceIDs.PumpType.DANA_I, "ABC123")).thenReturn(null)
        whenever(temporaryBasalDao.getTemporaryBasalActiveAt(31_000L)).thenReturn(Maybe.empty())

        val transaction = SyncPumpCancelTemporaryBasalIfAnyTransaction(
            timestamp, endPumpId, InterfaceIDs.PumpType.DANA_I, "ABC123"
        )
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updated).isEmpty()

        verify(temporaryBasalDao, never()).updateExistingEntry(any())
    }

    @Test
    fun `sets duration to 1 when timestamp equals start`() {
        val timestamp = 1000L
        val endPumpId = 200L
        val running = createTemporaryBasal(timestamp = 1000L, duration = 60_000L, endId = null)

        whenever(temporaryBasalDao.findByPumpEndIds(200L, InterfaceIDs.PumpType.DANA_I, "ABC123")).thenReturn(null)
        whenever(temporaryBasalDao.getTemporaryBasalActiveAt(1000L)).thenReturn(Maybe.just(running))

        val transaction = SyncPumpCancelTemporaryBasalIfAnyTransaction(
            timestamp, endPumpId, InterfaceIDs.PumpType.DANA_I, "ABC123"
        )
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updated).hasSize(1)
        assertThat(running.duration).isEqualTo(1L)
        assertThat(running.interfaceIDs.endId).isEqualTo(200L)
    }

    private fun createTemporaryBasal(
        timestamp: Long,
        duration: Long,
        endId: Long?
    ): TemporaryBasal = TemporaryBasal(
        timestamp = timestamp,
        rate = 1.5,
        duration = duration,
        type = TemporaryBasal.Type.NORMAL,
        isAbsolute = true,
        interfaceIDs_backing = InterfaceIDs(
            endId = endId,
            pumpId = 100L,
            pumpType = InterfaceIDs.PumpType.DANA_I,
            pumpSerial = "ABC123"
        )
    )
}

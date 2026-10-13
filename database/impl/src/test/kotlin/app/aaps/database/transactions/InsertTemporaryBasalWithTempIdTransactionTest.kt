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

class InsertTemporaryBasalWithTempIdTransactionTest {

    private lateinit var database: DelegatedAppDatabase
    private lateinit var temporaryBasalDao: TemporaryBasalDao

    @BeforeEach
    fun setup() {
        temporaryBasalDao = mock()
        database = mock()
        whenever(database.temporaryBasalDao).thenReturn(temporaryBasalDao)
    }

    @Test
    fun `inserts new temporary basal when not found by temp id`() {
        val tb = createTemporaryBasal(tempId = 500L, rate = 1.5, duration = 60_000L)

        whenever(temporaryBasalDao.findByPumpTempIds(500L, InterfaceIDs.PumpType.DANA_I, "ABC123")).thenReturn(null)
        whenever(temporaryBasalDao.insert(tb)).thenReturn(1L)

        val transaction = InsertTemporaryBasalWithTempIdTransaction(tb)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.inserted).hasSize(1)
        assertThat(tb.id).isEqualTo(1L)

        verify(temporaryBasalDao).insert(tb)
    }

    @Test
    fun `does not insert when temporary basal already exists by temp id`() {
        val tb = createTemporaryBasal(tempId = 500L, rate = 1.5, duration = 60_000L)
        val existing = createTemporaryBasal(tempId = 500L, rate = 1.5, duration = 60_000L)

        whenever(temporaryBasalDao.findByPumpTempIds(500L, InterfaceIDs.PumpType.DANA_I, "ABC123")).thenReturn(existing)

        val transaction = InsertTemporaryBasalWithTempIdTransaction(tb)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.inserted).isEmpty()

        verify(temporaryBasalDao, never()).insert(any())
    }

    private fun createTemporaryBasal(
        tempId: Long,
        rate: Double,
        duration: Long
    ): TemporaryBasal = TemporaryBasal(
        timestamp = System.currentTimeMillis(),
        rate = rate,
        duration = duration,
        type = TemporaryBasal.Type.NORMAL,
        isAbsolute = true,
        interfaceIDs_backing = InterfaceIDs(
            temporaryId = tempId,
            pumpType = InterfaceIDs.PumpType.DANA_I,
            pumpSerial = "ABC123"
        )
    )
}

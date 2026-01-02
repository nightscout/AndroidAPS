package app.aaps.database.transactions

import app.aaps.database.DelegatedAppDatabase
import app.aaps.database.daos.TotalDailyDoseDao
import app.aaps.database.entities.TotalDailyDose
import app.aaps.database.entities.embedments.InterfaceIDs
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.verify
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class SyncPumpTotalDailyDoseTransactionTest {

    private lateinit var database: DelegatedAppDatabase
    private lateinit var totalDailyDoseDao: TotalDailyDoseDao

    @BeforeEach
    fun setup() {
        totalDailyDoseDao = mock()
        database = mock()
        whenever(database.totalDailyDoseDao).thenReturn(totalDailyDoseDao)
    }

    @Test
    fun `inserts new TDD when not found by pump id or timestamp`() {
        val tdd = createTotalDailyDose(pumpId = 100L, timestamp = 1000L, basalAmount = 10.0, bolusAmount = 15.0)

        whenever(totalDailyDoseDao.findByPumpIds(100L, InterfaceIDs.PumpType.DANA_I, "ABC123")).thenReturn(null)
        whenever(totalDailyDoseDao.findByPumpTimestamp(1000L, InterfaceIDs.PumpType.DANA_I, "ABC123")).thenReturn(null)

        val transaction = SyncPumpTotalDailyDoseTransaction(tdd)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.inserted).hasSize(1)
        assertThat(result.updated).isEmpty()

        verify(totalDailyDoseDao).insertNewEntry(tdd)
    }

    @Test
    fun `updates existing TDD when found by pump id`() {
        val tdd = createTotalDailyDose(pumpId = 100L, timestamp = 1000L, basalAmount = 12.0, bolusAmount = 18.0, totalAmount = 30.0)
        val existing = createTotalDailyDose(pumpId = 100L, timestamp = 1000L, basalAmount = 10.0, bolusAmount = 15.0, totalAmount = 25.0)

        whenever(totalDailyDoseDao.findByPumpIds(100L, InterfaceIDs.PumpType.DANA_I, "ABC123")).thenReturn(existing)

        val transaction = SyncPumpTotalDailyDoseTransaction(tdd)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updated).hasSize(1)
        assertThat(result.inserted).isEmpty()
        assertThat(existing.basalAmount).isEqualTo(12.0)
        assertThat(existing.bolusAmount).isEqualTo(18.0)
        assertThat(existing.totalAmount).isEqualTo(30.0)

        verify(totalDailyDoseDao).updateExistingEntry(existing)
    }

    @Test
    fun `updates existing TDD when found by timestamp but not pump id`() {
        val tdd = createTotalDailyDose(pumpId = null, timestamp = 1000L, basalAmount = 12.0, bolusAmount = 18.0)
        val existing = createTotalDailyDose(pumpId = 50L, timestamp = 1000L, basalAmount = 10.0, bolusAmount = 15.0)

        whenever(totalDailyDoseDao.findByPumpTimestamp(1000L, InterfaceIDs.PumpType.DANA_I, "ABC123")).thenReturn(existing)

        val transaction = SyncPumpTotalDailyDoseTransaction(tdd)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updated).hasSize(1)
        assertThat(result.inserted).isEmpty()
        assertThat(existing.basalAmount).isEqualTo(12.0)
        assertThat(existing.bolusAmount).isEqualTo(18.0)

        verify(totalDailyDoseDao).updateExistingEntry(existing)
    }

    @Test
    fun `updates carbs in TDD`() {
        val tdd = createTotalDailyDose(pumpId = 100L, timestamp = 1000L, basalAmount = 10.0, bolusAmount = 15.0, carbs = 200.0)
        val existing = createTotalDailyDose(pumpId = 100L, timestamp = 1000L, basalAmount = 10.0, bolusAmount = 15.0, carbs = 150.0)

        whenever(totalDailyDoseDao.findByPumpIds(100L, InterfaceIDs.PumpType.DANA_I, "ABC123")).thenReturn(existing)

        val transaction = SyncPumpTotalDailyDoseTransaction(tdd)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updated).hasSize(1)
        assertThat(existing.carbs).isEqualTo(200.0)
    }

    private fun createTotalDailyDose(
        pumpId: Long?,
        timestamp: Long,
        basalAmount: Double,
        bolusAmount: Double,
        totalAmount: Double = basalAmount + bolusAmount,
        carbs: Double = 0.0
    ): TotalDailyDose = TotalDailyDose(
        timestamp = timestamp,
        basalAmount = basalAmount,
        bolusAmount = bolusAmount,
        totalAmount = totalAmount,
        carbs = carbs,
        interfaceIDs_backing = InterfaceIDs(
            pumpId = pumpId,
            pumpType = InterfaceIDs.PumpType.DANA_I,
            pumpSerial = "ABC123"
        )
    )
}

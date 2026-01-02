package app.aaps.database.transactions

import app.aaps.database.DelegatedAppDatabase
import app.aaps.database.daos.TotalDailyDoseDao
import app.aaps.database.entities.TotalDailyDose
import app.aaps.database.entities.embedments.InterfaceIDs
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class InsertOrUpdateCachedTotalDailyDoseTransactionTest {

    private lateinit var database: DelegatedAppDatabase
    private lateinit var totalDailyDoseDao: TotalDailyDoseDao

    @BeforeEach
    fun setup() {
        totalDailyDoseDao = mock()
        database = mock()
        whenever(database.totalDailyDoseDao).thenReturn(totalDailyDoseDao)
    }

    @Test
    fun `inserts new TDD when not found by timestamp`() {
        val tdd = createTotalDailyDose(timestamp = 1000L, basalAmount = 10.0, bolusAmount = 15.0)

        whenever(totalDailyDoseDao.findByPumpTimestamp(1000L, InterfaceIDs.PumpType.CACHE)).thenReturn(null)

        val transaction = InsertOrUpdateCachedTotalDailyDoseTransaction(tdd)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.inserted).hasSize(1)
        assertThat(result.inserted[0]).isEqualTo(tdd)
        assertThat(result.updated).isEmpty()
        assertThat(result.notUpdated).isEmpty()

        verify(totalDailyDoseDao).insertNewEntry(tdd)
        verify(totalDailyDoseDao, never()).updateExistingEntry(any())
    }

    @Test
    fun `updates existing TDD when basal amount changed`() {
        val tdd = createTotalDailyDose(timestamp = 1000L, basalAmount = 12.0, bolusAmount = 15.0)
        val existing = createTotalDailyDose(timestamp = 1000L, basalAmount = 10.0, bolusAmount = 15.0)

        whenever(totalDailyDoseDao.findByPumpTimestamp(1000L, InterfaceIDs.PumpType.CACHE)).thenReturn(existing)

        val transaction = InsertOrUpdateCachedTotalDailyDoseTransaction(tdd)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updated).hasSize(1)
        assertThat(result.updated[0].basalAmount).isEqualTo(12.0)
        assertThat(result.inserted).isEmpty()
        assertThat(result.notUpdated).isEmpty()

        verify(totalDailyDoseDao).updateExistingEntry(existing)
        verify(totalDailyDoseDao, never()).insertNewEntry(any())
    }

    @Test
    fun `updates existing TDD when bolus amount changed`() {
        val tdd = createTotalDailyDose(timestamp = 1000L, basalAmount = 10.0, bolusAmount = 20.0)
        val existing = createTotalDailyDose(timestamp = 1000L, basalAmount = 10.0, bolusAmount = 15.0)

        whenever(totalDailyDoseDao.findByPumpTimestamp(1000L, InterfaceIDs.PumpType.CACHE)).thenReturn(existing)

        val transaction = InsertOrUpdateCachedTotalDailyDoseTransaction(tdd)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updated).hasSize(1)
        assertThat(result.updated[0].bolusAmount).isEqualTo(20.0)
        assertThat(result.inserted).isEmpty()
        assertThat(result.notUpdated).isEmpty()
    }

    @Test
    fun `updates existing TDD when total amount changed`() {
        val tdd = createTotalDailyDose(timestamp = 1000L, basalAmount = 10.0, bolusAmount = 15.0, totalAmount = 30.0)
        val existing = createTotalDailyDose(timestamp = 1000L, basalAmount = 10.0, bolusAmount = 15.0, totalAmount = 25.0)

        whenever(totalDailyDoseDao.findByPumpTimestamp(1000L, InterfaceIDs.PumpType.CACHE)).thenReturn(existing)

        val transaction = InsertOrUpdateCachedTotalDailyDoseTransaction(tdd)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updated).hasSize(1)
        assertThat(result.updated[0].totalAmount).isEqualTo(30.0)
        assertThat(result.inserted).isEmpty()
        assertThat(result.notUpdated).isEmpty()
    }

    @Test
    fun `updates existing TDD when carbs changed`() {
        val tdd = createTotalDailyDose(timestamp = 1000L, basalAmount = 10.0, bolusAmount = 15.0, carbs = 200.0)
        val existing = createTotalDailyDose(timestamp = 1000L, basalAmount = 10.0, bolusAmount = 15.0, carbs = 150.0)

        whenever(totalDailyDoseDao.findByPumpTimestamp(1000L, InterfaceIDs.PumpType.CACHE)).thenReturn(existing)

        val transaction = InsertOrUpdateCachedTotalDailyDoseTransaction(tdd)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updated).hasSize(1)
        assertThat(result.updated[0].carbs).isEqualTo(200.0)
        assertThat(result.inserted).isEmpty()
        assertThat(result.notUpdated).isEmpty()
    }

    @Test
    fun `does not update when all fields are same`() {
        val tdd = createTotalDailyDose(timestamp = 1000L, basalAmount = 10.0, bolusAmount = 15.0, totalAmount = 25.0, carbs = 150.0)
        val existing = createTotalDailyDose(timestamp = 1000L, basalAmount = 10.0, bolusAmount = 15.0, totalAmount = 25.0, carbs = 150.0)

        whenever(totalDailyDoseDao.findByPumpTimestamp(1000L, InterfaceIDs.PumpType.CACHE)).thenReturn(existing)

        val transaction = InsertOrUpdateCachedTotalDailyDoseTransaction(tdd)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.notUpdated).hasSize(1)
        assertThat(result.updated).isEmpty()
        assertThat(result.inserted).isEmpty()

        verify(totalDailyDoseDao, never()).updateExistingEntry(any())
        verify(totalDailyDoseDao, never()).insertNewEntry(any())
    }

    @Test
    fun `updates multiple fields when changed`() {
        val tdd = createTotalDailyDose(timestamp = 1000L, basalAmount = 12.0, bolusAmount = 18.0, totalAmount = 30.0, carbs = 200.0)
        val existing = createTotalDailyDose(timestamp = 1000L, basalAmount = 10.0, bolusAmount = 15.0, totalAmount = 25.0, carbs = 150.0)

        whenever(totalDailyDoseDao.findByPumpTimestamp(1000L, InterfaceIDs.PumpType.CACHE)).thenReturn(existing)

        val transaction = InsertOrUpdateCachedTotalDailyDoseTransaction(tdd)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updated).hasSize(1)
        assertThat(result.updated[0].basalAmount).isEqualTo(12.0)
        assertThat(result.updated[0].bolusAmount).isEqualTo(18.0)
        assertThat(result.updated[0].totalAmount).isEqualTo(30.0)
        assertThat(result.updated[0].carbs).isEqualTo(200.0)
        assertThat(result.inserted).isEmpty()
        assertThat(result.notUpdated).isEmpty()
    }

    private fun createTotalDailyDose(
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
        interfaceIDs_backing = InterfaceIDs(pumpType = InterfaceIDs.PumpType.CACHE)
    )
}

package app.aaps.database.transactions

import app.aaps.database.DelegatedAppDatabase
import app.aaps.database.daos.BolusCalculatorResultDao
import app.aaps.database.entities.BolusCalculatorResult
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

class InsertOrUpdateBolusCalculatorResultTransactionTest {

    private lateinit var database: DelegatedAppDatabase
    private lateinit var bolusCalculatorResultDao: BolusCalculatorResultDao

    @BeforeEach
    fun setup() {
        bolusCalculatorResultDao = mock()
        database = mock()
        whenever(database.bolusCalculatorResultDao).thenReturn(bolusCalculatorResultDao)
    }

    @Test
    fun `inserts new bolus calculator result when id not found`() = runTest {
        val result = createBolusCalculatorResult(id = 1, totalInsulin = 5.0)

        whenever(bolusCalculatorResultDao.findById(1)).thenReturn(null)

        val transaction = InsertOrUpdateBolusCalculatorResultTransaction(result)
        transaction.database = database
        val txResult = transaction.run()

        assertThat(txResult.inserted).hasSize(1)
        assertThat(txResult.inserted[0]).isEqualTo(result)
        assertThat(txResult.updated).isEmpty()

        verify(bolusCalculatorResultDao).insertNewEntry(result)
        verify(bolusCalculatorResultDao, never()).updateExistingEntry(any())
    }

    @Test
    fun `updates existing bolus calculator result when id found`() = runTest {
        val result = createBolusCalculatorResult(id = 1, totalInsulin = 7.5)
        val existing = createBolusCalculatorResult(id = 1, totalInsulin = 5.0)

        whenever(bolusCalculatorResultDao.findById(1)).thenReturn(existing)

        val transaction = InsertOrUpdateBolusCalculatorResultTransaction(result)
        transaction.database = database
        val txResult = transaction.run()

        assertThat(txResult.updated).hasSize(1)
        assertThat(txResult.updated[0]).isEqualTo(result)
        assertThat(txResult.inserted).isEmpty()

        verify(bolusCalculatorResultDao).updateExistingEntry(result)
        verify(bolusCalculatorResultDao, never()).insertNewEntry(any())
    }

    @Test
    fun `updates total insulin value`() = runTest {
        val existing = createBolusCalculatorResult(id = 1, totalInsulin = 3.0)
        val updated = createBolusCalculatorResult(id = 1, totalInsulin = 8.0)

        whenever(bolusCalculatorResultDao.findById(1)).thenReturn(existing)

        val transaction = InsertOrUpdateBolusCalculatorResultTransaction(updated)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updated).hasSize(1)
        assertThat(result.updated[0].totalInsulin).isEqualTo(8.0)
    }

    @Test
    fun `inserts bolus calculator result with carbs`() = runTest {
        val result = createBolusCalculatorResult(id = 1, totalInsulin = 4.0, carbs = 50.0)

        whenever(bolusCalculatorResultDao.findById(1)).thenReturn(null)

        val transaction = InsertOrUpdateBolusCalculatorResultTransaction(result)
        transaction.database = database
        val txResult = transaction.run()

        assertThat(txResult.inserted).hasSize(1)
        assertThat(txResult.inserted[0].carbs).isEqualTo(50.0)
    }

    private fun createBolusCalculatorResult(
        id: Long,
        totalInsulin: Double,
        carbs: Double = 0.0
    ): BolusCalculatorResult = BolusCalculatorResult(
        timestamp = System.currentTimeMillis(),
        targetBGLow = 80.0,
        targetBGHigh = 120.0,
        isf = 50.0,
        ic = 10.0,
        bolusIOB = 0.0,
        wasBolusIOBUsed = false,
        basalIOB = 0.0,
        wasBasalIOBUsed = false,
        glucoseValue = 150.0,
        wasGlucoseUsed = false,
        glucoseDifference = 0.0,
        glucoseInsulin = 0.0,
        glucoseTrend = 0.0,
        wasTrendUsed = false,
        trendInsulin = 0.0,
        cob = 0.0,
        wasCOBUsed = false,
        cobInsulin = 0.0,
        carbs = carbs,
        wereCarbsUsed = carbs > 0.0,
        carbsInsulin = if (carbs > 0.0) carbs / 10.0 else 0.0,
        otherCorrection = 0.0,
        wasSuperbolusUsed = false,
        superbolusInsulin = 0.0,
        wasTempTargetUsed = false,
        totalInsulin = totalInsulin,
        percentageCorrection = 100,
        profileName = "Test",
        note = "",
        interfaceIDs_backing = InterfaceIDs()
    ).also { it.id = id }
}

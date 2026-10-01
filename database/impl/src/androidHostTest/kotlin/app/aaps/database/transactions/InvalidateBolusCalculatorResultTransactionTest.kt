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

class InvalidateBolusCalculatorResultTransactionTest {

    private lateinit var database: DelegatedAppDatabase
    private lateinit var bolusCalculatorResultDao: BolusCalculatorResultDao

    @BeforeEach
    fun setup() {
        bolusCalculatorResultDao = mock()
        database = mock()
        whenever(database.bolusCalculatorResultDao).thenReturn(bolusCalculatorResultDao)
    }

    @Test
    fun `invalidates valid bolus calculator result`() = runTest {
        val bcr = createBolusCalculatorResult(id = 1, isValid = true)

        whenever(bolusCalculatorResultDao.findById(1)).thenReturn(bcr)

        val transaction = InvalidateBolusCalculatorResultTransaction(id = 1)
        transaction.database = database
        val result = transaction.run()

        assertThat(bcr.isValid).isFalse()
        assertThat(result.invalidated).hasSize(1)

        verify(bolusCalculatorResultDao).updateExistingEntry(bcr)
    }

    @Test
    fun `does not update already invalid bolus calculator result`() = runTest {
        val bcr = createBolusCalculatorResult(id = 1, isValid = false)

        whenever(bolusCalculatorResultDao.findById(1)).thenReturn(bcr)

        val transaction = InvalidateBolusCalculatorResultTransaction(id = 1)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.invalidated).isEmpty()

        verify(bolusCalculatorResultDao, never()).updateExistingEntry(any())
    }

    @Test
    fun `throws exception when bolus calculator result not found`() = runTest {
        whenever(bolusCalculatorResultDao.findById(999)).thenReturn(null)

        val transaction = InvalidateBolusCalculatorResultTransaction(id = 999)
        transaction.database = database

        try {
            transaction.run()
            throw AssertionError("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("There is no such BolusCalculatorResult")
        }
    }

    private fun createBolusCalculatorResult(
        id: Long,
        isValid: Boolean
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
        glucoseValue = 100.0,
        wasGlucoseUsed = true,
        glucoseDifference = 0.0,
        glucoseInsulin = 0.0,
        glucoseTrend = 0.0,
        wasTrendUsed = false,
        trendInsulin = 0.0,
        cob = 0.0,
        wasCOBUsed = false,
        cobInsulin = 0.0,
        carbs = 0.0,
        wereCarbsUsed = false,
        carbsInsulin = 0.0,
        otherCorrection = 0.0,
        wasSuperbolusUsed = false,
        superbolusInsulin = 0.0,
        wasTempTargetUsed = false,
        totalInsulin = 0.0,
        percentageCorrection = 100,
        profileName = "Test",
        note = "",
        isValid = isValid,
        interfaceIDs_backing = InterfaceIDs()
    ).also { it.id = id }
}

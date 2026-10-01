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

class UpdateNsIdBolusCalculatorResultTransactionTest {

    private lateinit var database: DelegatedAppDatabase
    private lateinit var bolusCalculatorResultDao: BolusCalculatorResultDao

    @BeforeEach
    fun setup() {
        bolusCalculatorResultDao = mock()
        database = mock()
        whenever(database.bolusCalculatorResultDao).thenReturn(bolusCalculatorResultDao)
    }

    @Test
    fun `updates NS ID when different`() = runTest {
        val newNsId = "new-ns-123"
        val current = createBolusCalculatorResult(id = 1, nsId = "old-ns")
        val update = createBolusCalculatorResult(id = 1, nsId = newNsId)

        whenever(bolusCalculatorResultDao.findById(1)).thenReturn(current)

        val transaction = UpdateNsIdBolusCalculatorResultTransaction(listOf(update))
        transaction.database = database
        val result = transaction.run()

        assertThat(current.interfaceIDs.nightscoutId).isEqualTo(newNsId)
        assertThat(result.updatedNsId).hasSize(1)

        verify(bolusCalculatorResultDao).updateExistingEntry(current)
    }

    @Test
    fun `does not update when NS ID is same`() = runTest {
        val sameNsId = "same-ns"
        val current = createBolusCalculatorResult(id = 1, nsId = sameNsId)
        val update = createBolusCalculatorResult(id = 1, nsId = sameNsId)

        whenever(bolusCalculatorResultDao.findById(1)).thenReturn(current)

        val transaction = UpdateNsIdBolusCalculatorResultTransaction(listOf(update))
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updatedNsId).isEmpty()

        verify(bolusCalculatorResultDao, never()).updateExistingEntry(any())
    }

    @Test
    fun `skips when bolus calculator result not found`() = runTest {
        val update = createBolusCalculatorResult(id = 999, nsId = "new-ns")

        whenever(bolusCalculatorResultDao.findById(999)).thenReturn(null)

        val transaction = UpdateNsIdBolusCalculatorResultTransaction(listOf(update))
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updatedNsId).isEmpty()

        verify(bolusCalculatorResultDao, never()).updateExistingEntry(any())
    }

    private fun createBolusCalculatorResult(
        id: Long,
        nsId: String?
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
        interfaceIDs_backing = InterfaceIDs(nightscoutId = nsId)
    ).also { it.id = id }
}

package app.aaps.database.transactions

import app.aaps.database.DelegatedAppDatabase
import app.aaps.database.daos.BolusCalculatorResultDao
import app.aaps.database.entities.BolusCalculatorResult
import app.aaps.database.entities.embedments.InterfaceIDs
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.verify
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class SyncNsBolusCalculatorResultTransactionTest {

    private lateinit var database: DelegatedAppDatabase
    private lateinit var bolusCalculatorResultDao: BolusCalculatorResultDao

    @BeforeEach
    fun setup() {
        bolusCalculatorResultDao = mock()
        database = mock()
        whenever(database.bolusCalculatorResultDao).thenReturn(bolusCalculatorResultDao)
    }

    @Test
    fun `inserts new when nsId not found and no timestamp match`() {
        val bcr = createBolusCalculatorResult(id = 0, nsId = "ns-123", timestamp = 1000L)

        whenever(bolusCalculatorResultDao.findByNSId("ns-123")).thenReturn(null)
        whenever(bolusCalculatorResultDao.findByTimestamp(1000L)).thenReturn(null)

        val transaction = SyncNsBolusCalculatorResultTransaction(listOf(bcr))
        transaction.database = database
        val result = transaction.run()

        assertThat(result.inserted).hasSize(1)
        assertThat(result.updatedNsId).isEmpty()
        assertThat(result.invalidated).isEmpty()

        verify(bolusCalculatorResultDao).insertNewEntry(bcr)
    }

    @Test
    fun `updates nsId when timestamp matches but nsId is null`() {
        val bcr = createBolusCalculatorResult(id = 0, nsId = "ns-123", timestamp = 1000L)
        val existing = createBolusCalculatorResult(id = 1, nsId = null, timestamp = 1000L)

        whenever(bolusCalculatorResultDao.findByNSId("ns-123")).thenReturn(null)
        whenever(bolusCalculatorResultDao.findByTimestamp(1000L)).thenReturn(existing)

        val transaction = SyncNsBolusCalculatorResultTransaction(listOf(bcr))
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updatedNsId).hasSize(1)
        assertThat(existing.interfaceIDs.nightscoutId).isEqualTo("ns-123")

        verify(bolusCalculatorResultDao).updateExistingEntry(existing)
    }

    @Test
    fun `invalidates when valid becomes invalid`() {
        val bcr = createBolusCalculatorResult(id = 0, nsId = "ns-123", isValid = false)
        val existing = createBolusCalculatorResult(id = 1, nsId = "ns-123", isValid = true)

        whenever(bolusCalculatorResultDao.findByNSId("ns-123")).thenReturn(existing)

        val transaction = SyncNsBolusCalculatorResultTransaction(listOf(bcr))
        transaction.database = database
        val result = transaction.run()

        assertThat(result.invalidated).hasSize(1)
        assertThat(existing.isValid).isFalse()
    }

    private fun createBolusCalculatorResult(
        id: Long,
        nsId: String?,
        timestamp: Long = System.currentTimeMillis(),
        isValid: Boolean = true
    ): BolusCalculatorResult = BolusCalculatorResult(
        timestamp = timestamp,
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
        interfaceIDs_backing = InterfaceIDs(nightscoutId = nsId),
        isValid = isValid
    ).also { it.id = id }
}

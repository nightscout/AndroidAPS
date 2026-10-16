package app.aaps.database.transactions

import app.aaps.database.DelegatedAppDatabase
import app.aaps.database.daos.BolusDao
import app.aaps.database.daos.delegated.DelegatedBolusDao
import app.aaps.database.entities.Bolus
import app.aaps.database.entities.embedments.InsulinConfiguration
import app.aaps.database.entities.embedments.InterfaceIDs
import app.aaps.database.entities.interfaces.DBEntry
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class InsertBolusWithTempIdTransactionTest {

    private lateinit var database: DelegatedAppDatabase
    private lateinit var bolusDao: BolusDao

    @BeforeEach
    fun setup() {
        bolusDao = mock()
        database = mock()
        whenever(database.bolusDao).thenReturn(bolusDao)
    }

    @Test
    fun `inserts new bolus when not found by temp id`() = runTest {
        val bolus = createBolus(tempId = 500L, amount = 5.0)

        whenever(bolusDao.findByPumpTempIds(500L, InterfaceIDs.PumpType.DANA_I, "ABC123")).thenReturn(null)
        whenever(bolusDao.insertNewEntry(bolus)).thenReturn(1L)

        val transaction = InsertBolusWithTempIdTransaction(bolus)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.inserted).hasSize(1)
        assertThat(bolus.id).isEqualTo(1L)

        verify(bolusDao).insertNewEntry(bolus)
    }

    /** #5280: the raw insert reported no change, so the IOB and the overview saw the row only after the pump history. */
    @Test
    fun `a new bolus is reported as a change`() = runTest {
        val changes = mutableListOf<DBEntry>()
        whenever(database.bolusDao).thenReturn(DelegatedBolusDao(changes, bolusDao))
        val bolus = createBolus(tempId = 500L, amount = 5.0)
        whenever(bolusDao.findByPumpTempIds(500L, InterfaceIDs.PumpType.DANA_I, "ABC123")).thenReturn(null)
        whenever(bolusDao.insertNewEntry(bolus)).thenReturn(1L)

        val transaction = InsertBolusWithTempIdTransaction(bolus)
        transaction.database = database
        transaction.run()

        assertThat(changes).containsExactly(bolus)
    }

    @Test
    fun `does not insert when bolus already exists by temp id`() = runTest {
        val bolus = createBolus(tempId = 500L, amount = 5.0)
        val existing = createBolus(tempId = 500L, amount = 5.0)

        whenever(bolusDao.findByPumpTempIds(500L, InterfaceIDs.PumpType.DANA_I, "ABC123")).thenReturn(existing)

        val transaction = InsertBolusWithTempIdTransaction(bolus)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.inserted).isEmpty()

        verify(bolusDao, never()).insert(any())
        verify(bolusDao, never()).insertNewEntry(any())
    }

    private fun createBolus(
        tempId: Long,
        amount: Double
    ): Bolus = Bolus(
        timestamp = System.currentTimeMillis(),
        amount = amount,
        type = Bolus.Type.NORMAL,
        interfaceIDs_backing = InterfaceIDs(
            temporaryId = tempId,
            pumpType = InterfaceIDs.PumpType.DANA_I,
            pumpSerial = "ABC123"
        ),
        insulinConfiguration = InsulinConfiguration("some", 600000L, 60000L, 1.0)
    )
}

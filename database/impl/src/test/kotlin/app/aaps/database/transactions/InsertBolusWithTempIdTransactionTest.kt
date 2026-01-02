package app.aaps.database.transactions

import app.aaps.database.DelegatedAppDatabase
import app.aaps.database.daos.BolusDao
import app.aaps.database.entities.Bolus
import app.aaps.database.entities.embedments.InterfaceIDs
import com.google.common.truth.Truth.assertThat
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
    fun `inserts new bolus when not found by temp id`() {
        val bolus = createBolus(tempId = 500L, amount = 5.0)

        whenever(bolusDao.findByPumpTempIds(500L, InterfaceIDs.PumpType.DANA_I, "ABC123")).thenReturn(null)
        whenever(bolusDao.insert(bolus)).thenReturn(1L)

        val transaction = InsertBolusWithTempIdTransaction(bolus)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.inserted).hasSize(1)
        assertThat(bolus.id).isEqualTo(1L)

        verify(bolusDao).insert(bolus)
    }

    @Test
    fun `does not insert when bolus already exists by temp id`() {
        val bolus = createBolus(tempId = 500L, amount = 5.0)
        val existing = createBolus(tempId = 500L, amount = 5.0)

        whenever(bolusDao.findByPumpTempIds(500L, InterfaceIDs.PumpType.DANA_I, "ABC123")).thenReturn(existing)

        val transaction = InsertBolusWithTempIdTransaction(bolus)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.inserted).isEmpty()

        verify(bolusDao, never()).insert(any())
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
        )
    )
}

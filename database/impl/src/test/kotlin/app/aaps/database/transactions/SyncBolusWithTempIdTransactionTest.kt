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

class SyncBolusWithTempIdTransactionTest {

    private lateinit var database: DelegatedAppDatabase
    private lateinit var bolusDao: BolusDao

    @BeforeEach
    fun setup() {
        bolusDao = mock()
        database = mock()
        whenever(database.bolusDao).thenReturn(bolusDao)
    }

    @Test
    fun `updates existing bolus when found by temp id`() {
        val bolus = createBolus(tempId = 500L, pumpId = 100L, amount = 7.0, timestamp = 2000L)
        val existing = createBolus(tempId = 500L, pumpId = null, amount = 5.0, timestamp = 1000L)

        whenever(bolusDao.findByPumpTempIds(500L, InterfaceIDs.PumpType.DANA_I, "ABC123")).thenReturn(existing)

        val transaction = SyncBolusWithTempIdTransaction(bolus, null)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updated).hasSize(1)
        assertThat(existing.timestamp).isEqualTo(2000L)
        assertThat(existing.amount).isEqualTo(7.0)
        assertThat(existing.interfaceIDs.pumpId).isEqualTo(100L)

        verify(bolusDao).updateExistingEntry(existing)
    }

    @Test
    fun `does not update when not found by temp id`() {
        val bolus = createBolus(tempId = 500L, pumpId = 100L, amount = 7.0, timestamp = 2000L)

        whenever(bolusDao.findByPumpTempIds(500L, InterfaceIDs.PumpType.DANA_I, "ABC123")).thenReturn(null)

        val transaction = SyncBolusWithTempIdTransaction(bolus, null)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updated).isEmpty()

        verify(bolusDao, never()).updateExistingEntry(any())
    }

    @Test
    fun `updates type when provided`() {
        val bolus = createBolus(tempId = 500L, pumpId = 100L, amount = 5.0, timestamp = 1000L, type = Bolus.Type.NORMAL)
        val existing = createBolus(tempId = 500L, pumpId = null, amount = 5.0, timestamp = 1000L, type = Bolus.Type.NORMAL)

        whenever(bolusDao.findByPumpTempIds(500L, InterfaceIDs.PumpType.DANA_I, "ABC123")).thenReturn(existing)

        val transaction = SyncBolusWithTempIdTransaction(bolus, Bolus.Type.SMB)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updated).hasSize(1)
        assertThat(existing.type).isEqualTo(Bolus.Type.SMB)
    }

    private fun createBolus(
        tempId: Long,
        pumpId: Long?,
        amount: Double,
        timestamp: Long,
        type: Bolus.Type = Bolus.Type.NORMAL
    ): Bolus = Bolus(
        timestamp = timestamp,
        amount = amount,
        type = type,
        interfaceIDs_backing = InterfaceIDs(
            temporaryId = tempId,
            pumpId = pumpId,
            pumpType = InterfaceIDs.PumpType.DANA_I,
            pumpSerial = "ABC123"
        )
    )
}

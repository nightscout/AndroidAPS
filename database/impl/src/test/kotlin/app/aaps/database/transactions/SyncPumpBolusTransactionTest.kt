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

class SyncPumpBolusTransactionTest {

    private lateinit var database: DelegatedAppDatabase
    private lateinit var bolusDao: BolusDao

    @BeforeEach
    fun setup() {
        bolusDao = mock()
        database = mock()
        whenever(database.bolusDao).thenReturn(bolusDao)
    }

    @Test
    fun `inserts new bolus when not found by pump ids`() {
        val bolus = createBolus(pumpId = 100L, pumpType = InterfaceIDs.PumpType.DANA_I, pumpSerial = "ABC123", amount = 5.0)

        whenever(bolusDao.findByPumpIds(100L, InterfaceIDs.PumpType.DANA_I, "ABC123")).thenReturn(null)

        val transaction = SyncPumpBolusTransaction(bolus, null)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.inserted).hasSize(1)
        assertThat(result.updated).isEmpty()

        verify(bolusDao).insertNewEntry(bolus)
    }

    @Test
    fun `updates existing bolus when found by pump ids`() {
        val bolus = createBolus(pumpId = 100L, pumpType = InterfaceIDs.PumpType.DANA_I, pumpSerial = "ABC123", amount = 7.0, timestamp = 2000L)
        val existing = createBolus(pumpId = 100L, pumpType = InterfaceIDs.PumpType.DANA_I, pumpSerial = "ABC123", amount = 5.0, timestamp = 1000L)

        whenever(bolusDao.findByPumpIds(100L, InterfaceIDs.PumpType.DANA_I, "ABC123")).thenReturn(existing)

        val transaction = SyncPumpBolusTransaction(bolus, null)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updated).hasSize(1)
        assertThat(result.inserted).isEmpty()
        assertThat(existing.amount).isEqualTo(7.0)
        assertThat(existing.timestamp).isEqualTo(2000L)

        verify(bolusDao).updateExistingEntry(existing)
        verify(bolusDao, never()).insertNewEntry(any())
    }

    @Test
    fun `does not update when values are same`() {
        val bolus = createBolus(pumpId = 100L, pumpType = InterfaceIDs.PumpType.DANA_I, pumpSerial = "ABC123", amount = 5.0, timestamp = 1000L)
        val existing = createBolus(pumpId = 100L, pumpType = InterfaceIDs.PumpType.DANA_I, pumpSerial = "ABC123", amount = 5.0, timestamp = 1000L)

        whenever(bolusDao.findByPumpIds(100L, InterfaceIDs.PumpType.DANA_I, "ABC123")).thenReturn(existing)

        val transaction = SyncPumpBolusTransaction(bolus, null)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updated).isEmpty()
        assertThat(result.inserted).isEmpty()

        verify(bolusDao, never()).updateExistingEntry(any())
    }

    @Test
    fun `updates bolus type when provided`() {
        val bolus = createBolus(pumpId = 100L, pumpType = InterfaceIDs.PumpType.DANA_I, pumpSerial = "ABC123", amount = 5.0, type = Bolus.Type.NORMAL)
        val existing = createBolus(pumpId = 100L, pumpType = InterfaceIDs.PumpType.DANA_I, pumpSerial = "ABC123", amount = 5.0, type = Bolus.Type.NORMAL)

        whenever(bolusDao.findByPumpIds(100L, InterfaceIDs.PumpType.DANA_I, "ABC123")).thenReturn(existing)

        val transaction = SyncPumpBolusTransaction(bolus, Bolus.Type.SMB)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updated).hasSize(1)
        assertThat(existing.type).isEqualTo(Bolus.Type.SMB)
    }

    private fun createBolus(
        pumpId: Long,
        pumpType: InterfaceIDs.PumpType,
        pumpSerial: String,
        amount: Double,
        timestamp: Long = System.currentTimeMillis(),
        type: Bolus.Type = Bolus.Type.NORMAL
    ): Bolus = Bolus(
        timestamp = timestamp,
        amount = amount,
        type = type,
        interfaceIDs_backing = InterfaceIDs(
            pumpId = pumpId,
            pumpType = pumpType,
            pumpSerial = pumpSerial
        )
    )
}

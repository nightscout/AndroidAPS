package app.aaps.database.transactions

import app.aaps.database.DelegatedAppDatabase
import app.aaps.database.daos.TherapyEventDao
import app.aaps.database.entities.TherapyEvent
import app.aaps.database.entities.data.GlucoseUnit
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

class InsertOrUpdateTherapyEventTransactionTest {

    private lateinit var database: DelegatedAppDatabase
    private lateinit var therapyEventDao: TherapyEventDao

    @BeforeEach
    fun setup() {
        therapyEventDao = mock()
        database = mock()
        whenever(database.therapyEventDao).thenReturn(therapyEventDao)
    }

    @Test
    fun `inserts new therapy event when id not found`() = runTest {
        val event = createTherapyEvent(id = 1)

        whenever(therapyEventDao.findById(1)).thenReturn(null)

        val transaction = InsertOrUpdateTherapyEventTransaction(event)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.inserted).hasSize(1)
        assertThat(result.inserted[0]).isEqualTo(event)
        assertThat(result.updated).isEmpty()

        verify(therapyEventDao).insertNewEntry(event)
        verify(therapyEventDao, never()).updateExistingEntry(any())
    }

    @Test
    fun `updates existing therapy event when id found`() = runTest {
        val event = createTherapyEvent(id = 1)
        val existing = createTherapyEvent(id = 1)

        whenever(therapyEventDao.findById(1)).thenReturn(existing)

        val transaction = InsertOrUpdateTherapyEventTransaction(event)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updated).hasSize(1)
        assertThat(result.updated[0]).isEqualTo(event)
        assertThat(result.inserted).isEmpty()

        verify(therapyEventDao).updateExistingEntry(event)
        verify(therapyEventDao, never()).insertNewEntry(any())
    }

    @Test
    fun `inserts different therapy event types`() = runTest {
        val types = listOf(
            TherapyEvent.Type.CANNULA_CHANGE,
            TherapyEvent.Type.INSULIN_CHANGE,
            TherapyEvent.Type.SENSOR_CHANGE
        )

        types.forEach { type ->
            val event = createTherapyEvent(id = 1, type = type)
            whenever(therapyEventDao.findById(1)).thenReturn(null)

            val transaction = InsertOrUpdateTherapyEventTransaction(event)
            transaction.database = database
            val result = transaction.run()

            assertThat(result.inserted).hasSize(1)
            assertThat(result.inserted[0].type).isEqualTo(type)
        }
    }

    @Test
    fun `transaction result has correct structure`() = runTest {
        val result = InsertOrUpdateTherapyEventTransaction.TransactionResult()

        assertThat(result.inserted).isEmpty()
        assertThat(result.updated).isEmpty()
    }

    private fun createTherapyEvent(
        id: Long,
        type: TherapyEvent.Type = TherapyEvent.Type.NOTE
    ): TherapyEvent = TherapyEvent(
        timestamp = System.currentTimeMillis(),
        type = type,
        glucoseUnit = GlucoseUnit.MGDL,
        interfaceIDs_backing = InterfaceIDs()
    ).also { it.id = id }
}

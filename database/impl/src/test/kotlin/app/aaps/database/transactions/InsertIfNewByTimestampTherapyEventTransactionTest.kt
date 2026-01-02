package app.aaps.database.transactions

import app.aaps.database.DelegatedAppDatabase
import app.aaps.database.daos.TherapyEventDao
import app.aaps.database.entities.TherapyEvent
import app.aaps.database.entities.data.GlucoseUnit
import app.aaps.database.entities.embedments.InterfaceIDs
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class InsertIfNewByTimestampTherapyEventTransactionTest {

    private lateinit var database: DelegatedAppDatabase
    private lateinit var therapyEventDao: TherapyEventDao

    @BeforeEach
    fun setup() {
        therapyEventDao = mock()
        database = mock()
        whenever(database.therapyEventDao).thenReturn(therapyEventDao)
    }

    @Test
    fun `inserts new therapy event when not found by timestamp and type`() {
        val therapyEvent = createTherapyEvent(timestamp = 1000L, type = TherapyEvent.Type.NOTE)

        whenever(therapyEventDao.findByTimestamp(TherapyEvent.Type.NOTE, 1000L)).thenReturn(null)

        val transaction = InsertIfNewByTimestampTherapyEventTransaction(therapyEvent)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.inserted).hasSize(1)
        assertThat(result.existing).isEmpty()

        verify(therapyEventDao).insertNewEntry(therapyEvent)
    }

    @Test
    fun `does not insert when therapy event exists by timestamp and type`() {
        val therapyEvent = createTherapyEvent(timestamp = 1000L, type = TherapyEvent.Type.NOTE)
        val existing = createTherapyEvent(timestamp = 1000L, type = TherapyEvent.Type.NOTE)

        whenever(therapyEventDao.findByTimestamp(TherapyEvent.Type.NOTE, 1000L)).thenReturn(existing)

        val transaction = InsertIfNewByTimestampTherapyEventTransaction(therapyEvent)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.inserted).isEmpty()
        assertThat(result.existing).hasSize(1)

        verify(therapyEventDao, never()).insertNewEntry(any())
    }

    private fun createTherapyEvent(
        timestamp: Long,
        type: TherapyEvent.Type
    ): TherapyEvent = TherapyEvent(
        timestamp = timestamp,
        type = type,
        note = "Test",
        glucoseUnit = GlucoseUnit.MGDL,
        interfaceIDs_backing = InterfaceIDs()
    )
}

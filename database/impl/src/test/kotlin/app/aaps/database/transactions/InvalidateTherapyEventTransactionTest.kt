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

class InvalidateTherapyEventTransactionTest {

    private lateinit var database: DelegatedAppDatabase
    private lateinit var therapyEventDao: TherapyEventDao

    @BeforeEach
    fun setup() {
        therapyEventDao = mock()
        database = mock()
        whenever(database.therapyEventDao).thenReturn(therapyEventDao)
    }

    @Test
    fun `invalidates valid therapy event`() {
        val event = createTherapyEvent(id = 1, isValid = true)

        whenever(therapyEventDao.findById(1)).thenReturn(event)

        val transaction = InvalidateTherapyEventTransaction(id = 1)
        transaction.database = database
        val result = transaction.run()

        assertThat(event.isValid).isFalse()
        assertThat(result.invalidated).hasSize(1)
        assertThat(result.invalidated[0]).isEqualTo(event)

        verify(therapyEventDao).updateExistingEntry(event)
    }

    @Test
    fun `does not update already invalid therapy event`() {
        val event = createTherapyEvent(id = 1, isValid = false)

        whenever(therapyEventDao.findById(1)).thenReturn(event)

        val transaction = InvalidateTherapyEventTransaction(id = 1)
        transaction.database = database
        val result = transaction.run()

        assertThat(event.isValid).isFalse()
        assertThat(result.invalidated).isEmpty()

        verify(therapyEventDao, never()).updateExistingEntry(any())
    }

    @Test
    fun `throws exception when therapy event not found`() {
        whenever(therapyEventDao.findById(999)).thenReturn(null)

        val transaction = InvalidateTherapyEventTransaction(id = 999)
        transaction.database = database

        try {
            transaction.run()
            throw AssertionError("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("There is no such TherapyEvent")
        }
    }

    @Test
    fun `preserves event type when invalidating`() {
        val type = TherapyEvent.Type.CANNULA_CHANGE
        val event = createTherapyEvent(id = 1, isValid = true, type = type)

        whenever(therapyEventDao.findById(1)).thenReturn(event)

        val transaction = InvalidateTherapyEventTransaction(id = 1)
        transaction.database = database
        transaction.run()

        assertThat(event.type).isEqualTo(type)
        assertThat(event.isValid).isFalse()
    }

    private fun createTherapyEvent(
        id: Long,
        isValid: Boolean,
        type: TherapyEvent.Type = TherapyEvent.Type.NOTE
    ): TherapyEvent = TherapyEvent(
        timestamp = System.currentTimeMillis(),
        type = type,
        isValid = isValid,
        glucoseUnit = GlucoseUnit.MGDL,
        interfaceIDs_backing = InterfaceIDs()
    ).also { it.id = id }
}

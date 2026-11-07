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

class InvalidateTherapyEventsWithNoteTransactionTest {

    private lateinit var database: DelegatedAppDatabase
    private lateinit var therapyEventDao: TherapyEventDao

    @BeforeEach
    fun setup() {
        therapyEventDao = mock()
        database = mock()
        whenever(database.therapyEventDao).thenReturn(therapyEventDao)
    }

    @Test
    fun `invalidates events with matching note`() = runTest {
        val searchNote = "test note"
        val event1 = createTherapyEvent(id = 1, note = "This is a test note", isValid = true)
        val event2 = createTherapyEvent(id = 2, note = "Another test note here", isValid = true)
        val event3 = createTherapyEvent(id = 3, note = "Different text", isValid = true)

        whenever(therapyEventDao.getValidByType(TherapyEvent.Type.NOTE)).thenReturn(listOf(event1, event2, event3))

        val transaction = InvalidateTherapyEventsWithNoteTransaction(searchNote)
        transaction.database = database
        val result = transaction.run()

        assertThat(event1.isValid).isFalse()
        assertThat(event2.isValid).isFalse()
        assertThat(event3.isValid).isTrue() // Should not be invalidated
        assertThat(result.invalidated).hasSize(2)

        verify(therapyEventDao).updateExistingEntry(event1)
        verify(therapyEventDao).updateExistingEntry(event2)
    }

    @Test
    fun `does not invalidate events without matching note`() = runTest {
        val searchNote = "missing"
        val event1 = createTherapyEvent(id = 1, note = "This is a test", isValid = true)
        val event2 = createTherapyEvent(id = 2, note = "Another note", isValid = true)

        whenever(therapyEventDao.getValidByType(TherapyEvent.Type.NOTE)).thenReturn(listOf(event1, event2))

        val transaction = InvalidateTherapyEventsWithNoteTransaction(searchNote)
        transaction.database = database
        val result = transaction.run()

        assertThat(event1.isValid).isTrue()
        assertThat(event2.isValid).isTrue()
        assertThat(result.invalidated).isEmpty()

        verify(therapyEventDao, never()).updateExistingEntry(any())
    }

    @Test
    fun `does not invalidate already invalid events`() = runTest {
        val searchNote = "test"
        val event1 = createTherapyEvent(id = 1, note = "test note", isValid = false)

        whenever(therapyEventDao.getValidByType(TherapyEvent.Type.NOTE)).thenReturn(listOf(event1))

        val transaction = InvalidateTherapyEventsWithNoteTransaction(searchNote)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.invalidated).isEmpty()

        verify(therapyEventDao, never()).updateExistingEntry(any())
    }

    @Test
    fun `handles empty event list`() = runTest {
        val searchNote = "test"

        whenever(therapyEventDao.getValidByType(TherapyEvent.Type.NOTE)).thenReturn(emptyList())

        val transaction = InvalidateTherapyEventsWithNoteTransaction(searchNote)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.invalidated).isEmpty()

        verify(therapyEventDao, never()).updateExistingEntry(any())
    }

    @Test
    fun `handles events with null notes`() = runTest {
        val searchNote = "test"
        val event1 = createTherapyEvent(id = 1, note = null, isValid = true)
        val event2 = createTherapyEvent(id = 2, note = "test note", isValid = true)

        whenever(therapyEventDao.getValidByType(TherapyEvent.Type.NOTE)).thenReturn(listOf(event1, event2))

        val transaction = InvalidateTherapyEventsWithNoteTransaction(searchNote)
        transaction.database = database
        val result = transaction.run()

        assertThat(event1.isValid).isTrue() // Null note, should not match
        assertThat(event2.isValid).isFalse()
        assertThat(result.invalidated).hasSize(1)
    }

    private fun createTherapyEvent(
        id: Long,
        note: String?,
        isValid: Boolean
    ): TherapyEvent = TherapyEvent(
        timestamp = System.currentTimeMillis(),
        type = TherapyEvent.Type.NOTE,
        note = note,
        isValid = isValid,
        glucoseUnit = GlucoseUnit.MGDL,
        interfaceIDs_backing = InterfaceIDs()
    ).also { it.id = id }
}

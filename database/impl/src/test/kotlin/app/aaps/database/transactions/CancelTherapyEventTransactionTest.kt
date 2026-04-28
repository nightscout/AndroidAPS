package app.aaps.database.transactions

import app.aaps.database.DelegatedAppDatabase
import app.aaps.database.daos.TherapyEventDao
import app.aaps.database.entities.TherapyEvent
import app.aaps.database.entities.data.GlucoseUnit
import app.aaps.database.entities.embedments.InterfaceIDs
import app.aaps.database.entities.interfaces.end
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class CancelTherapyEventTransactionTest {

    private lateinit var database: DelegatedAppDatabase
    private lateinit var therapyEventDao: TherapyEventDao

    @BeforeEach
    fun setup() {
        therapyEventDao = mock()
        database = mock()
        whenever(database.therapyEventDao).thenReturn(therapyEventDao)
    }

    @Test
    fun `cuts active temporary therapy event`() = runTest {
        val timestamp = 31_000L
        val current = createTherapyEvent(id = 7, timestamp = 1_000L, duration = 60_000L)
        whenever(therapyEventDao.findById(7)).thenReturn(current)

        val transaction = CancelTherapyEventTransaction(id = 7, timestamp = timestamp)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updated).hasSize(1)
        assertThat(current.end).isEqualTo(timestamp)
        verify(therapyEventDao).updateExistingEntry(current)
    }

    @Test
    fun `cuts indefinite therapy event (duration=Long MAX_VALUE) to finite`() = runTest {
        val timestamp = 31_000L
        val current = createTherapyEvent(id = 7, timestamp = 1_000L, duration = Long.MAX_VALUE)
        whenever(therapyEventDao.findById(7)).thenReturn(current)

        val transaction = CancelTherapyEventTransaction(id = 7, timestamp = timestamp)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updated).hasSize(1)
        assertThat(current.end).isEqualTo(timestamp)
        assertThat(current.duration).isEqualTo(timestamp - 1_000L)
        verify(therapyEventDao).updateExistingEntry(current)
    }

    @Test
    fun `does nothing when id is missing`() = runTest {
        whenever(therapyEventDao.findById(99)).thenReturn(null)

        val transaction = CancelTherapyEventTransaction(id = 99, timestamp = 31_000L)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updated).isEmpty()
        verify(therapyEventDao, never()).updateExistingEntry(any())
    }

    @Test
    fun `does nothing when row is invalid`() = runTest {
        val current = createTherapyEvent(id = 7, timestamp = 1_000L, duration = 60_000L, isValid = false)
        whenever(therapyEventDao.findById(7)).thenReturn(current)

        val transaction = CancelTherapyEventTransaction(id = 7, timestamp = 31_000L)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updated).isEmpty()
        verify(therapyEventDao, never()).updateExistingEntry(any())
    }

    @Test
    fun `does nothing when row started at or after timestamp`() = runTest {
        val current = createTherapyEvent(id = 7, timestamp = 31_000L, duration = 60_000L)
        whenever(therapyEventDao.findById(7)).thenReturn(current)

        val transaction = CancelTherapyEventTransaction(id = 7, timestamp = 31_000L)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updated).isEmpty()
        verify(therapyEventDao, never()).updateExistingEntry(any())
    }

    @Test
    fun `does nothing when row already finished`() = runTest {
        val current = createTherapyEvent(id = 7, timestamp = 1_000L, duration = 5_000L)
        whenever(therapyEventDao.findById(7)).thenReturn(current)

        val transaction = CancelTherapyEventTransaction(id = 7, timestamp = 31_000L)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updated).isEmpty()
        verify(therapyEventDao, never()).updateExistingEntry(any())
    }

    private fun createTherapyEvent(
        id: Long,
        timestamp: Long,
        duration: Long,
        isValid: Boolean = true
    ): TherapyEvent = TherapyEvent(
        timestamp = timestamp,
        type = TherapyEvent.Type.NOTE,
        duration = duration,
        glucoseUnit = GlucoseUnit.MGDL,
        interfaceIDs_backing = InterfaceIDs(),
        isValid = isValid
    ).also { it.id = id }
}

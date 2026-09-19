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

class UpdateNsIdTherapyEventTransactionTest {

    private lateinit var database: DelegatedAppDatabase
    private lateinit var therapyEventDao: TherapyEventDao

    @BeforeEach
    fun setup() {
        therapyEventDao = mock()
        database = mock()
        whenever(database.therapyEventDao).thenReturn(therapyEventDao)
    }

    @Test
    fun `updates NS ID when different`() = runTest {
        val newNsId = "new-ns-123"
        val current = createTherapyEvent(id = 1, nsId = "old-ns")
        val update = createTherapyEvent(id = 1, nsId = newNsId)

        whenever(therapyEventDao.findById(1)).thenReturn(current)

        val transaction = UpdateNsIdTherapyEventTransaction(listOf(update))
        transaction.database = database
        val result = transaction.run()

        assertThat(current.interfaceIDs.nightscoutId).isEqualTo(newNsId)
        assertThat(result.updatedNsId).hasSize(1)

        verify(therapyEventDao).updateExistingEntry(current)
    }

    @Test
    fun `does not update when NS ID is same`() = runTest {
        val sameNsId = "same-ns"
        val current = createTherapyEvent(id = 1, nsId = sameNsId)
        val update = createTherapyEvent(id = 1, nsId = sameNsId)

        whenever(therapyEventDao.findById(1)).thenReturn(current)

        val transaction = UpdateNsIdTherapyEventTransaction(listOf(update))
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updatedNsId).isEmpty()

        verify(therapyEventDao, never()).updateExistingEntry(any())
    }

    @Test
    fun `skips when therapy event not found`() = runTest {
        val update = createTherapyEvent(id = 999, nsId = "new-ns")

        whenever(therapyEventDao.findById(999)).thenReturn(null)

        val transaction = UpdateNsIdTherapyEventTransaction(listOf(update))
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updatedNsId).isEmpty()

        verify(therapyEventDao, never()).updateExistingEntry(any())
    }

    @Test
    fun `handles empty list`() = runTest {
        val transaction = UpdateNsIdTherapyEventTransaction(emptyList())
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updatedNsId).isEmpty()

        verify(therapyEventDao, never()).updateExistingEntry(any())
    }

    private fun createTherapyEvent(
        id: Long,
        nsId: String?
    ): TherapyEvent = TherapyEvent(
        timestamp = System.currentTimeMillis(),
        type = TherapyEvent.Type.NOTE,
        glucoseUnit = GlucoseUnit.MGDL,
        interfaceIDs_backing = InterfaceIDs(nightscoutId = nsId)
    ).also { it.id = id }
}

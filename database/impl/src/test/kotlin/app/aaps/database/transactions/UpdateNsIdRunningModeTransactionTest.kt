package app.aaps.database.transactions

import app.aaps.database.DelegatedAppDatabase
import app.aaps.database.daos.RunningModeDao
import app.aaps.database.entities.RunningMode
import app.aaps.database.entities.embedments.InterfaceIDs
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class UpdateNsIdRunningModeTransactionTest {

    private lateinit var database: DelegatedAppDatabase
    private lateinit var runningModeDao: RunningModeDao

    @BeforeEach
    fun setup() {
        runningModeDao = mock()
        database = mock()
        whenever(database.runningModeDao).thenReturn(runningModeDao)
    }

    @Test
    fun `updates NS ID when different`() {
        val newNsId = "new-ns-123"
        val current = createRunningMode(id = 1, nsId = "old-ns")
        val update = createRunningMode(id = 1, nsId = newNsId)

        whenever(runningModeDao.findById(1)).thenReturn(current)

        val transaction = UpdateNsIdRunningModeTransaction(listOf(update))
        transaction.database = database
        val result = transaction.run()

        assertThat(current.interfaceIDs.nightscoutId).isEqualTo(newNsId)
        assertThat(result.updatedNsId).hasSize(1)

        verify(runningModeDao).updateExistingEntry(current)
    }

    @Test
    fun `does not update when NS ID is same`() {
        val sameNsId = "same-ns"
        val current = createRunningMode(id = 1, nsId = sameNsId)
        val update = createRunningMode(id = 1, nsId = sameNsId)

        whenever(runningModeDao.findById(1)).thenReturn(current)

        val transaction = UpdateNsIdRunningModeTransaction(listOf(update))
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updatedNsId).isEmpty()

        verify(runningModeDao, never()).updateExistingEntry(any())
    }

    @Test
    fun `skips when running mode not found`() {
        val update = createRunningMode(id = 999, nsId = "new-ns")

        whenever(runningModeDao.findById(999)).thenReturn(null)

        val transaction = UpdateNsIdRunningModeTransaction(listOf(update))
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updatedNsId).isEmpty()

        verify(runningModeDao, never()).updateExistingEntry(any())
    }

    private fun createRunningMode(
        id: Long,
        nsId: String?
    ): RunningMode = RunningMode(
        timestamp = System.currentTimeMillis(),
        mode = RunningMode.Mode.OPEN_LOOP,
        interfaceIDs_backing = InterfaceIDs(nightscoutId = nsId),
        duration = 0
    ).also { it.id = id }
}

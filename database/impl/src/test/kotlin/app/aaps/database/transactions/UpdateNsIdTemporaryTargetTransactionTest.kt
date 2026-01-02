package app.aaps.database.transactions

import app.aaps.database.DelegatedAppDatabase
import app.aaps.database.daos.TemporaryTargetDao
import app.aaps.database.entities.TemporaryTarget
import app.aaps.database.entities.embedments.InterfaceIDs
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class UpdateNsIdTemporaryTargetTransactionTest {

    private lateinit var database: DelegatedAppDatabase
    private lateinit var temporaryTargetDao: TemporaryTargetDao

    @BeforeEach
    fun setup() {
        temporaryTargetDao = mock()
        database = mock()
        whenever(database.temporaryTargetDao).thenReturn(temporaryTargetDao)
    }

    @Test
    fun `updates NS ID when different`() {
        val newNsId = "new-ns-123"
        val current = createTemporaryTarget(id = 1, nsId = "old-ns")
        val update = createTemporaryTarget(id = 1, nsId = newNsId)

        whenever(temporaryTargetDao.findById(1)).thenReturn(current)

        val transaction = UpdateNsIdTemporaryTargetTransaction(listOf(update))
        transaction.database = database
        val result = transaction.run()

        assertThat(current.interfaceIDs.nightscoutId).isEqualTo(newNsId)
        assertThat(result.updatedNsId).hasSize(1)

        verify(temporaryTargetDao).updateExistingEntry(current)
    }

    @Test
    fun `does not update when NS ID is same`() {
        val sameNsId = "same-ns"
        val current = createTemporaryTarget(id = 1, nsId = sameNsId)
        val update = createTemporaryTarget(id = 1, nsId = sameNsId)

        whenever(temporaryTargetDao.findById(1)).thenReturn(current)

        val transaction = UpdateNsIdTemporaryTargetTransaction(listOf(update))
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updatedNsId).isEmpty()

        verify(temporaryTargetDao, never()).updateExistingEntry(any())
    }

    @Test
    fun `skips when temporary target not found`() {
        val update = createTemporaryTarget(id = 999, nsId = "new-ns")

        whenever(temporaryTargetDao.findById(999)).thenReturn(null)

        val transaction = UpdateNsIdTemporaryTargetTransaction(listOf(update))
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updatedNsId).isEmpty()

        verify(temporaryTargetDao, never()).updateExistingEntry(any())
    }

    @Test
    fun `updates multiple temporary targets`() {
        val tt1 = createTemporaryTarget(id = 1, nsId = "old-1")
        val tt2 = createTemporaryTarget(id = 2, nsId = "old-2")
        val update1 = createTemporaryTarget(id = 1, nsId = "new-1")
        val update2 = createTemporaryTarget(id = 2, nsId = "new-2")

        whenever(temporaryTargetDao.findById(1)).thenReturn(tt1)
        whenever(temporaryTargetDao.findById(2)).thenReturn(tt2)

        val transaction = UpdateNsIdTemporaryTargetTransaction(listOf(update1, update2))
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updatedNsId).hasSize(2)
        assertThat(tt1.interfaceIDs.nightscoutId).isEqualTo("new-1")
        assertThat(tt2.interfaceIDs.nightscoutId).isEqualTo("new-2")

        verify(temporaryTargetDao).updateExistingEntry(tt1)
        verify(temporaryTargetDao).updateExistingEntry(tt2)
    }

    @Test
    fun `handles empty list`() {
        val transaction = UpdateNsIdTemporaryTargetTransaction(emptyList())
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updatedNsId).isEmpty()

        verify(temporaryTargetDao, never()).updateExistingEntry(any())
    }

    @Test
    fun `updates from null to actual NS ID`() {
        val current = createTemporaryTarget(id = 1, nsId = null)
        val update = createTemporaryTarget(id = 1, nsId = "new-ns")

        whenever(temporaryTargetDao.findById(1)).thenReturn(current)

        val transaction = UpdateNsIdTemporaryTargetTransaction(listOf(update))
        transaction.database = database
        val result = transaction.run()

        assertThat(current.interfaceIDs.nightscoutId).isEqualTo("new-ns")
        assertThat(result.updatedNsId).hasSize(1)

        verify(temporaryTargetDao).updateExistingEntry(current)
    }

    private fun createTemporaryTarget(
        id: Long,
        nsId: String?
    ): TemporaryTarget = TemporaryTarget(
        timestamp = System.currentTimeMillis(),
        duration = 60_000L,
        reason = TemporaryTarget.Reason.CUSTOM,
        lowTarget = 80.0,
        highTarget = 120.0,
        interfaceIDs_backing = InterfaceIDs(nightscoutId = nsId)
    ).also { it.id = id }
}

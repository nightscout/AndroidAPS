package app.aaps.database.transactions

import app.aaps.database.DelegatedAppDatabase
import app.aaps.database.daos.ExtendedBolusDao
import app.aaps.database.entities.ExtendedBolus
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

class UpdateNsIdExtendedBolusTransactionTest {

    private lateinit var database: DelegatedAppDatabase
    private lateinit var extendedBolusDao: ExtendedBolusDao

    @BeforeEach
    fun setup() {
        extendedBolusDao = mock()
        database = mock()
        whenever(database.extendedBolusDao).thenReturn(extendedBolusDao)
    }

    @Test
    fun `updates NS ID when different`() = runTest {
        val newNsId = "new-ns-123"
        val current = createExtendedBolus(id = 1, nsId = "old-ns")
        val update = createExtendedBolus(id = 1, nsId = newNsId)

        whenever(extendedBolusDao.findById(1)).thenReturn(current)

        val transaction = UpdateNsIdExtendedBolusTransaction(listOf(update))
        transaction.database = database
        val result = transaction.run()

        assertThat(current.interfaceIDs.nightscoutId).isEqualTo(newNsId)
        assertThat(result.updatedNsId).hasSize(1)

        verify(extendedBolusDao).updateExistingEntry(current)
    }

    @Test
    fun `does not update when NS ID is same`() = runTest {
        val sameNsId = "same-ns"
        val current = createExtendedBolus(id = 1, nsId = sameNsId)
        val update = createExtendedBolus(id = 1, nsId = sameNsId)

        whenever(extendedBolusDao.findById(1)).thenReturn(current)

        val transaction = UpdateNsIdExtendedBolusTransaction(listOf(update))
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updatedNsId).isEmpty()

        verify(extendedBolusDao, never()).updateExistingEntry(any())
    }

    @Test
    fun `skips when extended bolus not found`() = runTest {
        val update = createExtendedBolus(id = 999, nsId = "new-ns")

        whenever(extendedBolusDao.findById(999)).thenReturn(null)

        val transaction = UpdateNsIdExtendedBolusTransaction(listOf(update))
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updatedNsId).isEmpty()

        verify(extendedBolusDao, never()).updateExistingEntry(any())
    }

    private fun createExtendedBolus(
        id: Long,
        nsId: String?
    ): ExtendedBolus = ExtendedBolus(
        timestamp = System.currentTimeMillis(),
        amount = 5.0,
        duration = 120_000L,
        interfaceIDs_backing = InterfaceIDs(nightscoutId = nsId)
    ).also { it.id = id }
}

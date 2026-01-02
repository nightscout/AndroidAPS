package app.aaps.database.transactions

import app.aaps.database.DelegatedAppDatabase
import app.aaps.database.daos.CarbsDao
import app.aaps.database.entities.Carbs
import app.aaps.database.entities.embedments.InterfaceIDs
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class UpdateNsIdCarbsTransactionTest {

    private lateinit var database: DelegatedAppDatabase
    private lateinit var carbsDao: CarbsDao

    @BeforeEach
    fun setup() {
        carbsDao = mock()
        database = mock()
        whenever(database.carbsDao).thenReturn(carbsDao)
    }

    @Test
    fun `updates NS ID when different`() {
        val newNsId = "new-ns-123"
        val current = createCarbs(id = 1, nsId = "old-ns")
        val update = createCarbs(id = 1, nsId = newNsId)

        whenever(carbsDao.findById(1)).thenReturn(current)

        val transaction = UpdateNsIdCarbsTransaction(listOf(update))
        transaction.database = database
        val result = transaction.run()

        assertThat(current.interfaceIDs.nightscoutId).isEqualTo(newNsId)
        assertThat(result.updatedNsId).hasSize(1)
        assertThat(result.updatedNsId[0]).isEqualTo(current)

        verify(carbsDao).updateExistingEntry(current)
    }

    @Test
    fun `does not update when NS ID is same`() {
        val sameNsId = "same-ns"
        val current = createCarbs(id = 1, nsId = sameNsId)
        val update = createCarbs(id = 1, nsId = sameNsId)

        whenever(carbsDao.findById(1)).thenReturn(current)

        val transaction = UpdateNsIdCarbsTransaction(listOf(update))
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updatedNsId).isEmpty()

        verify(carbsDao, never()).updateExistingEntry(any())
    }

    @Test
    fun `skips when carbs not found`() {
        val update = createCarbs(id = 999, nsId = "new-ns")

        whenever(carbsDao.findById(999)).thenReturn(null)

        val transaction = UpdateNsIdCarbsTransaction(listOf(update))
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updatedNsId).isEmpty()

        verify(carbsDao, never()).updateExistingEntry(any())
    }

    @Test
    fun `updates multiple carbs`() {
        val carbs1 = createCarbs(id = 1, nsId = "old-1")
        val carbs2 = createCarbs(id = 2, nsId = "old-2")
        val update1 = createCarbs(id = 1, nsId = "new-1")
        val update2 = createCarbs(id = 2, nsId = "new-2")

        whenever(carbsDao.findById(1)).thenReturn(carbs1)
        whenever(carbsDao.findById(2)).thenReturn(carbs2)

        val transaction = UpdateNsIdCarbsTransaction(listOf(update1, update2))
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updatedNsId).hasSize(2)
        assertThat(carbs1.interfaceIDs.nightscoutId).isEqualTo("new-1")
        assertThat(carbs2.interfaceIDs.nightscoutId).isEqualTo("new-2")

        verify(carbsDao).updateExistingEntry(carbs1)
        verify(carbsDao).updateExistingEntry(carbs2)
    }

    @Test
    fun `handles empty carbs list`() {
        val transaction = UpdateNsIdCarbsTransaction(emptyList())
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updatedNsId).isEmpty()

        verify(carbsDao, never()).updateExistingEntry(any())
    }

    @Test
    fun `preserves carbs amount when updating NS ID`() {
        val amount = 50.0
        val current = createCarbs(id = 1, nsId = "old", amount = amount)
        val update = createCarbs(id = 1, nsId = "new")

        whenever(carbsDao.findById(1)).thenReturn(current)

        val transaction = UpdateNsIdCarbsTransaction(listOf(update))
        transaction.database = database
        transaction.run()

        assertThat(current.amount).isEqualTo(amount)
    }

    @Test
    fun `updates from null NS ID to actual value`() {
        val current = createCarbs(id = 1, nsId = null)
        val update = createCarbs(id = 1, nsId = "new-ns")

        whenever(carbsDao.findById(1)).thenReturn(current)

        val transaction = UpdateNsIdCarbsTransaction(listOf(update))
        transaction.database = database
        val result = transaction.run()

        assertThat(current.interfaceIDs.nightscoutId).isEqualTo("new-ns")
        assertThat(result.updatedNsId).hasSize(1)

        verify(carbsDao).updateExistingEntry(current)
    }

    private fun createCarbs(
        id: Long,
        nsId: String?,
        amount: Double = 50.0
    ): Carbs = Carbs(
        timestamp = System.currentTimeMillis(),
        amount = amount,
        duration = 0L,
        interfaceIDs_backing = InterfaceIDs(nightscoutId = nsId)
    ).also { it.id = id }
}

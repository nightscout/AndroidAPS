package app.aaps.database.transactions

import app.aaps.database.DelegatedAppDatabase
import app.aaps.database.daos.FoodDao
import app.aaps.database.entities.Food
import app.aaps.database.entities.embedments.InterfaceIDs
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class SyncNsFoodTransactionTest {

    private lateinit var database: DelegatedAppDatabase
    private lateinit var foodDao: FoodDao

    @BeforeEach
    fun setup() {
        foodDao = mock()
        database = mock()
        whenever(database.foodDao).thenReturn(foodDao)
    }

    @Test
    fun `inserts new food when nsId not found`() {
        val food = createFood(id = 0, nsId = "ns-123", name = "Apple", carbs = 15)

        whenever(foodDao.findByNSId("ns-123")).thenReturn(null)

        val transaction = SyncNsFoodTransaction(listOf(food))
        transaction.database = database
        val result = transaction.run()

        assertThat(result.inserted).hasSize(1)
        assertThat(result.inserted[0]).isEqualTo(food)
        assertThat(result.updated).isEmpty()
        assertThat(result.invalidated).isEmpty()

        verify(foodDao).insertNewEntry(food)
    }

    @Test
    fun `updates food when content changes`() {
        val food = createFood(id = 0, nsId = "ns-123", name = "Apple", carbs = 20)
        val existing = createFood(id = 1, nsId = "ns-123", name = "Apple", carbs = 15)

        whenever(foodDao.findByNSId("ns-123")).thenReturn(existing)

        val transaction = SyncNsFoodTransaction(listOf(food))
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updated).hasSize(1)
        assertThat(result.updated[0]).isEqualTo(existing)
        assertThat(result.inserted).isEmpty()
        assertThat(result.invalidated).isEmpty()

        verify(foodDao).updateExistingEntry(existing)
        verify(foodDao, never()).insertNewEntry(any())
    }

    @Test
    fun `invalidates food when valid becomes invalid`() {
        val food = createFood(id = 0, nsId = "ns-123", name = "Apple", carbs = 15, isValid = false)
        val existing = createFood(id = 1, nsId = "ns-123", name = "Apple", carbs = 15, isValid = true)

        whenever(foodDao.findByNSId("ns-123")).thenReturn(existing)

        val transaction = SyncNsFoodTransaction(listOf(food))
        transaction.database = database
        val result = transaction.run()

        assertThat(result.invalidated).hasSize(1)
        assertThat(result.invalidated[0]).isEqualTo(existing)
        assertThat(result.updated).isEmpty()
        assertThat(result.inserted).isEmpty()

        verify(foodDao).updateExistingEntry(existing)
    }

    @Test
    fun `does not update when content is same`() {
        val food = createFood(id = 0, nsId = "ns-123", name = "Apple", carbs = 15)
        val existing = createFood(id = 1, nsId = "ns-123", name = "Apple", carbs = 15)

        whenever(foodDao.findByNSId("ns-123")).thenReturn(existing)

        val transaction = SyncNsFoodTransaction(listOf(food))
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updated).isEmpty()
        assertThat(result.invalidated).isEmpty()
        assertThat(result.inserted).isEmpty()

        verify(foodDao, never()).updateExistingEntry(any())
        verify(foodDao, never()).insertNewEntry(any())
    }

    @Test
    fun `handles multiple foods`() {
        val food1 = createFood(id = 0, nsId = "ns-1", name = "Apple", carbs = 15)
        val food2 = createFood(id = 0, nsId = "ns-2", name = "Banana", carbs = 25)

        whenever(foodDao.findByNSId("ns-1")).thenReturn(null)
        whenever(foodDao.findByNSId("ns-2")).thenReturn(null)

        val transaction = SyncNsFoodTransaction(listOf(food1, food2))
        transaction.database = database
        val result = transaction.run()

        assertThat(result.inserted).hasSize(2)
    }

    private fun createFood(
        id: Long,
        nsId: String,
        name: String,
        carbs: Int,
        isValid: Boolean = true
    ): Food = Food(
        name = name,
        category = "Test",
        subCategory = "Test",
        portion = 100.0,
        carbs = carbs,
        fat = 0,
        protein = 0,
        energy = 0,
        unit = "g",
        gi = null,
        isValid = isValid,
        interfaceIDs_backing = InterfaceIDs(nightscoutId = nsId),
    ).also { it.id = id }
}

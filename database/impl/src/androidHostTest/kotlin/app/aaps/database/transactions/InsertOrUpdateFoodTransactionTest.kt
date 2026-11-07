package app.aaps.database.transactions

import app.aaps.database.DelegatedAppDatabase
import app.aaps.database.daos.FoodDao
import app.aaps.database.entities.Food
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

class InsertOrUpdateFoodTransactionTest {

    private lateinit var database: DelegatedAppDatabase
    private lateinit var foodDao: FoodDao

    @BeforeEach
    fun setup() {
        foodDao = mock()
        database = mock()
        whenever(database.foodDao).thenReturn(foodDao)
    }

    @Test
    fun `inserts new food when id not found`() = runTest {
        val food = createFood(id = 1, name = "Apple", carbs = 15)

        whenever(foodDao.findById(1)).thenReturn(null)

        val transaction = InsertOrUpdateFoodTransaction(food)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.inserted).hasSize(1)
        assertThat(result.inserted[0]).isEqualTo(food)
        assertThat(result.updated).isEmpty()

        verify(foodDao).insertNewEntry(food)
        verify(foodDao, never()).updateExistingEntry(any())
    }

    @Test
    fun `updates existing food when id found`() = runTest {
        val food = createFood(id = 1, name = "Apple", carbs = 20)
        val existing = createFood(id = 1, name = "Apple", carbs = 15)

        whenever(foodDao.findById(1)).thenReturn(existing)

        val transaction = InsertOrUpdateFoodTransaction(food)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updated).hasSize(1)
        assertThat(result.updated[0]).isEqualTo(food)
        assertThat(result.inserted).isEmpty()

        verify(foodDao).updateExistingEntry(food)
        verify(foodDao, never()).insertNewEntry(any())
    }

    @Test
    fun `updates food carbs value`() = runTest {
        val existing = createFood(id = 1, name = "Banana", carbs = 25)
        val updated = createFood(id = 1, name = "Banana", carbs = 30)

        whenever(foodDao.findById(1)).thenReturn(existing)

        val transaction = InsertOrUpdateFoodTransaction(updated)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updated).hasSize(1)
        assertThat(result.updated[0].carbs).isEqualTo(30)
    }

    @Test
    fun `inserts invalid food`() = runTest {
        val food = createFood(id = 1, name = "Test", carbs = 10, isValid = false)

        whenever(foodDao.findById(1)).thenReturn(null)

        val transaction = InsertOrUpdateFoodTransaction(food)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.inserted).hasSize(1)
        assertThat(result.inserted[0].isValid).isFalse()
    }

    private fun createFood(
        id: Long,
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
        interfaceIDs_backing = InterfaceIDs()
    ).also { it.id = id }
}

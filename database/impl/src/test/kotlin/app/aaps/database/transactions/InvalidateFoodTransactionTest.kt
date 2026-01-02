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

class InvalidateFoodTransactionTest {

    private lateinit var database: DelegatedAppDatabase
    private lateinit var foodDao: FoodDao

    @BeforeEach
    fun setup() {
        foodDao = mock()
        database = mock()
        whenever(database.foodDao).thenReturn(foodDao)
    }

    @Test
    fun `invalidates valid food`() {
        val food = createFood(id = 1, isValid = true)

        whenever(foodDao.findById(1)).thenReturn(food)

        val transaction = InvalidateFoodTransaction(id = 1)
        transaction.database = database
        val result = transaction.run()

        assertThat(food.isValid).isFalse()
        assertThat(result.invalidated).hasSize(1)

        verify(foodDao).updateExistingEntry(food)
    }

    @Test
    fun `does not update already invalid food`() {
        val food = createFood(id = 1, isValid = false)

        whenever(foodDao.findById(1)).thenReturn(food)

        val transaction = InvalidateFoodTransaction(id = 1)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.invalidated).isEmpty()

        verify(foodDao, never()).updateExistingEntry(any())
    }

    @Test
    fun `throws exception when food not found`() {
        whenever(foodDao.findById(999)).thenReturn(null)

        val transaction = InvalidateFoodTransaction(id = 999)
        transaction.database = database

        try {
            transaction.run()
            throw AssertionError("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("There is no such Food")
        }
    }

    private fun createFood(
        id: Long,
        isValid: Boolean
    ): Food = Food(
        name = "Test Food",
        category = "Test",
        subCategory = "Test",
        portion = 100.0,
        carbs = 50,
        isValid = isValid,
        interfaceIDs_backing = InterfaceIDs()
    ).also { it.id = id }
}

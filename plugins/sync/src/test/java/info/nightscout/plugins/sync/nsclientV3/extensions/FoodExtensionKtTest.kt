package info.nightscout.plugins.sync.nsclientV3.extensions

import info.nightscout.database.entities.Food
import info.nightscout.database.entities.embedments.InterfaceIDs
import info.nightscout.sdk.localmodel.food.NSFood
import info.nightscout.sdk.mapper.convertToRemoteAndBack
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

@Suppress("SpellCheckingInspection")
internal class FoodExtensionKtTest {

    @Test
    fun toFood() {
        val food = Food(
            isValid = true,
            name = "name",
            category = "category",
            subCategory = "subcategory",
            portion = 2.0,
            carbs = 20,
            fat = 21,
            protein = 22,
            energy = 23,
            unit = "g",
            gi = 25,
            interfaceIDs_backing = InterfaceIDs(
                nightscoutId = "nightscoutId"
            )
        )

        val food2 = (food.toNSFood().convertToRemoteAndBack() as NSFood).toFood()
        Assertions.assertTrue(food.contentEqualsTo(food2))
        Assertions.assertTrue(food.interfaceIdsEqualsTo(food2))
    }
}
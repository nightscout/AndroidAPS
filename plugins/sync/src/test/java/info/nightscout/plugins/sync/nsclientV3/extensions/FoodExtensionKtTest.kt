package info.nightscout.plugins.sync.nsclientV3.extensions

import app.aaps.core.nssdk.localmodel.food.NSFood
import app.aaps.core.nssdk.mapper.convertToRemoteAndBack
import app.aaps.database.entities.Food
import app.aaps.database.entities.embedments.InterfaceIDs
import com.google.common.truth.Truth.assertThat
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
        assertThat(food.contentEqualsTo(food2)).isTrue()
        assertThat(food.interfaceIdsEqualsTo(food2)).isTrue()
    }
}

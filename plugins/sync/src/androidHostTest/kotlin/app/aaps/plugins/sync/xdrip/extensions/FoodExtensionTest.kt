package app.aaps.plugins.sync.xdrip.extensions

import app.aaps.core.data.model.FD
import app.aaps.core.data.model.IDs
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/** Covers the xdrip [FD] toJson: field mapping and the add-only nightscout id. */
class FoodExtensionTest : TestBase() {

    private fun food(ids: IDs = IDs()) = FD(
        name = "Apple", portion = 100.0, carbs = 10, category = "Fruit",
        fat = 1, protein = 2, energy = 200, gi = 30, ids = ids
    )

    @Test
    fun mapsAllFields() {
        val json = food().toJson(isAdd = true)
        assertThat(json.getString("type")).isEqualTo("food")
        assertThat(json.getString("name")).isEqualTo("Apple")
        assertThat(json.getInt("carbs")).isEqualTo(10)
        assertThat(json.getDouble("portion")).isEqualTo(100.0)
        assertThat(json.getString("category")).isEqualTo("Fruit")
        assertThat(json.getInt("fat")).isEqualTo(1)
        assertThat(json.getString("unit")).isEqualTo("g")
    }

    @Test
    fun includesNightscoutIdOnAdd() {
        assertThat(food(IDs(nightscoutId = "N")).toJson(isAdd = true).getString("_id")).isEqualTo("N")
    }

    @Test
    fun omitsNightscoutIdWhenNotAdd() {
        assertThat(food(IDs(nightscoutId = "N")).toJson(isAdd = false).has("_id")).isFalse()
    }
}

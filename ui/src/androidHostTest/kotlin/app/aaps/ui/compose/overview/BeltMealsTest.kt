package app.aaps.ui.compose.overview

import app.aaps.core.data.model.BS
import app.aaps.core.data.model.CA
import app.aaps.core.data.model.ICfg
import app.aaps.core.data.time.T
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class BeltMealsTest {

    private val iCfg = ICfg(insulinLabel = "Test", peak = 75, dia = 5.0, concentration = 1.0)
    private val start = T.hours(10).msecs()

    private fun bolus(minute: Long, amount: Double, type: BS.Type = BS.Type.NORMAL) =
        BS(timestamp = start + T.mins(minute).msecs(), amount = amount, type = type, iCfg = iCfg)

    private fun carbs(minute: Long, amount: Double, durationMinutes: Long = 0) =
        CA(timestamp = start + T.mins(minute).msecs(), amount = amount, duration = T.mins(durationMinutes).msecs())

    @Test
    fun `a bolus and the carbs entered with it are one meal, up to 15 minutes apart either way`() {
        val meals = beltMeals(listOf(bolus(0, 4.5)), listOf(carbs(15, 30.0), carbs(-10, 20.0)))

        assertThat(meals).containsExactly(BeltMeal(start, 4.5, 50.0, 0L))
    }

    @Test
    fun `carbs further away and eCarbs are their own entries`() {
        val meals = beltMeals(listOf(bolus(0, 4.5)), listOf(carbs(16, 30.0), carbs(5, 20.0, durationMinutes = 180)))

        assertThat(meals).containsExactly(
            BeltMeal(start, 4.5, null, 0L),
            BeltMeal(start + T.mins(5).msecs(), null, 20.0, T.mins(180).msecs()),
            BeltMeal(start + T.mins(16).msecs(), null, 30.0, 0L)
        ).inOrder()
    }

    @Test
    fun `carbs join the nearest bolus`() {
        val meals = beltMeals(listOf(bolus(0, 1.0), bolus(20, 2.0)), listOf(carbs(12, 40.0)))

        assertThat(meals).containsExactly(BeltMeal(start, 1.0, null, 0L), BeltMeal(start + T.mins(20).msecs(), 2.0, 40.0, 0L)).inOrder()
    }

    @Test
    fun `SMBs, deleted entries and zero amounts are not shown`() {
        val deleted = bolus(30, 3.0).apply { isValid = false }
        val meals = beltMeals(listOf(bolus(0, 0.3, BS.Type.SMB), deleted), listOf(carbs(60, 0.0)))

        assertThat(meals).isEmpty()
    }
}

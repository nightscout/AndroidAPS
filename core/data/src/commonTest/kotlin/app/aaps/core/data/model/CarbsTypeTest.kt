package app.aaps.core.data.model

import kotlin.test.Test
import kotlin.test.assertEquals

class CarbsTypeTest {

    @Test
    fun `bread keeps all carbs now and has no eCarbs`() {
        assertEquals(65, CarbsType.BREAD.carbsNow(65))
        assertEquals(0, CarbsType.BREAD.eCarbs(65))
    }

    @Test
    fun `cake and pizza split the carbs with the fixed percentages`() {
        // 90 % now, 20 % as eCarbs. The two parts add up to more than 100 %, on purpose.
        assertEquals(54, CarbsType.CAKE.carbsNow(60))
        assertEquals(12, CarbsType.CAKE.eCarbs(60))
        // 80 % now, 35 % as eCarbs, whole grams rounded down.
        assertEquals(52, CarbsType.PIZZA.carbsNow(65))
        assertEquals(22, CarbsType.PIZZA.eCarbs(65))
    }

    @Test
    fun `zero carbs give zero parts`() {
        assertEquals(0, CarbsType.PIZZA.carbsNow(0))
        assertEquals(0, CarbsType.PIZZA.eCarbs(0))
    }
}

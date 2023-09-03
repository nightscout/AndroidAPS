package info.nightscout.core.data

import info.nightscout.interfaces.iob.MealData
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class MealDataTest {

    @Test fun canCreateObject() {
        val md = MealData()
        Assertions.assertEquals(0.0, md.carbs, 0.01)
    }
}
package info.nightscout.androidaps.data

import info.nightscout.interfaces.iob.MealData
import org.junit.Assert
import org.junit.jupiter.api.Test

class MealDataTest {

    @Test fun canCreateObject() {
        val md = MealData()
        Assert.assertEquals(0.0, md.carbs, 0.01)
    }
}
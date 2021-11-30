package info.nightscout.androidaps.data

import org.junit.Assert
import org.junit.Test

class MealDataTest {

    @Test fun canCreateObject() {
        val md = MealData()
        Assert.assertEquals(0.0, md.carbs, 0.01)
    }
}
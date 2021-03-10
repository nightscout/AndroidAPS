package info.nightscout.androidaps.data

import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
class MealDataTest {

    @Test fun canCreateObject() {
        val md = MealData()
        Assert.assertEquals(0.0, md.boluses, 0.01)
    }
}
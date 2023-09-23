package info.nightscout.core.data

import com.google.common.truth.Truth.assertThat
import info.nightscout.interfaces.iob.MealData
import org.junit.jupiter.api.Test

class MealDataTest {

    @Test fun canCreateObject() {
        val md = MealData()
        assertThat(md.carbs).isWithin(0.01).of(0.0)
    }
}

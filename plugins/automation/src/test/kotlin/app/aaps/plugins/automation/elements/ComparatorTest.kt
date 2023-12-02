package app.aaps.plugins.automation.elements

import app.aaps.plugins.automation.triggers.TriggerTestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class ComparatorTest : TriggerTestBase() {

    @Test
    fun checkTest() {
        assertThat(Comparator.Compare.IS_EQUAL.check(1, 1)).isTrue()
        assertThat(Comparator.Compare.IS_LESSER.check(1, 2)).isTrue()
        assertThat(Comparator.Compare.IS_EQUAL_OR_LESSER.check(1, 2)).isTrue()
        assertThat(Comparator.Compare.IS_EQUAL_OR_LESSER.check(2, 2)).isTrue()
        assertThat(Comparator.Compare.IS_GREATER.check(2, 1)).isTrue()
        assertThat(Comparator.Compare.IS_EQUAL_OR_GREATER.check(2, 1)).isTrue()
        assertThat(Comparator.Compare.IS_EQUAL_OR_GREATER.check(2, 2)).isTrue()
        assertThat(Comparator.Compare.IS_LESSER.check(2, 1)).isFalse()
        assertThat(Comparator.Compare.IS_EQUAL_OR_LESSER.check(2, 1)).isFalse()
        assertThat(Comparator.Compare.IS_GREATER.check(1, 2)).isFalse()
        assertThat(Comparator.Compare.IS_EQUAL_OR_GREATER.check(1, 2)).isFalse()
//        assertThat(Comparator.Compare.IS_NOT_AVAILABLE.check<Int?>(1, null)).isTrue()
    }

    @Test
    fun labelsTest() {
        assertThat(Comparator.Compare.labels(rh)).hasSize(6)
    }

    @Test
    fun setValueTest() {
        val c: Comparator = Comparator(rh).setValue(Comparator.Compare.IS_EQUAL_OR_GREATER)
        assertThat(c.value).isEqualTo(Comparator.Compare.IS_EQUAL_OR_GREATER)
    }
}

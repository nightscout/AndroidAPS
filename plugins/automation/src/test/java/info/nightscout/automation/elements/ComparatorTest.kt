package info.nightscout.automation.elements

import info.nightscout.automation.triggers.TriggerTestBase
import org.junit.Assert
import org.junit.jupiter.api.Test

class ComparatorTest : TriggerTestBase() {

    @Test
    fun checkTest() {
        Assert.assertTrue(Comparator.Compare.IS_EQUAL.check(1, 1))
        Assert.assertTrue(Comparator.Compare.IS_LESSER.check(1, 2))
        Assert.assertTrue(Comparator.Compare.IS_EQUAL_OR_LESSER.check(1, 2))
        Assert.assertTrue(Comparator.Compare.IS_EQUAL_OR_LESSER.check(2, 2))
        Assert.assertTrue(Comparator.Compare.IS_GREATER.check(2, 1))
        Assert.assertTrue(Comparator.Compare.IS_EQUAL_OR_GREATER.check(2, 1))
        Assert.assertTrue(Comparator.Compare.IS_EQUAL_OR_GREATER.check(2, 2))
        Assert.assertFalse(Comparator.Compare.IS_LESSER.check(2, 1))
        Assert.assertFalse(Comparator.Compare.IS_EQUAL_OR_LESSER.check(2, 1))
        Assert.assertFalse(Comparator.Compare.IS_GREATER.check(1, 2))
        Assert.assertFalse(Comparator.Compare.IS_EQUAL_OR_GREATER.check(1, 2))
//        Assert.assertTrue(Comparator.Compare.IS_NOT_AVAILABLE.check<Int?>(1, null))
    }

    @Test
    fun labelsTest() {
        Assert.assertEquals(6, Comparator.Compare.labels(rh).size)
    }

    @Test
    fun setValueTest() {
        val c: Comparator = Comparator(rh).setValue(Comparator.Compare.IS_EQUAL_OR_GREATER)
        Assert.assertEquals(Comparator.Compare.IS_EQUAL_OR_GREATER, c.value)
    }
}
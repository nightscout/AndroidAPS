package info.nightscout.androidaps.plugins.general.automation.elements

import info.nightscout.androidaps.plugins.general.automation.triggers.TriggerTestBase
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
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
        Assert.assertEquals(6, Comparator.Compare.labels(resourceHelper).size)
    }

    @Test
    fun setValueTest() {
        val c: Comparator = Comparator(injector).setValue(Comparator.Compare.IS_EQUAL_OR_GREATER)
        Assert.assertEquals(Comparator.Compare.IS_EQUAL_OR_GREATER, c.value)
    }
}
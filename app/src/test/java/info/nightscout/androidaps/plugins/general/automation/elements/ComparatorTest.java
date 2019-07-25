package info.nightscout.androidaps.plugins.general.automation.elements;

import junit.framework.Assert;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import info.AAPSMocker;
import info.nightscout.androidaps.MainApp;

@RunWith(PowerMockRunner.class)
@PrepareForTest({MainApp.class})
public class ComparatorTest {

    @Test
    public void checkTest() {
        Assert.assertTrue(Comparator.Compare.IS_EQUAL.check(1, 1));
        Assert.assertTrue(Comparator.Compare.IS_LESSER.check(1, 2));
        Assert.assertTrue(Comparator.Compare.IS_EQUAL_OR_LESSER.check(1, 2));
        Assert.assertTrue(Comparator.Compare.IS_EQUAL_OR_LESSER.check(2, 2));
        Assert.assertTrue(Comparator.Compare.IS_GREATER.check(2, 1));
        Assert.assertTrue(Comparator.Compare.IS_EQUAL_OR_GREATER.check(2, 1));
        Assert.assertTrue(Comparator.Compare.IS_EQUAL_OR_GREATER.check(2, 2));

        Assert.assertFalse(Comparator.Compare.IS_LESSER.check(2, 1));
        Assert.assertFalse(Comparator.Compare.IS_EQUAL_OR_LESSER.check(2, 1));
        Assert.assertFalse(Comparator.Compare.IS_GREATER.check(1, 2));
        Assert.assertFalse(Comparator.Compare.IS_EQUAL_OR_GREATER.check(1, 2));

        Assert.assertTrue(Comparator.Compare.IS_NOT_AVAILABLE.check(1, null));
    }

    @Test
    public void labelsTest() {
        Assert.assertEquals(6, Comparator.Compare.labels().size());
    }

    @Test
    public void getSetValueTest() {
        Comparator c = new Comparator().setValue(Comparator.Compare.IS_EQUAL_OR_GREATER);
        Assert.assertEquals(Comparator.Compare.IS_EQUAL_OR_GREATER, c.getValue());
    }

    @Before
    public void prepare() {
        AAPSMocker.mockMainApp();
        AAPSMocker.mockStrings();
    }
}
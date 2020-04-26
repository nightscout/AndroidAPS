package info.nightscout.androidaps.interfaces;

import junit.framework.Assert;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import info.AAPSMocker;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.utils.SP;

/**
 * Created by mike on 19.03.2018.
 */

@RunWith(PowerMockRunner.class)
@PrepareForTest({MainApp.class, SP.class, L.class})
public class ConstraintTest {

    @Test
    public void doTests() {
        Constraint<Boolean> b = new Constraint<>(true);
        Assert.assertEquals(Boolean.TRUE, b.value());
        Assert.assertEquals("", b.getReasons());
        Assert.assertEquals("", b.getMostLimitedReasons());
        b.set(false);
        Assert.assertEquals(Boolean.FALSE, b.value());
        Assert.assertEquals("", b.getReasons());
        Assert.assertEquals("", b.getMostLimitedReasons());
        b.set(true, "Set true", this);
        Assert.assertEquals(Boolean.TRUE, b.value());
        Assert.assertEquals("ConstraintTest: Set true", b.getReasons());
        Assert.assertEquals("ConstraintTest: Set true", b.getMostLimitedReasons());
        b.set(false, "Set false", this);
        Assert.assertEquals(Boolean.FALSE, b.value());
        Assert.assertEquals("ConstraintTest: Set true\nConstraintTest: Set false", b.getReasons());
        Assert.assertEquals("ConstraintTest: Set true\nConstraintTest: Set false", b.getMostLimitedReasons());

        Constraint<Double> d = new Constraint<>(10d);
        d.set(5d, "Set 5d", this);
        Assert.assertEquals(5d, d.value());
        Assert.assertEquals("ConstraintTest: Set 5d", d.getReasons());
        Assert.assertEquals("ConstraintTest: Set 5d", d.getMostLimitedReasons());
        d.setIfSmaller(6d, "Set 6d", this);
        Assert.assertEquals(5d, d.value());
        Assert.assertEquals("ConstraintTest: Set 5d\nConstraintTest: Set 6d", d.getReasons());
        Assert.assertEquals("ConstraintTest: Set 5d", d.getMostLimitedReasons());
        d.setIfSmaller(4d, "Set 4d", this);
        Assert.assertEquals(4d, d.value());
        Assert.assertEquals("ConstraintTest: Set 5d\nConstraintTest: Set 6d\nConstraintTest: Set 4d", d.getReasons());
        Assert.assertEquals("ConstraintTest: Set 4d", d.getMostLimitedReasons());
        Assert.assertEquals(10d, d.originalValue());
        d.setIfDifferent(7d, "Set 7d", this);
        Assert.assertEquals(7d, d.value());
        Assert.assertEquals("ConstraintTest: Set 5d\nConstraintTest: Set 6d\nConstraintTest: Set 4d\nConstraintTest: Set 7d", d.getReasons());
        Assert.assertEquals("ConstraintTest: Set 4d\nConstraintTest: Set 7d", d.getMostLimitedReasons());
        Assert.assertEquals(10d, d.originalValue());
    }

    @Before
    public void prepareMock() {
        AAPSMocker.mockMainApp();
        AAPSMocker.mockApplicationContext();
        AAPSMocker.mockSP();
        AAPSMocker.mockL();
    }
}

package info.nightscout.androidaps.interfaces;

import junit.framework.Assert;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.modules.junit4.PowerMockRunner;

/**
 * Created by mike on 19.03.2018.
 */

@RunWith(PowerMockRunner.class)
public class ConstraintTest {

    @Test
    public void doTests() throws Exception {
        Constraint<Boolean> b = new Constraint<>(true);
        Assert.assertEquals(Boolean.TRUE, b.value());
        Assert.assertEquals("", b.getReasons());
        b.set(false);
        Assert.assertEquals(Boolean.FALSE, b.value());
        Assert.assertEquals("", b.getReasons());
        b.set(true, "Set true");
        Assert.assertEquals(Boolean.TRUE, b.value());
        Assert.assertEquals("Set true", b.getReasons());
        b.set(false, "Set false");
        Assert.assertEquals(Boolean.FALSE, b.value());
        Assert.assertEquals("Set true\nSet false", b.getReasons());

        Constraint<Double> d = new Constraint<>(10d);
        d.set(5d, "Set 5d");
        Assert.assertEquals(5d, d.value());
        Assert.assertEquals("Set 5d", d.getReasons());
        d.setIfSmaller(6d, "Set 6d");
        Assert.assertEquals(5d, d.value());
        Assert.assertEquals("Set 5d\nSet 6d", d.getReasons());
        d.setIfSmaller(4d, "Set 4d");
        Assert.assertEquals(4d, d.value());
        Assert.assertEquals("Set 5d\nSet 6d\nSet 4d", d.getReasons());
    }
}

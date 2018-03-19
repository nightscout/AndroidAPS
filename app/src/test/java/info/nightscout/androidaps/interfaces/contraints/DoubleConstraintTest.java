package info.nightscout.androidaps.interfaces.contraints;

import junit.framework.Assert;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.modules.junit4.PowerMockRunner;

import info.nightscout.androidaps.interfaces.constrains.DoubleConstraint;

/**
 * Created by mike on 19.03.2018.
 */

@RunWith(PowerMockRunner.class)
public class DoubleConstraintTest {

    @Test
    public void doTests() throws Exception {
        DoubleConstraint c;

        c = new DoubleConstraint(10d);
        Assert.assertEquals(10d, c.getDouble());
        Assert.assertEquals("", c.getReasons());
        c.set(11d);
        Assert.assertEquals(11d, c.getDouble());
        Assert.assertEquals("", c.getReasons());
        c.set(9d, "Set 9d");
        Assert.assertEquals(9d, c.getDouble());
        Assert.assertEquals("Set 9d", c.getReasons());
        c.set(8d, "Set 8d");
        Assert.assertEquals(8d, c.getDouble());
        Assert.assertEquals("Set 9d\nSet 8d", c.getReasons());

    }
}

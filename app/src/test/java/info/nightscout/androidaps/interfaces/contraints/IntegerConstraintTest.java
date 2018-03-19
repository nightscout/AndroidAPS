package info.nightscout.androidaps.interfaces.contraints;

import junit.framework.Assert;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.modules.junit4.PowerMockRunner;

import info.nightscout.androidaps.interfaces.constrains.BooleanConstraint;
import info.nightscout.androidaps.interfaces.constrains.IntegerConstraint;

/**
 * Created by mike on 19.03.2018.
 */

@RunWith(PowerMockRunner.class)
public class IntegerConstraintTest {

    @Test
    public void doTests() throws Exception {
        IntegerConstraint c;

        c = new IntegerConstraint(10);
        Assert.assertEquals(10, c.getInteger());
        Assert.assertEquals("", c.getReasons());
        c.set(11);
        Assert.assertEquals(11, c.getInteger());
        Assert.assertEquals("", c.getReasons());
        c.set(9, "Set 9");
        Assert.assertEquals(9, c.getInteger());
        Assert.assertEquals("Set 9", c.getReasons());

    }
}

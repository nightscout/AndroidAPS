package info.nightscout.androidaps.interfaces.contraints;

import junit.framework.Assert;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.modules.junit4.PowerMockRunner;

import info.nightscout.androidaps.interfaces.constrains.BooleanConstraint;

/**
 * Created by mike on 19.03.2018.
 */

@RunWith(PowerMockRunner.class)
public class BooleanConstraintTest {

    @Test
    public void doTests() throws Exception {
        BooleanConstraint c;

        c = new BooleanConstraint(true);
        Assert.assertEquals(true, c.get());
        Assert.assertEquals("", c.getReasons());
        c.set(false);
        Assert.assertEquals(false, c.get());
        Assert.assertEquals("", c.getReasons());
        c.set(true, "Set true");
        Assert.assertEquals(true, c.get());
        Assert.assertEquals("Set true", c.getReasons());
    }
}

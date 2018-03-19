package info.nightscout.androidaps.interfaces.contraints;

import junit.framework.Assert;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.modules.junit4.PowerMockRunner;

import info.nightscout.androidaps.interfaces.constrains.Constraint;

/**
 * Created by mike on 19.03.2018.
 */

@RunWith(PowerMockRunner.class)
public class ConstraintTest {

    @Test
    public void doTests() throws Exception {
        Constraint<Boolean> c;

        c = new Constraint<Boolean>(true);
        Assert.assertEquals(Boolean.TRUE, c.get());
        Assert.assertEquals("", c.getReasons());
        c.set(false);
        Assert.assertEquals(Boolean.FALSE, c.get());
        Assert.assertEquals("", c.getReasons());
        c.set(true, "Set true");
        Assert.assertEquals(Boolean.TRUE, c.get());
        Assert.assertEquals("Set true", c.getReasons());
        c.set(false, "Set false");
        Assert.assertEquals(Boolean.FALSE, c.get());
        Assert.assertEquals("Set true\nSet false", c.getReasons());
    }
}

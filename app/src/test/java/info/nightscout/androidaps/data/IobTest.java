package info.nightscout.androidaps.data;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.modules.junit4.PowerMockRunner;

/**
 * Created by mike on 27.03.2018.
 */

@RunWith(PowerMockRunner.class)
public class IobTest {

    @Test
    public void plusTest() {
        Iob a = new Iob().iobContrib(1).activityContrib(2);
        Iob b = new Iob().iobContrib(3).activityContrib(4);
        a.plus(b);
        Assert.assertEquals(4d, a.iobContrib, 0.01d);
        Assert.assertEquals(6d, a.activityContrib, 0.01d);
    }

    @Test
    public void equalTest() {
        Iob a1 = new Iob().iobContrib(1).activityContrib(2);
        Iob a2 = new Iob().iobContrib(1).activityContrib(2);
        Iob b = new Iob().iobContrib(3).activityContrib(4);

        Assert.assertEquals(true, a1.equals(a1));
        Assert.assertEquals(true, a1.equals(a2));
        Assert.assertEquals(false, a1.equals(b));
        Assert.assertEquals(false, a1.equals(null));
        Assert.assertEquals(false, a1.equals(new Object()));
    }
    @Test
    public void hashCodeTest() {
        Iob a = new Iob().iobContrib(1).activityContrib(2);
        Assert.assertNotEquals(0, a.hashCode());
    }
}

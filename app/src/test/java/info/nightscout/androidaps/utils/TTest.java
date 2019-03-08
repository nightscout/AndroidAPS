package info.nightscout.androidaps.utils;

import org.junit.Assert;
import org.junit.Test;

/**
 * Created by mike on 26.03.2018.
 */

//@RunWith(PowerMockRunner.class)
public class TTest {

    @Test
    public void toUnits() {
        Assert.assertEquals(1, T.msecs(1000).secs());
        Assert.assertEquals(1, T.secs(60).mins());
        Assert.assertEquals(1, T.mins(60).hours());
        Assert.assertEquals(1, T.hours(24).days());
        Assert.assertEquals(24, T.days(1).hours());
        Assert.assertEquals(60000, T.mins(1).msecs());
    }

    @Test
    public void now() {
        Assert.assertTrue(Math.abs(T.now().msecs() - System.currentTimeMillis()) < 5000);
    }

    @Test
    public void additions() {
        long nowMsecs = System.currentTimeMillis();
        T now = T.msecs(nowMsecs);

        Assert.assertEquals(now.plus(T.secs(5)).msecs(), nowMsecs + 5 * 1000);
        Assert.assertEquals(now.plus(T.mins(5)).msecs(), nowMsecs + 5 * 60 * 1000);
        Assert.assertEquals(now.plus(T.hours(5)).msecs(), nowMsecs + 5 * 60 * 60 * 1000);
        Assert.assertEquals(now.plus(T.days(5)).msecs(), nowMsecs + 5 * 24 * 60 * 60 * 1000);
    }

    @Test
    public void subtractions() {
        long nowMsecs = System.currentTimeMillis();
        T now = T.msecs(nowMsecs);

        Assert.assertEquals(now.minus(T.secs(5)).msecs(), nowMsecs - 5 * 1000);
        Assert.assertEquals(now.minus(T.mins(5)).msecs(), nowMsecs - 5 * 60 * 1000);
        Assert.assertEquals(now.minus(T.hours(5)).msecs(), nowMsecs - 5 * 60 * 60 * 1000);
        Assert.assertEquals(now.minus(T.days(5)).msecs(), nowMsecs - 5 * 24 * 60 * 60 * 1000);
    }
}

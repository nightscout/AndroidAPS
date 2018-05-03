package info.nightscout.utils;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.modules.junit4.PowerMockRunner;

/**
 * Created by mike on 26.03.2018.
 */

@RunWith(PowerMockRunner.class)
public class TTest {

    @Test
    public void doTests() {
        Assert.assertEquals(1, T.msecs(1000).secs());
        Assert.assertEquals(1, T.secs(60).mins());
        Assert.assertEquals(1, T.mins(60).hours());
        Assert.assertEquals(1, T.hours(24).days());
        Assert.assertEquals(24, T.days(1).hours());
        Assert.assertEquals(60000, T.mins(1).msecs());
    }
}

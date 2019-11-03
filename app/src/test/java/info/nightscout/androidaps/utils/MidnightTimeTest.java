package info.nightscout.androidaps.utils;


import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.Calendar;
import java.util.Date;

import info.AAPSMocker;
import info.nightscout.androidaps.MainApp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Created by mike on 20.11.2017.
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest({Calendar.class})
public class MidnightTimeTest {

    @Test
    public void calc() {
        // We get real midnight
        long now = DateUtil.now();
        Assert.assertTrue(now >= MidnightTime.calc());
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(MidnightTime.calc());
        Assert.assertEquals(c.get(Calendar.HOUR_OF_DAY), 0);
        Assert.assertEquals(c.get(Calendar.MINUTE), 0);
        Assert.assertEquals(c.get(Calendar.SECOND), 0);
        Assert.assertEquals(c.get(Calendar.MILLISECOND), 0);
    }

    @Test
    public void calc_time() {
        // We get real midnight
        long now = DateUtil.now();
        long midnight = MidnightTime.calc(now);
        Assert.assertTrue(now >= midnight);
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(MidnightTime.calc(now));
        Assert.assertEquals(c.get(Calendar.HOUR_OF_DAY), 0);
        Assert.assertEquals(c.get(Calendar.MINUTE), 0);
        Assert.assertEquals(c.get(Calendar.SECOND), 0);
        Assert.assertEquals(c.get(Calendar.MILLISECOND), 0);
        // Assure we get the same time from cache
        Assert.assertEquals(midnight, MidnightTime.calc(now));
    }

    @Test
    public void resetCache() {
        long now = DateUtil.now();
        MidnightTime.calc(now);
        MidnightTime.resetCache();
        Assert.assertEquals(0, MidnightTime.times.size());
    }
    @Test
    public void log() {
        long now = DateUtil.now();
        MidnightTime.calc(now);
        Assert.assertTrue(MidnightTime.log().startsWith("Hits:"));
    }
}

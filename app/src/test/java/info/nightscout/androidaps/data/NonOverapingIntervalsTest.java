package info.nightscout.androidaps.data;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.modules.junit4.PowerMockRunner;

import info.nightscout.androidaps.db.TemporaryBasal;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.T;

/**
 * Created by mike on 26.03.2018.
 */

@RunWith(PowerMockRunner.class)
public class NonOverapingIntervalsTest {

    private final long startDate = DateUtil.now();
    NonOverlappingIntervals<TemporaryBasal> list = new NonOverlappingIntervals<>();

    @Test
    public void doTests() {
        // create one 10h interval and test value in and out
        list.add(new TemporaryBasal().date(startDate).duration((int) T.hours(10).mins()).absolute(1));
        Assert.assertEquals(null, list.getValueByInterval(startDate - T.secs(1).msecs()));
        Assert.assertEquals(1d, list.getValueByInterval(startDate).absoluteRate, 0.01d);
        Assert.assertEquals(null, list.getValueByInterval(startDate + T.hours(10).msecs() + 1));

        // stop temp  after 5h
        list.add(new TemporaryBasal().date(startDate + T.hours(5).msecs()).duration(0));
        Assert.assertEquals(null, list.getValueByInterval(startDate - T.secs(1).msecs()));
        Assert.assertEquals(1d, list.getValueByInterval(startDate).absoluteRate, 0.01d);
        Assert.assertEquals(1d, list.getValueByInterval(startDate + T.hours(5).msecs() - 1).absoluteRate, 0.01d);
        Assert.assertEquals(null, list.getValueByInterval(startDate + T.hours(5).msecs() + 1));
        Assert.assertEquals(null, list.getValueByInterval(startDate + T.hours(10).msecs() + 1));

        // insert 1h interval inside
        list.add(new TemporaryBasal().date(startDate + T.hours(3).msecs()).duration((int) T.hours(1).mins()).absolute(2));
        Assert.assertEquals(null, list.getValueByInterval(startDate - T.secs(1).msecs()));
        Assert.assertEquals(1d, list.getValueByInterval(startDate).absoluteRate, 0.01d);
        Assert.assertEquals(null, list.getValueByInterval(startDate + T.hours(5).msecs() - 1));
        Assert.assertEquals(2d, list.getValueByInterval(startDate + T.hours(3).msecs()).absoluteRate, 0.01d);
        Assert.assertEquals(2d, list.getValueByInterval(startDate + T.hours(4).msecs() - 1).absoluteRate, 0.01d);
        Assert.assertEquals(null, list.getValueByInterval(startDate + T.hours(4).msecs() + 1));
        Assert.assertEquals(null, list.getValueByInterval(startDate + T.hours(10).msecs() + 1));
    }

    @Test
    public void testCopyConstructor() {
        list.reset();
        list.add(new TemporaryBasal().date(startDate).duration((int) T.hours(10).mins()).absolute(1));
        NonOverlappingIntervals<TemporaryBasal> list2 = new NonOverlappingIntervals<>(list);
        Assert.assertEquals(1, list2.getList().size());
    }


}

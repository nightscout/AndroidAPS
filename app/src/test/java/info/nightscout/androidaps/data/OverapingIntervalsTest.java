package info.nightscout.androidaps.data;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.ArrayList;
import java.util.List;

import info.nightscout.androidaps.db.TempTarget;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.T;

/**
 * Created by mike on 26.03.2018.
 */

@RunWith(PowerMockRunner.class)
public class OverapingIntervalsTest {

    private final long startDate = DateUtil.now();
    OverlappingIntervals<TempTarget> list = new OverlappingIntervals<>();

    @Test
    public void doTests() {
        // create one 10h interval and test value in and out
        list.add(new TempTarget().date(startDate).duration((int) T.hours(10).mins()).low(100).high(100));
        Assert.assertEquals(null, list.getValueByInterval(startDate - T.secs(1).msecs()));
        Assert.assertEquals(100d, list.getValueByInterval(startDate).target(), 0.01d);
        Assert.assertEquals(null, list.getValueByInterval(startDate + T.hours(10).msecs() + 1));

        // stop temp target after 5h
        list.add(new TempTarget().date(startDate + T.hours(5).msecs()).duration(0));
        Assert.assertEquals(null, list.getValueByInterval(startDate - T.secs(1).msecs()));
        Assert.assertEquals(100d, list.getValueByInterval(startDate).target(), 0.01d);
        Assert.assertEquals(100d, list.getValueByInterval(startDate + T.hours(5).msecs() - 1).target(), 0.01d);
        Assert.assertEquals(null, list.getValueByInterval(startDate + T.hours(5).msecs() + 1));
        Assert.assertEquals(null, list.getValueByInterval(startDate + T.hours(10).msecs() + 1));

        // insert 1h interval inside
        list.add(new TempTarget().date(startDate + T.hours(3).msecs()).duration((int) T.hours(1).mins()).low(200).high(200));
        Assert.assertEquals(null, list.getValueByInterval(startDate - T.secs(1).msecs()));
        Assert.assertEquals(100d, list.getValueByInterval(startDate).target(), 0.01d);
        Assert.assertEquals(100d, list.getValueByInterval(startDate + T.hours(5).msecs() - 1).target(), 0.01d);
        Assert.assertEquals(null, list.getValueByInterval(startDate + T.hours(5).msecs() + 1));
        Assert.assertEquals(200d, list.getValueByInterval(startDate + T.hours(3).msecs()).target(), 0.01d);
        Assert.assertEquals(100d, list.getValueByInterval(startDate + T.hours(4).msecs() + 1).target(), 0.01d);
        Assert.assertEquals(null, list.getValueByInterval(startDate + T.hours(10).msecs() + 1));
    }

    @Test
    public void testCopyConstructor() {
        list.reset();
        list.add(new TempTarget().date(startDate).duration((int) T.hours(10).mins()).low(100).high(100));
        OverlappingIntervals<TempTarget> list2 = new OverlappingIntervals<>(list);
        Assert.assertEquals(1, list2.getList().size());
    }

    @Test
    public void testReversingArrays() {
        List<TempTarget> someList = new ArrayList<>();
        someList.add(new TempTarget().date(startDate).duration((int) T.hours(3).mins()).low(200).high(200));
        someList.add(new TempTarget().date(startDate + T.hours(1).msecs()).duration((int) T.hours(1).mins()).low(100).high(100));
        list.reset();
        list.add(someList);
        Assert.assertEquals(startDate, list.get(0).date);
        Assert.assertEquals(startDate + T.hours(1).msecs(), list.getReversed(0).date);
        Assert.assertEquals(startDate + T.hours(1).msecs(), list.getReversedList().get(0).date);

    }
}

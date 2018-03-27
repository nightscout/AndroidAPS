package info.nightscout.androidaps.data;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.modules.junit4.PowerMockRunner;

import info.nightscout.utils.DateUtil;

/**
 * Created by mike on 27.03.2018.
 */

@RunWith(PowerMockRunner.class)
public class IobTotalTest {

    long now = DateUtil.now();

    @Test
    public void copytest() {
        IobTotal a = new IobTotal(now);
        a.iob = 10;
        IobTotal b = a.copy();
        Assert.assertEquals(a.iob, b.iob, 0.01d);
    }

    @Test
    public void plusTest() {
        IobTotal a = new IobTotal(now);
        a.iob = 10;
        a.activity = 10;
        a.bolussnooze = 10;
        a.basaliob = 10;
        a.netbasalinsulin = 10;
        a.hightempinsulin = 10;
        a.netInsulin = 10;
        a.extendedBolusInsulin = 10;
        a.plus(a.copy());
        Assert.assertEquals(20, a.iob, 0.01d);
        Assert.assertEquals(20, a.activity, 0.01d);
        Assert.assertEquals(20, a.bolussnooze, 0.01d);
        Assert.assertEquals(20, a.basaliob, 0.01d);
        Assert.assertEquals(20, a.netbasalinsulin, 0.01d);
        Assert.assertEquals(20, a.hightempinsulin, 0.01d);
        Assert.assertEquals(20, a.netInsulin, 0.01d);
        Assert.assertEquals(20, a.extendedBolusInsulin, 0.01d);
    }

    @Test
    public void combineTest() {
        IobTotal a = new IobTotal(now);
        a.iob = 10;
        a.activity = 11;
        a.bolussnooze = 12;
        a.basaliob = 13;
        a.netbasalinsulin = 14;
        a.hightempinsulin = 15;
        a.netInsulin = 16;
        a.extendedBolusInsulin = 17;
        IobTotal b = a.copy();
        IobTotal c = IobTotal.combine(a, b);
        Assert.assertEquals(a.time, c.time, 0.01d);
        Assert.assertEquals(23, c.iob, 0.01d);
        Assert.assertEquals(22, c.activity, 0.01d);
        Assert.assertEquals(12, c.bolussnooze, 0.01d);
        Assert.assertEquals(26, c.basaliob, 0.01d);
        Assert.assertEquals(28, c.netbasalinsulin, 0.01d);
        Assert.assertEquals(30, c.hightempinsulin, 0.01d);
        Assert.assertEquals(32, c.netInsulin, 0.01d);
        Assert.assertEquals(34, c.extendedBolusInsulin, 0.01d);
    }

    @Test
    public void roundTest() {
        IobTotal a = new IobTotal(now);
        a.iob = 1.1111111111111;
        a.activity = 1.1111111111111;
        a.bolussnooze = 1.1111111111111;
        a.basaliob = 1.1111111111111;
        a.netbasalinsulin = 1.1111111111111;
        a.hightempinsulin = 1.1111111111111;
        a.netInsulin = 1.1111111111111;
        a.extendedBolusInsulin = 1.1111111111111;
        a.round();
        Assert.assertEquals(1.111d, a.iob, 0.00001d);
        Assert.assertEquals(1.1111d, a.activity, 0.00001d);
        Assert.assertEquals(1.1111d, a.bolussnooze, 0.00001d);
        Assert.assertEquals(1.111d, a.basaliob, 0.00001d);
        Assert.assertEquals(1.111d, a.netbasalinsulin, 0.00001d);
        Assert.assertEquals(1.111d, a.hightempinsulin, 0.00001d);
        Assert.assertEquals(1.111d, a.netInsulin, 0.00001d);
        Assert.assertEquals(1.111d, a.extendedBolusInsulin, 0.00001d);
    }

    @Test
    public void jsonTest() {
        IobTotal a = new IobTotal(now);
        a.iob = 10;
        a.activity = 11;
        a.bolussnooze = 12;
        a.basaliob = 13;
        a.netbasalinsulin = 14;
        a.hightempinsulin = 15;
        a.netInsulin = 16;
        a.extendedBolusInsulin = 17;
        try {
            JSONObject j = a.json();
            Assert.assertEquals(a.iob, j.getDouble("iob"), 0.0000001d);
            Assert.assertEquals(a.basaliob, j.getDouble("basaliob"), 0.0000001d);
            Assert.assertEquals(a.activity, j.getDouble("activity"), 0.0000001d);
            Assert.assertEquals(now, DateUtil.fromISODateString(j.getString("time")).getTime(), 1000);
        } catch (Exception e) {
            Assert.fail("Exception: " + e.getMessage());
        }
    }

    @Test
    public void determineBasalJsonTest() {
        IobTotal a = new IobTotal(now);
        a.iob = 10;
        a.activity = 11;
        a.bolussnooze = 12;
        a.basaliob = 13;
        a.netbasalinsulin = 14;
        a.hightempinsulin = 15;
        a.netInsulin = 16;
        a.extendedBolusInsulin = 17;
        a.iobWithZeroTemp = new IobTotal(now);
        try {
            JSONObject j = a.determineBasalJson();
            Assert.assertEquals(a.iob, j.getDouble("iob"), 0.0000001d);
            Assert.assertEquals(a.basaliob, j.getDouble("basaliob"), 0.0000001d);
            Assert.assertEquals(a.bolussnooze, j.getDouble("bolussnooze"), 0.0000001d);
            Assert.assertEquals(a.activity, j.getDouble("activity"), 0.0000001d);
            Assert.assertEquals(0, j.getLong("lastBolusTime"));
            Assert.assertEquals(now, DateUtil.fromISODateString(j.getString("time")).getTime(), 1000);
            Assert.assertNotNull(j.getJSONObject("iobWithZeroTemp"));
        } catch (Exception e) {
            Assert.fail("Exception: " + e.getMessage());
        }
    }
}

package info.nightscout.utils;


import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.Date;

import info.AAPSMocker;
import info.nightscout.androidaps.MainApp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Created by mike on 20.11.2017.
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest({MainApp.class})
public class DateUtilTest {

    @Test
    public void fromISODateStringTest() throws Exception {
        assertEquals(1511124634417L, DateUtil.fromISODateString("2017-11-19T22:50:34.417+0200").getTime());
        assertEquals(1511124634000L, DateUtil.fromISODateString("2017-11-19T22:50:34+0200").getTime());
        assertEquals(1512317365000L, DateUtil.fromISODateString("2017-12-03T16:09:25.000Z").getTime());
        assertEquals(1513902750000L, DateUtil.fromISODateString("2017-12-22T00:32:30Z").getTime());
    }

    @Test
    public void toISOStringTest() throws Exception {
        assertEquals("2017-12-22T00:32:30Z", DateUtil.toISOString(new Date(1513902750000L)));
        assertEquals("2017-12-22T00:32:30Z", DateUtil.toISOString(1513902750000L));
    }

    @Test
    public void toDateTest() {
        assertTrue(DateUtil.toDate((int) (T.hours(1).secs() + T.mins(1).secs() + 1)).toString().contains("01:01:00"));
    }

    @Test
    public void toSecondsTest() {
        Assert.assertEquals(3600, DateUtil.toSeconds("01:00"));
        Assert.assertEquals(3600, DateUtil.toSeconds("01:00 a.m."));
        Assert.assertEquals(3600, DateUtil.toSeconds("01:00 AM"));
    }

    @Test
    public void dateStringTest() {
        assertTrue(DateUtil.dateString(new Date(1513902750000L)).contains("22"));
        assertTrue(DateUtil.dateString(1513902750000L).contains("22"));
    }

    @Test
    public void timeStringTest() {
        assertTrue(DateUtil.timeString(new Date(1513902750000L)).contains("32"));
        assertTrue(DateUtil.timeString(1513902750000L).contains("32"));
    }

    @Test
    public void dateAndTimeStringTest() {
        assertTrue(DateUtil.dateAndTimeString(1513902750000L).contains("22"));
        assertTrue(DateUtil.dateAndTimeString(1513902750000L).contains("32"));
        assertTrue(DateUtil.dateAndTimeString(new Date(1513902750000L)).contains("22"));
        assertTrue(DateUtil.dateAndTimeString(new Date(1513902750000L)).contains("32"));
    }

   @Test
    public void dateAndTimeRangeStringTest() {
        assertTrue(DateUtil.dateAndTimeRangeString(1513902750000L, 1513902750000L).contains("22"));
        assertTrue(DateUtil.dateAndTimeRangeString(1513902750000L, 1513902750000L).contains("32"));
        assertTrue(DateUtil.dateAndTimeRangeString(1513902750000L, 1513902750000L).contains("22"));
        assertTrue(DateUtil.dateAndTimeRangeString(1513902750000L, 1513902750000L).contains("32"));
    }

    @Test
    public void timeStringFromSecondsTest() {
        Assert.assertEquals("1:00 AM", DateUtil.timeStringFromSeconds((int) T.hours(1).secs()));
    }

    @Test
    public void timeFrameStringTest() {
        Assert.assertEquals("(1h 1')", DateUtil.timeFrameString((T.hours(1).msecs() + T.mins(1).msecs())));
    }

    @Before
    public void prepareMock() {
        AAPSMocker.mockMainApp();
        AAPSMocker.mockStrings();
    }
}

package info.nightscout.androidaps.utils

import android.content.Context
import info.nightscout.androidaps.TestBase
import info.nightscout.core.main.R
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.shared.utils.DateUtil
import info.nightscout.shared.utils.T
import org.junit.Assert
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import java.util.Date

class DateUtilTest : TestBase() {

    @Mock lateinit var context: Context
    @Mock lateinit var rh: ResourceHelper

    @Test
    fun fromISODateStringTest() {
        Assert.assertEquals(1511124634417L, DateUtil(context).fromISODateString("2017-11-19T22:50:34.417+0200"))
        Assert.assertEquals(1511124634000L, DateUtil(context).fromISODateString("2017-11-19T22:50:34+0200"))
        Assert.assertEquals(1512317365000L, DateUtil(context).fromISODateString("2017-12-03T16:09:25.000Z"))
        Assert.assertEquals(1513902750000L, DateUtil(context).fromISODateString("2017-12-22T00:32:30Z"))
    }

    @Test
    fun toISOStringTest() {
        Assert.assertEquals("2017-12-22T00:32:30.000Z", DateUtil(context).toISOString(1513902750000L))
    }

    @Test fun secondsOfTheDayToMillisecondsTest() {
        Assert.assertTrue(Date(DateUtil(context).secondsOfTheDayToMilliseconds((T.hours(1).secs() + T.mins(1).secs() + 1).toInt())).toString().contains("01:01:00"))
    }

    @Test fun toSecondsTest() {
        Assert.assertEquals(3600, DateUtil(context).toSeconds("01:00").toLong())
        Assert.assertEquals(3600, DateUtil(context).toSeconds("01:00 a.m.").toLong())
        Assert.assertEquals(3600, DateUtil(context).toSeconds("01:00 AM").toLong())
    }

    @Test fun dateStringTest() {
        Assert.assertTrue(DateUtil(context).dateString(1513902750000L).contains("22"))
    }

    @Test fun timeStringTest() {
        Assert.assertTrue(DateUtil(context).timeString(1513902750000L).contains("32"))
    }

    @Test fun dateAndTimeStringTest() {
        Assert.assertTrue(DateUtil(context).dateAndTimeString(1513902750000L).contains("22"))
        Assert.assertTrue(DateUtil(context).dateAndTimeString(1513902750000L).contains("32"))
    }

    @Test fun dateAndTimeRangeStringTest() {
        Assert.assertTrue(DateUtil(context).dateAndTimeRangeString(1513902750000L, 1513902750000L).contains("22"))
        Assert.assertTrue(DateUtil(context).dateAndTimeRangeString(1513902750000L, 1513902750000L).contains("32"))
        Assert.assertTrue(DateUtil(context).dateAndTimeRangeString(1513902750000L, 1513902750000L).contains("22"))
        Assert.assertTrue(DateUtil(context).dateAndTimeRangeString(1513902750000L, 1513902750000L).contains("32"))
    }

    /*
    @Test
    public void timeStringFromSecondsTest() {
        Assert.assertEquals("1:00 AM", DateUtil.timeStringFromSeconds((int) T.hours(1).secs()));
    }
    */
    @Test fun timeFrameStringTest() {
        `when`(rh.gs(R.string.shorthour)).thenReturn("h")
        Assert.assertEquals("(1h 1')", DateUtil(context).timeFrameString(T.hours(1).msecs() + T.mins(1).msecs(), rh))
    }
}
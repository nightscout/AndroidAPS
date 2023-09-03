package info.nightscout.core.utils

import android.content.Context
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.shared.utils.DateUtil
import info.nightscout.shared.utils.T
import info.nightscout.sharedtests.TestBase
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import java.util.Date

class DateUtilTest : TestBase() {

    @Mock lateinit var context: Context
    @Mock lateinit var rh: ResourceHelper

    @Test
    fun fromISODateStringTest() {
        Assertions.assertEquals(1511124634417L, DateUtil(context).fromISODateString("2017-11-19T22:50:34.417+0200"))
        Assertions.assertEquals(1511124634000L, DateUtil(context).fromISODateString("2017-11-19T22:50:34+0200"))
        Assertions.assertEquals(1512317365000L, DateUtil(context).fromISODateString("2017-12-03T16:09:25.000Z"))
        Assertions.assertEquals(1513902750000L, DateUtil(context).fromISODateString("2017-12-22T00:32:30Z"))
    }

    @Test
    fun toISOStringTest() {
        Assertions.assertEquals("2017-12-22T00:32:30.000Z", DateUtil(context).toISOString(1513902750000L))
    }

    @Test fun secondsOfTheDayToMillisecondsTest() {
        Assertions.assertTrue(Date(DateUtil(context).secondsOfTheDayToMilliseconds((T.hours(1).secs() + T.mins(1).secs() + 1).toInt())).toString().contains("01:01:00"))
    }

    @Test fun toSecondsTest() {
        Assertions.assertEquals(3600, DateUtil(context).toSeconds("01:00").toLong())
        Assertions.assertEquals(3600, DateUtil(context).toSeconds("01:00 a.m.").toLong())
        Assertions.assertEquals(3600, DateUtil(context).toSeconds("01:00 AM").toLong())
    }

    @Test fun dateStringTest() {
        Assertions.assertTrue(DateUtil(context).dateString(1513902750000L).contains("22"))
    }

    @Test fun timeStringTest() {
        Assertions.assertTrue(DateUtil(context).timeString(1513902750000L).contains("32"))
    }

    @Test fun dateAndTimeStringTest() {
        Assertions.assertTrue(DateUtil(context).dateAndTimeString(1513902750000L).contains("22"))
        Assertions.assertTrue(DateUtil(context).dateAndTimeString(1513902750000L).contains("32"))
    }

    @Test fun dateAndTimeRangeStringTest() {
        Assertions.assertTrue(DateUtil(context).dateAndTimeRangeString(1513902750000L, 1513902750000L).contains("22"))
        Assertions.assertTrue(DateUtil(context).dateAndTimeRangeString(1513902750000L, 1513902750000L).contains("32"))
        Assertions.assertTrue(DateUtil(context).dateAndTimeRangeString(1513902750000L, 1513902750000L).contains("22"))
        Assertions.assertTrue(DateUtil(context).dateAndTimeRangeString(1513902750000L, 1513902750000L).contains("32"))
    }

    /*
    @Test
    public void timeStringFromSecondsTest() {
        Assertions.assertEquals("1:00 AM", DateUtil.timeStringFromSeconds((int) T.hours(1).secs()));
    }
    */
    @Test fun timeFrameStringTest() {
        `when`(rh.gs(info.nightscout.shared.R.string.shorthour)).thenReturn("h")
        Assertions.assertEquals("(1h 1')", DateUtil(context).timeFrameString(T.hours(1).msecs() + T.mins(1).msecs(), rh))
    }
}
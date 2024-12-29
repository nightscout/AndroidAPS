package app.aaps.core.objects.utils

import android.content.Context
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.shared.impl.utils.DateUtilImpl
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import java.util.Date
import java.util.SimpleTimeZone
import java.util.TimeZone

class DateUtilImplTest : TestBase() {

    @Mock lateinit var context: Context
    @Mock lateinit var rh: ResourceHelper

    companion object {

        private lateinit var savedTimeZone: TimeZone

        @BeforeAll
        @JvmStatic
        fun setDefaultTimezoneUtc() {
            savedTimeZone = TimeZone.getDefault()
            TimeZone.setDefault(SimpleTimeZone(0, "UTC"))
        }

        @AfterAll
        @JvmStatic
        fun restoreDefaultTimezone() {
            TimeZone.setDefault(savedTimeZone)
        }
    }

    @Test
    fun fromISODateStringTest() {
        assertThat(DateUtilImpl(context).fromISODateString("2017-11-19T22:50:34.417+0200")).isEqualTo(1511124634417L)
        assertThat(DateUtilImpl(context).fromISODateString("2017-11-19T22:50:34+0200")).isEqualTo(1511124634000L)
        assertThat(DateUtilImpl(context).fromISODateString("2017-12-03T16:09:25.000Z")).isEqualTo(1512317365000L)
        assertThat(DateUtilImpl(context).fromISODateString("2017-12-22T00:32:30Z")).isEqualTo(1513902750000L)
    }

    @Test
    fun toISOStringTest() {
        assertThat(DateUtilImpl(context).toISOString(1513902750000L)).isEqualTo("2017-12-22T00:32:30.000Z")
    }

    @Test fun secondsOfTheDayToMillisecondsTest() {
        assertThat(Date(DateUtilImpl(context).secondsOfTheDayToMilliseconds((T.hours(1).secs() + T.mins(1).secs() + 1).toInt())).toString()).contains("01:01:00")
    }

    @Test fun toSecondsTest() {
        assertThat(DateUtilImpl(context).toSeconds("01:00").toLong()).isEqualTo(3600)
        assertThat(DateUtilImpl(context).toSeconds("01:00 a.m.").toLong()).isEqualTo(3600)
        assertThat(DateUtilImpl(context).toSeconds("01:00 AM").toLong()).isEqualTo(3600)
    }

    @Test fun dateStringTest() {
        assertThat(DateUtilImpl(context).dateString(1513902750000L)).contains("22")
    }

    @Test fun timeStringTest() {
        assertThat(DateUtilImpl(context).timeString(1513902750000L)).contains("32")
    }

    @Test fun dateAndTimeStringTest() {
        assertThat(DateUtilImpl(context).dateAndTimeString(1513902750000L)).contains("22")
        assertThat(DateUtilImpl(context).dateAndTimeString(1513902750000L)).contains("32")
    }

    @Test fun dateAndTimeRangeStringTest() {
        assertThat(DateUtilImpl(context).dateAndTimeRangeString(1513902750000L, 1513902750000L)).contains("22")
        assertThat(DateUtilImpl(context).dateAndTimeRangeString(1513902750000L, 1513902750000L)).contains("32")
        assertThat(DateUtilImpl(context).dateAndTimeRangeString(1513902750000L, 1513902750000L)).contains("22")
        assertThat(DateUtilImpl(context).dateAndTimeRangeString(1513902750000L, 1513902750000L)).contains("32")
    }

    /*
    @Test
    public void timeStringFromSecondsTest() {
        assertThat(DateUtil.timeStringFromSeconds((int) T.hours(1).secs()));.isEqualTo("1:00 AM")
    }
    */
    @Test fun timeFrameStringTest() {
        `when`(rh.gs(app.aaps.core.interfaces.R.string.shorthour)).thenReturn("h")
        assertThat(DateUtilImpl(context).timeFrameString(T.hours(1).msecs() + T.mins(1).msecs(), rh)).isEqualTo("(1h 1')")
    }
}

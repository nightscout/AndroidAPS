package app.aaps.shared.impl.utils

import android.content.Context
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.R
import app.aaps.core.interfaces.resources.ResourceHelper
import com.google.common.truth.Truth
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.whenever
import java.util.Date
import java.util.SimpleTimeZone
import java.util.TimeZone

@ExtendWith(MockitoExtension::class)
class DateUtilImplOldTest() {

    @Mock lateinit var context: Context
    @Mock lateinit var rh: ResourceHelper

    companion object {

        private lateinit var savedTimeZone: TimeZone

        @BeforeEach
        
        fun setDefaultTimezoneUtc() {
            savedTimeZone = TimeZone.getDefault()
            TimeZone.setDefault(SimpleTimeZone(0, "UTC"))
        }

        @AfterAll
        
        fun restoreDefaultTimezone() {
            TimeZone.setDefault(savedTimeZone)
        }
    }

    @Test
    fun fromISODateStringTest() {
        Truth.assertThat(DateUtilImpl(context).fromISODateString("2017-11-19T22:50:34.417+0200")).isEqualTo(1511124634417L)
        Truth.assertThat(DateUtilImpl(context).fromISODateString("2017-11-19T22:50:34+0200")).isEqualTo(1511124634000L)
        Truth.assertThat(DateUtilImpl(context).fromISODateString("2017-12-03T16:09:25.000Z")).isEqualTo(1512317365000L)
        Truth.assertThat(DateUtilImpl(context).fromISODateString("2017-12-22T00:32:30Z")).isEqualTo(1513902750000L)
    }

    @Test
    fun toISOStringTest() {
        Truth.assertThat(DateUtilImpl(context).toISOString(1513902750000L)).isEqualTo("2017-12-22T00:32:30.000Z")
    }

    @Test fun secondsOfTheDayToMillisecondsTest() {
        Truth.assertThat(Date(DateUtilImpl(context).secondsOfTheDayToMillisecondsOfHoursAndMinutes((T.Companion.hours(1).secs() + T.Companion.mins(1).secs() + 1).toInt())).toString()).contains("01:01:00")
    }

    @Test fun toSecondsTest() {
        Truth.assertThat(DateUtilImpl(context).toSeconds("01:00").toLong()).isEqualTo(3600)
        Truth.assertThat(DateUtilImpl(context).toSeconds("01:00 a.m.").toLong()).isEqualTo(3600)
        Truth.assertThat(DateUtilImpl(context).toSeconds("01:00 AM").toLong()).isEqualTo(3600)
    }

    @Test fun dateStringTest() {
        Truth.assertThat(DateUtilImpl(context).dateString(1513902750000L)).contains("22")
    }

    @Test fun timeStringTest() {
        Truth.assertThat(DateUtilImpl(context).timeString(1513902750000L)).contains("32")
    }

    @Test fun dateAndTimeStringTest() {
        Truth.assertThat(DateUtilImpl(context).dateAndTimeString(1513902750000L)).contains("22")
        Truth.assertThat(DateUtilImpl(context).dateAndTimeString(1513902750000L)).contains("32")
    }

    @Test fun dateAndTimeRangeStringTest() {
        Truth.assertThat(DateUtilImpl(context).dateAndTimeRangeString(1513902750000L, 1513902750000L)).contains("22")
        Truth.assertThat(DateUtilImpl(context).dateAndTimeRangeString(1513902750000L, 1513902750000L)).contains("32")
        Truth.assertThat(DateUtilImpl(context).dateAndTimeRangeString(1513902750000L, 1513902750000L)).contains("22")
        Truth.assertThat(DateUtilImpl(context).dateAndTimeRangeString(1513902750000L, 1513902750000L)).contains("32")
    }

    /*
    @Test
    public void timeStringFromSecondsTest() {
        assertThat(DateUtil.timeStringFromSeconds((int) T.hours(1).secs()));.isEqualTo("1:00 AM")
    }
    */
    @Test fun timeFrameStringTest() {
        whenever(rh.gs(R.string.shorthour)).thenReturn("h")
        Truth.assertThat(DateUtilImpl(context).timeFrameString(T.Companion.hours(1).msecs() + T.Companion.mins(1).msecs(), rh)).isEqualTo("(1h 1')")
    }
}
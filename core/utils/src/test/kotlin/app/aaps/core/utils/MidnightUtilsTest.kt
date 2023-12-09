package app.aaps.core.utils

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.TimeZone

class MidnightUtilsTest {

    @BeforeEach fun setUp() {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Amsterdam"))
    }

    @Test
    fun secondsFromMidnight() {
        val time = LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        assertThat(MidnightUtils.secondsFromMidnight(time)).isIn(0..24 * 3600)
    }

    @Test
    fun testSecondsFromMidnight() {
        val midnight = LocalDate.now().atTime(LocalTime.MIDNIGHT).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        assertThat(MidnightUtils.secondsFromMidnight(midnight)).isEqualTo(0)
        val oneHourAfter = LocalDateTime.ofInstant(Instant.ofEpochMilli(midnight), ZoneId.systemDefault()).atZone(ZoneId.systemDefault()).plusHours(1).toInstant().toEpochMilli()
        assertThat(MidnightUtils.secondsFromMidnight(oneHourAfter)).isEqualTo(3600)
    }

    @Test
    fun milliSecFromMidnight() {
        val midnight = LocalDate.now().atTime(LocalTime.MIDNIGHT).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        assertThat(MidnightUtils.secondsFromMidnight(midnight)).isEqualTo(0)
        val oneHourAfter = LocalDateTime.ofInstant(Instant.ofEpochMilli(midnight), ZoneId.systemDefault()).atZone(ZoneId.systemDefault()).plusHours(1).toInstant().toEpochMilli()
        assertThat(MidnightUtils.milliSecFromMidnight(oneHourAfter)).isEqualTo(3600 * 1000)
    }

    @Test fun testDateTimeToDuration() {
        val dateTime = ZonedDateTime.of(1991, 8, 13, 23, 5, 1, 0, ZoneId.of("Europe/Amsterdam")).toInstant().toEpochMilli()
        assertThat(MidnightUtils.secondsFromMidnight(dateTime)).isEqualTo(83101)
        assertThat(MidnightUtils.milliSecFromMidnight(dateTime)).isEqualTo(83101 * 1000L)
    }

    @Test fun testDateTimeToDurationAtDstChange() {
        val dateTime = ZonedDateTime.of(2020, 10, 25, 23, 5, 1, 0, ZoneId.of("Europe/Amsterdam")).toInstant().toEpochMilli()
        assertThat(MidnightUtils.secondsFromMidnight(dateTime)).isEqualTo(83101)
        assertThat(MidnightUtils.milliSecFromMidnight(dateTime)).isEqualTo(83101 * 1000L)
    }

    @Test fun testDateTimeToDurationAtDstReverseChange() {
        val dateTime = ZonedDateTime.of(2020, 3, 29, 23, 5, 1, 0, ZoneId.of("Europe/Amsterdam")).toInstant().toEpochMilli()
        assertThat(MidnightUtils.secondsFromMidnight(dateTime)).isEqualTo(83101)
        assertThat(MidnightUtils.milliSecFromMidnight(dateTime)).isEqualTo(83101 * 1000L)
    }

    @Test fun testDateTimeInOtherZone() {
        TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))
        assertThat(ZoneId.systemDefault().id).isEqualTo("America/Los_Angeles")
        val dateTime = ZonedDateTime.of(2020, 3, 29, 23, 5, 1, 0, ZoneId.of("America/Los_Angeles")).toInstant().toEpochMilli()
        assertThat(MidnightUtils.secondsFromMidnight(dateTime)).isEqualTo(83101)
        assertThat(MidnightUtils.milliSecFromMidnight(dateTime)).isEqualTo(83101 * 1000L)
    }
}
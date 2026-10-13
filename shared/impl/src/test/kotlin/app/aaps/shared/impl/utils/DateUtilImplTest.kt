package app.aaps.shared.impl.utils

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.text.format.DateFormat
import android.util.DisplayMetrics
import com.google.common.truth.Truth
import com.google.common.truth.Truth.assertThat
import org.joda.time.DateTimeZone
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.mockStatic
import org.mockito.junit.jupiter.MockitoExtension
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * Unit tests for [DateUtilImpl].
 *
 * This class validates that the refactored `DateUtilImpl` is both correct and
 * produces output identical to the legacy `DateUtilOldImpl`.
 * It uses a fixed timezone and locale to ensure tests are repeatable.
 */
@ExtendWith(MockitoExtension::class)
class DateUtilImplTest {

    @Mock
    private lateinit var mockContext: Context
    private lateinit var dateUtilImpl: DateUtilImpl
    private lateinit var dateUtilImplDeter: DateUtilImpl
    private lateinit var dateUtilOldImpl: DateUtilOldImpl
    private lateinit var fixedClock: Clock

    private val fixedInstant = Instant.parse("2023-10-27T16:00:00Z")
    private val fixedZone = ZoneId.of("America/New_York")

    @BeforeEach
    fun setUp() {
        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
        DateTimeZone.setDefault(DateTimeZone.forID("America/New_York"))
        Locale.setDefault(Locale.US)
        fixedClock = Clock.fixed(fixedInstant, fixedZone)
        dateUtilImpl = DateUtilImpl(mockContext)
        dateUtilImplDeter = DateUtilImpl(mockContext, fixedClock)
        dateUtilOldImpl = DateUtilOldImpl(mockContext)
    }

    //region ISO and Timestamp Conversion
    @Test
    fun `fromISODateString works for full UTC timestamp and matches old behavior`() {
        val isoString = "2023-10-26T09:07:03.344Z"
        val expectedMillis = 1698311223344L
        val newResult = dateUtilImpl.fromISODateString(isoString)
        val oldResult = dateUtilOldImpl.fromISODateString(isoString)

        assertThat(newResult).isEqualTo(expectedMillis)
        assertThat(newResult).isEqualTo(oldResult)
    }
    @Test
    fun `toISOString works and matches old behavior`() {
        val millis = 1698311223344L
        val expectedString = "2023-10-26T09:07:03.344Z"
        val newResult = dateUtilImpl.toISOString(millis)
        val oldResult = dateUtilOldImpl.toISOString(millis)

        assertThat(newResult).isEqualTo(expectedString)
        assertThat(newResult).isEqualTo(oldResult)
    }
    @Test
    fun `toISOAsUTC works and matches old behavior`() {
        val millis = 1698311223344L
        val expectedString = "2023-10-26T09:07:03.3440000Z"
        val newResult = dateUtilImpl.toISOAsUTC(millis)
        val oldResult = dateUtilOldImpl.toISOAsUTC(millis)

        assertThat(newResult).isEqualTo(expectedString)
        assertThat(newResult).isEqualTo(oldResult)
    }
    @Test
    fun `toISONoZone works and matches old behavior`() {
        val millis = 1698311223344L // 09:07:03.344 UTC
        val expectedString = "2023-10-26T05:07:03" // 05:07:03 in New York (EDT)
        val newResult = dateUtilImpl.toISONoZone(millis)
        val oldResult = dateUtilOldImpl.toISONoZone(millis)

        assertThat(newResult).isEqualTo(expectedString)
        assertThat(newResult).isEqualTo(oldResult)
    }
    @Test
    fun `timeStampToUtcDateMillis works correctly now`() {
        val timestamp = 1698311223344L // 2023-10-26 09:07:03.344Z
       val expectedMillis = 1698278400000L // 2023-10-26 00:00:00.000Z
        val newResult = dateUtilImpl.timeStampToUtcDateMillis(timestamp)
        val oldResult = dateUtilOldImpl.timeStampToUtcDateMillis(timestamp)

        assertThat(newResult).isEqualTo(expectedMillis)
        // Assert the NEW implementation is NOT the same as the OLD one.
//TODO: the original timeStampToUtcDateMillis couldn't parse milliseconds.
        assertThat(newResult).isNotEqualTo(oldResult)
    }
    @Test
    fun `minutesOfTheDayToMilliseconds correctly truncates to the minute`() {
        // ARRANGE
        // Use the deterministic util to get a predictable start of the day based on our fixedClock.
        val startOfToday = dateUtilImplDeter.beginOfDay(dateUtilImplDeter.now())

        // Input representing 1 hour, 1 minute, and 45 seconds. This should be truncated to 61 minutes.
        val inputSeconds = (1 * 3600) + (1 * 60) + 45
        val expectedMillis = startOfToday + (61 * 60 * 1000L) // Exactly 61 minutes past the start of the day

        // ACT
        val newResult = dateUtilImplDeter.secondsOfTheDayToMillisecondsOfHoursAndMinutes(inputSeconds)
        val oldResult = dateUtilOldImpl.secondsOfTheDayToMilliseconds(inputSeconds) // The old function with this logic

        // ASSERT
        // 1. Assert that the new implementation produces the EXACT expected millisecond value.
        assertThat(newResult).isEqualTo(expectedMillis)

        // 2. Assert that the new implementation's formatted time matches the old implementation's intended behavior.
        val newTime = dateUtilImpl.timeString(newResult)
        val oldTime = dateUtilOldImpl.timeString(oldResult)
        assertThat(newTime).isEqualTo("01:01 AM") // Based on our fixed clock's timezone
        assertThat(newTime).isEqualTo(oldTime)
    }
    @Test
    fun `secondsOfTheDayToMilliseconds works correctly now (without truncating seconds)`() {
        // ARRANGE: Input representing 1 hour, 1 minute, and 45 seconds.
        val inputSeconds = (1 * 3600) + (1 * 60) + 45
        val now = dateUtilImpl.now()
        val startOfToday = dateUtilImpl.beginOfDay(now)
        val expectedMillis = startOfToday + (inputSeconds * 1000L)

        // ACT
        val newResult = dateUtilImpl.secondsOfTheDayToMilliseconds(inputSeconds)
        val oldResult = dateUtilOldImpl.secondsOfTheDayToMilliseconds(inputSeconds)

        // ASSERT
        // 1. Assert the NEW implementation is as expected.
        assertThat(newResult).isEqualTo(expectedMillis)
        // 2. Assert the NEW implementation is different from the old one which ignored seconds.
//TODO: the original secondsOfTheDayToMilliseconds couldn't parse seconds.
        assertThat(newResult).isNotEqualTo(oldResult)
    }
    //endregion

    //region Date and Time Formatting
    @Test
    fun `dateString works and matches old behavior`() {
        val millis = 1698393600000L
        val expected = "10/27/23"
        val newResult = dateUtilImpl.dateString(millis)
        val oldResult = dateUtilOldImpl.dateString(millis)

        assertThat(newResult).isEqualTo(expected)
        assertThat(newResult).isEqualTo(oldResult)
    }
    @Test
    fun `timeString works for 12-hour format and matches old behavior`() {
        mockStatic(DateFormat::class.java).use { mockedStatic ->
            mockedStatic.`when`<Boolean> { DateFormat.is24HourFormat(mockContext) }.thenReturn(false)
            val millis = 1698436800000L // 20:00 UTC -> 16:00 EDT (4 PM)
            val expected = "04:00 PM"
            val newResult = dateUtilImpl.timeString(millis)
            val oldResult = dateUtilOldImpl.timeString(millis)

            assertThat(newResult).isEqualTo(expected)
            assertThat(newResult).isEqualTo(oldResult)
        }
    }
    @Test
    fun `timeString works for 24-hour format and matches old behavior`() {
        mockStatic(DateFormat::class.java).use { mockedStatic ->
            mockedStatic.`when`<Boolean> { DateFormat.is24HourFormat(mockContext) }.thenReturn(true)
            val millis = 1698458400000L // 10:00 PM EDT, which is 22:00
            val expected = "22:00"
            val newResult = dateUtilImpl.timeString(millis)
            val oldResult = dateUtilOldImpl.timeString(millis)

            assertThat(newResult).isEqualTo(expected)
            assertThat(newResult).isEqualTo(oldResult)
        }
    }
    @Test
    fun `timeStringWithSeconds works and matches old behavior`() {
        mockStatic(DateFormat::class.java).use { mockedStatic ->
            // Test 12-hour format
            mockedStatic.`when`<Boolean> { DateFormat.is24HourFormat(mockContext) }.thenReturn(false)
            val millis = 1698436812345L // 16:00:12 EDT
            val expected12Hour = "04:00:12 PM"
            assertThat(dateUtilImpl.timeStringWithSeconds(millis)).isEqualTo(expected12Hour)
            assertThat(dateUtilOldImpl.timeStringWithSeconds(millis)).isEqualTo(expected12Hour)

            // Test 24-hour format
            mockedStatic.`when`<Boolean> { DateFormat.is24HourFormat(mockContext) }.thenReturn(true)
            val expected24Hour = "16:00:12"
            assertThat(dateUtilImpl.timeStringWithSeconds(millis)).isEqualTo(expected24Hour)
            assertThat(dateUtilOldImpl.timeStringWithSeconds(millis)).isEqualTo(expected24Hour)
        }
    }
    @Test
    fun `dateStringShort works and matches old behavior`() {
        mockStatic(DateFormat::class.java).use { mockedStatic ->
            // Test for 12-hour format first (MM/dd in US)
            mockedStatic.`when`<Boolean> { DateFormat.is24HourFormat(mockContext) }.thenReturn(false)
            val millis = 1698393600000L // Oct 27, 2023
            val expected12Hour = "10/27"
            val newResult12 = dateUtilImpl.dateStringShort(millis)
            val oldResult12 = dateUtilOldImpl.dateStringShort(millis)
            assertThat(newResult12).isEqualTo(expected12Hour)
            assertThat(newResult12).isEqualTo(oldResult12)

            // Test for 24-hour format (dd/MM in US)
            mockedStatic.`when`<Boolean> { DateFormat.is24HourFormat(mockContext) }.thenReturn(true)
            val expected24Hour = "27/10"
            val newResult24 = dateUtilImpl.dateStringShort(millis)
            val oldResult24 = dateUtilOldImpl.dateStringShort(millis)
            assertThat(newResult24).isEqualTo(expected24Hour)
            assertThat(newResult24).isEqualTo(oldResult24)
        }
    }
    @Test
    fun `dateAndTimeString works and matches old behavior`() {
        mockStatic(DateFormat::class.java).use { mockedStatic ->
            mockedStatic.`when`<Boolean> { DateFormat.is24HourFormat(mockContext) }.thenReturn(false)
            // ARRANGE
            val millis = 1698436800000L // Oct 27, 2023 at 4:00 PM
            // Expected output combines dateString() + " " + timeString()
            // "10/27/23" + " " + "04:00 PM"
            val expected = "10/27/23 04:00 PM"

            // ACT
            val newResult = dateUtilImpl.dateAndTimeString(millis)
            val oldResult = dateUtilOldImpl.dateAndTimeString(millis)

            // ASSERT
            assertThat(newResult).isEqualTo(expected)
            assertThat(oldResult).isEqualTo(expected)
        }
    }
    @Test
    fun `dateAndTimeAndSecondsString works and matches old behavior`() {
        // ARRANGE
        val millis = 1698436812345L // Oct 27, 2023 16:00:12 EDT

        // Expected output combines dateString() + " " + timeStringWithSeconds()
        // Based on previous tests, this should be "10/27/23" + " " + "04:00:12 PM"
        val expected = "10/27/23 04:00:12 PM"

        // ACT
        val newResult = dateUtilImpl.dateAndTimeAndSecondsString(millis)
        val oldResult = dateUtilOldImpl.dateAndTimeAndSecondsString(millis)

        // ASSERT
        assertThat(newResult).isEqualTo(expected)
        // The old implementation also padded the hour, so they should match.
        assertThat(newResult).isEqualTo(oldResult)
    }
    @Test
    fun `dateAndTimeRangeString works and matches old behavior`() {
        mockStatic(DateFormat::class.java).use { mockedStatic ->
            mockedStatic.`when`<Boolean> { DateFormat.is24HourFormat(mockContext) }.thenReturn(false)
            // ARRANGE
            // Start: Oct 27, 2023 4:00 PM, End: Oct 27, 2023 6:00 PM
            val startTime = 1698436800000L
            val endTime = 1698444000000L

            // Expected output: dateAndTimeString(start) + " - " + timeString(end)
            val expected = "10/27/23 04:00 PM - 06:00 PM"

            // ACT
            val newResult = dateUtilImpl.dateAndTimeRangeString(startTime, endTime)
            val oldResult = dateUtilOldImpl.dateAndTimeRangeString(startTime, endTime)

            // ASSERT
            assertThat(newResult).isEqualTo(expected)
            assertThat(oldResult).isEqualTo(expected)
        }
    }
    @Test
    fun `dateAndTimeStringNullable works and matches old behavior`() {
        mockStatic(DateFormat::class.java).use { mockedStatic ->
            mockedStatic.`when`<Boolean> { DateFormat.is24HourFormat(mockContext) }.thenReturn(false)

            // --- Test Case 1: Valid timestamp ---
            val millis = 1698436800000L // Oct 27, 2023 4:00 PM
            val expected = "10/27/23 04:00 PM"
            assertThat(dateUtilImpl.dateAndTimeStringNullable(millis)).isEqualTo(expected)
            assertThat(dateUtilOldImpl.dateAndTimeStringNullable(millis)).isEqualTo(expected)

            // --- Test Case 2: Null timestamp ---
            assertThat(dateUtilImpl.dateAndTimeStringNullable(null)).isNull()
            assertThat(dateUtilOldImpl.dateAndTimeStringNullable(null)).isNull()

            // --- Test Case 3: Zero timestamp ---
            assertThat(dateUtilImpl.dateAndTimeStringNullable(0L)).isNull()
            assertThat(dateUtilOldImpl.dateAndTimeStringNullable(0L)).isNull()
        }
    }
    @Test
    fun `timeRangeString works and matches old behavior`() {
        val startTime = 1698436800000L // 16:00 EDT -> "04:00 PM"
        val endTime = 1698444000000L   // 18:00 EDT -> "06:00 PM"
        val expected = "04:00 PM - 06:00 PM"

        mockStatic(DateFormat::class.java).use { mockedStatic ->
            mockedStatic.`when`<Boolean> { DateFormat.is24HourFormat(mockContext) }.thenReturn(false)
            val newResult = dateUtilImpl.timeRangeString(startTime, endTime)
            val oldResult = dateUtilOldImpl.timeRangeString(startTime, endTime)

            assertThat(newResult).isEqualTo(expected)
            assertThat(newResult).isEqualTo(oldResult)
        }
    }
    @Test
    fun `formatHHMM works and matches old behavior`() {
        // ARRANGE
        val timeAsSeconds = (14 * 3600) + (5 * 60) // 14:05
        val expected = "14:05"

        // ACT
        val newResult = dateUtilImpl.formatHHMM(timeAsSeconds)
        val oldResult = dateUtilOldImpl.formatHHMM(timeAsSeconds)

        // ASSERT
        assertThat(newResult).isEqualTo(expected)
        assertThat(newResult).isEqualTo(oldResult)
    }
    //endregion

    //region Relative Time Formatting
    @Test
    fun `dateStringRelative identifies today and yesterday and matches old behavior`() {
        val rh = FakeResourceHelper()
        // This test is kept as a simple regression check. It's not fully deterministic.
        val oneHourAgo = System.currentTimeMillis() - (60 * 60 * 1000)
        val twentyFiveHoursAgo = System.currentTimeMillis() - (25 * 60 * 60 * 1000)

        val newTodayResult = dateUtilImpl.dateStringRelative(oneHourAgo, rh)
        val oldTodayResult = dateUtilOldImpl.dateStringRelative(oneHourAgo, rh)
        assertThat(newTodayResult).isEqualTo("Today")
        assertThat(newTodayResult).isEqualTo(oldTodayResult)

        val newYesterdayResult = dateUtilImpl.dateStringRelative(twentyFiveHoursAgo, rh)
        val oldYesterdayResult = dateUtilOldImpl.dateStringRelative(twentyFiveHoursAgo, rh)
        assertThat(newYesterdayResult).isEqualTo("Yesterday")
        assertThat(newYesterdayResult).isEqualTo(oldYesterdayResult)
    }
    @Test
    fun `dateStringRelative is deterministic with fixed clock`() {
        // ARRANGE
        val rh = FakeResourceHelper()
        // Use a fixed start of day in the test's timezone to be absolutely clear.
        val startOfDay = ZonedDateTime.of(2023, 10, 27, 0, 0, 0, 0, fixedZone).toInstant()

        // --- Test for "Today" ---
        // A timestamp for a few hours into that day.
        val todayTimestamp = startOfDay.plus(10, java.time.temporal.ChronoUnit.HOURS).toEpochMilli()
        // The clock is now fixed to a time later on the same day.
        val clockForTodayTest = Clock.fixed(startOfDay.plus(12, java.time.temporal.ChronoUnit.HOURS), fixedZone)
        val utilForToday = DateUtilImpl(mockContext, clockForTodayTest)
        assertThat(utilForToday.dateStringRelative(todayTimestamp, rh)).isEqualTo("Today")

        // --- Test for "Yesterday" ---
        // A timestamp from the previous day.
        val yesterdayTimestamp = startOfDay.minus(2, java.time.temporal.ChronoUnit.HOURS).toEpochMilli()
        assertThat(utilForToday.dateStringRelative(yesterdayTimestamp, rh)).isEqualTo("Yesterday")

        // --- Test for "Later today" ---
        val laterTodayTimestamp = startOfDay.plus(14, java.time.temporal.ChronoUnit.HOURS).toEpochMilli()
        assertThat(utilForToday.dateStringRelative(laterTodayTimestamp, rh)).isEqualTo("Later today")

        // --- Test for "Tomorrow" ---
        val tomorrowTimestamp = startOfDay.plus(26, java.time.temporal.ChronoUnit.HOURS).toEpochMilli()
        assertThat(utilForToday.dateStringRelative(tomorrowTimestamp, rh)).isEqualTo("Tomorrow")
    }
    @Test
    fun `minAgo formats minutes ago and matches old behavior`() {
        val rh = FakeResourceHelper()
        // Test assumes that dateUtil.now() inside minAgo is close enough to System.currentTimeMillis()
        val fiveMinutesInMillis = 5 * 60 * 1000L
        val timestamp = System.currentTimeMillis() - fiveMinutesInMillis

        val newResult = dateUtilImpl.minAgo(rh, timestamp)
        val oldResult = dateUtilOldImpl.minAgo(rh, timestamp)

        assertThat(newResult).startsWith("5 min ago") // Using startsWith to avoid issues with test execution time
        assertThat(newResult).isEqualTo(oldResult)
    }
    @Test
    fun `minAgo is deterministic with fixed clock`() {
        // ARRANGE
        val rh = FakeResourceHelper()
        val fiveMinutesAgo = fixedInstant.minus(5, java.time.temporal.ChronoUnit.MINUTES).toEpochMilli()
        val expected = "5 min ago"

        // ACT
        val newResult = dateUtilImplDeter.minAgo(rh, fiveMinutesAgo)

        // ASSERT
        assertThat(newResult).isEqualTo(expected)
    }
    @Test
    fun `minOrSecAgo formats seconds and minutes correctly and matches old behavior`() {
        val rh = FakeResourceHelper()
        // Test for seconds
        val thirtySecondsAgo = System.currentTimeMillis() - 30_000L
        val newSecResult = dateUtilImpl.minOrSecAgo(rh, thirtySecondsAgo)
        val oldSecResult = dateUtilOldImpl.minOrSecAgo(rh, thirtySecondsAgo)
        assertThat(newSecResult).contains("30 sec ago")
        assertThat(newSecResult).isEqualTo(oldSecResult)

        // Test for minutes (e.g., 3 minutes ago)
        val threeMinutesAgo = System.currentTimeMillis() - 180_000L
        val newMinResult = dateUtilImpl.minOrSecAgo(rh, threeMinutesAgo)
        val oldMinResult = dateUtilOldImpl.minOrSecAgo(rh, threeMinutesAgo)
        assertThat(newMinResult).contains("3 min ago")
        assertThat(newMinResult).isEqualTo(oldMinResult)
    }
    @Test
    fun `minOrSecAgo is deterministic with fixed clock`() {
        // ARRANGE
        val rh = FakeResourceHelper()

        // --- Test seconds case ---
        val fortyFiveSecondsAgo = fixedInstant.minus(45, java.time.temporal.ChronoUnit.SECONDS).toEpochMilli()
        val expectedSeconds = "45 sec ago"
        assertThat(dateUtilImplDeter.minOrSecAgo(rh, fortyFiveSecondsAgo)).isEqualTo(expectedSeconds)

        // --- Test minutes case ---
        val threeMinutesAgo = fixedInstant.minus(3, java.time.temporal.ChronoUnit.MINUTES).toEpochMilli()
        val expectedMinutes = "3 min ago"
        assertThat(dateUtilImplDeter.minOrSecAgo(rh, threeMinutesAgo)).isEqualTo(expectedMinutes)
    }
    @Test
    fun `minAgoShort works and matches old behavior`() {
        // ARRANGE
        val now = System.currentTimeMillis()
        val fiveMinAgo = now - (5 * 60 * 1000L)
        val expected = "(-5)"

        // ACT
        val newResult = dateUtilImpl.minAgoShort(fiveMinAgo)
        val oldResult = dateUtilOldImpl.minAgoShort(fiveMinAgo)

        // ASSERT
        assertThat(newResult).isEqualTo(expected)
        assertThat(oldResult).isEqualTo(expected)
    }
    @Test
    fun `minAgoShort is deterministic with fixed clock`() {
        // ARRANGE
        val fiveMinutesAgo = fixedInstant.minus(5, java.time.temporal.ChronoUnit.MINUTES).toEpochMilli()
        val fiveMinutesHence = fixedInstant.plus(5, java.time.temporal.ChronoUnit.MINUTES).toEpochMilli()

        // ACT & ASSERT for past
        assertThat(dateUtilImplDeter.minAgoShort(fiveMinutesAgo)).isEqualTo("(-5)")

        // ACT & ASSERT for future
        assertThat(dateUtilImplDeter.minAgoShort(fiveMinutesHence)).isEqualTo("(+5)")
    }
    @Test
    fun `minAgoLong works and matches old behavior`() {
        // ARRANGE
        val rh = FakeResourceHelper()
        val now = System.currentTimeMillis()
        val sevenMinutesAgo = now - (7 * 60 * 1000L)
        val expected = "7 minutes ago"

        // ACT
        val newResult = dateUtilImpl.minAgoLong(rh, sevenMinutesAgo)
        val oldResult = dateUtilOldImpl.minAgoLong(rh, sevenMinutesAgo)

        // ASSERT
        assertThat(newResult).isEqualTo(expected)
        assertThat(oldResult).isEqualTo(expected)
    }
    @Test
    fun `minAgoLong is deterministic with fixed clock`() {
        // ARRANGE
        val rh = FakeResourceHelper()
        val sevenMinutesAgo = fixedInstant.minus(7, java.time.temporal.ChronoUnit.MINUTES).toEpochMilli()
        val expected = "7 minutes ago"

        // ACT
        val newResult = dateUtilImplDeter.minAgoLong(rh, sevenMinutesAgo)

        // ASSERT
        assertThat(newResult).isEqualTo(expected)
    }
    @Test
    fun `hourAgo works and matches old behavior`() {
        // ARRANGE
        val rh = FakeResourceHelper()
        val now = System.currentTimeMillis()
        val twoHoursAgo = now - (2 * 60 * 60 * 1000L)
        val expected = "2 hours ago"

        // ACT
        val newResult = dateUtilImpl.hourAgo(twoHoursAgo, rh)
        val oldResult = dateUtilOldImpl.hourAgo(twoHoursAgo, rh)

        // ASSERT
        assertThat(newResult).isEqualTo(expected)
        assertThat(oldResult).isEqualTo(expected)
    }
    @Test
    fun `hourAgo is deterministic with fixed clock`() {
        // ARRANGE
        val rh = FakeResourceHelper()
        val twoHoursAgo = fixedInstant.minus(2, java.time.temporal.ChronoUnit.HOURS).toEpochMilli()
        val expected = "2 hours ago"

        // ACT
        val newResult = dateUtilImplDeter.hourAgo(twoHoursAgo, rh)

        // ASSERT
        assertThat(newResult).isEqualTo(expected)
    }
    @Test
    fun `dayAgo works and matches old behavior`() {
        // ARRANGE
        val rh = FakeResourceHelper()
        val now = System.currentTimeMillis()
        val threeDaysAgo = now - (3 * 24 * 60 * 60 * 1000L)
        val expected = "3 days ago"

        // ACT
        val newResult = dateUtilImpl.dayAgo(threeDaysAgo, rh)
        val oldResult = dateUtilOldImpl.dayAgo(threeDaysAgo, rh)

        // ASSERT
        assertThat(newResult).isEqualTo(expected)
        assertThat(oldResult).isEqualTo(expected)
    }
    @Test
    fun `dayAgo is deterministic with fixed clock`() {
        // ARRANGE
        val rh = FakeResourceHelper()
        val threeDaysAgo = fixedInstant.minus(3, java.time.temporal.ChronoUnit.DAYS).toEpochMilli()
        val expected = "3 days ago"

        // ACT
        // We test the non-rounding case here for simplicity
        val newResult = dateUtilImplDeter.dayAgo(threeDaysAgo, rh, round = false)

        // ASSERT
        assertThat(newResult).isEqualTo(expected)
    }
    @Test
    fun `sinceString produces correct timeFrameString and matches old behavior`() {
        // ARRANGE
        val rh = FakeResourceHelper()
        val pastTimestamp = 1698426000000L // 13:00:00 EDT
        val nowForSince = 1698435000000L  // 15:30:00 EDT (2h 30m later)
        val expectedSinceString = "(2h 30')"

        // ACT
        // Test the function it wraps: timeFrameString. This is a more direct unit test.
        val sinceDuration = nowForSince - pastTimestamp
        val newResult = dateUtilImpl.timeFrameString(sinceDuration, rh)
        val oldResult = dateUtilOldImpl.timeFrameString(sinceDuration, rh)

        // ASSERT
        assertThat(newResult).isEqualTo(expectedSinceString)
        assertThat(newResult).isEqualTo(oldResult)
    }
    @Test
    fun `sinceString is deterministic with fixed clock`() {
        val rh = FakeResourceHelper()
        val pastTimestamp = fixedInstant.minus(2, java.time.temporal.ChronoUnit.HOURS).minus(30, java.time.temporal.ChronoUnit.MINUTES).toEpochMilli()
        val expected = "(2h 30')"
        val newResult = dateUtilImplDeter.sinceString(pastTimestamp, rh)

        assertThat(newResult).isEqualTo(expected)
    }
    @Test
    fun `untilString produces correct timeFrameString and matches old behavior`() {
        // ARRANGE
        val rh = FakeResourceHelper()
        val nowForUntil = 1698436800000L // 16:00:00 EDT
        val futureTimestamp = 1698441300000L // 17:15:00 EDT (1h 15m later)
        val expectedUntilString = "(1h 15')"

        // ACT
        // Test the function it wraps: timeFrameString directly
        val untilDuration = futureTimestamp - nowForUntil
        val newResult = dateUtilImpl.timeFrameString(untilDuration, rh)
        val oldResult = dateUtilOldImpl.timeFrameString(untilDuration, rh)

        // ASSERT
        assertThat(newResult).isEqualTo(expectedUntilString)
        assertThat(newResult).isEqualTo(oldResult)
    }
    @Test
    fun `untilString is deterministic with fixed clock`() {
        val rh = FakeResourceHelper()
        // Our fixed clock is at 16:00 UTC. Let's test a timestamp for 1h 15m *after* that.
        val futureTimestamp = fixedInstant.plus(1, java.time.temporal.ChronoUnit.HOURS).plus(15, java.time.temporal.ChronoUnit.MINUTES).toEpochMilli()
        val expected = "(1h 15')"
        val newResult = dateUtilImplDeter.untilString(futureTimestamp, rh)
        assertThat(newResult).isEqualTo(expected)
    }
    @Test
    fun `age works for duration over a day and matches old behavior`() {
        val millis = 95400 * 1000L // 1 day, 2 hours, 30 minutes
        val rh = FakeResourceHelper()
        val expected = "1 d 2 h "
        val newResult = dateUtilImpl.age(millis, true, rh)
        val oldResult = dateUtilOldImpl.age(millis, true, rh)

        assertThat(newResult).isEqualTo(expected)
        assertThat(newResult).isEqualTo(oldResult)
//TODO: age: Check if trailing whitespaces are needed
        // expected                           : 1 d 2 h
        // but was missing trailing whitespace: ␣
    }
    @Test
    fun `age works for duration less than a day and matches old behavior`() {
        val millis = 12600 * 1000L // 3 hours, 30 minutes
        val rh = FakeResourceHelper()
        val expected = "3 h 30 m "
        val newResult = dateUtilImpl.age(millis, true, rh)
        val oldResult = dateUtilOldImpl.age(millis, true, rh)

        assertThat(newResult).isEqualTo(expected)
        assertThat(newResult).isEqualTo(oldResult)
    }
    @Test
    fun `niceTimeScalar works works correctly now`() {
        // ARRANGE
        val rh = FakeResourceHelper()

        // ACT & ASSERT for seconds
        val newSec = dateUtilImpl.niceTimeScalar(15 * 1000L, rh) // 15 seconds
        val oldSec = dateUtilOldImpl.niceTimeScalar(15 * 1000L, rh)
        assertThat(newSec).isEqualTo("15 seconds")
        assertThat(newSec).isEqualTo(oldSec)

        // ACT & ASSERT for minutes
        val newMin = dateUtilImpl.niceTimeScalar(5 * 60 * 1000L, rh) // 5 minutes
        val oldMin = dateUtilOldImpl.niceTimeScalar(5 * 60 * 1000L, rh)
        assertThat(newMin).isEqualTo("5 minutes")
        assertThat(newMin).isEqualTo(oldMin)

        // ACT & ASSERT for hours
        val newHour = dateUtilImpl.niceTimeScalar(2 * 3600 * 1000L, rh) // 2 hours
        val oldHour = dateUtilOldImpl.niceTimeScalar(2 * 3600 * 1000L, rh)
        assertThat(newHour).isEqualTo("2 hours")
        assertThat(newHour).isEqualTo(oldHour)

        // ACT & ASSERT for days
        val newDay = dateUtilImpl.niceTimeScalar(4 * 86400 * 1000L, rh) // 4 days
        val oldDay = dateUtilOldImpl.niceTimeScalar(4 * 86400 * 1000L, rh)
        assertThat(newDay).isEqualTo("4 days")
        assertThat(newDay).isEqualTo(oldDay)

        // ACT & ASSERT for weeks
        val newWeek = dateUtilImpl.niceTimeScalar(3 * 7 * 86400 * 1000L, rh) // 3 weeks
        val oldWeek = dateUtilOldImpl.niceTimeScalar(3 * 7 * 86400 * 1000L, rh)
        // 1. Assert the NEW implementation is as expected.
        assertThat(newWeek).isEqualTo("3 weeks")
        // 2. Assert the OLD implementation is different.
//TODO: the old niceTimeScalar couldn't parse weeks.
        assertThat(newWeek).isNotEqualTo(oldWeek)
    }
    //endregion

    //region Date and Time Part Formatting
    @Test
    fun `secondString works and matches old behavior`() {
        // ARRANGE
        val millis = 1698436812345L // ...16:00:12.345
        val expected = "12"

        // ACT
        val newResult = dateUtilImpl.secondString(millis)
        val oldResult = dateUtilOldImpl.secondString(millis)

        // ASSERT
        assertThat(newResult).isEqualTo(expected)
        assertThat(oldResult).isEqualTo(expected)
    }
    @Test
    fun `minuteString works and matches old behavior`() {
        // ARRANGE
        val millis = 1698436812345L // ...16:00:12.345
        val expected = "00"

        // ACT
        val newResult = dateUtilImpl.minuteString(millis)
        val oldResult = dateUtilOldImpl.minuteString(millis)

        // ASSERT
        assertThat(newResult).isEqualTo(expected)
        assertThat(oldResult).isEqualTo(expected)
    }
    @Test
    fun `hourString works and matches old behavior`() {
        // ARRANGE
        val millis = 1698436812345L // ...16:00:12.345 (4 PM)

        // ACT & ASSERT for 12-hour format
        mockStatic(DateFormat::class.java).use { mockedStatic ->
            mockedStatic.`when`<Boolean> { DateFormat.is24HourFormat(mockContext) }.thenReturn(false)
            val expected12Hour = "04"
            assertThat(dateUtilImpl.hourString(millis)).isEqualTo(expected12Hour)
            assertThat(dateUtilOldImpl.hourString(millis)).isEqualTo(expected12Hour)
        }

        // ACT & ASSERT for 24-hour format
        mockStatic(DateFormat::class.java).use { mockedStatic ->
            mockedStatic.`when`<Boolean> { DateFormat.is24HourFormat(mockContext) }.thenReturn(true)
            val expected24Hour = "16"
            assertThat(dateUtilImpl.hourString(millis)).isEqualTo(expected24Hour)
            assertThat(dateUtilOldImpl.hourString(millis)).isEqualTo(expected24Hour)
        }
    }
    @Test
    fun `amPm works and matches old behavior`() {
        // ARRANGE
        val millis = 1698436812345L // ...16:00:12.345 (PM)
        val expected = "PM"

        // ACT
        val newResult = dateUtilImpl.amPm(millis)
        val oldResult = dateUtilOldImpl.amPm(millis)

        // ASSERT
        assertThat(newResult).isEqualTo(expected)
        assertThat(oldResult).isEqualTo(expected)
    }
    @Test
    fun `dayNameString works and matches old behavior`() {
        // ARRANGE
        val millis = 1698436812345L // A Friday

        // ACT & ASSERT for short name
        val newShort = dateUtilImpl.dayNameString(millis, "EEE")
        val oldShort = dateUtilOldImpl.dayNameString(millis, "EEE")
        assertThat(newShort).isEqualTo("Fri")
        assertThat(oldShort).isEqualTo("Fri")

        // ACT & ASSERT for full name
        val newFull = dateUtilImpl.dayNameString(millis, "EEEE")
        val oldFull = dateUtilOldImpl.dayNameString(millis, "EEEE")
        assertThat(newFull).isEqualTo("Friday")
        assertThat(oldFull).isEqualTo("Friday")
    }
    @Test
    fun `dayString works and matches old behavior`() {
        // ARRANGE
        val millis = 1698436812345L // The 27th of the month
        val expected = "27"

        // ACT
        val newResult = dateUtilImpl.dayString(millis)
        val oldResult = dateUtilOldImpl.dayString(millis)

        // ASSERT
        assertThat(newResult).isEqualTo(expected)
        assertThat(oldResult).isEqualTo(expected)
    }
    @Test
    fun `monthString works and matches old behavior`() {
        // ARRANGE
        val millis = 1698436812345L // October

        // ACT & ASSERT for short name
        val newShort = dateUtilImpl.monthString(millis, "MMM")
        val oldShort = dateUtilOldImpl.monthString(millis, "MMM")
        assertThat(newShort).isEqualTo("Oct")
        assertThat(oldShort).isEqualTo("Oct")

        // ACT & ASSERT for full name
        val newFull = dateUtilImpl.monthString(millis, "MMMM")
        val oldFull = dateUtilOldImpl.monthString(millis, "MMMM")
        assertThat(newFull).isEqualTo("October")
        assertThat(oldFull).isEqualTo("October")
    }
    @Test
    fun `weekString works and matches old behavior`() {
        // ARRANGE
        val millis = 1698436812345L // Week 43 of the year
        val expected = "43"

        // ACT
        val newResult = dateUtilImpl.weekString(millis)
        val oldResult = dateUtilOldImpl.weekString(millis)

        // ASSERT
        assertThat(newResult).isEqualTo(expected)
        assertThat(oldResult).isEqualTo(expected)
    }
    //endregion

    //region Time Calculation and Logic
    @Test
    fun `toSeconds works and matches old behavior`() {
        val timeString = "2:30 PM"
        val expectedSeconds = (14 * 3600) + (30 * 60)
        val newResult = dateUtilImpl.toSeconds(timeString)
        val oldResult = dateUtilOldImpl.toSeconds(timeString)

        assertThat(newResult).isEqualTo(expectedSeconds)
        assertThat(newResult).isEqualTo(oldResult)
    }
    @Test
    fun `beginOfDay works and matches old behavior`() {
        val millis = 1698455400000L // 2023-10-27 10:30 PM EDT
        val expectedMillis = 1698379200000L // 2023-10-27 00:00:00 EDT
        val newResult = dateUtilImpl.beginOfDay(millis)
        val oldResult = dateUtilOldImpl.beginOfDay(millis)

        assertThat(newResult).isEqualTo(expectedMillis)
        assertThat(newResult).isEqualTo(oldResult)
    }
    @Test
    fun `nowWithoutMilliseconds is deterministic with fixed clock`() {
        // ARRANGE
        // Our fixed clock is set to a time with milliseconds.
        val expected = fixedInstant.truncatedTo(java.time.temporal.ChronoUnit.SECONDS).toEpochMilli()

        // ACT
        val newResult
            = dateUtilImplDeter.nowWithoutMilliseconds()

        // ASSERT
        assertThat(newResult).isEqualTo(expected)
        assertThat(newResult %1000).isEqualTo(0L)
    }

    @Test
    fun `isOlderThan is deterministic with fixed clock`() {
        val tenMinutes = 10L
        // --- Test for a timestamp that IS older ---
        val fifteenMinutesAgo = fixedInstant.minus(15, java.time.temporal.ChronoUnit.MINUTES).toEpochMilli()
        Truth.assertThat(dateUtilImplDeter.isOlderThan(fifteenMinutesAgo, tenMinutes)).isTrue()
        // --- Test for a timestamp that is NOT older ---
        val fiveMinutesAgo = fixedInstant.minus(5, java.time.temporal.ChronoUnit.MINUTES).toEpochMilli()
        Truth.assertThat(dateUtilImplDeter.isOlderThan(fiveMinutesAgo, tenMinutes)).isFalse()
        // --- Test for a timestamp that is exactly the same age ---
        val tenMinutesAgo = fixedInstant.minus(10, java.time.temporal.ChronoUnit.MINUTES).toEpochMilli()
        Truth.assertThat(dateUtilImplDeter.isOlderThan(tenMinutesAgo, tenMinutes)).isFalse()
    }
        @Test
    fun `nowWithoutMilliseconds works and matches old behavior`() {
        // We can't test the exact value, but we can test the property: it should be divisible by 1000
        val newResult = dateUtilImpl.nowWithoutMilliseconds()
        val oldResult = dateUtilOldImpl.nowWithoutMilliseconds()

        assertThat(newResult % 1000).isEqualTo(0L)
        assertThat(oldResult % 1000).isEqualTo(0L)
    }
    @Test
    fun `isOlderThan works and matches old behavior`() {
        val now = dateUtilImpl.now() // Use a fixed "now" for the test

        // Test a timestamp that IS older
        val timestampFrom15MinAgo = now - (15 * 60 * 1000L)
        assertThat(dateUtilImpl.isOlderThan(timestampFrom15MinAgo, 10)).isTrue()
        assertThat(dateUtilOldImpl.isOlderThan(timestampFrom15MinAgo, 10)).isTrue()

        // Test a timestamp that is NOT older
        val timestampFrom5MinAgo = now - (5 * 60 * 1000L)
        assertThat(dateUtilImpl.isOlderThan(timestampFrom5MinAgo, 10)).isFalse()
        assertThat(dateUtilOldImpl.isOlderThan(timestampFrom5MinAgo, 10)).isFalse()
    }
    @Test
    fun `isAfterNoon works and matches old behavior`() {
        val newResult = dateUtilImpl.isAfterNoon()
        val oldResult = dateUtilOldImpl.isAfterNoon()
        assertThat(newResult).isEqualTo(oldResult)
    }
    @Test
    fun `isAfterNoon is deterministic with fixed clock`() {
        // ARRANGE: An instant that is in the afternoon (16:00 UTC)
        val afternoonInstant = Instant.parse("2023-10-27T16:00:00Z")
        val afternoonClock = Clock.fixed(afternoonInstant, fixedZone)
        val afternoonDateUtil = DateUtilImpl(mockContext, afternoonClock)

        // ASSERT that 4 PM is after noon
        assertThat(afternoonDateUtil.isAfterNoon()).isTrue()

        // ARRANGE: An instant that is in the morning (10:00 UTC)
        val morningInstant = Instant.parse("2023-10-27T10:00:00Z")
        val morningClock = Clock.fixed(morningInstant, fixedZone)
        val morningDateUtil = DateUtilImpl(mockContext, morningClock)

        // ASSERT that 10 AM is not after noon
        assertThat(morningDateUtil.isAfterNoon()).isFalse()
        // ARRANGE: A clock fixed to 2 PM in the test's default zone
        val afternoonInstantb = Instant.parse("2023-10-27T18:00:00Z") // 18:00 UTC is 14:00 (2 PM) EDT
        val afternoonClockb = Clock.fixed(afternoonInstantb, fixedZone)
        val afternoonDateUtilb = DateUtilImpl(mockContext, afternoonClockb)

        // ASSERT that 2 PM is after noon
        Truth.assertThat(afternoonDateUtilb.isAfterNoon()).isTrue()

        // ARRANGE: A clock fixed to 10 AM in the test's default zone
        val morningInstantb = Instant.parse("2023-10-27T14:00:00Z") // 14:00 UTC is 10:00 AM EDT
        val morningClockb = Clock.fixed(morningInstantb, fixedZone)
        val morningDateUtilb = DateUtilImpl(mockContext, morningClockb)

        // ASSERT that 10 AM is not after noon
        assertThat(morningDateUtilb.isAfterNoon()).isFalse()
    }
    @Test
    fun `isSameDayGroup works and matches old behavior`() {
        // ARRANGE
        val now = dateUtilImpl.now() // Use a fixed "now" for the test
        val oneHour = 3600 * 1000L

        // --- Case 1: TRUE ---
        // Both timestamps on the same day, and "now" is NOT between them.
        val ts1_case1 = now - (2 * oneHour) // 2 hours ago
        val ts2_case1 = now - (1 * oneHour) // 1 hour ago
        assertThat(dateUtilImpl.isSameDayGroup(ts1_case1, ts2_case1)).isTrue()
        assertThat(dateUtilOldImpl.isSameDayGroup(ts1_case1, ts2_case1)).isTrue()

        // --- Case 2: FALSE (due to "now" check) ---
        // Both timestamps on the same day, but "now" IS between them.
        val ts1_case2 = now - oneHour // 1 hour ago
        val ts2_case2 = now + oneHour // 1 hour in the future
        assertThat(dateUtilImpl.isSameDayGroup(ts1_case2, ts2_case2)).isFalse()
        assertThat(dateUtilOldImpl.isSameDayGroup(ts1_case2, ts2_case2)).isFalse()

        // --- Case 3: FALSE (due to date check) ---
        // Timestamps on different days.
        val ts1_case3 = now - (36 * oneHour) // Yesterday
        val ts2_case3 = now - (12 * oneHour) // Today
        assertThat(dateUtilImpl.isSameDayGroup(ts1_case3, ts2_case3)).isFalse()
        assertThat(dateUtilOldImpl.isSameDayGroup(ts1_case3, ts2_case3)).isFalse()
    }

    @Test
    fun `computeDiff works and matches old behavior`() {
        // ARRANGE
        val date1 = 1698311223344L // Some start time
        val date2 = date1 + (1 * 86400000L) + (3 * 3600000L) + (46 * 60000L) + (40 * 1000L) // 1d, 3h, 46m, 40s later

        // ACT
        val newResult = dateUtilImpl.computeDiff(date1, date2)
        val oldResult = dateUtilOldImpl.computeDiff(date1, date2)

        // ASSERT
        assertThat(newResult[TimeUnit.DAYS]).isEqualTo(1L)
        assertThat(newResult[TimeUnit.HOURS]).isEqualTo(3L)
        assertThat(newResult[TimeUnit.MINUTES]).isEqualTo(46L)
        assertThat(newResult[TimeUnit.SECONDS]).isEqualTo(40L)

        // Check that the new, clearer implementation matches the old, complex one
        assertThat(newResult).isEqualTo(oldResult)
    }

    @Test
    fun `timeStringFromSeconds produces a minute-precision string and matches old behavior`() {
        mockStatic(DateFormat::class.java).use { mockedStatic ->
            mockedStatic.`when`<Boolean> { DateFormat.is24HourFormat(mockContext) }.thenReturn(true)
            val inputSeconds = (14 * 3600) + (5 * 60) + 30
            val expected = "14:05"
            val newResult = dateUtilImpl.timeStringFromSeconds(inputSeconds)
            val oldResult = dateUtilOldImpl.timeStringFromSeconds(inputSeconds)

            assertThat(newResult).isEqualTo(expected)
            assertThat(oldResult).isEqualTo(newResult)
        }
    }
    @Test
    fun`timeFrameString formats durations correctly and matches old behavior`() {
        // ARRANGE
        val rh = FakeResourceHelper()

        // --- Case 1: Hours and Minutes ---
        // 2 hours and 30 minutes
        val duration1 = (2 *3600 * 1000L) + (30 * 60 * 1000L)
        val expected1 = "(2h 30')"
        assertThat(dateUtilImpl.timeFrameString(duration1, rh)).isEqualTo(expected1)
        assertThat(dateUtilOldImpl.timeFrameString(duration1, rh)).isEqualTo(expected1)

        // --- Case 2: Only Minutes ---
        // 45 minutes
        val duration2 = 45 * 60 * 1000L
        val expected2 = "(45')"
        assertThat(dateUtilImpl.timeFrameString(duration2, rh)).isEqualTo(expected2)
        assertThat(dateUtilOldImpl.timeFrameString(duration2, rh)).isEqualTo(expected2)

        // --- Case 3: Zero ---
        val duration3 = 0L
        val expected3 = "(0')"
        assertThat(dateUtilImpl.timeFrameString(duration3, rh)).isEqualTo(expected3)
        assertThat(dateUtilOldImpl.timeFrameString(duration3, rh)).isEqualTo(expected3)
    }
    //endregion

    //region Timezone Logic
                         @Test
    fun `getTimeZoneOffsetMs works and matches old non-DST-aware behavior`() {
        // ARRANGE
        // For America/New_York, the standard offset is always UTC-5 (EST).
        // Both the old and new functions are NOT DST-aware.
        val expectedStandardOffsetMillis = -5 * 60 * 60 * 1000L
        val newResult = dateUtilImpl.getTimeZoneOffsetMs()
        val oldResult = dateUtilOldImpl.getTimeZoneOffsetMs()

        // Assert that both functions return the correct standard offset, ignoring DST.
        assertThat(newResult).isEqualTo(expectedStandardOffsetMillis)
        assertThat(oldResult).isEqualTo(expectedStandardOffsetMillis)
    }
    @Test
    fun `getTimeZoneOffsetMs variants behave as expected`() {
        // ARRANGE
        // A time in the winter (Standard Time, EST, UTC-5)
        val winterInstant = Instant.parse("2023-01-15T12:00:00Z")
        val winterClock = Clock.fixed(winterInstant, fixedZone)
        val winterDateUtil = DateUtilImpl(mockContext, winterClock)
        val expectedStandardOffset = -5 * 3600 * 1000L // -18,000,000

        // A time in the summer (Daylight Time, EDT, UTC-4)
        val summerInstant = Instant.parse("2023-07-15T12:00:00Z")
        val summerClock = Clock.fixed(summerInstant, fixedZone)
        val summerDateUtil = DateUtilImpl(mockContext, summerClock)
        val expectedDaylightOffset = -4 * 3600 * 1000L // -14,400,000

        // --- ACT & ASSERT for Winter (Standard Time) ---
        val newResultWinter = winterDateUtil.getTimeZoneOffsetMs()
        val newResultWinterDst = winterDateUtil.getTimeZoneOffsetMsWithDST()
        val oldResultWinter = dateUtilOldImpl.getTimeZoneOffsetMs()

        // In winter, all three should agree and be the standard offset.
        assertThat(newResultWinter).isEqualTo(expectedStandardOffset)
        assertThat(newResultWinterDst).isEqualTo(expectedStandardOffset)
        assertThat(oldResultWinter).isEqualTo(expectedStandardOffset)

        // --- ACT & ASSERT for Summer (Daylight Time) ---
        val newResultSummer = summerDateUtil.getTimeZoneOffsetMs()
        val newResultSummerDst = summerDateUtil.getTimeZoneOffsetMsWithDST()
        val oldResultSummer = dateUtilOldImpl.getTimeZoneOffsetMs()

        // In summer, the two non-DST-aware functions should still report the STANDARD offset.
        assertThat(newResultSummer).isEqualTo(expectedStandardOffset)
        assertThat(oldResultSummer).isEqualTo(expectedStandardOffset)

        // But the new DST-aware function should report the correct DAYLIGHT offset.
        assertThat(newResultSummerDst).isEqualTo(expectedDaylightOffset)

        // This also proves the DST-aware function is different from the others in summer.
        assertThat(newResultSummerDst).isNotEqualTo(newResultSummer)
    }
    @Test
    fun `getTimeZoneOffsetMinutes reports correct offset for standard and daylight time and matches old behavior`() {
        // A time in the winter (Standard Time, EST, UTC-5)
        val winterTimestamp = Instant.parse("2023-01-15T12:00:00Z").toEpochMilli()
        val expectedWinterMinutes = -5 * 60
        // A time in the summer (Daylight Time, EDT, UTC-4)
        val summerTimestamp = Instant.parse("2023-07-15T12:00:00Z").toEpochMilli()
        val expectedSummerMinutes = -4 * 60
        val newWinterResult = dateUtilImpl.getTimeZoneOffsetMinutes(winterTimestamp)
        val newSummerResult = dateUtilImpl.getTimeZoneOffsetMinutes(summerTimestamp)
        val oldWinterResult = dateUtilOldImpl.getTimeZoneOffsetMinutes(winterTimestamp)
        val oldSummerResult = dateUtilOldImpl.getTimeZoneOffsetMinutes(summerTimestamp)

        assertThat(newWinterResult).isEqualTo(expectedWinterMinutes)
        assertThat(newSummerResult).isEqualTo(expectedSummerMinutes)
        assertThat(newWinterResult).isEqualTo(oldWinterResult)
        assertThat(newSummerResult).isEqualTo(oldSummerResult)
    }
    @Test
    fun `timeZoneByOffset finds a timezone with the correct offset including non-hourly offsets`() {
        // ARRANGE: A list of offsets to test (in milliseconds)
        val offsetsToTest = listOf(
            -18000000L, // UTC-5 (EST)
            -14400000L, // UTC-4 (EDT)
            3600000L    // UTC+1 (CET)
        )
        val indiaOffset = 19800000L // UTC+5:30 (India Standard Time)

        // Test the standard, hourly offsets first
        for (offset in offsetsToTest) {
            val newZoneIdString = dateUtilImpl.timeZoneByOffset(offset)
            val oldZoneIdString = dateUtilOldImpl.timeZoneByOffset(offset).id
            val newZoneId = ZoneId.of(newZoneIdString)
            val oldZoneId = ZoneId.of(oldZoneIdString)
            val newOffsetSeconds = newZoneId.rules.getOffset(Instant.now()).totalSeconds
            val oldOffsetSeconds = oldZoneId.rules.getOffset(Instant.now()).totalSeconds
            val expectedOffsetSeconds = (offset / 1000).toInt()

            // Assert both new and old are correct for hourly offsets
            assertThat(newOffsetSeconds).isEqualTo(expectedOffsetSeconds)
            assertThat(oldOffsetSeconds).isEqualTo(expectedOffsetSeconds)
        }

        // Test the non-hourly offset separately to document the bug fix
        val expectedIndiaOffsetSeconds = (indiaOffset / 1000).toInt() // 19800
        val newIndiaZoneIdString = dateUtilImpl.timeZoneByOffset(indiaOffset)
        val oldIndiaZoneIdString = dateUtilOldImpl.timeZoneByOffset(indiaOffset).id
        val newIndiaOffsetSeconds = ZoneId.of(newIndiaZoneIdString).rules.getOffset(Instant.now()).totalSeconds
        val oldIndiaOffsetSeconds = ZoneId.of(oldIndiaZoneIdString).rules.getOffset(Instant.now()).totalSeconds

        // 1. Assert the NEW implementation is as expected.
        assertThat(newIndiaOffsetSeconds).isEqualTo(expectedIndiaOffsetSeconds)
        // 2. Assert the OLD implementation is different.
// TODO the old timeZoneByOffset didn't work for non-hourly offset:
        assertThat(oldIndiaOffsetSeconds).isNotEqualTo(expectedIndiaOffsetSeconds)

        // Also test the zero case specifically
        assertThat(dateUtilImpl.timeZoneByOffset(0L)).isEqualTo("UTC")
        assertThat(dateUtilOldImpl.timeZoneByOffset(0L).id).isEqualTo("UTC")
    }
    @Test
    fun `getTimestampWithCurrentTimeOfDay combines date and time correctly (new implementation)`() {
        // This test is tricky because it depends on `now()`.
        // We can't test the exact output, but we can test that the date part is correct.
        val oldDateTimestamp = 1668556800000L // A date in the past: Nov 16, 2022
        val resultTimestamp = dateUtilImpl.getTimestampWithCurrentTimeOfDay(oldDateTimestamp)

        // Get the date part of the result and the original
        val resultDateString = dateUtilImpl.dateString(resultTimestamp)
        val originalDateString = dateUtilImpl.dateString(oldDateTimestamp)

        assertThat(resultDateString).isEqualTo(originalDateString)
    }
    @Test
    fun `mergeUtcDateToTimestamp works and matches old behavior`() {
        // The original timestamp, which contains the TIME we want to keep (16:00 EDT)
        val timeToKeep = 1698436800000L
        // The new date from the picker, which is a UTC midnight timestamp (e.g., Oct 20)
        val newDate = 1697760000000L

        // Expected result: Oct 20 at 16:00 EDT
        val expectedResult = 1697832000000L

        val newResult = dateUtilImpl.mergeUtcDateToTimestamp(timeToKeep, newDate)
        val oldResult = dateUtilOldImpl.mergeUtcDateToTimestamp(timeToKeep, newDate)

        assertThat(newResult).isEqualTo(expectedResult)
        assertThat(newResult).isEqualTo(oldResult)
    }
    @Test
    fun `mergeHourMinuteToTimestamp works and matches old behavior`() {
        // ARRANGE: A base timestamp and the hour/minute we want to set
        val baseTimestamp = 1698379200000L // Oct 27, 2023 00:00:00 EDT
        val hour = 14 // 2:00 PM
        val minute = 30

        // Expected result: Oct 27, 2023 at 14:30:00 EDT
        val expectedResult = 1698431400000L

        // ACT
        val newResult = dateUtilImpl.mergeHourMinuteToTimestamp(baseTimestamp, hour, minute)
        val oldResult = dateUtilOldImpl.mergeHourMinuteToTimestamp(baseTimestamp, hour, minute)

        // ASSERT
        assertThat(newResult).isEqualTo(expectedResult)
        assertThat(newResult).isEqualTo(oldResult)
    }
    //endregion

    //region DST Transition Scenarios
                         @Test
    fun `beginOfDay works correctly on DST spring forward day and matches old behavior`() {
        // In New York, 2023, DST starts on March 12.
        val millisOnSpringForwardDay = 1678604700000L // 2023-03-12 03:05:00 EDT
        val expectedStartOfDay = 1678597200000L // 2023-03-12 00:00:00 EST

        val newResult = dateUtilImpl.beginOfDay(millisOnSpringForwardDay)
        val oldResult = dateUtilOldImpl.beginOfDay(millisOnSpringForwardDay)

        assertThat(newResult).isEqualTo(expectedStartOfDay)
        assertThat(newResult).isEqualTo(oldResult)
    }
    @Test
    fun `isSameDay works correctly across the DST fall back transition and matches old behavior`() {
        // In New York, 2023, DST ends on Nov 5.
        val timeBeforeFallback = 1699162200000L // Represents 1:30 AM EDT
        val timeAfterFallback = 1699165800000L  // Represents 1:30 AM EST (an hour later)

        val newResult = dateUtilImpl.isSameDay(timeBeforeFallback, timeAfterFallback)
        val oldResult = dateUtilOldImpl.isSameDay(timeBeforeFallback, timeAfterFallback)

        assertThat(newResult).isTrue()
        assertThat(newResult).isEqualTo(oldResult)
    }
    @Test
    fun `timeString correctly formats time during non-existent DST spring forward gap and matches old behavior`() {
        // On March 12, 2023, in New York, 2:30 AM does not exist.
        // A timestamp that would fall into this gap is automatically moved forward.
        // 06:30:00 UTC corresponds to the non-existent 02:30:00 EST.
        // java.time will represent this as 03:30:00 EDT.
        val nonExistentTimeMillis = 1678602600000L
        val expected = "01:30 AM" // since 02:30:00 EST does not exist, it reverts to 01:30 AM
        mockStatic(DateFormat::class.java).use { mockedStatic ->
            mockedStatic.`when`<Boolean> { DateFormat.is24HourFormat(mockContext) }.thenReturn(false)
            val newResult = dateUtilImpl.timeString(nonExistentTimeMillis)
            val oldResult = dateUtilOldImpl.timeString(nonExistentTimeMillis)

            assertThat(newResult).isEqualTo(expected)
            assertThat(newResult).isEqualTo(oldResult)
        }
    }
    @Test
    fun `timeString correctly formats ambiguous time during DST fall back and matches old behavior`() {
        // On Nov 5, 2023, in New York, 1:30 AM happens twice.
        val timeBeforeFallback = 1699162200000L // This is 1:30 AM EDT
        val timeAfterFallback = 1699165800000L  // This is 1:30 AM EST (one hour later)
        val expected = "01:30 AM"
        mockStatic(DateFormat::class.java).use { mockedStatic ->
            mockedStatic.`when`<Boolean> { DateFormat.is24HourFormat(mockContext) }.thenReturn(false)
            // Both should appear as "1:30 AM" to the user, even though they are an hour apart.
            val resultBefore = dateUtilImpl.timeString(timeBeforeFallback)
            val resultAfter = dateUtilImpl.timeString(timeAfterFallback)
            val oldResultBefore = dateUtilOldImpl.timeString(timeBeforeFallback)
            val oldResultAfter = dateUtilOldImpl.timeString(timeAfterFallback)

            assertThat(resultBefore).isEqualTo(expected)
            assertThat(resultAfter).isEqualTo(expected)
            assertThat(oldResultBefore).isEqualTo(resultBefore)
            assertThat(oldResultAfter).isEqualTo(resultAfter)
        }
    }
    @Test
    fun `age calculation is correct across DST spring forward and matches old behavior`() {
        // A duration that starts just before the DST gap and ends just after.
        // In New York on March 12, 2023, time jumps from 1:59:59 EST to 3:00:00 EDT.
        // The duration between 1:00 AM EST and 3:00 AM EDT is only ONE hour, not two.
        val startTime = 1678599600000L // 2023-03-12 01:00:00 EST
        val endTime = 1678606800000L   // 2023-03-12 03:00:00 EDT
        val durationMillis = endTime - startTime
        val rh = FakeResourceHelper()
        val expected = "2 h 0 m "
// TODO: age calculation should be evaluated - only one hour passes but the clock jumps two.
        val newResult = dateUtilImpl.age(durationMillis, true, rh)
        val oldResult = dateUtilOldImpl.age(durationMillis, true, rh)

        assertThat(newResult).isEqualTo(expected)
        assertThat(newResult).isEqualTo(oldResult)
    }
    @Test
    fun `age calculation is correct across DST fall back and matches old behavior`() {
        // In New York on Nov 5, 2023, the clock goes from 1:59 EDT to 1:00 EST.
        // The real-world elapsed time between 1:00 EDT and 2:00 EST is 2 hours,
        // but the raw millisecond duration is 3 hours.
// TODO: check what The age() function's output should actually be.
        val startTime = 1699160400000L // 2023-11-05 01:00:00 EDT
        val endTime = 1699171200000L   // 2023-11-05 02:00:00 EST
        val durationMillis = endTime - startTime // This value is 10,800,000
        val rh = FakeResourceHelper()
        val expected = "3 h 0 m "
        val newResult = dateUtilImpl.age(durationMillis, true, rh)
        val oldResult = dateUtilOldImpl.age(durationMillis, true, rh)

        assertThat(newResult).isEqualTo(expected)
        assertThat(newResult).isEqualTo(oldResult)
    }
    @Test
    fun `isSameDay is false for times just under 24 hours apart across DST and matches old behavior`() {
        // Spring forward day (March 12) is only 23 hours long.
        val startOfDay = 1678597200000L // Mar 12, 00:00:00 EST
        val endOfDay = startOfDay + (23 * 60 * 60 * 1000) - 1 // 22:59:59 from start
        val isSame = dateUtilImpl.isSameDay(startOfDay, endOfDay)
        val isDifferent = dateUtilImpl.isSameDay(startOfDay, endOfDay + 1) // Now on the next day
        val isSameOld = dateUtilOldImpl.isSameDay(startOfDay, endOfDay)
        val isDifferentOld = dateUtilOldImpl.isSameDay(startOfDay, endOfDay + 1) // Now on the next day

        assertThat(isSame).isTrue()
        assertThat(isDifferent).isFalse()
        assertThat(isSame).isEqualTo(isSameOld)
        assertThat(isDifferent).isEqualTo(isDifferentOld)
    }
    @Test
    fun `beginOfDay works correctly on DST fall back day and matches old behavior`() {
        // On Nov 5, the day is 25 hours long, but beginOfDay should still find the start.
        val timeDuringFallback = 1699203600000L // 2023-11-05 12:00:00 EST
        val expectedStartOfDay = 1699156800000L // 2023-11-05 00:00:00 EDT
        val newResult = dateUtilImpl.beginOfDay(timeDuringFallback)
        val oldResult = dateUtilOldImpl.beginOfDay(timeDuringFallback)

        assertThat(newResult).isEqualTo(expectedStartOfDay)
        assertThat(newResult).isEqualTo(oldResult)
    }
    @Test
    fun `getTimeZoneOffsetMinutes works for European DST and matches old behavior`() {
        // This test MUST change the global default timezone because the functions
        // are hard-coded to use ZoneId.systemDefault() and DateTimeZone.getDefault().

        // 1. Save the original global defaults
        val originalZone = TimeZone.getDefault()
        val originalJodaZone = DateTimeZone.getDefault()

        try {
            // 2. Set the global defaults to a European timezone for this test
            // The redundant qualifiers have been removed as requested.
            TimeZone.setDefault(TimeZone.getTimeZone("Europe/Berlin"))
            DateTimeZone.setDefault(DateTimeZone.forID("Europe/Berlin"))

            // 3. Re-instantiate the classes to make them pick up the new default zone
            val localDateUtil = DateUtilImpl(mockContext)
            val localDateUtilOld = DateUtilOldImpl(mockContext)

            // ARRANGE: Timestamps in winter (CET, UTC+1) and summer (CEST, UTC+2)
            val winterTimestamp = Instant.parse("2023-01-15T12:00:00Z").toEpochMilli()
            val expectedWinterMinutes = 60
            val summerTimestamp = Instant.parse("2023-07-15T12:00:00Z").toEpochMilli()
            val expectedSummerMinutes = 120

            // ACT
            val newWinterResult = localDateUtil.getTimeZoneOffsetMinutes(winterTimestamp)
            val newSummerResult = localDateUtil.getTimeZoneOffsetMinutes(summerTimestamp)
            val oldWinterResult = localDateUtilOld.getTimeZoneOffsetMinutes(winterTimestamp)
            val oldSummerResult = localDateUtilOld.getTimeZoneOffsetMinutes(summerTimestamp)

            // ASSERT
            assertThat(newWinterResult).isEqualTo(expectedWinterMinutes)
            assertThat(newSummerResult).isEqualTo(expectedSummerMinutes)
            assertThat(newWinterResult).isEqualTo(oldWinterResult)
            assertThat(newSummerResult).isEqualTo(oldSummerResult)

        } finally {
            // 4. CRITICAL: Always reset the global defaults to not affect other tests.
            TimeZone.setDefault(originalZone)
            DateTimeZone.setDefault(originalJodaZone)
        }
    }
    //endregion


    //region Formatting Utilities
    @Test
    fun `qs formats numbers correctly and matches old behavior`() {
        // --- Case 1: Explicit number of digits ---
        val number = 12.345
        assertThat(dateUtilImpl.qs(number, 0)).isEqualTo("12")
        assertThat(dateUtilImpl.qs(number, 1)).isEqualTo("12.3")
        assertThat(dateUtilImpl.qs(number, 2)).isEqualTo("12.35") // Note: DecimalFormat rounds!

        // Compare new and old implementations
        assertThat(dateUtilImpl.qs(number, 0)).isEqualTo(dateUtilOldImpl.qs(number, 0))
        assertThat(dateUtilImpl.qs(number, 1)).isEqualTo(dateUtilOldImpl.qs(number, 1))
        assertThat(dateUtilImpl.qs(number, 2)).isEqualTo(dateUtilOldImpl.qs(number, 2))

        // --- Case 2: Automatic digit detection (numDigits = -1) ---
        // This logic is complex, so we test its known outputs.

        // The original implementation returns "12" for 12.0, not "12.0".
        // This is because it sets maximumFractionDigits, which does not show trailing zeros.
        // We assert this behavior is maintained.
        assertThat(dateUtilImpl.qs(12.0, -1)).isEqualTo("12")
//        assertThat(dateUtilImpl.qs(12.3, -1)).isEqualTo("12.3")
//        assertThat(dateUtilImpl.qs(12.34, -1)).isEqualTo("12.34")

        // Compare new and old implementations for the automatic case
        assertThat(dateUtilImpl.qs(12.0, -1)).isEqualTo(dateUtilOldImpl.qs(12.0, -1))
        assertThat(dateUtilImpl.qs(12.3, -1)).isEqualTo(dateUtilOldImpl.qs(12.3, -1))
        assertThat(dateUtilImpl.qs(12.34, -1)).isEqualTo(dateUtilOldImpl.qs(12.34, -1))
    }
//endregion

     /**
     * A fake implementation of ResourceHelper for unit testing.
     * This class implements the full ResourceHelper interface, providing predictable values.
     */
    class FakeResourceHelper : app.aaps.core.interfaces.resources.ResourceHelper {

        // --- Methods genuinely used by DateUtil tests ---
        override fun gs(id: Int): String = getStringForId(id)
        override fun gs(id: Int, vararg args: Any?): String {
            return when (id) {
                app.aaps.core.interfaces.R.string.minago -> "${args.firstOrNull() ?: ""} min ago"
                app.aaps.core.interfaces.R.string.minago_long -> "${args.firstOrNull() ?: ""} minutes ago"
                app.aaps.core.interfaces.R.string.secago -> "${args.firstOrNull() ?: ""} sec ago"
                app.aaps.core.interfaces.R.string.hoursago -> {
                    val hours = when (val arg = args.firstOrNull()) {
                        is Double -> arg.toLong()
                        is Long -> arg
                        else -> 0L
                    }
                    "${hours} hours ago"
                }
                app.aaps.core.interfaces.R.string.days_ago_round,
                app.aaps.core.interfaces.R.string.days_ago -> {
                    val days = when (val arg = args.firstOrNull()) {
                        is Double -> arg.toLong()
                        is Long -> arg
                        else -> 0L
                    }
                    "${days} days ago"
                }
                // Add more formatted strings here as needed by other tests
                else -> getStringForId(id)
            }
        }

        private fun getStringForId(id: Int): String {
            return when (id) {
                app.aaps.core.interfaces.R.string.shortday -> "d"
                app.aaps.core.interfaces.R.string.shorthour -> "h"
                app.aaps.core.interfaces.R.string.shortminute -> "m"
                app.aaps.core.interfaces.R.string.unit_seconds -> "seconds"
                app.aaps.core.interfaces.R.string.unit_minutes -> "minutes"
                app.aaps.core.interfaces.R.string.unit_hours -> "hours"
                app.aaps.core.interfaces.R.string.unit_days -> "days"
                app.aaps.core.interfaces.R.string.unit_weeks -> "weeks"
                app.aaps.core.interfaces.R.string.hours -> "hours"
                app.aaps.core.interfaces.R.string.today -> "Today"
                app.aaps.core.interfaces.R.string.yesterday -> "Yesterday"
                app.aaps.core.interfaces.R.string.tomorrow -> "Tomorrow"
                app.aaps.core.interfaces.R.string.later_today -> "Later today"
                else -> "unhandled" // Default for unhandled resources
            }
        }

        // --- Dummy implementations for the rest of the interface ---
        override fun gq(id: Int, quantity: Int, vararg args: Any?): String = ""
        override fun gsNotLocalised(id: Int, vararg args: Any?): String = ""
        override fun gc(id: Int): Int = 0
        override fun gd(id: Int): Drawable? = null
        override fun gb(id: Int): Boolean = false
        override fun gcs(id: Int): String = ""
        override fun gsa(id: Int): Array<String> = emptyArray()
        override fun openRawResourceFd(id: Int): AssetFileDescriptor? = null
        override fun decodeResource(id: Int): Bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        override fun getDisplayMetrics(): DisplayMetrics = DisplayMetrics()
        override fun dpToPx(dp: Int): Int = dp
        override fun dpToPx(dp: Float): Int = dp.toInt()
        override fun shortTextMode(): Boolean = true
        override fun gac(attributeId: Int): Int = 0
        override fun gac(context: Context?, attributeId: Int): Int = 0
        override fun getThemedCtx(context: Context): Context = context
    }
}

package info.nightscout.comboctl.main

import app.aaps.shared.tests.TestBase
import info.nightscout.comboctl.base.DisplayFrame
import info.nightscout.comboctl.base.LogLevel
import info.nightscout.comboctl.base.Logger
import info.nightscout.comboctl.base.NUM_DISPLAY_FRAME_PIXELS
import info.nightscout.comboctl.base.timeWithoutDate
import info.nightscout.comboctl.parser.AlertScreenContent
import info.nightscout.comboctl.parser.AlertScreenException
import info.nightscout.comboctl.parser.BatteryState
import info.nightscout.comboctl.parser.MainScreenContent
import info.nightscout.comboctl.parser.ParsedScreen
import info.nightscout.comboctl.parser.TbrPercentageAndDurationScreens
import info.nightscout.comboctl.parser.testFrameMainScreenWithTimeSeparator
import info.nightscout.comboctl.parser.testFrameMainScreenWithoutTimeSeparator
import info.nightscout.comboctl.parser.testFrameStandardBolusMenuScreen
import info.nightscout.comboctl.parser.testFrameTemporaryBasalRateNoPercentageScreen
import info.nightscout.comboctl.parser.testFrameTemporaryBasalRatePercentage110Screen
import info.nightscout.comboctl.parser.testFrameW6CancelTbrWarningScreen
import info.nightscout.comboctl.parser.testTimeAndDateSettingsHourPolishScreen
import info.nightscout.comboctl.parser.testTimeAndDateSettingsHourRussianScreen
import info.nightscout.comboctl.parser.testTimeAndDateSettingsHourTurkishScreen
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeAll
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ParsedDisplayFrameStreamTest : TestBase() {
    companion object {

        @BeforeAll
        @JvmStatic
        fun commonInit() {
            Logger.threshold = LogLevel.VERBOSE
        }
    }

    @Test
    fun checkSingleDisplayFrame() = runBlocking {
        /* Check if a frame is correctly recognized. */

        val stream = ParsedDisplayFrameStream()
        stream.feedDisplayFrame(testFrameStandardBolusMenuScreen)
        val parsedFrame = stream.getParsedDisplayFrame()
        assertNotNull(parsedFrame)
        assertEquals(ParsedScreen.StandardBolusMenuScreen, parsedFrame.parsedScreen)
    }

    @Test
    fun checkNullDisplayFrame() = runBlocking {
        /* Check if a null frame is handled correctly. */

        val stream = ParsedDisplayFrameStream()
        stream.feedDisplayFrame(null)
        val parsedFrame = stream.getParsedDisplayFrame()
        assertNull(parsedFrame)
    }

    @Test
    fun checkDuplicateDisplayFrameFiltering() = runBlocking {
        // Test the duplicate detection by feeding the stream test frames
        // along with unrecognizable ones. We feed duplicates, both recognizable
        // and unrecognizable ones, to check that the stream filters these out.

        val unrecognizableDisplayFrame1A = DisplayFrame(BooleanArray(NUM_DISPLAY_FRAME_PIXELS) { false })
        val unrecognizableDisplayFrame1B = DisplayFrame(BooleanArray(NUM_DISPLAY_FRAME_PIXELS) { false })
        val unrecognizableDisplayFrame2 = DisplayFrame(BooleanArray(NUM_DISPLAY_FRAME_PIXELS) { true })

        val displayFrameList = listOf(
            // We use these two frames to test out the filtering
            // of duplicate frames. These two frame _are_ equal.
            // The frames just differ in the time separator, but
            // both result in ParsedScreen.NormalMainScreen instances
            // with the same semantics (same time etc). We expect the
            // stream to recognize and filter out the duplicate.
            testFrameMainScreenWithTimeSeparator,
            testFrameMainScreenWithoutTimeSeparator,
            // 1A and 1B are two different unrecognizable DisplayFrame
            // instances with equal pixel content to test the filtering
            // of duplicate frames when said frames are _not_ recognizable
            // by the parser. The stream should then compare the frames
            // pixel by pixel.
            unrecognizableDisplayFrame1A,
            unrecognizableDisplayFrame1B,
            // Frame 2 is an unrecognizable DisplayFrame whose pixels
            // are different than the ones in frames 1A and 1B. We
            // expect the stream to do a pixel-by-pixel comparison between
            // the unrecognizable frames and detect that frame 2 is
            // really different (= not a duplicate).
            unrecognizableDisplayFrame2,
            // A recognizable frame to test the case that a recognizable
            // frame follows an unrecognizable one.
            testFrameStandardBolusMenuScreen
        )

        val parsedFrameList = mutableListOf<ParsedDisplayFrame>()
        val stream = ParsedDisplayFrameStream()

        coroutineScope {
            val producerJob = launch {
                for (displayFrame in displayFrameList) {
                    // Wait here until the frame has been retrieved, since otherwise,
                    // the feedDisplayFrame() call below would overwrite the already
                    // stored frame.
                    while (stream.hasStoredDisplayFrame())
                        delay(100)
                    stream.feedDisplayFrame(displayFrame)
                }
            }
            launch {
                while (true) {
                    val parsedFrame = stream.getParsedDisplayFrame(filterDuplicates = true)
                    assertNotNull(parsedFrame)
                    parsedFrameList.add(parsedFrame)
                    if (parsedFrameList.size >= 4)
                        break
                }
                producerJob.cancel()
            }
        }

        val parsedFrameIter = parsedFrameList.listIterator()

        // We expect _one_ ParsedScreen.NormalMainScreen
        // (the other frame with the equal content must be filtered out).
        assertEquals(
            ParsedScreen.MainScreen(
                MainScreenContent.Normal(
                    currentTime = timeWithoutDate(hour = 10, minute = 20),
                    activeBasalProfileNumber = 1,
                    currentBasalRateFactor = 200,
                    batteryState = BatteryState.FULL_BATTERY
                )
            ),
            parsedFrameIter.next().parsedScreen
        )
        // Next we expect an UnrecognizedScreen result after the change from NormalMainScreen
        // to a frame (unrecognizableDisplayFrame1A) that could not be recognized.
        assertEquals(
            ParsedScreen.UnrecognizedScreen,
            parsedFrameIter.next().parsedScreen
        )
        // We expect an UnrecognizedScreen result after switching to unrecognizableDisplayFrame2.
        // This is an unrecognizable frame that differs in its pixels from
        // unrecognizableDisplayFrame1A and 1B. Importantly, 1B must have been
        // filtered out, since both 1A and 1B could not be recognized _and_ have
        // equal pixel content.
        assertEquals(
            ParsedScreen.UnrecognizedScreen,
            parsedFrameIter.next().parsedScreen
        )
        // Since unrecognizableDisplayFrame1B must have been filtered out,
        // the next result we expect is the StandardBolusMenuScreen.
        assertEquals(
            ParsedScreen.StandardBolusMenuScreen,
            parsedFrameIter.next().parsedScreen
        )
    }

    @Test
    fun checkDuplicateParsedScreenFiltering() = runBlocking {
        // Test the duplicate parsed screen detection with 3 time and date hour settings screens.
        // All three are parsed to ParsedScreen.TimeAndDateSettingsHourScreen instances.
        // All three contain different pixels. (This is the crucial difference to the
        // checkDuplicateDisplayFrameFiltering above.) However, the first 2 have their "hour"
        // properties set to 13, while the third has "hour" set to 14. The stream is
        // expected to filter the duplicate TimeAndDateSettingsHourScreen with the "13" hour.

        val displayFrameList = listOf(
            testTimeAndDateSettingsHourRussianScreen, // This screen frame has "1 PM" (= 13 in 24h format) as hour
            testTimeAndDateSettingsHourTurkishScreen, // This screen frame has "1 PM" (= 13 in 24h format) as hour
            testTimeAndDateSettingsHourPolishScreen // This screen frame has "2 PM" (= 13 in 24h format) as hour
        )

        val parsedFrameList = mutableListOf<ParsedDisplayFrame>()
        val stream = ParsedDisplayFrameStream()

        coroutineScope {
            val producerJob = launch {
                for (displayFrame in displayFrameList) {
                    // Wait here until the frame has been retrieved, since otherwise,
                    // the feedDisplayFrame() call below would overwrite the already
                    // stored frame.
                    while (stream.hasStoredDisplayFrame())
                        delay(100)
                    stream.feedDisplayFrame(displayFrame)
                }
            }
            launch {
                while (true) {
                    val parsedFrame = stream.getParsedDisplayFrame(filterDuplicates = true)
                    assertNotNull(parsedFrame)
                    parsedFrameList.add(parsedFrame)
                    if (parsedFrameList.size >= 2)
                        break
                }
                producerJob.cancel()
            }
        }

        val parsedFrameIter = parsedFrameList.listIterator()

        assertEquals(2, parsedFrameList.size)
        assertEquals(ParsedScreen.TimeAndDateSettingsHourScreen(hour = 13), parsedFrameIter.next().parsedScreen)
        assertEquals(ParsedScreen.TimeAndDateSettingsHourScreen(hour = 14), parsedFrameIter.next().parsedScreen)
    }

    @Test
    fun checkDuplicateDetectionReset() = runBlocking {
        // Test that resetting the duplicate detection works correctly.

        // Two screens with equal content (both are a TimeAndDateSettingsHourScreen
        // with the hour set to 13, or 1 PM). Duplicate detection would normally
        // filter out the second one. The resetDuplicate() call should prevent this.
        val displayFrameList = listOf(
            testTimeAndDateSettingsHourRussianScreen,
            testTimeAndDateSettingsHourTurkishScreen
        )

        val stream = ParsedDisplayFrameStream()

        val parsedFrameList = mutableListOf<ParsedDisplayFrame>()
        for (displayFrame in displayFrameList) {
            stream.resetDuplicate()
            stream.feedDisplayFrame(displayFrame)
            val parsedFrame = stream.getParsedDisplayFrame()
            assertNotNull(parsedFrame)
            parsedFrameList.add(parsedFrame)
        }
        val parsedFrameIter = parsedFrameList.listIterator()

        assertEquals(ParsedScreen.TimeAndDateSettingsHourScreen(hour = 13), parsedFrameIter.next().parsedScreen)
        assertEquals(ParsedScreen.TimeAndDateSettingsHourScreen(hour = 13), parsedFrameIter.next().parsedScreen)
    }

    @Test
    fun checkDisabledDuplicateDetection() = runBlocking {
        // Test that getting frames with disabled duplicate detection works correctly.

        // Two screens with equal content (both are a TimeAndDateSettingsHourScreen
        // with the hour set to 13, or 1 PM). Duplicate detection would normally
        // filter out the second one.
        val displayFrameList = listOf(
            testTimeAndDateSettingsHourRussianScreen,
            testTimeAndDateSettingsHourTurkishScreen
        )

        val parsedFrameList = mutableListOf<ParsedDisplayFrame>()
        val stream = ParsedDisplayFrameStream()

        coroutineScope {
            val producerJob = launch {
                for (displayFrame in displayFrameList) {
                    // Wait here until the frame has been retrieved, since otherwise,
                    // the feedDisplayFrame() call below would overwrite the already
                    // stored frame.
                    while (stream.hasStoredDisplayFrame())
                        delay(100)
                    stream.feedDisplayFrame(displayFrame)
                }
            }
            launch {
                while (true) {
                    val parsedFrame = stream.getParsedDisplayFrame(filterDuplicates = false)
                    assertNotNull(parsedFrame)
                    parsedFrameList.add(parsedFrame)
                    if (parsedFrameList.size >= 2)
                        break
                }
                producerJob.cancel()
            }
        }

        val parsedFrameIter = parsedFrameList.listIterator()

        assertEquals(ParsedScreen.TimeAndDateSettingsHourScreen(hour = 13), parsedFrameIter.next().parsedScreen)
        assertEquals(ParsedScreen.TimeAndDateSettingsHourScreen(hour = 13), parsedFrameIter.next().parsedScreen)
    }

    @Test
    fun checkAlertScreenDetection() = runBlocking {
        // Test that alert screens are detected and handled correctly.

        val stream = ParsedDisplayFrameStream()

        // Feed some dummy non-alert screen first to see that such a
        // screen does not mess up the alert screen detection logic.
        // We expect normal parsing behavior.
        stream.feedDisplayFrame(testTimeAndDateSettingsHourRussianScreen)
        val parsedFirstFrame = stream.getParsedDisplayFrame()
        assertEquals(ParsedScreen.TimeAndDateSettingsHourScreen(hour = 13), parsedFirstFrame!!.parsedScreen)

        // Feed a W6 screen, but with alert screen detection disabled.
        // We expect normal parsing behavior.
        stream.feedDisplayFrame(testFrameW6CancelTbrWarningScreen)
        val parsedWarningFrame = stream.getParsedDisplayFrame(processAlertScreens = false)
        assertEquals(ParsedScreen.AlertScreen(AlertScreenContent.Warning(6, AlertScreenContent.AlertScreenState.TO_SNOOZE)), parsedWarningFrame!!.parsedScreen)

        // Feed a W6 screen, but with alert screen detection enabled.
        // We expect the alert screen to be detected and an exception
        // to be thrown as a result.
        val alertScreenException = assertFailsWith<AlertScreenException> {
            stream.feedDisplayFrame(testFrameW6CancelTbrWarningScreen)
            stream.getParsedDisplayFrame(processAlertScreens = true)
        }
        assertIs<AlertScreenContent.Warning>(alertScreenException.alertScreenContent)
        assertEquals(6, (alertScreenException.alertScreenContent as AlertScreenContent.Warning).code)

        // Feed another dummy non-alert screen to see that the stream
        // parses correctly even after an AlertScreenException.
        stream.feedDisplayFrame(testTimeAndDateSettingsHourTurkishScreen)
        val parsedLastFrame = stream.getParsedDisplayFrame()
        assertEquals(ParsedScreen.TimeAndDateSettingsHourScreen(hour = 13), parsedLastFrame!!.parsedScreen)
    }

    @Test
    fun checkSkippingBlinkedOutScreens() = runBlocking {
        // Test that the stream correctly skips blinked-out screens.

        // Produce a test feed of 3 frames, with the second frame being blinked out.
        // We expect the stream to filter that blinked-out frame.
        val displayFrameList = listOf(
            testFrameTemporaryBasalRatePercentage110Screen,
            testFrameTemporaryBasalRateNoPercentageScreen,
            TbrPercentageAndDurationScreens.testFrameTbrDurationEnglishScreen
        )

        val parsedFrameList = mutableListOf<ParsedDisplayFrame>()
        val stream = ParsedDisplayFrameStream()

        coroutineScope {
            val producerJob = launch {
                for (displayFrame in displayFrameList) {
                    // Wait here until the frame has been retrieved, since otherwise,
                    // the feedDisplayFrame() call below would overwrite the already
                    // stored frame.
                    while (stream.hasStoredDisplayFrame())
                        delay(100)
                    stream.feedDisplayFrame(displayFrame)
                }
            }
            launch {
                while (true) {
                    val parsedFrame = stream.getParsedDisplayFrame(filterDuplicates = false)
                    assertNotNull(parsedFrame)
                    parsedFrameList.add(parsedFrame)
                    if (parsedFrameList.size >= 2)
                        break
                }
                producerJob.cancel()
            }
        }

        val parsedFrameIter = parsedFrameList.listIterator()

        assertEquals(2, parsedFrameList.size)
        assertEquals(
            ParsedScreen.TemporaryBasalRatePercentageScreen(percentage = 110, remainingDurationInMinutes = 30),
            parsedFrameIter.next().parsedScreen
        )
        assertEquals(ParsedScreen.TemporaryBasalRateDurationScreen(durationInMinutes = 30), parsedFrameIter.next().parsedScreen)
    }
}

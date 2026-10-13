package info.nightscout.comboctl.parser

import app.aaps.shared.tests.TestBase
import info.nightscout.comboctl.base.DisplayFrame
import info.nightscout.comboctl.base.timeWithoutDate
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.fail

class ParserTest : TestBase() {
    class TestContext(displayFrame: DisplayFrame, tokenOffset: Int, skipTitleString: Boolean = false, parseTopLeftTime: Boolean = false) {

        val tokens = findTokens(displayFrame)
        val parseContext = ParseContext(tokens, tokenOffset)

        init {
            if (skipTitleString)
                StringParser().parse(parseContext)
            if (parseTopLeftTime)
                parseContext.topLeftTime = (TimeParser().parse(parseContext) as ParseResult.Value<*>).value as LocalDateTime
        }
    }

    // Tests for the basic parsers

    @Test
    fun checkSingleGlyphParser() {
        // Test the SingleGlyphParser by trying to parse token #9
        // with it. We expect that token to be Symbol.LARGE_RESERVOIR_FULL.
        // When the SingleGlyphParser succeeds, it returns ParseResult.NoValue,
        // since its purpose is to check that a particular glyph is at the
        // current parse context position. There's no point in it having
        // a return value, since its success is the information we want.

        val testContext = TestContext(testFrameQuickinfoMainScreen, 9)
        val result = SingleGlyphParser(Glyph.LargeSymbol(LargeSymbol.RESERVOIR_FULL)).parse(testContext.parseContext)
        assertEquals(ParseResult.NoValue::class, result::class)
    }

    fun checkSingleGlyphTypeParser() {
        // Test the SingleGlyphTypeParser by trying to parse token #9
        // with it. We expect that token to be Symbol.LARGE_RESERVOIR_FULL.
        // When the SingleGlyphTypeParser succeeds, it returns the symbol
        // as the result. Unlike SingleGlyphParser, this parser does not
        // look for a particular glyph - it just tests that the *type* of
        // the glyph matches. This is why it does have a result (the exact
        // glpyh that was found).

        val testContext = TestContext(testFrameQuickinfoMainScreen, 9)
        val result = SingleGlyphTypeParser(Glyph.LargeSymbol::class).parse(testContext.parseContext)
        assertEquals(ParseResult.Value::class, result::class)
        assertEquals(LargeSymbol.RESERVOIR_FULL, ((result as ParseResult.Value<*>).value as Glyph.LargeSymbol).symbol)
    }

    @Test
    fun checkTitleStringParser() {
        val testContext = TestContext(testFrameQuickinfoMainScreen, 0)
        val result = StringParser().parse(testContext.parseContext)
        assertEquals(ParseResult.Value::class, result::class)
        assertEquals("QUICK INFO", (result as ParseResult.Value<*>).value as String)
    }

    @Test
    fun checkIntegerParser() {
        val testContext = TestContext(testFrameQuickinfoMainScreen, 10)
        val result = IntegerParser().parse(testContext.parseContext)
        assertEquals(ParseResult.Value::class, result::class)
        assertEquals(213, (result as ParseResult.Value<*>).value as Int)
    }

    @Test
    fun checkDecimalParser() {
        val testContext = TestContext(testFrameBasalRateTotalScreen1, 15)
        val result = DecimalParser().parse(testContext.parseContext)
        assertEquals(ParseResult.Value::class, result::class)
        assertEquals(56970, (result as ParseResult.Value<*>).value as Int)
    }

    @Test
    fun checkDateUSParser() {
        val testContext = TestContext(testUSDateFormatScreen, 20)
        val result = DateParser().parse(testContext.parseContext)
        assertEquals(ParseResult.Value::class, result::class)
        assertEquals(LocalDate(year = 2011, month = 2, day = 3), (result as ParseResult.Value<*>).value as LocalDate)
    }

    @Test
    fun checkDateEUParser() {
        val testContext = TestContext(testEUDateFormatScreen, 20)
        val result = DateParser().parse(testContext.parseContext)
        assertEquals(ParseResult.Value::class, result::class)
        assertEquals(LocalDate(year = 2011, month = 2, day = 3), (result as ParseResult.Value<*>).value as LocalDate)
    }

    @Test
    fun checkTime12HrParser() {
        val testContext = TestContext(testTimeAndDateSettingsHour12hFormatScreen, 8)
        val result = TimeParser().parse(testContext.parseContext)
        assertEquals(ParseResult.Value::class, result::class)
        assertEquals(timeWithoutDate(hour = 20, minute = 34), (result as ParseResult.Value<*>).value as LocalDateTime)
    }

    @Test
    fun checkTime24HrParser() {
        val testContext = TestContext(testTimeAndDateSettingsHour24hFormatScreen, 9)
        val result = TimeParser().parse(testContext.parseContext)
        assertEquals(ParseResult.Value::class, result::class)
        assertEquals(timeWithoutDate(hour = 10, minute = 22), (result as ParseResult.Value<*>).value as LocalDateTime)
    }

    @Test
    fun checkSuccessfulOptionalParser() {
        // Test the OptionalParser by attempting to parse the
        // second token in the test screen. That token is a
        // small "1" digit. Here, we attempt to parse it as
        // such a small digit. The OptionalParser should
        // return the SingleGlyphTypeParser's result.

        val testContext = TestContext(testFrameMainScreenWithTimeSeparator, 1)
        val result = OptionalParser(
            SingleGlyphTypeParser(Glyph.SmallDigit::class)
        ).parse(testContext.parseContext)

        assertEquals(ParseResult.Value::class, result::class)
        assertEquals(1, ((result as ParseResult.Value<*>).value as Glyph.SmallDigit).digit)
    }

    @Test
    fun checkFailedOptionalParser() {
        // Test the OptionalParser by attempting to parse the
        // second token in the test screen. That token is a
        // small "1" digit. Here, we attempt to parse it as
        // a small symbol instead of a digit to provoke a
        // parsing failure. The OptionalParser should
        // return ParseResult.Null as the result due to
        // the SingleGlyphTypeParser's failure.

        val testContext = TestContext(testFrameMainScreenWithTimeSeparator, 1)
        val result = OptionalParser(
            SingleGlyphTypeParser(Glyph.SmallSymbol::class)
        ).parse(testContext.parseContext)

        assertEquals(ParseResult.Null::class, result::class)
    }

    @Test
    fun checkFirstSuccessParser() {
        // Test the FirstSuccessParser by attempting to apply two
        // parsers to a test screen. We expect the first one
        // (the IntegerParser) to fail, since the first token
        // in the screen is not an integer. We expect the second
        // one (the SequenceParser) to succeed, and the overall
        // FirstSuccessParser to return that subparser's result.

        val testContext = TestContext(testFrameMainScreenWithTimeSeparator, 0)
        val result = FirstSuccessParser(
            listOf(
                IntegerParser(),
                SequenceParser(
                    listOf(
                        SingleGlyphParser(Glyph.SmallSymbol(SmallSymbol.CLOCK)),
                        TimeParser(),
                        SingleGlyphTypeParser(Glyph.LargeSymbol::class),
                        DecimalParser()
                    )
                )
            )
        ).parse(testContext.parseContext)

        assertEquals(ParseResult.Sequence::class, result::class)
        val sequence = result as ParseResult.Sequence
        assertEquals(3, sequence.values.size)
        assertEquals(timeWithoutDate(hour = 10, minute = 20), sequence.valueAt(0))
        assertEquals(Glyph.LargeSymbol(LargeSymbol.BASAL), sequence.valueAt(1))
        assertEquals(200, sequence.valueAt(2))
    }

    @Test
    fun checkSequenceParser() {
        val testContext = TestContext(testTimeAndDateSettingsHour24hFormatScreen, 0)
        val result = SequenceParser(
            listOf(
                StringParser(),
                SingleGlyphParser(Glyph.LargeSymbol(LargeSymbol.CLOCK)),
                IntegerParser(GlyphDigitParseMode.LARGE_DIGITS_ONLY),
                TimeParser()
            )
        ).parse(testContext.parseContext)

        assertEquals(ParseResult.Sequence::class, result::class)
        val sequence = result as ParseResult.Sequence
        assertEquals(3, sequence.values.size)
        assertEquals("STUNDE", result.valueAt<String>(0))
        assertEquals(10, result.valueAt<Int>(1))
        assertEquals(timeWithoutDate(hour = 10, minute = 22), result.valueAt<LocalDateTime>(2))
    }

    @Test
    fun checkSequenceWithOptionalAndNonMatchingParser() {
        // This test combines SequenceParser and OptionalParser.
        // We expect the OptionalParser's subparser to fail, and
        // the SequenceParser's resulting Sequence to contain a
        // Null value at the position of the OptionalParser.

        val testContext = TestContext(testTimeAndDateSettingsHour24hFormatScreen, 0)
        val result = SequenceParser(
            listOf(
                StringParser(),
                SingleGlyphParser(Glyph.LargeSymbol(LargeSymbol.CLOCK)),
                IntegerParser(GlyphDigitParseMode.LARGE_DIGITS_ONLY),
                OptionalParser(StringParser()),
                TimeParser()
            )
        ).parse(testContext.parseContext)

        assertEquals(ParseResult.Sequence::class, result::class)
        val sequence = result as ParseResult.Sequence
        assertEquals(4, sequence.values.size)
        assertEquals("STUNDE", sequence.valueAt(0))
        assertEquals(10, sequence.valueAt(1))
        assertEquals(null, result.valueAtOrNull<String>(2))
        assertEquals(timeWithoutDate(hour = 10, minute = 22), sequence.valueAt(3))
    }

    @Test
    fun checkSequenceWithOptionalAndMatchingParser() {
        // This test combines SequenceParser and OptionalParser.
        // We expect the OptionalParser's subparser to succeed,
        //  and the SequenceParser's resulting Sequence to contain
        // a string value at the position of the OptionalParser.

        val testContext = TestContext(testTimeAndDateSettingsHour12hFormatScreen, 0)
        val result = SequenceParser(
            listOf(
                StringParser(),
                SingleGlyphParser(Glyph.LargeSymbol(LargeSymbol.CLOCK)),
                IntegerParser(GlyphDigitParseMode.LARGE_DIGITS_ONLY),
                OptionalParser(StringParser()),
                TimeParser()
            )
        ).parse(testContext.parseContext)

        assertEquals(ParseResult.Sequence::class, result::class)
        val sequence = result as ParseResult.Sequence
        assertEquals(4, sequence.values.size)
        assertEquals("HOUR", sequence.valueAt(0))
        assertEquals(8, sequence.valueAt(1))
        assertEquals("PM", sequence.valueAtOrNull(2))
        assertEquals(timeWithoutDate(hour = 20, minute = 34), sequence.valueAt(3))
    }

    // Tests for screen parsing

    // The MainScreen tests start at token #1, since the first token in
    // the main screen is the SMALL_CLOCK symbol. We do not test for
    // that here, because during parsing, that symbol is expected to
    // already have been parsed.

    @Test
    fun checkNormalMainScreenWithTimeSeparatorParsing() {
        val testContext = TestContext(testFrameMainScreenWithTimeSeparator, 1, parseTopLeftTime = true)
        val result = NormalMainScreenParser().parse(testContext.parseContext)

        assertEquals(ParseResult.Value::class, result::class)
        val screen = (result as ParseResult.Value<*>).value as ParsedScreen.MainScreen
        assertEquals(
            MainScreenContent.Normal(
                currentTime = testContext.parseContext.topLeftTime!!,
                activeBasalProfileNumber = 1,
                currentBasalRateFactor = 200,
                batteryState = BatteryState.FULL_BATTERY
            ),
            screen.content
        )
    }

    @Test
    fun checkNormalMainScreenWithoutTimeSeparatorParsing() {
        val testContext = TestContext(testFrameMainScreenWithoutTimeSeparator, 1, parseTopLeftTime = true)
        val result = NormalMainScreenParser().parse(testContext.parseContext)

        assertEquals(ParseResult.Value::class, result::class)
        val screen = (result as ParseResult.Value<*>).value as ParsedScreen.MainScreen
        assertEquals(
            MainScreenContent.Normal(
                currentTime = testContext.parseContext.topLeftTime!!,
                activeBasalProfileNumber = 1,
                currentBasalRateFactor = 200,
                batteryState = BatteryState.FULL_BATTERY
            ),
            screen.content
        )
    }

    @Test
    fun checkTbrMainScreenParsing() {
        val testContext = TestContext(testFrameMainScreenWithTbrInfo, 1, parseTopLeftTime = true)
        val result = TbrMainScreenParser().parse(testContext.parseContext)

        assertEquals(ParseResult.Value::class, result::class)
        val screen = (result as ParseResult.Value<*>).value as ParsedScreen.MainScreen
        assertEquals(
            MainScreenContent.Tbr(
                currentTime = testContext.parseContext.topLeftTime!!,
                remainingTbrDurationInMinutes = 30,
                tbrPercentage = 110,
                activeBasalProfileNumber = 1,
                currentBasalRateFactor = 220,
                batteryState = BatteryState.FULL_BATTERY
            ),
            screen.content
        )
    }

    @Test
    fun checkTbr90MainScreenParsing() {
        // Variant of checkTbrMainScreenParsing with a 90% TBR.
        // This is relevant, since at a TBR < 100%, the screen
        // includes a DOWN symbol instead of an UP one next
        // to the basal rate icon.
        val testContext = TestContext(testFrameMainScreenWith90TbrInfo, 1, parseTopLeftTime = true)
        val result = TbrMainScreenParser().parse(testContext.parseContext)

        assertEquals(ParseResult.Value::class, result::class)
        val screen = (result as ParseResult.Value<*>).value as ParsedScreen.MainScreen
        assertEquals(
            MainScreenContent.Tbr(
                currentTime = testContext.parseContext.topLeftTime!!,
                remainingTbrDurationInMinutes = 5,
                tbrPercentage = 90,
                activeBasalProfileNumber = 1,
                currentBasalRateFactor = 45,
                batteryState = BatteryState.FULL_BATTERY
            ),
            screen.content
        )
    }

    @Test
    fun checkStoppedMainScreenWithTimeSeparatorParsing() {
        val testContext = TestContext(testFrameMainScreenStoppedWithTimeSeparator, 1, parseTopLeftTime = true)
        val result = StoppedMainScreenParser().parse(testContext.parseContext)

        assertEquals(ParseResult.Value::class, result::class)
        val screen = (result as ParseResult.Value<*>).value as ParsedScreen.MainScreen
        assertEquals(
            MainScreenContent.Stopped(
                currentDateTime = LocalDateTime(
                    year = 0,
                    month = 4,
                    day = 21,
                    hour = testContext.parseContext.topLeftTime!!.hour,
                    minute = testContext.parseContext.topLeftTime!!.minute,
                    second = 0
                ),
                batteryState = BatteryState.FULL_BATTERY
            ),
            screen.content
        )
    }

    @Test
    fun checkStoppedMainScreenWithoutTimeSeparatorParsing() {
        val testContext = TestContext(testFrameMainScreenStoppedWithoutTimeSeparator, 1, parseTopLeftTime = true)
        val result = StoppedMainScreenParser().parse(testContext.parseContext)

        assertEquals(ParseResult.Value::class, result::class)
        val screen = (result as ParseResult.Value<*>).value as ParsedScreen.MainScreen
        assertEquals(
            MainScreenContent.Stopped(
                currentDateTime = LocalDateTime(
                    year = 0,
                    month = 4,
                    day = 21,
                    hour = testContext.parseContext.topLeftTime!!.hour,
                    minute = testContext.parseContext.topLeftTime!!.minute,
                    second = 0
                ),
                batteryState = BatteryState.FULL_BATTERY
            ),
            screen.content
        )
    }

    @Test
    fun checkNormalMainScreenWithNoBatteryParsing() {
        val testContext = TestContext(testFrameMainScreenWithNoBattery, 1, parseTopLeftTime = true)
        val result = StoppedMainScreenParser().parse(testContext.parseContext)

        assertEquals(ParseResult.Value::class, result::class)
        val screen = (result as ParseResult.Value<*>).value as ParsedScreen.MainScreen
        assertEquals(
            MainScreenContent.Stopped(
                currentDateTime = LocalDateTime(
                    year = 0,
                    month = 2,
                    day = 1,
                    hour = testContext.parseContext.topLeftTime!!.hour,
                    minute = testContext.parseContext.topLeftTime!!.minute,
                    second = 0
                ),
                batteryState = BatteryState.NO_BATTERY
            ),
            screen.content
        )
    }

    @Test
    fun checkNormalMainScreenWithLowBatteryParsing() {
        val testContext = TestContext(testFrameMainScreenWithLowBattery, 1, parseTopLeftTime = true)
        val result = NormalMainScreenParser().parse(testContext.parseContext)

        assertEquals(ParseResult.Value::class, result::class)
        val screen = (result as ParseResult.Value<*>).value as ParsedScreen.MainScreen
        assertEquals(
            MainScreenContent.Normal(
                currentTime = testContext.parseContext.topLeftTime!!,
                activeBasalProfileNumber = 1,
                currentBasalRateFactor = 80,
                batteryState = BatteryState.LOW_BATTERY
            ),
            screen.content
        )
    }

    @Test
    fun checkStoppedMainScreenWithLowBatteryParsing() {
        val testContext = TestContext(testFrameMainScreenStoppedWithLowBattery, 1, parseTopLeftTime = true)
        val result = StoppedMainScreenParser().parse(testContext.parseContext)

        assertEquals(ParseResult.Value::class, result::class)
        val screen = (result as ParseResult.Value<*>).value as ParsedScreen.MainScreen
        assertEquals(
            MainScreenContent.Stopped(
                currentDateTime = LocalDateTime(
                    year = 0,
                    month = 1,
                    day = 1,
                    hour = testContext.parseContext.topLeftTime!!.hour,
                    minute = testContext.parseContext.topLeftTime!!.minute,
                    second = 0
                ),
                batteryState = BatteryState.LOW_BATTERY
            ),
            screen.content
        )
    }

    @Test
    fun checkTbr90MainScreenWithLowBatteryParsing() {
        val testContext = TestContext(testFrameMainScreenWith90TbrInfoAndLowBattery, 1, parseTopLeftTime = true)
        val result = TbrMainScreenParser().parse(testContext.parseContext)

        assertEquals(ParseResult.Value::class, result::class)
        val screen = (result as ParseResult.Value<*>).value as ParsedScreen.MainScreen
        assertEquals(
            MainScreenContent.Tbr(
                currentTime = testContext.parseContext.topLeftTime!!,
                remainingTbrDurationInMinutes = 15,
                tbrPercentage = 90,
                activeBasalProfileNumber = 1,
                currentBasalRateFactor = 72,
                batteryState = BatteryState.LOW_BATTERY
            ),
            screen.content
        )
    }

    @Test
    fun checkExtendedBolusMainScreenParsing() {
        val testContext = TestContext(testFrameMainScreenWithExtendedBolusInfo, 1, parseTopLeftTime = true)
        val result = ExtendedAndMultiwaveBolusMainScreenParser().parse(testContext.parseContext)

        assertEquals(ParseResult.Value::class, result::class)
        val screen = (result as ParseResult.Value<*>).value as ParsedScreen.MainScreen
        assertEquals(
            MainScreenContent.ExtendedOrMultiwaveBolus(
                currentTime = testContext.parseContext.topLeftTime!!,
                remainingBolusDurationInMinutes = 3 * 60 + 0,
                isExtendedBolus = true,
                remainingBolusAmount = 2300,
                tbrIsActive = false,
                activeBasalProfileNumber = 1,
                currentBasalRateFactor = 790,
                batteryState = BatteryState.FULL_BATTERY
            ),
            screen.content
        )
    }

    @Test
    fun checkExtendedBolusWithTbrMainScreenParsing() {
        val testContext = TestContext(testFrameMainScreenWithExtendedBolusInfoAndTbr, 1, parseTopLeftTime = true)
        val result = ExtendedAndMultiwaveBolusMainScreenParser().parse(testContext.parseContext)
        assertEquals(ParseResult.Value::class, result::class)
        val screen = (result as ParseResult.Value<*>).value as ParsedScreen.MainScreen
        assertEquals(
            MainScreenContent.ExtendedOrMultiwaveBolus(
                currentTime = testContext.parseContext.topLeftTime!!,
                remainingBolusDurationInMinutes = 1 * 60 + 31,
                isExtendedBolus = true,
                remainingBolusAmount = 1300,
                tbrIsActive = true,
                activeBasalProfileNumber = 1,
                currentBasalRateFactor = 384,
                batteryState = BatteryState.FULL_BATTERY
            ),
            screen.content
        )
    }

    @Test
    fun checkMultiwaveBolusMainScreenParsing() {
        val testContext = TestContext(testFrameMainScreenWithMultiwaveBolusInfo, 1, parseTopLeftTime = true)
        val result = ExtendedAndMultiwaveBolusMainScreenParser().parse(testContext.parseContext)

        assertEquals(ParseResult.Value::class, result::class)
        val screen = (result as ParseResult.Value<*>).value as ParsedScreen.MainScreen
        assertEquals(
            MainScreenContent.ExtendedOrMultiwaveBolus(
                currentTime = testContext.parseContext.topLeftTime!!,
                remainingBolusDurationInMinutes = 1 * 60 + 30,
                isExtendedBolus = false,
                remainingBolusAmount = 1700,
                tbrIsActive = false,
                activeBasalProfileNumber = 1,
                currentBasalRateFactor = 790,
                batteryState = BatteryState.FULL_BATTERY
            ),
            screen.content
        )
    }

    @Test
    fun checkMenuScreenParsing() {
        val testScreens = listOf(
            Pair(testFrameStandardBolusMenuScreen, ParsedScreen.StandardBolusMenuScreen),
            Pair(testFrameExtendedBolusMenuScreen, ParsedScreen.ExtendedBolusMenuScreen),
            Pair(testFrameMultiwaveBolusMenuScreen, ParsedScreen.MultiwaveBolusMenuScreen),
            Pair(testFrameBluetoothSettingsMenuScreen, ParsedScreen.BluetoothSettingsMenuScreen),
            Pair(testFrameMenuSettingsMenuScreen, ParsedScreen.MenuSettingsMenuScreen),
            Pair(testFrameMyDataMenuScreen, ParsedScreen.MyDataMenuScreen),
            Pair(testFrameBasalRateProfileSelectionMenuScreen, ParsedScreen.BasalRateProfileSelectionMenuScreen),
            Pair(testFramePumpSettingsMenuScreen, ParsedScreen.PumpSettingsMenuScreen),
            Pair(testFrameReminderSettingsMenuScreen, ParsedScreen.ReminderSettingsMenuScreen),
            Pair(testFrameTimeAndDateSettingsMenuScreen, ParsedScreen.TimeAndDateSettingsMenuScreen),
            Pair(testFrameStopPumpMenuScreen, ParsedScreen.StopPumpMenuScreen),
            Pair(testFrameTemporaryBasalRateMenuScreen, ParsedScreen.TemporaryBasalRateMenuScreen),
            Pair(testFrameTherapySettingsMenuScreen, ParsedScreen.TherapySettingsMenuScreen),
            Pair(testFrameProgramBasalRate1MenuScreen, ParsedScreen.BasalRate1ProgrammingMenuScreen),
            Pair(testFrameProgramBasalRate2MenuScreen, ParsedScreen.BasalRate2ProgrammingMenuScreen),
            Pair(testFrameProgramBasalRate3MenuScreen, ParsedScreen.BasalRate3ProgrammingMenuScreen),
            Pair(testFrameProgramBasalRate4MenuScreen, ParsedScreen.BasalRate4ProgrammingMenuScreen),
            Pair(testFrameProgramBasalRate5MenuScreen, ParsedScreen.BasalRate5ProgrammingMenuScreen)
        )

        for (testScreen in testScreens) {
            val testContext = TestContext(testScreen.first, 0)
            val result = MenuScreenParser().parse(testContext.parseContext)
            assertEquals(ParseResult.Value::class, result::class)
            val screen = (result as ParseResult.Value<*>).value as ParsedScreen
            assertEquals(testScreen.second, screen)
        }
    }

    @Test
    fun checkBasalRateTotalScreenParsing() {
        val testScreens = listOf(
            Pair(testFrameBasalRateTotalScreen0, ParsedScreen.BasalRateTotalScreen(5160, 1)),
            Pair(testFrameBasalRateTotalScreen1, ParsedScreen.BasalRateTotalScreen(56970, 2))
        )

        for (testScreen in testScreens) {
            val testContext = TestContext(testScreen.first, 0, skipTitleString = true)
            val result = BasalRateTotalScreenParser().parse(testContext.parseContext)
            assertEquals(ParseResult.Value::class, result::class)
            val screen = (result as ParseResult.Value<*>).value as ParsedScreen.BasalRateTotalScreen
            assertEquals(testScreen.second, screen)
        }
    }

    @Test
    fun checkBasalRateFactorSettingsScreenParsing() {
        val testScreens = listOf(
            Pair(
                testFrameBasalRateFactorSettingNoFactorScreen,
                ParsedScreen.BasalRateFactorSettingScreen(
                    timeWithoutDate(hour = 2, minute = 0),
                    timeWithoutDate(hour = 3, minute = 0),
                    null, 1
                )
            ),
            Pair(
                testFrameBasalRateFactorSettingScreen0,
                ParsedScreen.BasalRateFactorSettingScreen(
                    timeWithoutDate(hour = 2, minute = 0),
                    timeWithoutDate(hour = 3, minute = 0),
                    120, 1
                )
            ),
            Pair(
                testFrameBasalRateFactorSettingScreen1,
                ParsedScreen.BasalRateFactorSettingScreen(
                    timeWithoutDate(hour = 2, minute = 0),
                    timeWithoutDate(hour = 3, minute = 0),
                    10000, 2
                )
            ),
            Pair(
                testFrameBasalRateFactorSettingScreenAM,
                ParsedScreen.BasalRateFactorSettingScreen(
                    timeWithoutDate(hour = 0, minute = 0),
                    timeWithoutDate(hour = 1, minute = 0),
                    50, 1
                )
            ),
            Pair(
                testFrameBasalRateFactorSettingScreenAMPM,
                ParsedScreen.BasalRateFactorSettingScreen(
                    timeWithoutDate(hour = 11, minute = 0),
                    timeWithoutDate(hour = 12, minute = 0),
                    0, 3
                )
            ),
            Pair(
                testFrameBasalRateFactorSettingScreenPMAM,
                ParsedScreen.BasalRateFactorSettingScreen(
                    timeWithoutDate(hour = 23, minute = 0),
                    timeWithoutDate(hour = 0, minute = 0),
                    0, 3
                )
            ),
            // This tests a special case where the basal rate factor setting screen
            // shows a time period of 23:00 - 24:00 instead of 23:00 - 00:00.
            // The "24:00" needs to be parsed correctly - as 00:00.
            // If this is not done, then an hour value of 24 may be passed
            // on to kotlinx.datetime LocalDateTime, which does _not_ accept
            // HourOfDay values outside of the 0..23 range.
            Pair(
                testFrameBasalRateFactorSettingScreenMidnightAs24,
                ParsedScreen.BasalRateFactorSettingScreen(
                    timeWithoutDate(hour = 23, minute = 0),
                    timeWithoutDate(hour = 0, minute = 0),
                    800, 1
                )
            )
        )

        for (testScreen in testScreens) {
            val testContext = TestContext(testScreen.first, 1, parseTopLeftTime = true)
            val result = BasalRateFactorSettingScreenParser().parse(testContext.parseContext)
            assertEquals(ParseResult.Value::class, result::class)
            val screen = (result as ParseResult.Value<*>).value as ParsedScreen.BasalRateFactorSettingScreen
            assertEquals(testScreen.second.numUnits == null, screen.isBlinkedOut)
            assertEquals(testScreen.second, screen)
        }
    }

    @Test
    fun checkQuickinfoScreenParsing() {
        val testContext = TestContext(testFrameQuickinfoMainScreen, 0, skipTitleString = true)
        val result = QuickinfoScreenParser().parse(testContext.parseContext)

        assertEquals(ParseResult.Value::class, result::class)
        val screen = (result as ParseResult.Value<*>).value as ParsedScreen
        assertEquals(
            ParsedScreen.QuickinfoMainScreen(
                Quickinfo(availableUnits = 213, reservoirState = ReservoirState.FULL)
            ),
            screen
        )
    }

    @Test
    fun checkW6CancelTbrWarningScreenParsing() {
        val testContext = TestContext(testFrameW6CancelTbrWarningScreen, 0, skipTitleString = true)
        val result = AlertScreenParser().parse(testContext.parseContext)

        assertEquals(ParseResult.Value::class, result::class)
        val alertScreen = (result as ParseResult.Value<*>).value as ParsedScreen.AlertScreen
        assertEquals(AlertScreenContent.Warning(6, AlertScreenContent.AlertScreenState.TO_SNOOZE), alertScreen.content)
    }

    @Test
    fun checkW8CancelBolusWarningScreenParsing() {
        val testScreens = listOf(
            Pair(testFrameW8CancelBolusWarningScreen0, AlertScreenContent.None),
            Pair(testFrameW8CancelBolusWarningScreen1, AlertScreenContent.Warning(8, AlertScreenContent.AlertScreenState.TO_SNOOZE)),
            Pair(testFrameW8CancelBolusWarningScreen2, AlertScreenContent.None),
            Pair(testFrameW8CancelBolusWarningScreen3, AlertScreenContent.Warning(8, AlertScreenContent.AlertScreenState.TO_CONFIRM))
        )

        for (testScreen in testScreens) {
            val testContext = TestContext(testScreen.first, 0, skipTitleString = true)
            val result = AlertScreenParser().parse(testContext.parseContext)
            assertEquals(ParseResult.Value::class, result::class)
            val screen = (result as ParseResult.Value<*>).value as ParsedScreen.AlertScreen
            assertEquals(testScreen.second, screen.content)
        }
    }

    @Test
    fun checkE2BatteryEmptyErrorScreenParsing() {
        val testScreens = listOf(
            Pair(testFrameE2BatteryEmptyErrorScreen0, AlertScreenContent.None),
            Pair(testFrameE2BatteryEmptyErrorScreen1, AlertScreenContent.Error(2, AlertScreenContent.AlertScreenState.ERROR_TEXT))
        )

        for (testScreen in testScreens) {
            val testContext = TestContext(testScreen.first, 0, skipTitleString = true)
            val result = AlertScreenParser().parse(testContext.parseContext)
            assertEquals(ParseResult.Value::class, result::class)
            val screen = (result as ParseResult.Value<*>).value as ParsedScreen.AlertScreen
            assertEquals(testScreen.second, screen.content)
        }
    }

    @Test
    fun checkE4OcclusionErrorScreenParsing() {
        val testScreens = listOf(
            Pair(testFrameE4OcclusionErrorScreen0, AlertScreenContent.Error(4, AlertScreenContent.AlertScreenState.ERROR_TEXT)),
            Pair(testFrameE4OcclusionErrorScreen1, AlertScreenContent.None),
            Pair(testFrameE4OcclusionErrorScreen2, AlertScreenContent.Error(4, AlertScreenContent.AlertScreenState.ERROR_TEXT)),
            Pair(testFrameE4OcclusionErrorScreen3, AlertScreenContent.None),
        )

        for (testScreen in testScreens) {
            val testContext = TestContext(testScreen.first, 0, skipTitleString = true)
            val result = AlertScreenParser().parse(testContext.parseContext)
            assertEquals(ParseResult.Value::class, result::class)
            val screen = (result as ParseResult.Value<*>).value as ParsedScreen.AlertScreen
            assertEquals(testScreen.second, screen.content)
        }
    }

    @Test
    fun checkTemporaryBasalRatePercentageScreenParsing() {
        val testScreens = listOf(
            Pair(testFrameTemporaryBasalRatePercentage100Screen, 100),
            Pair(testFrameTemporaryBasalRatePercentage110Screen, 110),
            Pair(testFrameTemporaryBasalRateNoPercentageScreen, null),
            Pair(TbrPercentageAndDurationScreens.testFrameTbrPercentageEnglishScreen, 110),
            Pair(TbrPercentageAndDurationScreens.testFrameTbrPercentageSpanishScreen, 110),
            Pair(TbrPercentageAndDurationScreens.testFrameTbrPercentageFrenchScreen, 110),
            Pair(TbrPercentageAndDurationScreens.testFrameTbrPercentageItalianScreen, 110),
            Pair(TbrPercentageAndDurationScreens.testFrameTbrPercentageRussianScreen, 110),
            Pair(TbrPercentageAndDurationScreens.testFrameTbrPercentageTurkishScreen, 110),
            Pair(TbrPercentageAndDurationScreens.testFrameTbrPercentagePolishScreen, 100),
            Pair(TbrPercentageAndDurationScreens.testFrameTbrPercentageCzechScreen, 110),
            Pair(TbrPercentageAndDurationScreens.testFrameTbrPercentageHungarianScreen, 110),
            Pair(TbrPercentageAndDurationScreens.testFrameTbrPercentageSlovakScreen, 110),
            Pair(TbrPercentageAndDurationScreens.testFrameTbrPercentageRomanianScreen, 110),
            Pair(TbrPercentageAndDurationScreens.testFrameTbrPercentageCroatianScreen, 110),
            Pair(TbrPercentageAndDurationScreens.testFrameTbrPercentageDutchScreen, 110),
            Pair(TbrPercentageAndDurationScreens.testFrameTbrPercentageGreekScreen, 110),
            Pair(TbrPercentageAndDurationScreens.testFrameTbrPercentageFinnishScreen, 110),
            Pair(TbrPercentageAndDurationScreens.testFrameTbrPercentageNorwegianScreen, 110),
            Pair(TbrPercentageAndDurationScreens.testFrameTbrPercentagePortugueseScreen, 110),
            Pair(TbrPercentageAndDurationScreens.testFrameTbrPercentageSwedishScreen, 110),
            Pair(TbrPercentageAndDurationScreens.testFrameTbrPercentageDanishScreen, 110),
            Pair(TbrPercentageAndDurationScreens.testFrameTbrPercentageGermanScreen, 110),
            Pair(TbrPercentageAndDurationScreens.testFrameTbrPercentageSlovenianScreen, 110),
            Pair(TbrPercentageAndDurationScreens.testFrameTbrPercentageLithuanianScreen, 110),
        )

        for (testScreen in testScreens) {
            val testContext = TestContext(testScreen.first, 0, skipTitleString = true)
            val result = TemporaryBasalRatePercentageScreenParser().parse(testContext.parseContext)
            assertEquals(ParseResult.Value::class, result::class)
            val screen = (result as ParseResult.Value<*>).value as ParsedScreen.TemporaryBasalRatePercentageScreen
            assertEquals(testScreen.second == null, screen.isBlinkedOut)
            assertEquals(testScreen.second, screen.percentage)
        }
    }

    @Test
    fun checkTemporaryBasalRatePercentageScreenPercentAndDurationParsing() {
        val testContext = TestContext(testFrameTemporaryBasalRatePercentage110Screen, 0, skipTitleString = true)
        val result = TemporaryBasalRatePercentageScreenParser().parse(testContext.parseContext)
        assertEquals(ParseResult.Value::class, result::class)
        val screen = (result as ParseResult.Value<*>).value as ParsedScreen.TemporaryBasalRatePercentageScreen
        assertEquals(false, screen.isBlinkedOut)
        assertEquals(110, screen.percentage)
        assertEquals(30, screen.remainingDurationInMinutes)
    }

    @Test
    fun checkTemporaryBasalRateDurationScreenParsing() {
        val testScreens = listOf(
            Pair(testFrameTbrDurationNoDurationScreen, null),
            Pair(TbrPercentageAndDurationScreens.testFrameTbrDurationEnglishScreen, 30),
            Pair(TbrPercentageAndDurationScreens.testFrameTbrDurationSpanishScreen, 30),
            Pair(TbrPercentageAndDurationScreens.testFrameTbrDurationFrenchScreen, 30),
            Pair(TbrPercentageAndDurationScreens.testFrameTbrDurationItalianScreen, 30),
            Pair(TbrPercentageAndDurationScreens.testFrameTbrDurationRussianScreen, 30),
            Pair(TbrPercentageAndDurationScreens.testFrameTbrDurationTurkishScreen, 30),
            Pair(TbrPercentageAndDurationScreens.testFrameTbrDurationPolishScreen, 30),
            Pair(TbrPercentageAndDurationScreens.testFrameTbrDurationCzechScreen, 30),
            Pair(TbrPercentageAndDurationScreens.testFrameTbrDurationHungarianScreen, 30),
            Pair(TbrPercentageAndDurationScreens.testFrameTbrDurationSlovakScreen, 30),
            Pair(TbrPercentageAndDurationScreens.testFrameTbrDurationRomanianScreen, 30),
            Pair(TbrPercentageAndDurationScreens.testFrameTbrDurationCroatianScreen, 30),
            Pair(TbrPercentageAndDurationScreens.testFrameTbrDurationDutchScreen, 30),
            Pair(TbrPercentageAndDurationScreens.testFrameTbrDurationGreekScreen, 30),
            Pair(TbrPercentageAndDurationScreens.testFrameTbrDurationFinnishScreen, 30),
            Pair(TbrPercentageAndDurationScreens.testFrameTbrDurationNorwegianScreen, 30),
            Pair(TbrPercentageAndDurationScreens.testFrameTbrDurationPortugueseScreen, 30),
            Pair(TbrPercentageAndDurationScreens.testFrameTbrDurationSwedishScreen, 30),
            Pair(TbrPercentageAndDurationScreens.testFrameTbrDurationDanishScreen, 30),
            Pair(TbrPercentageAndDurationScreens.testFrameTbrDurationGermanScreen, 30),
            Pair(TbrPercentageAndDurationScreens.testFrameTbrDurationSlovenianScreen, 15),
            Pair(TbrPercentageAndDurationScreens.testFrameTbrDurationLithuanianScreen, 15),
        )

        for (testScreen in testScreens) {
            val testContext = TestContext(testScreen.first, 0, skipTitleString = true)
            val result = TemporaryBasalRateDurationScreenParser().parse(testContext.parseContext)
            assertEquals(ParseResult.Value::class, result::class)
            val screen = (result as ParseResult.Value<*>).value as ParsedScreen.TemporaryBasalRateDurationScreen
            assertEquals(testScreen.second == null, screen.isBlinkedOut)
            assertEquals(testScreen.second, screen.durationInMinutes)
        }
    }

    @Test
    fun checkTemporaryBasalRate24HoursDurationParsing() {
        val testContext = TestContext(testFrameTbrDuration24HoursScreen, 0, skipTitleString = true)
        val result = TemporaryBasalRateDurationScreenParser().parse(testContext.parseContext)
        assertEquals(ParseResult.Value::class, result::class)
        val screen = (result as ParseResult.Value<*>).value as ParsedScreen.TemporaryBasalRateDurationScreen
        assertEquals(false, screen.isBlinkedOut)
        assertEquals(24 * 60, screen.durationInMinutes)
    }

    @Test
    fun checkTimeAndDateSettingsScreenParsing() {
        val testScreens = listOf(
            Pair(testTimeAndDateSettingsHour12hFormatScreen, ParsedScreen.TimeAndDateSettingsHourScreen(20)),
            Pair(testTimeAndDateSettingsHour24hFormatScreen, ParsedScreen.TimeAndDateSettingsHourScreen(10)),

            Pair(testTimeAndDateSettingsHourEnglishScreen, ParsedScreen.TimeAndDateSettingsHourScreen(8)),
            Pair(testTimeAndDateSettingsMinuteEnglishScreen, ParsedScreen.TimeAndDateSettingsMinuteScreen(35)),
            Pair(testTimeAndDateSettingsYearEnglishScreen, ParsedScreen.TimeAndDateSettingsYearScreen(2015)),
            Pair(testTimeAndDateSettingsMonthEnglishScreen, ParsedScreen.TimeAndDateSettingsMonthScreen(4)),
            Pair(testTimeAndDateSettingsDayEnglishScreen, ParsedScreen.TimeAndDateSettingsDayScreen(27)),

            Pair(testTimeAndDateSettingsHourSpanishScreen, ParsedScreen.TimeAndDateSettingsHourScreen(8)),
            Pair(testTimeAndDateSettingsMinuteSpanishScreen, ParsedScreen.TimeAndDateSettingsMinuteScreen(36)),
            Pair(testTimeAndDateSettingsYearSpanishScreen, ParsedScreen.TimeAndDateSettingsYearScreen(2015)),
            Pair(testTimeAndDateSettingsMonthSpanishScreen, ParsedScreen.TimeAndDateSettingsMonthScreen(4)),
            Pair(testTimeAndDateSettingsDaySpanishScreen, ParsedScreen.TimeAndDateSettingsDayScreen(27)),

            Pair(testTimeAndDateSettingsHourFrenchScreen, ParsedScreen.TimeAndDateSettingsHourScreen(10)),
            Pair(testTimeAndDateSettingsMinuteFrenchScreen, ParsedScreen.TimeAndDateSettingsMinuteScreen(4)),
            Pair(testTimeAndDateSettingsYearFrenchScreen, ParsedScreen.TimeAndDateSettingsYearScreen(2015)),
            Pair(testTimeAndDateSettingsMonthFrenchScreen, ParsedScreen.TimeAndDateSettingsMonthScreen(4)),
            Pair(testTimeAndDateSettingsDayFrenchScreen, ParsedScreen.TimeAndDateSettingsDayScreen(27)),

            Pair(testTimeAndDateSettingsHourItalianScreen, ParsedScreen.TimeAndDateSettingsHourScreen(13)),
            Pair(testTimeAndDateSettingsMinuteItalianScreen, ParsedScreen.TimeAndDateSettingsMinuteScreen(48)),
            Pair(testTimeAndDateSettingsYearItalianScreen, ParsedScreen.TimeAndDateSettingsYearScreen(2015)),
            Pair(testTimeAndDateSettingsMonthItalianScreen, ParsedScreen.TimeAndDateSettingsMonthScreen(4)),
            Pair(testTimeAndDateSettingsDayItalianScreen, ParsedScreen.TimeAndDateSettingsDayScreen(27)),

            Pair(testTimeAndDateSettingsHourRussianScreen, ParsedScreen.TimeAndDateSettingsHourScreen(13)),
            Pair(testTimeAndDateSettingsMinuteRussianScreen, ParsedScreen.TimeAndDateSettingsMinuteScreen(52)),
            Pair(testTimeAndDateSettingsYearRussianScreen, ParsedScreen.TimeAndDateSettingsYearScreen(2015)),
            Pair(testTimeAndDateSettingsMonthRussianScreen, ParsedScreen.TimeAndDateSettingsMonthScreen(4)),
            Pair(testTimeAndDateSettingsDayRussianScreen, ParsedScreen.TimeAndDateSettingsDayScreen(27)),

            Pair(testTimeAndDateSettingsHourTurkishScreen, ParsedScreen.TimeAndDateSettingsHourScreen(13)),
            Pair(testTimeAndDateSettingsMinuteTurkishScreen, ParsedScreen.TimeAndDateSettingsMinuteScreen(53)),
            Pair(testTimeAndDateSettingsYearTurkishScreen, ParsedScreen.TimeAndDateSettingsYearScreen(2015)),
            Pair(testTimeAndDateSettingsMonthTurkishScreen, ParsedScreen.TimeAndDateSettingsMonthScreen(4)),
            Pair(testTimeAndDateSettingsDayTurkishScreen, ParsedScreen.TimeAndDateSettingsDayScreen(27)),

            Pair(testTimeAndDateSettingsHourPolishScreen, ParsedScreen.TimeAndDateSettingsHourScreen(14)),
            Pair(testTimeAndDateSettingsMinutePolishScreen, ParsedScreen.TimeAndDateSettingsMinuteScreen(34)),
            Pair(testTimeAndDateSettingsYearPolishScreen, ParsedScreen.TimeAndDateSettingsYearScreen(2015)),
            Pair(testTimeAndDateSettingsMonthPolishScreen, ParsedScreen.TimeAndDateSettingsMonthScreen(4)),
            Pair(testTimeAndDateSettingsDayPolishScreen, ParsedScreen.TimeAndDateSettingsDayScreen(27)),

            Pair(testTimeAndDateSettingsHourCzechScreen, ParsedScreen.TimeAndDateSettingsHourScreen(14)),
            Pair(testTimeAndDateSettingsMinuteCzechScreen, ParsedScreen.TimeAndDateSettingsMinuteScreen(34)),
            Pair(testTimeAndDateSettingsYearCzechScreen, ParsedScreen.TimeAndDateSettingsYearScreen(2015)),
            Pair(testTimeAndDateSettingsMonthCzechScreen, ParsedScreen.TimeAndDateSettingsMonthScreen(4)),
            Pair(testTimeAndDateSettingsDayCzechScreen, ParsedScreen.TimeAndDateSettingsDayScreen(27)),

            Pair(testTimeAndDateSettingsHourHungarianScreen, ParsedScreen.TimeAndDateSettingsHourScreen(14)),
            Pair(testTimeAndDateSettingsMinuteHungarianScreen, ParsedScreen.TimeAndDateSettingsMinuteScreen(34)),
            Pair(testTimeAndDateSettingsYearHungarianScreen, ParsedScreen.TimeAndDateSettingsYearScreen(2015)),
            Pair(testTimeAndDateSettingsMonthHungarianScreen, ParsedScreen.TimeAndDateSettingsMonthScreen(4)),
            Pair(testTimeAndDateSettingsDayHungarianScreen, ParsedScreen.TimeAndDateSettingsDayScreen(27)),

            Pair(testTimeAndDateSettingsHourSlovakScreen, ParsedScreen.TimeAndDateSettingsHourScreen(14)),
            Pair(testTimeAndDateSettingsMinuteSlovakScreen, ParsedScreen.TimeAndDateSettingsMinuteScreen(34)),
            Pair(testTimeAndDateSettingsYearSlovakScreen, ParsedScreen.TimeAndDateSettingsYearScreen(2015)),
            Pair(testTimeAndDateSettingsMonthSlovakScreen, ParsedScreen.TimeAndDateSettingsMonthScreen(4)),
            Pair(testTimeAndDateSettingsDaySlovakScreen, ParsedScreen.TimeAndDateSettingsDayScreen(27)),

            Pair(testTimeAndDateSettingsHourRomanianScreen, ParsedScreen.TimeAndDateSettingsHourScreen(14)),
            Pair(testTimeAndDateSettingsMinuteRomanianScreen, ParsedScreen.TimeAndDateSettingsMinuteScreen(34)),
            Pair(testTimeAndDateSettingsYearRomanianScreen, ParsedScreen.TimeAndDateSettingsYearScreen(2015)),
            Pair(testTimeAndDateSettingsMonthRomanianScreen, ParsedScreen.TimeAndDateSettingsMonthScreen(4)),
            Pair(testTimeAndDateSettingsDayRomanianScreen, ParsedScreen.TimeAndDateSettingsDayScreen(27)),

            Pair(testTimeAndDateSettingsHourCroatianScreen, ParsedScreen.TimeAndDateSettingsHourScreen(14)),
            Pair(testTimeAndDateSettingsMinuteCroatianScreen, ParsedScreen.TimeAndDateSettingsMinuteScreen(34)),
            Pair(testTimeAndDateSettingsYearCroatianScreen, ParsedScreen.TimeAndDateSettingsYearScreen(2015)),
            Pair(testTimeAndDateSettingsMonthCroatianScreen, ParsedScreen.TimeAndDateSettingsMonthScreen(4)),
            Pair(testTimeAndDateSettingsDayCroatianScreen, ParsedScreen.TimeAndDateSettingsDayScreen(27)),

            Pair(testTimeAndDateSettingsHourDutchScreen, ParsedScreen.TimeAndDateSettingsHourScreen(14)),
            Pair(testTimeAndDateSettingsMinuteDutchScreen, ParsedScreen.TimeAndDateSettingsMinuteScreen(34)),
            Pair(testTimeAndDateSettingsYearDutchScreen, ParsedScreen.TimeAndDateSettingsYearScreen(2015)),
            Pair(testTimeAndDateSettingsMonthDutchScreen, ParsedScreen.TimeAndDateSettingsMonthScreen(4)),
            Pair(testTimeAndDateSettingsDayDutchScreen, ParsedScreen.TimeAndDateSettingsDayScreen(27)),

            Pair(testTimeAndDateSettingsHourGreekScreen, ParsedScreen.TimeAndDateSettingsHourScreen(14)),
            Pair(testTimeAndDateSettingsMinuteGreekScreen, ParsedScreen.TimeAndDateSettingsMinuteScreen(34)),
            Pair(testTimeAndDateSettingsYearGreekScreen, ParsedScreen.TimeAndDateSettingsYearScreen(2015)),
            Pair(testTimeAndDateSettingsMonthGreekScreen, ParsedScreen.TimeAndDateSettingsMonthScreen(4)),
            Pair(testTimeAndDateSettingsDayGreekScreen, ParsedScreen.TimeAndDateSettingsDayScreen(27)),

            Pair(testTimeAndDateSettingsHourFinnishScreen, ParsedScreen.TimeAndDateSettingsHourScreen(14)),
            Pair(testTimeAndDateSettingsMinuteFinnishScreen, ParsedScreen.TimeAndDateSettingsMinuteScreen(34)),
            Pair(testTimeAndDateSettingsYearFinnishScreen, ParsedScreen.TimeAndDateSettingsYearScreen(2015)),
            Pair(testTimeAndDateSettingsMonthFinnishScreen, ParsedScreen.TimeAndDateSettingsMonthScreen(4)),
            Pair(testTimeAndDateSettingsDayFinnishScreen, ParsedScreen.TimeAndDateSettingsDayScreen(27)),

            Pair(testTimeAndDateSettingsHourNorwegianScreen, ParsedScreen.TimeAndDateSettingsHourScreen(14)),
            Pair(testTimeAndDateSettingsMinuteNorwegianScreen, ParsedScreen.TimeAndDateSettingsMinuteScreen(34)),
            Pair(testTimeAndDateSettingsYearNorwegianScreen, ParsedScreen.TimeAndDateSettingsYearScreen(2015)),
            Pair(testTimeAndDateSettingsMonthNorwegianScreen, ParsedScreen.TimeAndDateSettingsMonthScreen(4)),
            Pair(testTimeAndDateSettingsDayNorwegianScreen, ParsedScreen.TimeAndDateSettingsDayScreen(27)),

            Pair(testTimeAndDateSettingsHourPortugueseScreen, ParsedScreen.TimeAndDateSettingsHourScreen(14)),
            Pair(testTimeAndDateSettingsMinutePortugueseScreen, ParsedScreen.TimeAndDateSettingsMinuteScreen(34)),
            Pair(testTimeAndDateSettingsYearPortugueseScreen, ParsedScreen.TimeAndDateSettingsYearScreen(2015)),
            Pair(testTimeAndDateSettingsMonthPortugueseScreen, ParsedScreen.TimeAndDateSettingsMonthScreen(4)),
            Pair(testTimeAndDateSettingsDayPortugueseScreen, ParsedScreen.TimeAndDateSettingsDayScreen(27)),

            Pair(testTimeAndDateSettingsHourSwedishScreen, ParsedScreen.TimeAndDateSettingsHourScreen(14)),
            Pair(testTimeAndDateSettingsMinuteSwedishScreen, ParsedScreen.TimeAndDateSettingsMinuteScreen(34)),
            Pair(testTimeAndDateSettingsYearSwedishScreen, ParsedScreen.TimeAndDateSettingsYearScreen(2015)),
            Pair(testTimeAndDateSettingsMonthSwedishScreen, ParsedScreen.TimeAndDateSettingsMonthScreen(4)),
            Pair(testTimeAndDateSettingsDaySwedishScreen, ParsedScreen.TimeAndDateSettingsDayScreen(27)),

            Pair(testTimeAndDateSettingsHourDanishScreen, ParsedScreen.TimeAndDateSettingsHourScreen(14)),
            Pair(testTimeAndDateSettingsMinuteDanishScreen, ParsedScreen.TimeAndDateSettingsMinuteScreen(34)),
            Pair(testTimeAndDateSettingsYearDanishScreen, ParsedScreen.TimeAndDateSettingsYearScreen(2015)),
            Pair(testTimeAndDateSettingsMonthDanishScreen, ParsedScreen.TimeAndDateSettingsMonthScreen(4)),
            Pair(testTimeAndDateSettingsDayDanishScreen, ParsedScreen.TimeAndDateSettingsDayScreen(27)),

            Pair(testTimeAndDateSettingsHourGermanScreen, ParsedScreen.TimeAndDateSettingsHourScreen(10)),
            Pair(testTimeAndDateSettingsMinuteGermanScreen, ParsedScreen.TimeAndDateSettingsMinuteScreen(22)),
            Pair(testTimeAndDateSettingsYearGermanScreen, ParsedScreen.TimeAndDateSettingsYearScreen(2015)),
            Pair(testTimeAndDateSettingsMonthGermanScreen, ParsedScreen.TimeAndDateSettingsMonthScreen(4)),
            Pair(testTimeAndDateSettingsDayGermanScreen, ParsedScreen.TimeAndDateSettingsDayScreen(21)),

            Pair(testTimeAndDateSettingsHourSlovenianScreen, ParsedScreen.TimeAndDateSettingsHourScreen(19)),
            Pair(testTimeAndDateSettingsMinuteSlovenianScreen, ParsedScreen.TimeAndDateSettingsMinuteScreen(50)),
            Pair(testTimeAndDateSettingsYearSlovenianScreen, ParsedScreen.TimeAndDateSettingsYearScreen(2023)),
            Pair(testTimeAndDateSettingsMonthSlovenianScreen, ParsedScreen.TimeAndDateSettingsMonthScreen(3)),
            Pair(testTimeAndDateSettingsDaySlovenianScreen, ParsedScreen.TimeAndDateSettingsDayScreen(8)),

            Pair(testTimeAndDateSettingsHourLithuanianScreen, ParsedScreen.TimeAndDateSettingsHourScreen(20)),
            Pair(testTimeAndDateSettingsMinuteLithuanianScreen, ParsedScreen.TimeAndDateSettingsMinuteScreen(16)),
            Pair(testTimeAndDateSettingsYearLithuanianScreen, ParsedScreen.TimeAndDateSettingsYearScreen(2023)),
            Pair(testTimeAndDateSettingsMonthLithuanianScreen, ParsedScreen.TimeAndDateSettingsMonthScreen(3)),
            Pair(testTimeAndDateSettingsDayLithuanianScreen, ParsedScreen.TimeAndDateSettingsDayScreen(8)),

            // Extra test to verify that a *minute* 24 is not incorrectly interpreted
            // as an *hour* 24 and thus translated to 0 (this is done because the Combo
            // may show midnight as both hour 0 and hour 24).
            Pair(testTimeAndDateSettingsMinute24Screen, ParsedScreen.TimeAndDateSettingsMinuteScreen(24))
        )

        for (testScreen in testScreens) {
            val testContext = TestContext(testScreen.first, 0)

            val titleString = (StringParser().parse(testContext.parseContext) as ParseResult.Value<*>).value as String
            val titleId = knownScreenTitles[titleString]
            assertNotNull(titleId)

            val result = TimeAndDateSettingsScreenParser(titleId).parse(testContext.parseContext)
            assertEquals(ParseResult.Value::class, result::class)
            val screen = (result as ParseResult.Value<*>).value as ParsedScreen
            assertEquals(testScreen.second, screen)
        }
    }

    @Test
    fun checkMyDataScreenParsing() {
        val testScreens = listOf(
            Pair(
                testMyDataBolusDataEnglishScreen,
                ParsedScreen.MyDataBolusDataScreen(
                    index = 1, totalNumEntries = 30,
                    timestamp = LocalDateTime(year = 0, month = 1, day = 8, hour = 9, minute = 57, second = 0),
                    bolusAmount = 1000, bolusType = MyDataBolusType.STANDARD, durationInMinutes = null
                )
            ),
            Pair(
                testMyDataErrorDataEnglishScreen,
                ParsedScreen.MyDataErrorDataScreen(
                    index = 1, totalNumEntries = 30,
                    timestamp = LocalDateTime(year = 0, month = 1, day = 28, hour = 11, minute = 0, second = 0),
                    alert = AlertScreenContent.Warning(6, AlertScreenContent.AlertScreenState.HISTORY_ENTRY)
                )
            ),
            Pair(
                testMyDataDailyTotalsEnglishScreen,
                ParsedScreen.MyDataDailyTotalsScreen(
                    index = 1, totalNumEntries = 30, date = LocalDate(year = 0, day = 30, month = 1),
                    totalDailyAmount = 26900
                )
            ),
            Pair(
                testMyDataTbrDataEnglishScreen,
                ParsedScreen.MyDataTbrDataScreen(
                    index = 1, totalNumEntries = 30,
                    timestamp = LocalDateTime(year = 0, month = 1, day = 28, hour = 11, minute = 0, second = 0),
                    percentage = 110, durationInMinutes = 0
                )
            ),
            Pair(
                testMyDataBolusDataSpanishScreen,
                ParsedScreen.MyDataBolusDataScreen(
                    index = 1, totalNumEntries = 30,
                    timestamp = LocalDateTime(year = 0, month = 1, day = 8, hour = 9, minute = 57, second = 0),
                    bolusAmount = 1000, bolusType = MyDataBolusType.STANDARD, durationInMinutes = null
                )
            ),
            Pair(
                testMyDataErrorDataSpanishScreen,
                ParsedScreen.MyDataErrorDataScreen(
                    index = 1, totalNumEntries = 30,
                    timestamp = LocalDateTime(year = 0, month = 1, day = 28, hour = 11, minute = 0, second = 0),
                    alert = AlertScreenContent.Warning(6, AlertScreenContent.AlertScreenState.HISTORY_ENTRY)
                )
            ),
            Pair(
                testMyDataDailyTotalsSpanishScreen,
                ParsedScreen.MyDataDailyTotalsScreen(
                    index = 1, totalNumEntries = 30, date = LocalDate(year = 0, day = 30, month = 1),
                    totalDailyAmount = 26900
                )
            ),
            Pair(
                testMyDataTbrDataSpanishScreen,
                ParsedScreen.MyDataTbrDataScreen(
                    index = 1, totalNumEntries = 30,
                    timestamp = LocalDateTime(year = 0, month = 1, day = 28, hour = 11, minute = 0, second = 0),
                    percentage = 110, durationInMinutes = 0
                )
            ),
            Pair(
                testMyDataBolusDataFrenchScreen,
                ParsedScreen.MyDataBolusDataScreen(
                    index = 1, totalNumEntries = 30,
                    timestamp = LocalDateTime(year = 0, month = 5, day = 10, hour = 15, minute = 21, second = 0),
                    bolusAmount = 4000, bolusType = MyDataBolusType.EXTENDED, durationInMinutes = 5
                )
            ),
            Pair(
                testMyDataErrorDataFrenchScreen,
                ParsedScreen.MyDataErrorDataScreen(
                    index = 1, totalNumEntries = 30,
                    timestamp = LocalDateTime(year = 0, month = 5, day = 11, hour = 21, minute = 56, second = 0),
                    alert = AlertScreenContent.Warning(7, AlertScreenContent.AlertScreenState.HISTORY_ENTRY)
                )
            ),
            Pair(
                testMyDataDailyTotalsFrenchScreen,
                ParsedScreen.MyDataDailyTotalsScreen(
                    index = 1, totalNumEntries = 30, date = LocalDate(year = 0, day = 12, month = 5),
                    totalDailyAmount = 7600
                )
            ),
            Pair(
                testMyDataTbrDataFrenchScreen,
                ParsedScreen.MyDataTbrDataScreen(
                    index = 1, totalNumEntries = 30,
                    timestamp = LocalDateTime(year = 0, month = 5, day = 11, hour = 21, minute = 56, second = 0),
                    percentage = 110, durationInMinutes = 60
                )
            ),
            Pair(
                testMyDataBolusDataItalianScreen,
                ParsedScreen.MyDataBolusDataScreen(
                    index = 1, totalNumEntries = 30,
                    timestamp = LocalDateTime(year = 0, month = 5, day = 12, hour = 16, minute = 30, second = 0),
                    bolusAmount = 2700, bolusType = MyDataBolusType.MULTI_WAVE, durationInMinutes = 13
                )
            ),
            Pair(
                testMyDataErrorDataItalianScreen,
                ParsedScreen.MyDataErrorDataScreen(
                    index = 1, totalNumEntries = 30,
                    timestamp = LocalDateTime(year = 0, month = 5, day = 11, hour = 21, minute = 56, second = 0),
                    alert = AlertScreenContent.Warning(7, AlertScreenContent.AlertScreenState.HISTORY_ENTRY)
                )
            ),
            Pair(
                testMyDataDailyTotalsItalianScreen,
                ParsedScreen.MyDataDailyTotalsScreen(
                    index = 1, totalNumEntries = 30, date = LocalDate(year = 0, day = 12, month = 5),
                    totalDailyAmount = 11200
                )
            ),
            Pair(
                testMyDataTbrDataItalianScreen,
                ParsedScreen.MyDataTbrDataScreen(
                    index = 1, totalNumEntries = 30,
                    timestamp = LocalDateTime(year = 0, month = 5, day = 11, hour = 21, minute = 56, second = 0),
                    percentage = 110, durationInMinutes = 60
                )
            ),
            Pair(
                testMyDataBolusDataRussianScreen,
                ParsedScreen.MyDataBolusDataScreen(
                    index = 1, totalNumEntries = 30,
                    timestamp = LocalDateTime(year = 0, month = 5, day = 12, hour = 16, minute = 30, second = 0),
                    bolusAmount = 2700, bolusType = MyDataBolusType.MULTI_WAVE, durationInMinutes = 13
                )
            ),
            Pair(
                testMyDataErrorDataRussianScreen,
                ParsedScreen.MyDataErrorDataScreen(
                    index = 1, totalNumEntries = 30,
                    timestamp = LocalDateTime(year = 0, month = 5, day = 11, hour = 21, minute = 56, second = 0),
                    alert = AlertScreenContent.Warning(7, AlertScreenContent.AlertScreenState.HISTORY_ENTRY)
                )
            ),
            Pair(
                testMyDataDailyTotalsRussianScreen,
                ParsedScreen.MyDataDailyTotalsScreen(
                    index = 1, totalNumEntries = 30, date = LocalDate(year = 0, day = 12, month = 5),
                    totalDailyAmount = 12900
                )
            ),
            Pair(
                testMyDataTbrDataRussianScreen,
                ParsedScreen.MyDataTbrDataScreen(
                    index = 1, totalNumEntries = 30,
                    timestamp = LocalDateTime(year = 0, month = 5, day = 11, hour = 21, minute = 56, second = 0),
                    percentage = 110, durationInMinutes = 60
                )
            ),
            Pair(
                testMyDataBolusDataTurkishScreen,
                ParsedScreen.MyDataBolusDataScreen(
                    index = 1, totalNumEntries = 30,
                    timestamp = LocalDateTime(year = 0, month = 5, day = 12, hour = 16, minute = 30, second = 0),
                    bolusAmount = 2700, bolusType = MyDataBolusType.MULTI_WAVE, durationInMinutes = 13
                )
            ),
            Pair(
                testMyDataErrorDataTurkishScreen,
                ParsedScreen.MyDataErrorDataScreen(
                    index = 1, totalNumEntries = 30,
                    timestamp = LocalDateTime(year = 0, month = 5, day = 11, hour = 21, minute = 56, second = 0),
                    alert = AlertScreenContent.Warning(7, AlertScreenContent.AlertScreenState.HISTORY_ENTRY)
                )
            ),
            Pair(
                testMyDataDailyTotalsTurkishScreen,
                ParsedScreen.MyDataDailyTotalsScreen(
                    index = 1, totalNumEntries = 30, date = LocalDate(year = 0, day = 12, month = 5),
                    totalDailyAmount = 12900
                )
            ),
            Pair(
                testMyDataTbrDataTurkishScreen,
                ParsedScreen.MyDataTbrDataScreen(
                    index = 1, totalNumEntries = 30,
                    timestamp = LocalDateTime(year = 0, month = 5, day = 11, hour = 21, minute = 56, second = 0),
                    percentage = 110, durationInMinutes = 60
                )
            ),
            Pair(
                testMyDataBolusDataPolishScreen,
                ParsedScreen.MyDataBolusDataScreen(
                    index = 1, totalNumEntries = 30,
                    timestamp = LocalDateTime(year = 0, month = 5, day = 12, hour = 16, minute = 30, second = 0),
                    bolusAmount = 2700, bolusType = MyDataBolusType.MULTI_WAVE, durationInMinutes = 13
                )
            ),
            Pair(
                testMyDataErrorDataPolishScreen,
                ParsedScreen.MyDataErrorDataScreen(
                    index = 1, totalNumEntries = 30,
                    timestamp = LocalDateTime(year = 0, month = 5, day = 11, hour = 21, minute = 56, second = 0),
                    alert = AlertScreenContent.Warning(7, AlertScreenContent.AlertScreenState.HISTORY_ENTRY)
                )
            ),
            Pair(
                testMyDataDailyTotalsPolishScreen,
                ParsedScreen.MyDataDailyTotalsScreen(
                    index = 1, totalNumEntries = 30, date = LocalDate(year = 0, day = 12, month = 5),
                    totalDailyAmount = 12900
                )
            ),
            Pair(
                testMyDataTbrDataPolishScreen,
                ParsedScreen.MyDataTbrDataScreen(
                    index = 1, totalNumEntries = 30,
                    timestamp = LocalDateTime(year = 0, month = 5, day = 11, hour = 21, minute = 56, second = 0),
                    percentage = 110, durationInMinutes = 60
                )
            ),
            Pair(
                testMyDataBolusDataCzechScreen,
                ParsedScreen.MyDataBolusDataScreen(
                    index = 1, totalNumEntries = 30,
                    timestamp = LocalDateTime(year = 0, month = 5, day = 12, hour = 16, minute = 30, second = 0),
                    bolusAmount = 2700, bolusType = MyDataBolusType.MULTI_WAVE, durationInMinutes = 13
                )
            ),
            Pair(
                testMyDataErrorDataCzechScreen,
                ParsedScreen.MyDataErrorDataScreen(
                    index = 1, totalNumEntries = 30,
                    timestamp = LocalDateTime(year = 0, month = 5, day = 11, hour = 21, minute = 56, second = 0),
                    alert = AlertScreenContent.Warning(7, AlertScreenContent.AlertScreenState.HISTORY_ENTRY)
                )
            ),
            Pair(
                testMyDataDailyTotalsCzechScreen,
                ParsedScreen.MyDataDailyTotalsScreen(
                    index = 1, totalNumEntries = 30, date = LocalDate(year = 0, day = 12, month = 5),
                    totalDailyAmount = 13900
                )
            ),
            Pair(
                testMyDataTbrDataCzechScreen,
                ParsedScreen.MyDataTbrDataScreen(
                    index = 1, totalNumEntries = 30,
                    timestamp = LocalDateTime(year = 0, month = 5, day = 11, hour = 21, minute = 56, second = 0),
                    percentage = 110, durationInMinutes = 60
                )
            ),
            Pair(
                testMyDataBolusDataHungarianScreen,
                ParsedScreen.MyDataBolusDataScreen(
                    index = 1, totalNumEntries = 30,
                    timestamp = LocalDateTime(year = 0, month = 5, day = 12, hour = 16, minute = 30, second = 0),
                    bolusAmount = 2700, bolusType = MyDataBolusType.MULTI_WAVE, durationInMinutes = 13
                )
            ),
            Pair(
                testMyDataErrorDataHungarianScreen,
                ParsedScreen.MyDataErrorDataScreen(
                    index = 1, totalNumEntries = 30,
                    timestamp = LocalDateTime(year = 0, month = 5, day = 11, hour = 21, minute = 56, second = 0),
                    alert = AlertScreenContent.Warning(7, AlertScreenContent.AlertScreenState.HISTORY_ENTRY)
                )
            ),
            Pair(
                testMyDataDailyTotalsHungarianScreen,
                ParsedScreen.MyDataDailyTotalsScreen(
                    index = 1, totalNumEntries = 30, date = LocalDate(year = 0, day = 12, month = 5),
                    totalDailyAmount = 13900
                )
            ),
            Pair(
                testMyDataTbrDataHungarianScreen,
                ParsedScreen.MyDataTbrDataScreen(
                    index = 1, totalNumEntries = 30,
                    timestamp = LocalDateTime(year = 0, month = 5, day = 11, hour = 21, minute = 56, second = 0),
                    percentage = 110, durationInMinutes = 60
                )
            ),
            Pair(
                testMyDataBolusDataSlovakScreen,
                ParsedScreen.MyDataBolusDataScreen(
                    index = 1, totalNumEntries = 30,
                    timestamp = LocalDateTime(year = 0, month = 5, day = 12, hour = 16, minute = 30, second = 0),
                    bolusAmount = 2700, bolusType = MyDataBolusType.MULTI_WAVE, durationInMinutes = 13
                )
            ),
            Pair(
                testMyDataErrorDataSlovakScreen,
                ParsedScreen.MyDataErrorDataScreen(
                    index = 1, totalNumEntries = 30,
                    timestamp = LocalDateTime(year = 0, month = 5, day = 11, hour = 21, minute = 56, second = 0),
                    alert = AlertScreenContent.Warning(7, AlertScreenContent.AlertScreenState.HISTORY_ENTRY)
                )
            ),
            Pair(
                testMyDataDailyTotalsSlovakScreen,
                ParsedScreen.MyDataDailyTotalsScreen(
                    index = 1, totalNumEntries = 30, date = LocalDate(year = 0, day = 12, month = 5),
                    totalDailyAmount = 13900
                )
            ),
            Pair(
                testMyDataTbrDataSlovakScreen,
                ParsedScreen.MyDataTbrDataScreen(
                    index = 1, totalNumEntries = 30,
                    timestamp = LocalDateTime(year = 0, month = 5, day = 11, hour = 21, minute = 56, second = 0),
                    percentage = 110, durationInMinutes = 60
                )
            ),
            Pair(
                testMyDataBolusDataRomanianScreen,
                ParsedScreen.MyDataBolusDataScreen(
                    index = 1, totalNumEntries = 30,
                    timestamp = LocalDateTime(year = 0, month = 5, day = 12, hour = 16, minute = 30, second = 0),
                    bolusAmount = 2700, bolusType = MyDataBolusType.MULTI_WAVE, durationInMinutes = 13
                )
            ),
            Pair(
                testMyDataErrorDataRomanianScreen,
                ParsedScreen.MyDataErrorDataScreen(
                    index = 1, totalNumEntries = 30,
                    timestamp = LocalDateTime(year = 0, month = 5, day = 11, hour = 21, minute = 56, second = 0),
                    alert = AlertScreenContent.Warning(7, AlertScreenContent.AlertScreenState.HISTORY_ENTRY)
                )
            ),
            Pair(
                testMyDataDailyTotalsRomanianScreen,
                ParsedScreen.MyDataDailyTotalsScreen(
                    index = 1, totalNumEntries = 30, date = LocalDate(year = 0, day = 12, month = 5),
                    totalDailyAmount = 13900
                )
            ),
            Pair(
                testMyDataTbrDataRomanianScreen,
                ParsedScreen.MyDataTbrDataScreen(
                    index = 1, totalNumEntries = 30,
                    timestamp = LocalDateTime(year = 0, month = 5, day = 11, hour = 21, minute = 56, second = 0),
                    percentage = 110, durationInMinutes = 60
                )
            ),
            Pair(
                testMyDataBolusDataCroatianScreen,
                ParsedScreen.MyDataBolusDataScreen(
                    index = 1, totalNumEntries = 30,
                    timestamp = LocalDateTime(year = 0, month = 5, day = 12, hour = 16, minute = 30, second = 0),
                    bolusAmount = 2700, bolusType = MyDataBolusType.MULTI_WAVE, durationInMinutes = 13
                )
            ),
            Pair(
                testMyDataErrorDataCroatianScreen,
                ParsedScreen.MyDataErrorDataScreen(
                    index = 1, totalNumEntries = 30,
                    timestamp = LocalDateTime(year = 0, month = 2, day = 1, hour = 1, minute = 6, second = 0),
                    alert = AlertScreenContent.Warning(1, AlertScreenContent.AlertScreenState.HISTORY_ENTRY)
                )
            ),
            Pair(
                testMyDataDailyTotalsCroatianScreen,
                ParsedScreen.MyDataDailyTotalsScreen(
                    index = 1, totalNumEntries = 30, date = LocalDate(year = 0, day = 10, month = 2),
                    totalDailyAmount = 5800
                )
            ),
            Pair(
                testMyDataTbrDataCroatianScreen,
                ParsedScreen.MyDataTbrDataScreen(
                    index = 1, totalNumEntries = 30,
                    timestamp = LocalDateTime(year = 0, month = 6, day = 11, hour = 17, minute = 25, second = 0),
                    percentage = 240, durationInMinutes = 60
                )
            ),
            Pair(
                testMyDataBolusDataDutchScreen,
                ParsedScreen.MyDataBolusDataScreen(
                    index = 1, totalNumEntries = 30,
                    timestamp = LocalDateTime(year = 0, month = 5, day = 12, hour = 16, minute = 30, second = 0),
                    bolusAmount = 2700, bolusType = MyDataBolusType.MULTI_WAVE, durationInMinutes = 13
                )
            ),
            Pair(
                testMyDataErrorDataDutchScreen,
                ParsedScreen.MyDataErrorDataScreen(
                    index = 1, totalNumEntries = 30,
                    timestamp = LocalDateTime(year = 0, month = 2, day = 1, hour = 1, minute = 6, second = 0),
                    alert = AlertScreenContent.Warning(1, AlertScreenContent.AlertScreenState.HISTORY_ENTRY)
                )
            ),
            Pair(
                testMyDataDailyTotalsDutchScreen,
                ParsedScreen.MyDataDailyTotalsScreen(
                    index = 1, totalNumEntries = 30, date = LocalDate(year = 0, day = 10, month = 2),
                    totalDailyAmount = 5800
                )
            ),
            Pair(
                testMyDataTbrDataDutchScreen,
                ParsedScreen.MyDataTbrDataScreen(
                    index = 1, totalNumEntries = 30,
                    timestamp = LocalDateTime(year = 0, month = 6, day = 11, hour = 17, minute = 25, second = 0),
                    percentage = 240, durationInMinutes = 60
                )
            ),
            Pair(
                testMyDataBolusDataGreekScreen,
                ParsedScreen.MyDataBolusDataScreen(
                    index = 1, totalNumEntries = 30,
                    timestamp = LocalDateTime(year = 0, month = 5, day = 12, hour = 16, minute = 30, second = 0),
                    bolusAmount = 2700, bolusType = MyDataBolusType.MULTI_WAVE, durationInMinutes = 13
                )
            ),
            Pair(
                testMyDataErrorDataGreekScreen,
                ParsedScreen.MyDataErrorDataScreen(
                    index = 1, totalNumEntries = 30,
                    timestamp = LocalDateTime(year = 0, month = 2, day = 1, hour = 1, minute = 6, second = 0),
                    alert = AlertScreenContent.Warning(1, AlertScreenContent.AlertScreenState.HISTORY_ENTRY)
                )
            ),
            Pair(
                testMyDataDailyTotalsGreekScreen,
                ParsedScreen.MyDataDailyTotalsScreen(
                    index = 1, totalNumEntries = 30, date = LocalDate(year = 0, day = 10, month = 2),
                    totalDailyAmount = 5800
                )
            ),
            Pair(
                testMyDataTbrDataGreekScreen,
                ParsedScreen.MyDataTbrDataScreen(
                    index = 1, totalNumEntries = 30,
                    timestamp = LocalDateTime(year = 0, month = 6, day = 11, hour = 17, minute = 25, second = 0),
                    percentage = 240, durationInMinutes = 60
                )
            ),
            Pair(
                testMyDataBolusDataFinnishScreen,
                ParsedScreen.MyDataBolusDataScreen(
                    index = 1, totalNumEntries = 30,
                    timestamp = LocalDateTime(year = 0, month = 5, day = 12, hour = 16, minute = 30, second = 0),
                    bolusAmount = 2700, bolusType = MyDataBolusType.MULTI_WAVE, durationInMinutes = 13
                )
            ),
            Pair(
                testMyDataErrorDataFinnishScreen,
                ParsedScreen.MyDataErrorDataScreen(
                    index = 1, totalNumEntries = 30,
                    timestamp = LocalDateTime(year = 0, month = 2, day = 1, hour = 1, minute = 6, second = 0),
                    alert = AlertScreenContent.Warning(1, AlertScreenContent.AlertScreenState.HISTORY_ENTRY)
                )
            ),
            Pair(
                testMyDataDailyTotalsFinnishScreen,
                ParsedScreen.MyDataDailyTotalsScreen(
                    index = 1, totalNumEntries = 30, date = LocalDate(year = 0, day = 10, month = 2),
                    totalDailyAmount = 5900
                )
            ),
            Pair(
                testMyDataTbrDataFinnishScreen,
                ParsedScreen.MyDataTbrDataScreen(
                    index = 1, totalNumEntries = 30,
                    timestamp = LocalDateTime(year = 0, month = 6, day = 11, hour = 17, minute = 25, second = 0),
                    percentage = 240, durationInMinutes = 60
                )
            ),
            Pair(
                testMyDataBolusDataNorwegianScreen,
                ParsedScreen.MyDataBolusDataScreen(
                    index = 1, totalNumEntries = 30,
                    timestamp = LocalDateTime(year = 0, month = 5, day = 12, hour = 16, minute = 30, second = 0),
                    bolusAmount = 2700, bolusType = MyDataBolusType.MULTI_WAVE, durationInMinutes = 13
                )
            ),
            Pair(
                testMyDataErrorDataNorwegianScreen,
                ParsedScreen.MyDataErrorDataScreen(
                    index = 1, totalNumEntries = 30,
                    timestamp = LocalDateTime(year = 0, month = 2, day = 1, hour = 1, minute = 6, second = 0),
                    alert = AlertScreenContent.Warning(1, AlertScreenContent.AlertScreenState.HISTORY_ENTRY)
                )
            ),
            Pair(
                testMyDataDailyTotalsNorwegianScreen,
                ParsedScreen.MyDataDailyTotalsScreen(
                    index = 1, totalNumEntries = 30, date = LocalDate(year = 0, day = 10, month = 2),
                    totalDailyAmount = 5900
                )
            ),
            Pair(
                testMyDataTbrDataNorwegianScreen,
                ParsedScreen.MyDataTbrDataScreen(
                    index = 1, totalNumEntries = 30,
                    timestamp = LocalDateTime(year = 0, month = 6, day = 11, hour = 17, minute = 25, second = 0),
                    percentage = 240, durationInMinutes = 60
                )
            ),
            Pair(
                testMyDataBolusDataPortugueseScreen,
                ParsedScreen.MyDataBolusDataScreen(
                    index = 1, totalNumEntries = 30,
                    timestamp = LocalDateTime(year = 0, month = 5, day = 12, hour = 16, minute = 30, second = 0),
                    bolusAmount = 2700, bolusType = MyDataBolusType.MULTI_WAVE, durationInMinutes = 13
                )
            ),
            Pair(
                testMyDataErrorDataPortugueseScreen,
                ParsedScreen.MyDataErrorDataScreen(
                    index = 1, totalNumEntries = 30,
                    timestamp = LocalDateTime(year = 0, month = 2, day = 1, hour = 1, minute = 6, second = 0),
                    alert = AlertScreenContent.Warning(1, AlertScreenContent.AlertScreenState.HISTORY_ENTRY)
                )
            ),
            Pair(
                testMyDataDailyTotalsPortugueseScreen,
                ParsedScreen.MyDataDailyTotalsScreen(
                    index = 1, totalNumEntries = 30, date = LocalDate(year = 0, day = 10, month = 2),
                    totalDailyAmount = 5900
                )
            ),
            Pair(
                testMyDataTbrDataPortugueseScreen,
                ParsedScreen.MyDataTbrDataScreen(
                    index = 1, totalNumEntries = 30,
                    timestamp = LocalDateTime(year = 0, month = 6, day = 11, hour = 17, minute = 25, second = 0),
                    percentage = 240, durationInMinutes = 60
                )
            ),
            Pair(
                testMyDataBolusDataSwedishScreen,
                ParsedScreen.MyDataBolusDataScreen(
                    index = 1, totalNumEntries = 30,
                    timestamp = LocalDateTime(year = 0, month = 5, day = 12, hour = 16, minute = 30, second = 0),
                    bolusAmount = 2700, bolusType = MyDataBolusType.MULTI_WAVE, durationInMinutes = 13
                )
            ),
            Pair(
                testMyDataErrorDataSwedishScreen,
                ParsedScreen.MyDataErrorDataScreen(
                    index = 1, totalNumEntries = 30,
                    timestamp = LocalDateTime(year = 0, month = 2, day = 1, hour = 1, minute = 6, second = 0),
                    alert = AlertScreenContent.Warning(1, AlertScreenContent.AlertScreenState.HISTORY_ENTRY)
                )
            ),
            Pair(
                testMyDataDailyTotalsSwedishScreen,
                ParsedScreen.MyDataDailyTotalsScreen(
                    index = 1, totalNumEntries = 30, date = LocalDate(year = 0, day = 10, month = 2),
                    totalDailyAmount = 5900
                )
            ),
            Pair(
                testMyDataTbrDataSwedishScreen,
                ParsedScreen.MyDataTbrDataScreen(
                    index = 1, totalNumEntries = 30,
                    timestamp = LocalDateTime(year = 0, month = 6, day = 11, hour = 17, minute = 25, second = 0),
                    percentage = 240, durationInMinutes = 60
                )
            ),
            Pair(
                testMyDataBolusDataDanishScreen,
                ParsedScreen.MyDataBolusDataScreen(
                    index = 1, totalNumEntries = 30,
                    timestamp = LocalDateTime(year = 0, month = 5, day = 12, hour = 16, minute = 30, second = 0),
                    bolusAmount = 2700, bolusType = MyDataBolusType.MULTI_WAVE, durationInMinutes = 13
                )
            ),
            Pair(
                testMyDataErrorDataDanishScreen,
                ParsedScreen.MyDataErrorDataScreen(
                    index = 1, totalNumEntries = 30,
                    timestamp = LocalDateTime(year = 0, month = 2, day = 1, hour = 1, minute = 6, second = 0),
                    alert = AlertScreenContent.Warning(1, AlertScreenContent.AlertScreenState.HISTORY_ENTRY)
                )
            ),
            Pair(
                testMyDataDailyTotalsDanishScreen,
                ParsedScreen.MyDataDailyTotalsScreen(
                    index = 1, totalNumEntries = 30, date = LocalDate(year = 0, day = 10, month = 2),
                    totalDailyAmount = 5900
                )
            ),
            Pair(
                testMyDataTbrDataDanishScreen,
                ParsedScreen.MyDataTbrDataScreen(
                    index = 1, totalNumEntries = 30,
                    timestamp = LocalDateTime(year = 0, month = 6, day = 11, hour = 17, minute = 25, second = 0),
                    percentage = 240, durationInMinutes = 60
                )
            ),
            Pair(
                testMyDataBolusDataGermanScreen,
                ParsedScreen.MyDataBolusDataScreen(
                    index = 1, totalNumEntries = 30,
                    timestamp = LocalDateTime(year = 0, month = 1, day = 8, hour = 9, minute = 57, second = 0),
                    bolusAmount = 1000, bolusType = MyDataBolusType.STANDARD, durationInMinutes = null
                )
            ),
            Pair(
                testMyDataErrorDataGermanScreen,
                ParsedScreen.MyDataErrorDataScreen(
                    index = 1, totalNumEntries = 30,
                    timestamp = LocalDateTime(year = 0, month = 1, day = 28, hour = 11, minute = 0, second = 0),
                    alert = AlertScreenContent.Warning(6, AlertScreenContent.AlertScreenState.HISTORY_ENTRY)
                )
            ),
            Pair(
                testMyDataDailyTotalsGermanScreen,
                ParsedScreen.MyDataDailyTotalsScreen(
                    index = 1, totalNumEntries = 30, date = LocalDate(year = 0, day = 30, month = 1),
                    totalDailyAmount = 26900
                )
            ),
            Pair(
                testMyDataTbrDataGermanScreen,
                ParsedScreen.MyDataTbrDataScreen(
                    index = 1, totalNumEntries = 30,
                    timestamp = LocalDateTime(year = 0, month = 1, day = 28, hour = 11, minute = 0, second = 0),
                    percentage = 110, durationInMinutes = 0
                )
            ),
            Pair(
                testMyDataBolusDataSlovenianScreen,
                ParsedScreen.MyDataBolusDataScreen(
                    index = 1, totalNumEntries = 30,
                    timestamp = LocalDateTime(year = 0, month = 1, day = 1, hour = 23, minute = 21, second = 0),
                    bolusAmount = 3200, bolusType = MyDataBolusType.MULTI_WAVE, durationInMinutes = 43
                )
            ),
            Pair(
                testMyDataErrorDataSlovenianScreen,
                ParsedScreen.MyDataErrorDataScreen(
                    index = 1, totalNumEntries = 30,
                    timestamp = LocalDateTime(year = 0, month = 3, day = 8, hour = 17, minute = 31, second = 0),
                    alert = AlertScreenContent.Warning(6, AlertScreenContent.AlertScreenState.HISTORY_ENTRY)
                )
            ),
            Pair(
                testMyDataDailyTotalsSlovenianScreen,
                ParsedScreen.MyDataDailyTotalsScreen(
                    index = 1, totalNumEntries = 30, date = LocalDate(year = 0, day = 8, month = 3),
                    totalDailyAmount = 32100
                )
            ),
            Pair(
                testMyDataTbrDataSlovenianScreen,
                ParsedScreen.MyDataTbrDataScreen(
                    index = 1, totalNumEntries = 30,
                    timestamp = LocalDateTime(year = 0, month = 3, day = 8, hour = 17, minute = 31, second = 0),
                    percentage = 110, durationInMinutes = 1
                )
            ),
            Pair(
                testMyDataBolusDataLithuanianScreen,
                ParsedScreen.MyDataBolusDataScreen(
                    index = 1, totalNumEntries = 30,
                    timestamp = LocalDateTime(year = 0, month = 1, day = 1, hour = 23, minute = 21, second = 0),
                    bolusAmount = 3200, bolusType = MyDataBolusType.MULTI_WAVE, durationInMinutes = 43
                )
            ),
            Pair(
                testMyDataErrorDataLithuanianScreen,
                ParsedScreen.MyDataErrorDataScreen(
                    index = 1, totalNumEntries = 30,
                    timestamp = LocalDateTime(year = 0, month = 3, day = 8, hour = 20, minute = 6, second = 0),
                    alert = AlertScreenContent.Warning(6, AlertScreenContent.AlertScreenState.HISTORY_ENTRY)
                )
            ),
            Pair(
                testMyDataDailyTotalsLithuanianScreen,
                ParsedScreen.MyDataDailyTotalsScreen(
                    index = 1, totalNumEntries = 30, date = LocalDate(year = 0, day = 8, month = 3),
                    totalDailyAmount = 33600
                )
            ),
            Pair(
                testMyDataTbrDataLithuanianScreen,
                ParsedScreen.MyDataTbrDataScreen(
                    index = 1, totalNumEntries = 30,
                    timestamp = LocalDateTime(year = 0, month = 3, day = 8, hour = 20, minute = 6, second = 0),
                    percentage = 110, durationInMinutes = 1
                )
            ),
        )

        for (testScreen in testScreens) {
            val testContext = TestContext(testScreen.first, 0)

            val titleString = (StringParser().parse(testContext.parseContext) as ParseResult.Value<*>).value as String
            val titleId = knownScreenTitles[titleString]
            assertNotNull(titleId)

            val result: ParseResult = when (titleId) {
                TitleID.BOLUS_DATA   -> MyDataBolusDataScreenParser().parse(testContext.parseContext)
                TitleID.ERROR_DATA   -> MyDataErrorDataScreenParser().parse(testContext.parseContext)
                TitleID.DAILY_TOTALS -> MyDataDailyTotalsScreenParser().parse(testContext.parseContext)
                TitleID.TBR_DATA     -> MyDataTbrDataScreenParser().parse(testContext.parseContext)

                else                 -> {
                    fail("Unknown title string \"$titleString\"")
                }
            }

            assertEquals(ParseResult.Value::class, result::class)
            val screen = (result as ParseResult.Value<*>).value as ParsedScreen
            assertEquals(testScreen.second, screen)
        }
    }

    @Test
    fun checkAlertScreenTextParsing() {
        val testScreens = listOf(
            Pair(
                AlertSnoozeAndConfirmScreens.testAlertScreenSnoozeTextEnglishScreen,
                AlertScreenContent.Warning(6, AlertScreenContent.AlertScreenState.TO_SNOOZE)
            ),
            Pair(
                AlertSnoozeAndConfirmScreens.testAlertScreenConfirmTextEnglishScreen,
                AlertScreenContent.Warning(6, AlertScreenContent.AlertScreenState.TO_CONFIRM)
            ),
            Pair(
                AlertSnoozeAndConfirmScreens.testAlertScreenSnoozeTextSpanishScreen,
                AlertScreenContent.Warning(6, AlertScreenContent.AlertScreenState.TO_SNOOZE)
            ),
            Pair(
                AlertSnoozeAndConfirmScreens.testAlertScreenConfirmTextSpanishScreen,
                AlertScreenContent.Warning(6, AlertScreenContent.AlertScreenState.TO_CONFIRM)
            ),
            Pair(
                AlertSnoozeAndConfirmScreens.testAlertScreenSnoozeTextFrenchScreen,
                AlertScreenContent.Warning(6, AlertScreenContent.AlertScreenState.TO_SNOOZE)
            ),
            Pair(
                AlertSnoozeAndConfirmScreens.testAlertScreenConfirmTextFrenchScreen,
                AlertScreenContent.Warning(6, AlertScreenContent.AlertScreenState.TO_CONFIRM)
            ),
            Pair(
                AlertSnoozeAndConfirmScreens.testAlertScreenSnoozeTextItalianScreen,
                AlertScreenContent.Warning(6, AlertScreenContent.AlertScreenState.TO_SNOOZE)
            ),
            Pair(
                AlertSnoozeAndConfirmScreens.testAlertScreenConfirmTextItalianScreen,
                AlertScreenContent.Warning(6, AlertScreenContent.AlertScreenState.TO_CONFIRM)
            ),
            Pair(
                AlertSnoozeAndConfirmScreens.testAlertScreenSnoozeTextRussianScreen,
                AlertScreenContent.Warning(6, AlertScreenContent.AlertScreenState.TO_SNOOZE)
            ),
            Pair(
                AlertSnoozeAndConfirmScreens.testAlertScreenConfirmTextRussianScreen,
                AlertScreenContent.Warning(6, AlertScreenContent.AlertScreenState.TO_CONFIRM)
            ),
            Pair(
                AlertSnoozeAndConfirmScreens.testAlertScreenSnoozeTextTurkishScreen,
                AlertScreenContent.Warning(6, AlertScreenContent.AlertScreenState.TO_SNOOZE)
            ),
            Pair(
                AlertSnoozeAndConfirmScreens.testAlertScreenConfirmTextTurkishScreen,
                AlertScreenContent.Warning(6, AlertScreenContent.AlertScreenState.TO_CONFIRM)
            ),
            Pair(
                AlertSnoozeAndConfirmScreens.testAlertScreenSnoozeTextPolishScreen,
                AlertScreenContent.Warning(6, AlertScreenContent.AlertScreenState.TO_SNOOZE)
            ),
            Pair(
                AlertSnoozeAndConfirmScreens.testAlertScreenConfirmTextPolishScreen,
                AlertScreenContent.Warning(6, AlertScreenContent.AlertScreenState.TO_CONFIRM)
            ),
            Pair(
                AlertSnoozeAndConfirmScreens.testAlertScreenSnoozeTextCzechScreen,
                AlertScreenContent.Warning(6, AlertScreenContent.AlertScreenState.TO_SNOOZE)
            ),
            Pair(
                AlertSnoozeAndConfirmScreens.testAlertScreenConfirmTextCzechScreen,
                AlertScreenContent.Warning(6, AlertScreenContent.AlertScreenState.TO_CONFIRM)
            ),
            Pair(
                AlertSnoozeAndConfirmScreens.testAlertScreenSnoozeTextHungarianScreen,
                AlertScreenContent.Warning(6, AlertScreenContent.AlertScreenState.TO_SNOOZE)
            ),
            Pair(
                AlertSnoozeAndConfirmScreens.testAlertScreenConfirmTextHungarianScreen,
                AlertScreenContent.Warning(6, AlertScreenContent.AlertScreenState.TO_CONFIRM)
            ),
            Pair(
                AlertSnoozeAndConfirmScreens.testAlertScreenSnoozeTextSlovakScreen,
                AlertScreenContent.Warning(6, AlertScreenContent.AlertScreenState.TO_SNOOZE)
            ),
            Pair(
                AlertSnoozeAndConfirmScreens.testAlertScreenConfirmTextSlovakScreen,
                AlertScreenContent.Warning(6, AlertScreenContent.AlertScreenState.TO_CONFIRM)
            ),
            Pair(
                AlertSnoozeAndConfirmScreens.testAlertScreenSnoozeTextRomanianScreen,
                AlertScreenContent.Warning(6, AlertScreenContent.AlertScreenState.TO_SNOOZE)
            ),
            Pair(
                AlertSnoozeAndConfirmScreens.testAlertScreenConfirmTextRomanianScreen,
                AlertScreenContent.Warning(6, AlertScreenContent.AlertScreenState.TO_CONFIRM)
            ),
            Pair(
                AlertSnoozeAndConfirmScreens.testAlertScreenSnoozeTextCroatianScreen,
                AlertScreenContent.Warning(6, AlertScreenContent.AlertScreenState.TO_SNOOZE)
            ),
            Pair(
                AlertSnoozeAndConfirmScreens.testAlertScreenConfirmTextCroatianScreen,
                AlertScreenContent.Warning(6, AlertScreenContent.AlertScreenState.TO_CONFIRM)
            ),
            Pair(
                AlertSnoozeAndConfirmScreens.testAlertScreenSnoozeTextDutchScreen,
                AlertScreenContent.Warning(6, AlertScreenContent.AlertScreenState.TO_SNOOZE)
            ),
            Pair(
                AlertSnoozeAndConfirmScreens.testAlertScreenConfirmTextDutchScreen,
                AlertScreenContent.Warning(6, AlertScreenContent.AlertScreenState.TO_CONFIRM)
            ),
            Pair(
                AlertSnoozeAndConfirmScreens.testAlertScreenSnoozeTextGreekScreen,
                AlertScreenContent.Warning(6, AlertScreenContent.AlertScreenState.TO_SNOOZE)
            ),
            Pair(
                AlertSnoozeAndConfirmScreens.testAlertScreenConfirmTextGreekScreen,
                AlertScreenContent.Warning(6, AlertScreenContent.AlertScreenState.TO_CONFIRM)
            ),
            Pair(
                AlertSnoozeAndConfirmScreens.testAlertScreenSnoozeTextFinnishScreen,
                AlertScreenContent.Warning(6, AlertScreenContent.AlertScreenState.TO_SNOOZE)
            ),
            Pair(
                AlertSnoozeAndConfirmScreens.testAlertScreenConfirmTextFinnishScreen,
                AlertScreenContent.Warning(6, AlertScreenContent.AlertScreenState.TO_CONFIRM)
            ),
            Pair(
                AlertSnoozeAndConfirmScreens.testAlertScreenSnoozeTextNorwegianScreen,
                AlertScreenContent.Warning(6, AlertScreenContent.AlertScreenState.TO_SNOOZE)
            ),
            Pair(
                AlertSnoozeAndConfirmScreens.testAlertScreenConfirmTextNorwegianScreen,
                AlertScreenContent.Warning(6, AlertScreenContent.AlertScreenState.TO_CONFIRM)
            ),
            Pair(
                AlertSnoozeAndConfirmScreens.testAlertScreenSnoozeTextPortugueseScreen,
                AlertScreenContent.Warning(6, AlertScreenContent.AlertScreenState.TO_SNOOZE)
            ),
            Pair(
                AlertSnoozeAndConfirmScreens.testAlertScreenConfirmTextPortugueseScreen,
                AlertScreenContent.Warning(6, AlertScreenContent.AlertScreenState.TO_CONFIRM)
            ),
            Pair(
                AlertSnoozeAndConfirmScreens.testAlertScreenSnoozeTextSwedishScreen,
                AlertScreenContent.Warning(6, AlertScreenContent.AlertScreenState.TO_SNOOZE)
            ),
            Pair(
                AlertSnoozeAndConfirmScreens.testAlertScreenConfirmTextSwedishScreen,
                AlertScreenContent.Warning(6, AlertScreenContent.AlertScreenState.TO_CONFIRM)
            ),
            Pair(
                AlertSnoozeAndConfirmScreens.testAlertScreenSnoozeTextDanishScreen,
                AlertScreenContent.Warning(6, AlertScreenContent.AlertScreenState.TO_SNOOZE)
            ),
            Pair(
                AlertSnoozeAndConfirmScreens.testAlertScreenConfirmTextDanishScreen,
                AlertScreenContent.Warning(6, AlertScreenContent.AlertScreenState.TO_CONFIRM)
            ),
            Pair(
                AlertSnoozeAndConfirmScreens.testAlertScreenSnoozeTextGermanScreen,
                AlertScreenContent.Warning(6, AlertScreenContent.AlertScreenState.TO_SNOOZE)
            ),
            Pair(
                AlertSnoozeAndConfirmScreens.testAlertScreenConfirmTextGermanScreen,
                AlertScreenContent.Warning(6, AlertScreenContent.AlertScreenState.TO_CONFIRM)
            ),
            Pair(
                AlertSnoozeAndConfirmScreens.testAlertScreenSnoozeTextSlovenianScreen,
                AlertScreenContent.Warning(6, AlertScreenContent.AlertScreenState.TO_SNOOZE)
            ),
            Pair(
                AlertSnoozeAndConfirmScreens.testAlertScreenConfirmTextSlovenianScreen,
                AlertScreenContent.Warning(6, AlertScreenContent.AlertScreenState.TO_CONFIRM)
            ),
            Pair(
                AlertSnoozeAndConfirmScreens.testAlertScreenSnoozeTextLithuanianScreen,
                AlertScreenContent.Warning(6, AlertScreenContent.AlertScreenState.TO_SNOOZE)
            ),
            Pair(
                AlertSnoozeAndConfirmScreens.testAlertScreenConfirmTextLithuanianScreen,
                AlertScreenContent.Warning(6, AlertScreenContent.AlertScreenState.TO_CONFIRM)
            ),
        )

        for (testScreen in testScreens) {
            val testContext = TestContext(testScreen.first, 0, skipTitleString = true)
            val result = AlertScreenParser().parse(testContext.parseContext)

            assertEquals(ParseResult.Value::class, result::class)
            val alertScreen = (result as ParseResult.Value<*>).value as ParsedScreen.AlertScreen
            assertEquals(testScreen.second, alertScreen.content)
        }
    }

    @Test
    fun checkToplevelScreenParsing() {
        val testScreens = listOf(
            Pair(testFrameStandardBolusMenuScreen, ParsedScreen.StandardBolusMenuScreen),
            Pair(testFrameExtendedBolusMenuScreen, ParsedScreen.ExtendedBolusMenuScreen),
            Pair(testFrameMultiwaveBolusMenuScreen, ParsedScreen.MultiwaveBolusMenuScreen),
            Pair(testFrameBluetoothSettingsMenuScreen, ParsedScreen.BluetoothSettingsMenuScreen),
            Pair(testFrameMenuSettingsMenuScreen, ParsedScreen.MenuSettingsMenuScreen),
            Pair(testFrameMyDataMenuScreen, ParsedScreen.MyDataMenuScreen),
            Pair(testFrameBasalRateProfileSelectionMenuScreen, ParsedScreen.BasalRateProfileSelectionMenuScreen),
            Pair(testFramePumpSettingsMenuScreen, ParsedScreen.PumpSettingsMenuScreen),
            Pair(testFrameReminderSettingsMenuScreen, ParsedScreen.ReminderSettingsMenuScreen),
            Pair(testFrameTimeAndDateSettingsMenuScreen, ParsedScreen.TimeAndDateSettingsMenuScreen),
            Pair(testFrameStopPumpMenuScreen, ParsedScreen.StopPumpMenuScreen),
            Pair(testFrameTemporaryBasalRateMenuScreen, ParsedScreen.TemporaryBasalRateMenuScreen),
            Pair(testFrameTherapySettingsMenuScreen, ParsedScreen.TherapySettingsMenuScreen),
            Pair(testFrameProgramBasalRate1MenuScreen, ParsedScreen.BasalRate1ProgrammingMenuScreen),
            Pair(testFrameProgramBasalRate2MenuScreen, ParsedScreen.BasalRate2ProgrammingMenuScreen),
            Pair(testFrameProgramBasalRate3MenuScreen, ParsedScreen.BasalRate3ProgrammingMenuScreen),
            Pair(testFrameProgramBasalRate4MenuScreen, ParsedScreen.BasalRate4ProgrammingMenuScreen),
            Pair(testFrameProgramBasalRate5MenuScreen, ParsedScreen.BasalRate5ProgrammingMenuScreen)
        )

        for (testScreen in testScreens) {
            val testContext = TestContext(testScreen.first, 0)
            val result = ToplevelScreenParser().parse(testContext.parseContext)
            assertEquals(ParseResult.Value::class, result::class)
            val screen = (result as ParseResult.Value<*>).value as ParsedScreen
            assertEquals(testScreen.second, screen)
        }
    }
}

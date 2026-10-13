package info.nightscout.comboctl.parser

import info.nightscout.comboctl.base.ComboException
import info.nightscout.comboctl.base.DisplayFrame
import info.nightscout.comboctl.base.combinedDateTime
import info.nightscout.comboctl.base.timeWithoutDate
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.atTime
import kotlin.reflect.KClassifier

/*****************************************
 *** Screen and screen content classes ***
 *****************************************/

/* Screens are the final result of parser runs. */

/**
 * Possible bolus types in the bolus data screen in the "My Data" bolus history.
 */
enum class MyDataBolusType {
    STANDARD,
    MULTI_WAVE,
    EXTENDED
}

/**
 * Possible battery states in the main screens.
 */
enum class BatteryState {
    NO_BATTERY,
    LOW_BATTERY,
    FULL_BATTERY
}

private fun batteryStateFromSymbol(symbol: SmallSymbol?): BatteryState =
    when (symbol) {
        SmallSymbol.NO_BATTERY -> BatteryState.NO_BATTERY
        SmallSymbol.LOW_BATTERY -> BatteryState.LOW_BATTERY
        else -> BatteryState.FULL_BATTERY
    }

/**
 * Possible contents of [ParsedScreen.MainScreen].
 */
sealed class MainScreenContent {
    data class Normal(
        val currentTime: LocalDateTime,
        val activeBasalProfileNumber: Int,
        val currentBasalRateFactor: Int,
        val batteryState: BatteryState
    ) : MainScreenContent()

    data class Stopped(
        val currentDateTime: LocalDateTime,
        val batteryState: BatteryState
    ) : MainScreenContent()

    data class Tbr(
        val currentTime: LocalDateTime,
        val remainingTbrDurationInMinutes: Int,
        val tbrPercentage: Int,
        val activeBasalProfileNumber: Int,
        val currentBasalRateFactor: Int,
        val batteryState: BatteryState
    ) : MainScreenContent()

    data class ExtendedOrMultiwaveBolus(
        val currentTime: LocalDateTime,
        val remainingBolusDurationInMinutes: Int,
        val isExtendedBolus: Boolean,
        val remainingBolusAmount: Int,
        val tbrIsActive: Boolean,
        val activeBasalProfileNumber: Int,
        val currentBasalRateFactor: Int,
        val batteryState: BatteryState
    ) : MainScreenContent()
}

/**
 * Possible contents of alert (= warning/error) screens.
 */
sealed class AlertScreenContent {
    enum class AlertScreenState {
        TO_SNOOZE,
        TO_CONFIRM,
        // Used when the alert is an error. The text in error screens is not
        // interpreted, since it is anyway fully up to the user to interpret it.
        ERROR_TEXT,
        HISTORY_ENTRY
    }

    data class Warning(val code: Int, val state: AlertScreenState) : AlertScreenContent()
    data class Error(val code: Int, val state: AlertScreenState) : AlertScreenContent()

    /**
     * "Content" while the alert symbol & code currently are "blinked out".
     */
    object None : AlertScreenContent()
}

/**
 * Exception thrown when an alert screens appear.
 *
 * @property alertScreenContent The content of the alert screen(s).
 */
class AlertScreenException(val alertScreenContent: AlertScreenContent) :
    ComboException("RT alert screen appeared with content: $alertScreenContent")

/**
 * Result of a successful [ToplevelScreenParser] run.
 *
 * Subclasses which have hour quantities use a 0..23 range for the hour.
 * (Even if the screen showed the hour in the 12-hour AM/PM format, it is
 * converted to the 24-hour format.) Minute quantities use a 0..59 range.
 *
 * Insulin units use an integer-encoded-decimal scheme. The last 3 digits of
 * the integer make up the 3 most significant fractional digits of a decimal.
 * For example, "37.5" is encoded as 37500, "10" as 10000, "0.02" as 20 etc.
 *
 * If [isBlinkedOut] is true, then the actual contents of the screen are
 * currently "blinked out", that is, the screen is blinking, and it is
 * at the moment in the phase when the contents aren't shown.
 */
sealed class ParsedScreen(val isBlinkedOut: Boolean = false) {
    object UnrecognizedScreen : ParsedScreen()

    data class MainScreen(val content: MainScreenContent) : ParsedScreen()

    object BasalRateProfileSelectionMenuScreen : ParsedScreen()
    object BluetoothSettingsMenuScreen : ParsedScreen()
    object ExtendedBolusMenuScreen : ParsedScreen()
    object MultiwaveBolusMenuScreen : ParsedScreen()
    object MenuSettingsMenuScreen : ParsedScreen()
    object MyDataMenuScreen : ParsedScreen()
    object BasalRate1ProgrammingMenuScreen : ParsedScreen()
    object BasalRate2ProgrammingMenuScreen : ParsedScreen()
    object BasalRate3ProgrammingMenuScreen : ParsedScreen()
    object BasalRate4ProgrammingMenuScreen : ParsedScreen()
    object BasalRate5ProgrammingMenuScreen : ParsedScreen()
    object PumpSettingsMenuScreen : ParsedScreen()
    object ReminderSettingsMenuScreen : ParsedScreen()
    object TimeAndDateSettingsMenuScreen : ParsedScreen()
    object StandardBolusMenuScreen : ParsedScreen()
    object StopPumpMenuScreen : ParsedScreen()
    object TemporaryBasalRateMenuScreen : ParsedScreen()
    object TherapySettingsMenuScreen : ParsedScreen()

    data class AlertScreen(val content: AlertScreenContent) :
        ParsedScreen(isBlinkedOut = (content is AlertScreenContent.None))

    data class BasalRateTotalScreen(val totalNumUnits: Int, val basalRateNumber: Int) : ParsedScreen()
    data class BasalRateFactorSettingScreen(
        val beginTime: LocalDateTime,
        val endTime: LocalDateTime,
        val numUnits: Int?,
        val basalRateNumber: Int
    ) : ParsedScreen(isBlinkedOut = (numUnits == null))

    data class TemporaryBasalRatePercentageScreen(val percentage: Int?, val remainingDurationInMinutes: Int?) :
        ParsedScreen(isBlinkedOut = (percentage == null))
    data class TemporaryBasalRateDurationScreen(val durationInMinutes: Int?) :
        ParsedScreen(isBlinkedOut = (durationInMinutes == null))

    data class QuickinfoMainScreen(val quickinfo: Quickinfo) : ParsedScreen()

    data class TimeAndDateSettingsHourScreen(val hour: Int?) :
        ParsedScreen(isBlinkedOut = (hour == null))
    data class TimeAndDateSettingsMinuteScreen(val minute: Int?) :
        ParsedScreen(isBlinkedOut = (minute == null))
    data class TimeAndDateSettingsYearScreen(val year: Int?) :
        ParsedScreen(isBlinkedOut = (year == null))
    data class TimeAndDateSettingsMonthScreen(val month: Int?) :
        ParsedScreen(isBlinkedOut = (month == null))
    data class TimeAndDateSettingsDayScreen(val day: Int?) :
        ParsedScreen(isBlinkedOut = (day == null))

    /**
     * Bolus history entry in the "My Data" section.
     */
    data class MyDataBolusDataScreen(
        /**
         * Index of the currently shown bolus. Valid range is 1 to [totalNumEntries].
         */
        val index: Int,

        /**
         * Total number of bolus entries in the pump's history.
         */
        val totalNumEntries: Int,

        /**
         * Timestamp of when the bolus finished, in localtime.
         */
        val timestamp: LocalDateTime,

        /**
         * Bolus amount in 0.1 IU units.
         */
        val bolusAmount: Int,

        /**
         * Type of the bolus (standard / extended / multiwave).
         */
        val bolusType: MyDataBolusType,

        /**
         * Duration of the bolus in minutes. Set to null if this is a standard bolus.
         */
        val durationInMinutes: Int?
    ) : ParsedScreen()

    /**
     * Alert history entry in the "My Data" section.
     *
     * (These can be both errors and warnings. The section is called "error data" though.)
     */
    data class MyDataErrorDataScreen(
        /**
         * Index of the currently shown alert. Valid range is 1 to [totalNumEntries].
         */
        val index: Int,

        /**
         * Total number of alert entries in the pump's history.
         */
        val totalNumEntries: Int,

        /**
         * Timestamp of when the alert occurred, in localtime.
         */
        val timestamp: LocalDateTime,

        /**
         * The alert that occurred.
         */
        val alert: AlertScreenContent
    ) : ParsedScreen()

    /**
     * Total daily dosage (TDD) history entry in the "My Data" section.
     */
    data class MyDataDailyTotalsScreen(
        /**
         * Index of the currently shown TDD entry. Valid range is 1 to [totalNumEntries].
         */
        val index: Int,

        /**
         * Total number of TDD entries in the pump's history.
         */
        val totalNumEntries: Int,

        /**
         * Day for which this entry specifies the TDD amount, in localtime.
         */
        val date: LocalDate,

        /**
         * TDD amount in 1 IU units.
         */
        val totalDailyAmount: Int
    ) : ParsedScreen()

    /**
     * TBR history entry in the "My Data" section.
     */
    data class MyDataTbrDataScreen(
        /**
         * Index of the currently shown TBR entry. Valid range is 1 to [totalNumEntries].
         */
        val index: Int,

        /**
         * Total number of TBR entries in the pump's history.
         */
        val totalNumEntries: Int,

        /**
         * Timestamp when this TBR ended.
         */
        val timestamp: LocalDateTime,

        /**
         * TBR percentage, in the 0-500 range.
         */
        val percentage: Int,

        /**
         * TBR duration in minutes, in the 15-1440 range.
         */
        val durationInMinutes: Int
    ) : ParsedScreen()
}

/***************************************************
 *** Fundamental parsers and parser base classes ***
 ***************************************************/

private fun amPmTo24Hour(hour: Int, amPm: String) =
    if ((hour == 12) && (amPm == "AM"))
        0
    else if ((hour != 12) && (amPm == "PM"))
        hour + 12
    else if (hour == 24)
        0
    else
        hour

/**
 * Context used to keep track of parse state.
 */
class ParseContext(
    val tokens: List<Token>,
    var currentIndex: Int,
    var topLeftTime: LocalDateTime? = null
) {
    fun hasMoreTokens() = (currentIndex < tokens.size)

    fun nextToken() = tokens[currentIndex]

    fun advance() = currentIndex++
}

/**
 * Possible parser results.
 *
 * @property isSuccess true if the result is considered a success.
 */
sealed class ParseResult(val isSuccess: Boolean) {
    /** Used when the parser returns a value. This encapsulates  said value. */
    class Value<T>(val value: T) : ParseResult(true)

    /**
     * Indicates that the parser successfully parsed the expected
     * content, but that the content has no values. This is used
     * if for a certain symbol is expected to be there, but is not
     * actually needed as a value. NoValue results will not be
     * included in @Sequence results produced by @SequenceParser.*/
    object NoValue : ParseResult(true)

    /**
     * Used by @OptionalParser, and returned if the optional
     * content specified in that parser was not found. This
     * value is still considered a success since the missing
     * content is _optional_.
     */
    object Null : ParseResult(true)

    /**
     * Returned by @Parser.parse if the @ParseContext
     * reaches the end of the list of tokens.
     */
    object EndOfTokens : ParseResult(false)

    /**
     * Indicates that the parser did not find the expected content.
     */
    object Failed : ParseResult(false)

    /**
     * Result of a @SequenceParser.
     *
     * For convenience, this has the @valueAt and @valueAtOrNull
     * functions to take a value out of that sequence. Example:
     * If element no. 2 in the sequence is an Int, then this
     * call gets it:
     *
     *     val value = (parseResult as ParseResult.Sequence).valueAt<Int>(2)
     *
     * @valueAtOrNull works similarly, except that it returns null
     * if the parse result at that index is not of type Value<*>.
     *
     * Note that trying to access an index beyond the valid range
     * still produces an [IndexOutOfBoundsException] even when
     * using @valueAtOrNull.
     */
    class Sequence(val values: List<ParseResult>) : ParseResult(true) {
        inline fun <reified T> valueAt(index: Int) = (values[index] as Value<*>).value as T

        inline fun <reified T> valueAtOrNull(index: Int): T? {
            return when (val value = values[index]) {
                is Value<*> -> value.value as T
                else -> null
            }
        }

        val size: Int
            get() = values.size
    }
}

/**
 * Parser base class.
 *
 * @property returnsValue If true, parsing will produce a value.
 *           The main parser which doesn't do that is [SingleGlyphParser].
 */
open class Parser(val returnsValue: Boolean = true) {
    fun parse(parseContext: ParseContext): ParseResult {
        if (!parseContext.hasMoreTokens())
            return ParseResult.EndOfTokens

        // Preserve the original index in the context. That way, should
        // parseImpl fail, the original value of currentIndex prior to
        // the parseImpl call can be restored. This is especially important
        // when using the FirstSuccessParser, since that one tries multiple
        // parsers until one succeds. Restoring the currentIndex is essential
        // to give all those parsers the chance to parse the same tokens.
        val originalIndex = parseContext.currentIndex
        val result = parseImpl(parseContext)
        if (!result.isSuccess)
            parseContext.currentIndex = originalIndex

        return result
    }

    protected open fun parseImpl(parseContext: ParseContext): ParseResult = ParseResult.Failed
}

/**
 * Parses a single specific glyph.
 *
 * This is used in cases where the screen is expected to have a specific
 * glyph at a certain position in the list of tokens. One example would
 * be a clock symbol at the top left corner.
 *
 * Since this looks for a specific glyph, it does not have an actual
 * return value. Instead, the information about whether or not parsing
 * succeeded already tells everything. That's why this either returns
 * [ParseResult.NoValue] (when the specified glyph was found) or
 * [ParseResult.Failed] (when the glyph was not found).
 */
class SingleGlyphParser(private val glyph: Glyph) : Parser(returnsValue = false) {
    override fun parseImpl(parseContext: ParseContext): ParseResult {
        return if (parseContext.nextToken().glyph == glyph) {
            parseContext.advance()
            ParseResult.NoValue
        } else
            ParseResult.Failed
    }
}

/**
 * Parses a single glyph of a specific type.
 *
 * Similarly to [SingleGlyphParser], this parses the next token as
 * a glyph, returning [ParseResult.Failed] if that token is not a
 * glyph or the specified [glyphType]. Unlike [SingleGlyphParser],
 * this does have an actual return value, since this "only" specifies
 * the glyph _type_, not the actual glyph.
 *
 * This parses is used for example when a screen can contain a token
 * that has a symbol at a specific position, and the symbol indicates
 * something (like whether an alert screen contains a warning or an error).
 *
 * The type is specified via the ::class property. Example:
 *
 *     SingleGlyphTypeParser(Glyph.LargeSymbol::class)
 *
 * @property glyphType Type of the glyph to expect.
 */
class SingleGlyphTypeParser(private val glyphType: KClassifier) : Parser() {
    override fun parseImpl(parseContext: ParseContext): ParseResult {
        val token = parseContext.nextToken()

        return if (token.glyph::class == glyphType) {
            parseContext.advance()
            ParseResult.Value(token.glyph)
        } else
            ParseResult.Failed
    }
}

/**
 * Parses the available tokens as one string until a non-string glyph is found or the end is reached.
 *
 * Strings can consist of characters and one of the ".:/()-" symbols.
 * This also parses whitespaces and adds them to the produced string.
 * Whitespaces are detected by measuring the distance between glyphs.
 */
class StringParser : Parser() {
    override fun parseImpl(parseContext: ParseContext): ParseResult {
        var parsedString = ""
        var lastToken: Token? = null

        while (parseContext.hasMoreTokens()) {
            val token = parseContext.nextToken()
            val glyph = token.glyph

            // Check if there's a newline or space between the matches.
            // If so, we'll insert a whitespace character into the string.
            val prependWhitespace = if (lastToken != null)
                checkForWhitespaceAndNewline(lastToken, token)
            else
                false

            val character = when (glyph) {
                is Glyph.SmallCharacter -> glyph.character
                Glyph.SmallSymbol(SmallSymbol.DOT) -> '.'
                Glyph.SmallSymbol(SmallSymbol.SEPARATOR) -> ':'
                Glyph.SmallSymbol(SmallSymbol.DIVIDE) -> '/'
                Glyph.SmallSymbol(SmallSymbol.BRACKET_LEFT) -> '('
                Glyph.SmallSymbol(SmallSymbol.BRACKET_RIGHT) -> ')'
                Glyph.SmallSymbol(SmallSymbol.MINUS) -> '-'
                else -> break
            }

            if (prependWhitespace)
                parsedString += ' '

            parsedString += character
            parseContext.advance()
            lastToken = token
        }

        return if (parsedString.isEmpty())
            ParseResult.Failed
        else
            ParseResult.Value(parsedString.uppercase())
    }

    // If true, then there is a whitespace between the matches,
    // or the second match is located in a line below the first one.
    private fun checkForWhitespaceAndNewline(firstToken: Token, secondToken: Token): Boolean {
        val y1 = firstToken.y
        val y2 = secondToken.y

        if ((y1 + firstToken.pattern.height + 1) == y2)
            return true

        val x1 = firstToken.x
        val x2 = secondToken.x

        return (x1 + firstToken.pattern.width + 1 + 3) < x2
    }
}

enum class GlyphDigitParseMode {
    ALL_DIGITS,
    SMALL_DIGITS_ONLY,
    LARGE_DIGITS_ONLY
}

/**
 * Parses the available tokens as one integer until a non-integer glyph is found or the end is reached.
 *
 * @property parseMode Parse mode. Useful for restricting the valid integer glyphs.
 * @property checkForWhitespace If set to true, this checks for whitespaces
 *           and stops parsing if a whitespace is found. Useful for when there
 *           are multiple integers visually in a sequence.
 */
class IntegerParser(
    private val parseMode: GlyphDigitParseMode = GlyphDigitParseMode.ALL_DIGITS,
    private val checkForWhitespace: Boolean = false
) : Parser() {

    override fun parseImpl(parseContext: ParseContext): ParseResult {
        var integer = 0
        var foundDigits = false
        var previousToken: Token? = null

        while (parseContext.hasMoreTokens()) {
            val token = parseContext.nextToken()

            if (checkForWhitespace && (previousToken != null)) {
                val x1 = previousToken.x
                val x2 = token.x
                if ((x1 + previousToken.pattern.width + 1 + 3) < x2)
                    break
            }

            when (val glyph = token.glyph) {
                is Glyph.SmallDigit ->
                    when (parseMode) {
                        GlyphDigitParseMode.ALL_DIGITS,
                        GlyphDigitParseMode.SMALL_DIGITS_ONLY -> integer = integer * 10 + glyph.digit
                        else -> break
                    }

                is Glyph.LargeDigit ->
                    when (parseMode) {
                        GlyphDigitParseMode.ALL_DIGITS,
                        GlyphDigitParseMode.LARGE_DIGITS_ONLY -> integer = integer * 10 + glyph.digit
                        else -> break
                    }

                else -> break
            }

            foundDigits = true

            parseContext.advance()

            previousToken = token
        }

        return if (foundDigits)
            ParseResult.Value(integer)
        else
            ParseResult.Failed
    }
}

/**
 * Parses the available tokens as one decimal until a non-decimal glyph is found or the end is reached.
 *
 * Decimals are made of digits and the dot symbol. They are encoded in an Int value,
 * using a fixed-point decimal representation. The point is shifted by 3 digits to
 * the left. If for example the decimal "2.13" is parsed, the resulting Int is set
 * to 2130. This is preferred over floating point data types, since the latter can
 * be lossy, depending on the parsed value (because some decimals cannot be directly
 * represented by IEEE 754 floating point math).
 *
 * This parser also works if the dot symbol is missing. Then, the parsed number
 * is treated as a decimal that only has an integer portion.
 */
class DecimalParser : Parser() {
    override fun parseImpl(parseContext: ParseContext): ParseResult {
        var integerPart = 0
        var fractionalPart = 0
        var parseFractional = false
        var foundDigits = false

        while (parseContext.hasMoreTokens()) {
            val token = parseContext.nextToken()

            when (val glyph = token.glyph) {
                is Glyph.SmallDigit -> integerPart = integerPart * 10 + glyph.digit
                is Glyph.LargeDigit -> integerPart = integerPart * 10 + glyph.digit

                Glyph.SmallSymbol(SmallSymbol.DOT),
                Glyph.LargeSymbol(LargeSymbol.DOT) -> {
                    parseFractional = true
                    parseContext.advance()
                    break
                }

                else -> break
            }

            foundDigits = true

            parseContext.advance()
        }

        if (parseFractional) {
            var numFractionalDigits = 0
            while (parseContext.hasMoreTokens() && (numFractionalDigits < 3)) {
                val token = parseContext.nextToken()

                when (val glyph = token.glyph) {
                    is Glyph.SmallDigit -> {
                        fractionalPart = fractionalPart * 10 + glyph.digit
                        numFractionalDigits++
                    }

                    is Glyph.LargeDigit -> {
                        fractionalPart = fractionalPart * 10 + glyph.digit
                        numFractionalDigits++
                    }

                    else -> break
                }

                foundDigits = true

                parseContext.advance()
            }

            for (i in 0 until (3 - numFractionalDigits)) {
                fractionalPart *= 10
            }
        }

        return if (foundDigits)
            ParseResult.Value(integerPart * 1000 + fractionalPart)
        else
            ParseResult.Failed
    }
}

/**
 * Parses the available tokens as a date.
 *
 * The following date formats are used by the Combo:
 *
 *   DD.MM
 *   MM/DD
 *   DD.MM.YY
 *   MM/DD/YY
 *
 * The parser handles all of these cases.
 *
 * The result is a [DateTime] instance with the hour/minute/second fields set to zero.
 */
class DateParser : Parser() {
    private val dateRegex = "(\\d\\d)([/\\.])(\\d\\d)([/\\.](\\d\\d))?".toRegex()
    private val asciiDigitOffset = '0'.code

    override fun parseImpl(parseContext: ParseContext): ParseResult {
        // To be able to handle all date formats without too much
        // convoluted (and error prone) parsing code, we use regex.

        var dateString = ""

        while (parseContext.hasMoreTokens()) {
            val token = parseContext.nextToken()
            val glyph = token.glyph

            dateString += when (glyph) {
                // Valid glyphs are converted to characters and added to the string.
                is Glyph.SmallDigit -> (glyph.digit + asciiDigitOffset).toChar()
                is Glyph.LargeDigit -> (glyph.digit + asciiDigitOffset).toChar()
                is Glyph.SmallCharacter -> glyph.character
                is Glyph.LargeCharacter -> glyph.character
                Glyph.SmallSymbol(SmallSymbol.DIVIDE) -> '/'
                Glyph.SmallSymbol(SmallSymbol.DOT) -> '.'
                Glyph.LargeSymbol(LargeSymbol.DOT) -> '.'

                // Invalid glyph -> the date string ended, stop scan.
                else -> break
            }

            parseContext.advance()
        }

        val regexResult = dateRegex.find(dateString) ?: return ParseResult.Failed

        val regexGroups = regexResult.groups
        val separator = regexGroups[2]!!.value
        var year = 0
        var month: Int
        var day: Int

        if (separator == ".") {
            day = regexGroups[1]!!.value.toInt(radix = 10)
            month = regexGroups[3]!!.value.toInt(radix = 10)
        } else if (separator == "/") {
            day = regexGroups[3]!!.value.toInt(radix = 10)
            month = regexGroups[1]!!.value.toInt(radix = 10)
        } else
            return ParseResult.Failed

        if (regexGroups[5] != null) {
            year = regexGroups[5]!!.value.toInt(radix = 10) + 2000 // Combo years always start at the year 2000
        }

        return ParseResult.Value(LocalDate(year = year, month = month, day = day))
    }
}

/**
 * Parses the available tokens as a time.
 *
 * The following time formats are used by the Combo:
 *
 *   HH:MM
 *   HH:MM(AM/PM)
 *   HH(AM/PM)
 *
 * Examples:
 *   14:00
 *   11:47AM
 *   09PM
 *
 * The parser handles all of these cases.
 *
 * The result is a [DateTime] instance with the year/month/day fields set to zero.
 */
class TimeParser : Parser() {
    private val timeRegex = "(\\d\\d):?(\\d\\d)(AM|PM)?|(\\d\\d)(AM|PM)".toRegex()
    private val asciiDigitOffset = '0'.code

    override fun parseImpl(parseContext: ParseContext): ParseResult {
        // To be able to handle all time formats without too much
        // convoluted (and error prone) parsing code, we use regex.

        var timeString = ""

        while (parseContext.hasMoreTokens()) {
            val token = parseContext.nextToken()
            val glyph = token.glyph

            timeString += when (glyph) {
                // Valid glyphs are converted to characters and added to the string.
                is Glyph.SmallDigit -> (glyph.digit + asciiDigitOffset).toChar()
                is Glyph.LargeDigit -> (glyph.digit + asciiDigitOffset).toChar()
                is Glyph.SmallCharacter -> glyph.character
                is Glyph.LargeCharacter -> glyph.character
                Glyph.SmallSymbol(SmallSymbol.SEPARATOR) -> ':'
                Glyph.LargeSymbol(LargeSymbol.SEPARATOR) -> ':'

                // Invalid glyph -> the time string ended, stop scan.
                else -> break
            }

            parseContext.advance()
        }

        val regexResult = timeRegex.find(timeString) ?: return ParseResult.Failed

        // Analyze the regex find result.
        // The Regex result groups are:
        //
        // #0: The entire string
        // #1: Hour from a HH:MM or HH:MM(AM/PM) format
        // #2: Minute from a HH:MM or HH:MM(AM/PM) format
        // #3: AM/PM specifier from a HH:MM or HH:MM(AM/PM) format
        // #4: Hour from a HH(AM/PM) format
        // #5: AM/PM specifier from a HH(AM/PM) format
        //
        // Groups without a found value are set to null.

        val regexGroups = regexResult.groups
        var hour: Int
        var minute = 0

        if (regexGroups[1] != null) {
            // Possibility 1: This is a time string that matches
            // one of these two formats:
            //
            //     HH:MM
            //     HH:MM(AM/PM)
            //
            // This means that group #2 must not be null, since it
            // contains the minute, and these are required here.

            if (regexGroups[2] == null)
                return ParseResult.Failed

            hour = regexGroups[1]!!.value.toInt(radix = 10)
            minute = regexGroups[2]!!.value.toInt(radix = 10)

            // Special case that can happen in basal rate factor
            // setting screens. The screen that shows the factor
            // that starts at 23:00 and ends at 00:00 shows a
            // time range from 23:00 to 24:00, and _not to 00:00
            // for some reason. Catch this here, otherwise the
            // LocalDateTime class will throw an IllegalArgumentException.
            if (hour == 24)
                hour = 0

            // If there is an AM/PM specifier, convert the hour
            // to the 24-hour format.
            if (regexGroups[3] != null)
                hour = amPmTo24Hour(hour, regexGroups[3]!!.value)
        } else if (regexGroups[4] != null) {
            // Possibility 2: This is a time string that matches
            // this format:
            //
            //     HH(AM/PM)
            //
            // This means that group #5 must not be null, since it
            // contains the AM/PM specifier, and it is required here.

            if (regexGroups[5] == null)
                return ParseResult.Failed

            hour = amPmTo24Hour(
                regexGroups[4]!!.value.toInt(radix = 10),
                regexGroups[5]!!.value
            )
        } else
            return ParseResult.Failed

        return ParseResult.Value(timeWithoutDate(hour = hour, minute = minute))
    }
}

/**
 * Parses the available tokens as a duration.
 *
 * Durations are shown similar to how times show up, in HH:MM format. They are never
 * using any other format though (unlike times, which can show up in AM/PM form for example).
 * Also, a duration of 24:00 is valid, while a time of 24:00 is not.
 *
 * The result is an Int that contains the duration in minutes.
 *
 * @property parseMode Parse mode. Useful for restricting the valid integer glyphs.
 */
class DurationParser(private val parseMode: GlyphDigitParseMode = GlyphDigitParseMode.ALL_DIGITS) : Parser() {
    override fun parseImpl(parseContext: ParseContext): ParseResult {
        var tokenCounter = 0
        val digits = mutableListOf<Int>()
        var gotSeparator = false

        while (parseContext.hasMoreTokens() && (tokenCounter < 5)) {
            val token = parseContext.nextToken()
            when (val glyph = token.glyph) {
                is Glyph.SmallDigit -> when (parseMode) {
                    GlyphDigitParseMode.SMALL_DIGITS_ONLY,
                    GlyphDigitParseMode.ALL_DIGITS ->
                        digits.add(glyph.digit)
                    else                           ->
                        return ParseResult.Failed
                }
                is Glyph.LargeDigit -> when (parseMode) {
                    GlyphDigitParseMode.LARGE_DIGITS_ONLY,
                    GlyphDigitParseMode.ALL_DIGITS ->
                        digits.add(glyph.digit)
                    else                           ->
                        return ParseResult.Failed
                }
                Glyph.SmallSymbol(SmallSymbol.SEPARATOR),
                Glyph.LargeSymbol(LargeSymbol.SEPARATOR) -> {
                    if (tokenCounter != 2)
                        return ParseResult.Failed
                    else
                        gotSeparator = true
                }
                else -> Unit
            }
            parseContext.advance()
            tokenCounter++
        }

        if (!gotSeparator || (digits.size != 4))
            return ParseResult.Failed

        val hour = digits[0] * 10 + digits[1]
        val minute = digits[2] * 10 + digits[3]

        return ParseResult.Value(hour * 60 + minute)
    }
}

/******************************************
 *** Intermediate-level utility parsers ***
 ******************************************/

/**
 * Parses tokens using the specified subparser, returning [ParseResult.Null] or [ParseResult.NoValue] if that subparser fails to parse.
 *
 * This is useful when contents in a screen are not always available. One prominent
 * example is a blinking text or number. The subparser does the actual token parsing.
 * If that subparser returns [ParseResult.Failed], the OptionalParser returns
 * [ParseResult.Null] or [ParseResult.NoValue] instead. (It returns the latter if
 * the subparer's returnsValue property is set to true.) This is particularly useful
 * with the other utility parsers which themselves return [ParseResult.Failed] if at
 * least one of their subparsers fail. Using OptionalParser as one of their subparsers
 * prevents that.
 *
 * @property subParser Parser to parse tokens with.
 */
class OptionalParser(private val subParser: Parser) : Parser() {
    override fun parseImpl(parseContext: ParseContext): ParseResult {
        val parseResult = subParser.parse(parseContext)

        return if (parseResult.isSuccess) {
            parseResult
        } else {
            if (subParser.returnsValue)
                ParseResult.Null
            else
                ParseResult.NoValue
        }
    }
}

/**
 * Tries to parse tokens with the specified subparsers, stopping when one subparser succeeds or all subparsers failed.
 *
 * This parser tries its subparsers in the order by which they are stored in the
 * [subParsers] list. The first subparser that succeeds is the one whose return
 * value is forwarded and used as this parser's return value.
 *
 * @property subParsers List of parsers to try to parse tokens with.
 */
class FirstSuccessParser(private val subParsers: List<Parser>) : Parser() {
    override fun parseImpl(parseContext: ParseContext): ParseResult {
        for (subParser in subParsers) {
            val parseResult = subParser.parse(parseContext)

            if (parseResult.isSuccess)
                return parseResult
        }

        return ParseResult.Failed
    }
}

/**
 * Parses a sequence of tokens using the specified subparsers.
 *
 * This is useful for parsing entire screens at once. For example, if
 * a screen contains a date, followed by a specific symbol, followed by
 * another symbol (not a specific one though) and an optional integer,
 * then parsing looks like this:
 *
 *     val parseResult = SequenceParser(
 *         listOf(
 *             DateParser(),
 *             SingleGlyphParser(Glyph.SmallSymbol(Symbol.SMALL_CLOCK)),
 *             SingleGlyphTypeParser(Glyph.LargeSymbol::class),
 *             OptionalParser(DecimalParser())
 *         )
 *     ).parse(parseContext)
 *
 * Retrieving the values then looks like this:
 *
 *     parseResult as ParseResult.Sequence
 *     val date = parseResult.valueAt<DateTime>(0)
 *     val symbolGlyph = parseResult.valueAt<Glyph>(1)
 *     val optionalInteger = parseResult.valueAtOrNull<Int>(2)
 *
 * Note that the [SingleGlyphParser] is skipped (the indices go from 0 to 2).
 * This is because SingleGlyphParser's returnsValue property is set to false.
 * The valueAt function skips parsers whose returnsValue property is false.
 * Also, with optional parsers, it is recommended to use valueAtOrNull<>
 * instead of value<>, since the former returns null if the OptionalParser
 * returns [ParseResult.Null], while the latter raises an exception (cast error).
 *
 * @property subParsers List of parsers to parse tokens with.
 * @property allowIncompleteSequences If true, then partial results are
 *           OK; that is, as soon as one of the subparsers returns
 *           [ParseResult.EndOfTokens], this function call returns the
 *           sequence of values parsed so far. If instead set to true,
 *           [ParseResult.EndOfTokens] is returned in that case.
 */
class SequenceParser(private val subParsers: List<Parser>, private val allowIncompleteSequences: Boolean = false) : Parser() {
    override fun parseImpl(parseContext: ParseContext): ParseResult {
        val parseResults = mutableListOf<ParseResult>()
        for (subParser in subParsers) {
            val parseResult = subParser.parse(parseContext)

            when (parseResult) {
                is ParseResult.Value<*> -> parseResults.add(parseResult)
                is ParseResult.Sequence -> parseResults.add(parseResult)
                ParseResult.NoValue -> Unit
                ParseResult.Null -> parseResults.add(ParseResult.Null)
                ParseResult.EndOfTokens -> if (allowIncompleteSequences) break else return ParseResult.EndOfTokens
                ParseResult.Failed -> return ParseResult.Failed
            }
        }

        return ParseResult.Sequence(parseResults)
    }
}

/*************************************
 *** Top-level screen parser class ***
 *************************************/

/**
 * Top-level parser.
 *
 * This is the main entrypoint for parsing tokens that were previously
 * extracted out of a [DisplayFrame]. Typically, this is not used directly.
 * Instead, this is used by [parseDisplayFrame].
 */
class ToplevelScreenParser : Parser() {
    override fun parseImpl(parseContext: ParseContext) = FirstSuccessParser(
        listOf(
            TopLeftClockScreenParser(),
            MenuScreenParser(),
            TitleStringScreenParser()
        )
    ).parse(parseContext)
}

/**
 * Parses a [DisplayFrame] by tokenizing it and then parsing the tokens.
 *
 * @param displayFrame Display frame to parse.
 * @return Parsed screen, or [ParsedScreen.UnrecognizedScreen] if parsing failed.
 */
fun parseDisplayFrame(displayFrame: DisplayFrame): ParsedScreen {
    val tokens = findTokens(displayFrame)
    val parseContext = ParseContext(tokens, 0)
    val parseResult = ToplevelScreenParser().parse(parseContext)
    return when (parseResult) {
        is ParseResult.Value<*> -> parseResult.value as ParsedScreen
        else -> ParsedScreen.UnrecognizedScreen
    }
}

/******************************************
 *** Screen parser intermediate classes ***
 ******************************************/

class TitleStringScreenParser : Parser() {
    override fun parseImpl(parseContext: ParseContext): ParseResult {
        val parseResult = StringParser().parse(parseContext)

        if (!parseResult.isSuccess)
            return ParseResult.Failed

        val titleString = (parseResult as ParseResult.Value<*>).value as String

        // Get an ID for the title. This ID is language independent
        // and thus much more useful for identifying the screen here.
        val titleId = knownScreenTitles[titleString]

        when (titleId) {
            TitleID.QUICK_INFO -> return QuickinfoScreenParser().parse(parseContext)
            TitleID.TBR_PERCENTAGE -> return TemporaryBasalRatePercentageScreenParser().parse(parseContext)
            TitleID.TBR_DURATION -> return TemporaryBasalRateDurationScreenParser().parse(parseContext)
            TitleID.HOUR,
            TitleID.MINUTE,
            TitleID.YEAR,
            TitleID.MONTH,
            TitleID.DAY -> return TimeAndDateSettingsScreenParser(titleId).parse(parseContext)
            TitleID.BOLUS_DATA -> return MyDataBolusDataScreenParser().parse(parseContext)
            TitleID.ERROR_DATA -> return MyDataErrorDataScreenParser().parse(parseContext)
            TitleID.DAILY_TOTALS -> return MyDataDailyTotalsScreenParser().parse(parseContext)
            TitleID.TBR_DATA -> return MyDataTbrDataScreenParser().parse(parseContext)
            else -> Unit
        }

        // Further parsers follow that do not actually use
        // the title string for identification, and instead
        // just skip the title string. To not have to parse
        // that string again, these parsers are run here,
        // after the string was already parsed.

        return FirstSuccessParser(
            listOf(
                AlertScreenParser(),
                BasalRateTotalScreenParser()
            )
        ).parse(parseContext)
    }
}

class MenuScreenParser : Parser() {
    override fun parseImpl(parseContext: ParseContext): ParseResult {
        val lastGlyph = parseContext.tokens.last().glyph

        when (lastGlyph) {
            Glyph.LargeSymbol(LargeSymbol.BOLUS) -> return ParseResult.Value(ParsedScreen.StandardBolusMenuScreen)
            Glyph.LargeSymbol(LargeSymbol.EXTENDED_BOLUS) -> return ParseResult.Value(ParsedScreen.ExtendedBolusMenuScreen)
            Glyph.LargeSymbol(LargeSymbol.MULTIWAVE_BOLUS) -> return ParseResult.Value(ParsedScreen.MultiwaveBolusMenuScreen)
            Glyph.LargeSymbol(LargeSymbol.BLUETOOTH_SETTINGS) -> return ParseResult.Value(ParsedScreen.BluetoothSettingsMenuScreen)
            Glyph.LargeSymbol(LargeSymbol.MENU_SETTINGS) -> return ParseResult.Value(ParsedScreen.MenuSettingsMenuScreen)
            Glyph.LargeSymbol(LargeSymbol.MY_DATA) -> return ParseResult.Value(ParsedScreen.MyDataMenuScreen)
            Glyph.LargeSymbol(LargeSymbol.BASAL) -> return ParseResult.Value(ParsedScreen.BasalRateProfileSelectionMenuScreen)
            Glyph.LargeSymbol(LargeSymbol.PUMP_SETTINGS) -> return ParseResult.Value(ParsedScreen.PumpSettingsMenuScreen)
            Glyph.LargeSymbol(LargeSymbol.REMINDER_SETTINGS) -> return ParseResult.Value(ParsedScreen.ReminderSettingsMenuScreen)
            Glyph.LargeSymbol(LargeSymbol.CALENDAR_AND_CLOCK) -> return ParseResult.Value(ParsedScreen.TimeAndDateSettingsMenuScreen)
            Glyph.LargeSymbol(LargeSymbol.STOP) -> return ParseResult.Value(ParsedScreen.StopPumpMenuScreen)
            Glyph.LargeSymbol(LargeSymbol.TBR) -> return ParseResult.Value(ParsedScreen.TemporaryBasalRateMenuScreen)
            Glyph.LargeSymbol(LargeSymbol.THERAPY_SETTINGS) -> return ParseResult.Value(ParsedScreen.TherapySettingsMenuScreen)
            else -> Unit
        }

        // Special case: If the semi-last glyph is a LARGE_BASAL symbol,
        // and the last glyph is a large digit, this may be one of the
        // basal rate programming menu screens.
        if ((parseContext.tokens.size >= 2) &&
            (lastGlyph is Glyph.LargeDigit) &&
            (parseContext.tokens[parseContext.tokens.size - 2].glyph == Glyph.LargeSymbol(LargeSymbol.BASAL))) {
            return ParseResult.Value(when (lastGlyph.digit) {
                1 -> ParsedScreen.BasalRate1ProgrammingMenuScreen
                2 -> ParsedScreen.BasalRate2ProgrammingMenuScreen
                3 -> ParsedScreen.BasalRate3ProgrammingMenuScreen
                4 -> ParsedScreen.BasalRate4ProgrammingMenuScreen
                5 -> ParsedScreen.BasalRate5ProgrammingMenuScreen
                else -> ParsedScreen.UnrecognizedScreen
            })
        }

        return ParseResult.Failed
    }
}

class TopLeftClockScreenParser : Parser() {
    override fun parseImpl(parseContext: ParseContext): ParseResult {
        val parseResult = SequenceParser(
            listOf(
                SingleGlyphParser(Glyph.SmallSymbol(SmallSymbol.CLOCK)),
                TimeParser()
            )
        ).parse(parseContext)

        if (!parseResult.isSuccess)
            return ParseResult.Failed

        parseResult as ParseResult.Sequence

        parseContext.topLeftTime = parseResult.valueAtOrNull<LocalDateTime>(0)

        return FirstSuccessParser(
            listOf(
                BasalRateFactorSettingScreenParser(),
                NormalMainScreenParser(),
                TbrMainScreenParser(),
                StoppedMainScreenParser(),
                ExtendedAndMultiwaveBolusMainScreenParser()
            )
        ).parse(parseContext)
    }
}

/*****************************
 *** Screen parser classes ***
 *****************************/

class AlertScreenParser : Parser() {
    override fun parseImpl(parseContext: ParseContext): ParseResult {
        val parseResult = SequenceParser(
            listOf(
                OptionalParser(SingleGlyphTypeParser(Glyph.LargeSymbol::class)), // warning/error symbol
                OptionalParser(SingleGlyphTypeParser(Glyph.LargeCharacter::class)), // "W" or "E"
                OptionalParser(IntegerParser()), // warning/error number
                OptionalParser(SingleGlyphTypeParser(Glyph.LargeSymbol::class)), // stop symbol (shown in suspended state)
                SingleGlyphParser(Glyph.SmallSymbol(SmallSymbol.CHECK)),
                StringParser() // snooze / confirm text
            )
        ).parse(parseContext)

        if (!parseResult.isSuccess)
            return ParseResult.Failed

        parseResult as ParseResult.Sequence

        return when (parseResult.valueAtOrNull<Glyph>(0)) {
            Glyph.LargeSymbol(LargeSymbol.WARNING) -> {
                val stateString = parseResult.valueAt<String>(4)
                val alertState = when (knownScreenTitles[stateString]) {
                    TitleID.ALERT_TO_SNOOZE -> AlertScreenContent.AlertScreenState.TO_SNOOZE
                    TitleID.ALERT_TO_CONFIRM -> AlertScreenContent.AlertScreenState.TO_CONFIRM
                    else -> return ParseResult.Failed
                }
                ParseResult.Value(ParsedScreen.AlertScreen(
                    AlertScreenContent.Warning(parseResult.valueAt(2), alertState)
                ))
            }

            Glyph.LargeSymbol(LargeSymbol.ERROR) -> {
                ParseResult.Value(ParsedScreen.AlertScreen(
                    AlertScreenContent.Error(
                        parseResult.valueAt(2),
                        // We don't really care about the state string if an error is shown.
                        // It's not like any logic here will interpret it; that text is
                        // purely for the user. So, don't bother interpreting it here, and
                        // just assign a generic ERROR_TEXT state value instead.
                        AlertScreenContent.AlertScreenState.ERROR_TEXT
                    )
                ))
            }

            else -> ParseResult.Value(ParsedScreen.AlertScreen(AlertScreenContent.None))
        }
    }
}

class QuickinfoScreenParser : Parser() {
    override fun parseImpl(parseContext: ParseContext): ParseResult {
        val parseResult = SequenceParser(
            listOf(
                SingleGlyphTypeParser(Glyph.LargeSymbol::class),
                IntegerParser()
            )
        ).parse(parseContext)

        parseResult as ParseResult.Sequence

        val reservoirState = when (parseResult.valueAt<Glyph>(0)) {
            Glyph.LargeSymbol(LargeSymbol.RESERVOIR_EMPTY) -> ReservoirState.EMPTY
            Glyph.LargeSymbol(LargeSymbol.RESERVOIR_LOW) -> ReservoirState.LOW
            Glyph.LargeSymbol(LargeSymbol.RESERVOIR_FULL) -> ReservoirState.FULL
            else -> return ParseResult.Failed
        }

        val availableUnits = parseResult.valueAt<Int>(1)

        return ParseResult.Value(
            ParsedScreen.QuickinfoMainScreen(
                Quickinfo(availableUnits = availableUnits, reservoirState = reservoirState)
            )
        )
    }
}

class BasalRateTotalScreenParser : Parser() {
    override fun parseImpl(parseContext: ParseContext): ParseResult {
        val parseResult = SequenceParser(
            listOf(
                SingleGlyphParser(Glyph.LargeSymbol(LargeSymbol.BASAL_SET)),
                DecimalParser(),
                SingleGlyphParser(Glyph.LargeCharacter('u')),
                IntegerParser(GlyphDigitParseMode.SMALL_DIGITS_ONLY),
                SingleGlyphParser(Glyph.SmallSymbol(SmallSymbol.CHECK)),
                SingleGlyphTypeParser(Glyph.SmallCharacter::class)
            )
        ).parse(parseContext)

        if (!parseResult.isSuccess)
            return ParseResult.Failed

        parseResult as ParseResult.Sequence

        return ParseResult.Value(
            ParsedScreen.BasalRateTotalScreen(
                totalNumUnits = parseResult.valueAt<Int>(0),
                basalRateNumber = parseResult.valueAt<Int>(1)
            )
        )
    }
}

class TemporaryBasalRatePercentageScreenParser : Parser() {
    override fun parseImpl(parseContext: ParseContext): ParseResult {
        val parseResult = SequenceParser(
            listOf(
                OptionalParser(SingleGlyphParser(Glyph.SmallSymbol(SmallSymbol.PERCENT))),
                SingleGlyphParser(Glyph.LargeSymbol(LargeSymbol.BASAL)),
                OptionalParser(IntegerParser()), // TBR percentage
                SingleGlyphParser(Glyph.LargeSymbol(LargeSymbol.PERCENT)),
                SingleGlyphParser(Glyph.SmallSymbol(SmallSymbol.ARROW)),
                DurationParser(GlyphDigitParseMode.SMALL_DIGITS_ONLY)
            ),
            allowIncompleteSequences = true
        ).parse(parseContext)

        if (!parseResult.isSuccess)
            return ParseResult.Failed

        parseResult as ParseResult.Sequence

        val remainingTbrDurationParseResult = if (parseResult.size >= 2)
            parseResult.valueAtOrNull<Int>(1)
        else
            null
        val remainingTbrDurationInMinutes = remainingTbrDurationParseResult ?: 0

        return ParseResult.Value(
            ParsedScreen.TemporaryBasalRatePercentageScreen(
                percentage = parseResult.valueAtOrNull<Int>(0),
                remainingDurationInMinutes = remainingTbrDurationInMinutes
            )
        )
    }
}

class TemporaryBasalRateDurationScreenParser : Parser() {
    override fun parseImpl(parseContext: ParseContext): ParseResult {
        val parseResult = SequenceParser(
            listOf(
                SingleGlyphParser(Glyph.LargeSymbol(LargeSymbol.ARROW)),
                OptionalParser(DurationParser(GlyphDigitParseMode.LARGE_DIGITS_ONLY))
            )
        ).parse(parseContext)

        if (!parseResult.isSuccess)
            return ParseResult.Failed

        parseResult as ParseResult.Sequence
        val durationParseResult = parseResult.valueAtOrNull<Int>(0)

        return ParseResult.Value(
            ParsedScreen.TemporaryBasalRateDurationScreen(
                durationInMinutes = durationParseResult
            )
        )
    }
}

class NormalMainScreenParser : Parser() {
    override fun parseImpl(parseContext: ParseContext): ParseResult {
        require(parseContext.topLeftTime != null)

        val parseResult = SequenceParser(
            listOf(
                SingleGlyphParser(Glyph.LargeSymbol(LargeSymbol.BASAL)),
                DecimalParser(), // Current basal rate factor
                SingleGlyphParser(Glyph.LargeSymbol(LargeSymbol.UNITS_PER_HOUR)),
                SingleGlyphTypeParser(Glyph.SmallDigit::class), // Basal rate number,
                SingleGlyphTypeParser(Glyph.SmallSymbol::class) // Battery state
            ),
            allowIncompleteSequences = true
        ).parse(parseContext)

        if (!parseResult.isSuccess)
            return ParseResult.Failed

        parseResult as ParseResult.Sequence
        if (parseResult.size < 2)
            return ParseResult.Failed

        val batteryState = batteryStateFromSymbol(
            if (parseResult.size >= 3) parseResult.valueAt<Glyph.SmallSymbol>(2).symbol else null
        )

        return ParseResult.Value(
            ParsedScreen.MainScreen(
                MainScreenContent.Normal(
                    currentTime = parseContext.topLeftTime!!,
                    activeBasalProfileNumber = parseResult.valueAt<Glyph.SmallDigit>(1).digit,
                    currentBasalRateFactor = parseResult.valueAt<Int>(0),
                    batteryState = batteryState
                )
            )
        )
    }
}

class TbrMainScreenParser : Parser() {
    override fun parseImpl(parseContext: ParseContext): ParseResult {
        require(parseContext.topLeftTime != null)

        val parseResult = SequenceParser(
            listOf(
                SingleGlyphParser(Glyph.SmallSymbol(SmallSymbol.ARROW)),
                DurationParser(GlyphDigitParseMode.SMALL_DIGITS_ONLY), // Remaining TBR duration
                SingleGlyphParser(Glyph.LargeSymbol(LargeSymbol.BASAL)),
                FirstSuccessParser(
                    listOf(
                        SingleGlyphParser(Glyph.SmallSymbol(SmallSymbol.UP)),
                        SingleGlyphParser(Glyph.SmallSymbol(SmallSymbol.DOWN))
                    )
                ),
                IntegerParser(GlyphDigitParseMode.LARGE_DIGITS_ONLY), // TBR percentage
                SingleGlyphParser(Glyph.LargeSymbol(LargeSymbol.PERCENT)),
                SingleGlyphTypeParser(Glyph.SmallDigit::class), // Basal rate number
                DecimalParser(), // Current basal rate factor
                SingleGlyphParser(Glyph.SmallSymbol(SmallSymbol.UNITS_PER_HOUR)),
                SingleGlyphTypeParser(Glyph.SmallSymbol::class) // Battery state
            ),
            allowIncompleteSequences = true
        ).parse(parseContext)

        if (!parseResult.isSuccess)
            return ParseResult.Failed

        parseResult as ParseResult.Sequence
        if (parseResult.size < 4)
            return ParseResult.Failed

        val batteryState = batteryStateFromSymbol(
            if (parseResult.size >= 5) parseResult.valueAt<Glyph.SmallSymbol>(4).symbol else null
        )

        val remainingTbrDuration = parseResult.valueAt<Int>(0)

        return ParseResult.Value(
            ParsedScreen.MainScreen(
                MainScreenContent.Tbr(
                    currentTime = parseContext.topLeftTime!!,
                    remainingTbrDurationInMinutes = remainingTbrDuration,
                    tbrPercentage = parseResult.valueAt<Int>(1),
                    activeBasalProfileNumber = parseResult.valueAt<Glyph.SmallDigit>(2).digit,
                    currentBasalRateFactor = parseResult.valueAt<Int>(3),
                    batteryState = batteryState
                )
            )
        )
    }
}

class ExtendedAndMultiwaveBolusMainScreenParser : Parser() {
    override fun parseImpl(parseContext: ParseContext): ParseResult {
        require(parseContext.topLeftTime != null)

        val parseResult = SequenceParser(
            listOf(
                SingleGlyphParser(Glyph.SmallSymbol(SmallSymbol.ARROW)),
                DurationParser(GlyphDigitParseMode.SMALL_DIGITS_ONLY), // Remaining extended/multiwave bolus duration
                SingleGlyphTypeParser(Glyph.LargeSymbol::class), // Extended / multiwave symbol
                OptionalParser(SingleGlyphTypeParser(Glyph.SmallSymbol::class)), // TBR arrow up/down symbol (only present if TBR is active)
                DecimalParser(), // Remaining bolus amount
                SingleGlyphParser(Glyph.LargeCharacter('u')),
                SingleGlyphTypeParser(Glyph.SmallDigit::class), // Active basal rate number
                DecimalParser(), // Current basal rate factor
                SingleGlyphParser(Glyph.SmallSymbol(SmallSymbol.UNITS_PER_HOUR)),
                SingleGlyphTypeParser(Glyph.SmallSymbol::class) // Battery state
            ),
            allowIncompleteSequences = true
        ).parse(parseContext)

        if (!parseResult.isSuccess)
            return ParseResult.Failed

        parseResult as ParseResult.Sequence
        if (parseResult.size < 6)
            return ParseResult.Failed

        // At that location, only the extended and multiwave bolus symbols
        // are valid. Otherwise, this isn't an extended/multiwave bolus screen.
        val isExtendedBolus = when (parseResult.valueAt<Glyph.LargeSymbol>(1).symbol) {
            LargeSymbol.EXTENDED_BOLUS -> true
            LargeSymbol.MULTIWAVE_BOLUS -> false
            else -> return ParseResult.Failed
        }

        val tbrIsActive = when (parseResult.valueAtOrNull<Glyph.SmallSymbol>(2)?.symbol) {
            SmallSymbol.UP,
            SmallSymbol.DOWN -> true
            null -> false
            else -> return ParseResult.Failed
        }

        val batteryState = batteryStateFromSymbol(
            if (parseResult.size >= 7) parseResult.valueAt<Glyph.SmallSymbol>(6).symbol else null
        )

        val remainingBolusDuration = parseResult.valueAt<Int>(0)

        return ParseResult.Value(
            ParsedScreen.MainScreen(
                MainScreenContent.ExtendedOrMultiwaveBolus(
                    currentTime = parseContext.topLeftTime!!,
                    remainingBolusDurationInMinutes = remainingBolusDuration,
                    isExtendedBolus = isExtendedBolus,
                    remainingBolusAmount = parseResult.valueAt<Int>(3),
                    tbrIsActive = tbrIsActive,
                    activeBasalProfileNumber = parseResult.valueAt<Glyph.SmallDigit>(4).digit,
                    currentBasalRateFactor = parseResult.valueAt<Int>(5),
                    batteryState = batteryState
                )
            )
        )
    }
}

class StoppedMainScreenParser : Parser() {
    override fun parseImpl(parseContext: ParseContext): ParseResult {
        require(parseContext.topLeftTime != null)

        val parseResult = SequenceParser(
            listOf(
                SingleGlyphParser(Glyph.SmallSymbol(SmallSymbol.CALENDAR)),
                DateParser(), // Current date
                SingleGlyphParser(Glyph.LargeSymbol(LargeSymbol.STOP)),
                SingleGlyphTypeParser(Glyph.SmallSymbol::class) // Battery state
            ),
            allowIncompleteSequences = true
        ).parse(parseContext)

        if (!parseResult.isSuccess)
            return ParseResult.Failed

        parseResult as ParseResult.Sequence
        if (parseResult.size < 1)
            return ParseResult.Failed

        val currentDate = parseResult.valueAt<LocalDate>(0)

        val batteryState = batteryStateFromSymbol(
            if (parseResult.size >= 2) parseResult.valueAt<Glyph.SmallSymbol>(1).symbol else null
        )

        return ParseResult.Value(
            ParsedScreen.MainScreen(
                MainScreenContent.Stopped(
                    currentDateTime = currentDate.atTime(
                        hour = parseContext.topLeftTime!!.hour,
                        minute = parseContext.topLeftTime!!.minute,
                        second = 0,
                        nanosecond = 0
                    ),
                    batteryState = batteryState
                )
            )
        )
    }
}

class BasalRateFactorSettingScreenParser : Parser() {
    override fun parseImpl(parseContext: ParseContext): ParseResult {
        require(parseContext.topLeftTime != null)

        val parseResult = SequenceParser(
            listOf(
                SingleGlyphParser(Glyph.SmallSymbol(SmallSymbol.MINUS)),
                TimeParser(),
                SingleGlyphParser(Glyph.LargeSymbol(LargeSymbol.BASAL)),
                OptionalParser(DecimalParser()),
                SingleGlyphParser(Glyph.LargeSymbol(LargeSymbol.UNITS_PER_HOUR)),
                IntegerParser(GlyphDigitParseMode.SMALL_DIGITS_ONLY)
            )
        ).parse(parseContext)

        if (!parseResult.isSuccess)
            return ParseResult.Failed

        parseResult as ParseResult.Sequence
        val beginTime = parseContext.topLeftTime!!
        val endTime = parseResult.valueAt<LocalDateTime>(0)
        val numUnits = parseResult.valueAtOrNull<Int>(1)
        val basalRateNumber = parseResult.valueAt<Int>(2)

        return ParseResult.Value(
            ParsedScreen.BasalRateFactorSettingScreen(
                beginTime = beginTime,
                endTime = endTime,
                numUnits = numUnits,
                basalRateNumber = basalRateNumber
            )
        )
    }
}

class TimeAndDateSettingsScreenParser(val titleId: TitleID) : Parser() {
    override fun parseImpl(parseContext: ParseContext): ParseResult {
        val parseResult = SequenceParser(
            listOf(
                SingleGlyphTypeParser(Glyph.LargeSymbol::class),
                OptionalParser(IntegerParser(GlyphDigitParseMode.LARGE_DIGITS_ONLY)), // Quantity
                OptionalParser(StringParser()) // AM/PM
            )
        ).parse(parseContext)

        if (!parseResult.isSuccess)
            return ParseResult.Failed

        parseResult as ParseResult.Sequence
        val symbolGlyph = parseResult.valueAt<Glyph.LargeSymbol>(0)
        val ampm = parseResult.valueAtOrNull<String>(2)
        var quantity = parseResult.valueAtOrNull<Int>(1)

        // The AM/PM -> 24 hour translation must only be attempted if the
        // quantity is an hour. If it is a minute, this translation might
        // incorrectly change minute 24 into minute 0 for example.
        if ((titleId == TitleID.HOUR) && (quantity != null))
            quantity = amPmTo24Hour(quantity, ampm ?: "")

        val expectedSymbol = when (titleId) {
            TitleID.HOUR,
            TitleID.MINUTE -> LargeSymbol.CLOCK
            TitleID.YEAR,
            TitleID.MONTH,
            TitleID.DAY -> LargeSymbol.CALENDAR
            else -> return ParseResult.Failed
        }

        if (symbolGlyph.symbol != expectedSymbol)
            return ParseResult.Failed

        return ParseResult.Value(
            when (titleId) {
                TitleID.HOUR -> ParsedScreen.TimeAndDateSettingsHourScreen(quantity)
                TitleID.MINUTE -> ParsedScreen.TimeAndDateSettingsMinuteScreen(quantity)
                TitleID.YEAR -> ParsedScreen.TimeAndDateSettingsYearScreen(quantity)
                TitleID.MONTH -> ParsedScreen.TimeAndDateSettingsMonthScreen(quantity)
                TitleID.DAY -> ParsedScreen.TimeAndDateSettingsDayScreen(quantity)
                else -> return ParseResult.Failed
            }
        )
    }
}

class MyDataBolusDataScreenParser : Parser() {
    override fun parseImpl(parseContext: ParseContext): ParseResult {
        val parseResult = SequenceParser(
            listOf(
                SingleGlyphTypeParser(Glyph.SmallSymbol::class), // Bolus type
                DecimalParser(), // Bolus amount,
                SingleGlyphParser(Glyph.SmallCharacter('U')),
                IntegerParser(GlyphDigitParseMode.SMALL_DIGITS_ONLY), // Index
                SingleGlyphParser(Glyph.SmallSymbol(SmallSymbol.DIVIDE)),
                IntegerParser(GlyphDigitParseMode.SMALL_DIGITS_ONLY), // Total num entries
                OptionalParser(SingleGlyphParser(Glyph.SmallSymbol(SmallSymbol.ARROW))),
                OptionalParser(DurationParser(GlyphDigitParseMode.SMALL_DIGITS_ONLY)), // Duration - only present in multiwave and extended bolus entries
                SingleGlyphParser(Glyph.SmallSymbol(SmallSymbol.CLOCK)),
                TimeParser(), // Timestamp time
                SingleGlyphParser(Glyph.SmallSymbol(SmallSymbol.CALENDAR)),
                DateParser() // Timestamp date
            )
        ).parse(parseContext)

        if (!parseResult.isSuccess)
            return ParseResult.Failed

        parseResult as ParseResult.Sequence
        val bolusType = when (parseResult.valueAt<Glyph.SmallSymbol>(0).symbol) {
            SmallSymbol.BOLUS -> MyDataBolusType.STANDARD
            SmallSymbol.MULTIWAVE_BOLUS -> MyDataBolusType.MULTI_WAVE
            SmallSymbol.EXTENDED_BOLUS -> MyDataBolusType.EXTENDED
            else -> return ParseResult.Failed
        }
        val bolusAmount = parseResult.valueAt<Int>(1)
        val index = parseResult.valueAt<Int>(2)
        val totalNumEntries = parseResult.valueAt<Int>(3)
        val duration = parseResult.valueAtOrNull<Int>(4)
        val timestamp = combinedDateTime(
            date = parseResult.valueAt<LocalDate>(6),
            time = parseResult.valueAt<LocalDateTime>(5)
        )

        return ParseResult.Value(
            ParsedScreen.MyDataBolusDataScreen(
                index = index,
                totalNumEntries = totalNumEntries,
                timestamp = timestamp,
                bolusAmount = bolusAmount,
                bolusType = bolusType,
                durationInMinutes = duration
            )
        )
    }
}

class MyDataErrorDataScreenParser : Parser() {
    override fun parseImpl(parseContext: ParseContext): ParseResult {
        val parseResult = SequenceParser(
            listOf(
                SingleGlyphTypeParser(Glyph.SmallSymbol::class), // Alert type
                SingleGlyphTypeParser(Glyph.SmallCharacter::class), // Alert letter ('W' or 'E')
                IntegerParser(GlyphDigitParseMode.SMALL_DIGITS_ONLY, checkForWhitespace = true), // Alert number
                IntegerParser(GlyphDigitParseMode.SMALL_DIGITS_ONLY), // Index
                SingleGlyphParser(Glyph.SmallSymbol(SmallSymbol.DIVIDE)),
                IntegerParser(GlyphDigitParseMode.SMALL_DIGITS_ONLY), // Total num entries
                StringParser(), // Alert description - ignored
                SingleGlyphParser(Glyph.SmallSymbol(SmallSymbol.CLOCK)),
                TimeParser(), // Timestamp time
                SingleGlyphParser(Glyph.SmallSymbol(SmallSymbol.CALENDAR)),
                DateParser() // Timestamp date
            )
        ).parse(parseContext)

        if (!parseResult.isSuccess)
            return ParseResult.Failed

        parseResult as ParseResult.Sequence
        val alertType = parseResult.valueAt<Glyph.SmallSymbol>(0).symbol
        // skipping value #1 (the alert letter)
        val alertNumber = parseResult.valueAt<Int>(2)
        val index = parseResult.valueAt<Int>(3)
        val totalNumEntries = parseResult.valueAt<Int>(4)
        // skipping value #5 (the alert description)
        val timestamp = combinedDateTime(
            date = parseResult.valueAt<LocalDate>(7),
            time = parseResult.valueAt<LocalDateTime>(6)
        )

        return ParseResult.Value(
            ParsedScreen.MyDataErrorDataScreen(
                index = index,
                totalNumEntries = totalNumEntries,
                timestamp = timestamp,
                alert = if (alertType == SmallSymbol.WARNING)
                    AlertScreenContent.Warning(alertNumber, AlertScreenContent.AlertScreenState.HISTORY_ENTRY)
                else
                    AlertScreenContent.Error(alertNumber, AlertScreenContent.AlertScreenState.HISTORY_ENTRY)
            )
        )
    }
}

class MyDataDailyTotalsScreenParser : Parser() {
    override fun parseImpl(parseContext: ParseContext): ParseResult {
        val parseResult = SequenceParser(
            listOf(
                IntegerParser(GlyphDigitParseMode.SMALL_DIGITS_ONLY), // Index
                SingleGlyphParser(Glyph.SmallSymbol(SmallSymbol.DIVIDE)),
                IntegerParser(GlyphDigitParseMode.SMALL_DIGITS_ONLY), // Total num entries
                SingleGlyphParser(Glyph.SmallSymbol(SmallSymbol.SUM)),
                DecimalParser(), // Total daily amount
                SingleGlyphParser(Glyph.SmallCharacter('U')),
                SingleGlyphParser(Glyph.SmallSymbol(SmallSymbol.CALENDAR)),
                DateParser() // Timestamp date
            )
        ).parse(parseContext)

        if (!parseResult.isSuccess)
            return ParseResult.Failed

        parseResult as ParseResult.Sequence
        val index = parseResult.valueAt<Int>(0)
        val totalNumEntries = parseResult.valueAt<Int>(1)
        val totalDailyAmount = parseResult.valueAt<Int>(2)
        val date = parseResult.valueAt<LocalDate>(3)

        return ParseResult.Value(
            ParsedScreen.MyDataDailyTotalsScreen(
                index = index,
                totalNumEntries = totalNumEntries,
                date = date,
                totalDailyAmount = totalDailyAmount
            )
        )
    }
}

class MyDataTbrDataScreenParser : Parser() {
    override fun parseImpl(parseContext: ParseContext): ParseResult {
        val parseResult = SequenceParser(
            listOf(
                SingleGlyphTypeParser(Glyph.SmallSymbol::class), // TBR type - is ignored (it only indicates whether or not TBR was < or > 100%)
                IntegerParser(GlyphDigitParseMode.SMALL_DIGITS_ONLY), // Percentage
                SingleGlyphParser(Glyph.SmallSymbol(SmallSymbol.PERCENT)),
                IntegerParser(GlyphDigitParseMode.SMALL_DIGITS_ONLY), // Index
                SingleGlyphParser(Glyph.SmallSymbol(SmallSymbol.DIVIDE)),
                IntegerParser(GlyphDigitParseMode.SMALL_DIGITS_ONLY), // Total num entries
                SingleGlyphParser(Glyph.SmallSymbol(SmallSymbol.ARROW)),
                DurationParser(GlyphDigitParseMode.SMALL_DIGITS_ONLY), // Duration
                SingleGlyphParser(Glyph.SmallSymbol(SmallSymbol.CLOCK)),
                TimeParser(), // Timestamp time
                SingleGlyphParser(Glyph.SmallSymbol(SmallSymbol.CALENDAR)),
                DateParser() // Timestamp date
            )
        ).parse(parseContext)

        if (!parseResult.isSuccess)
            return ParseResult.Failed

        parseResult as ParseResult.Sequence
        val percentage = parseResult.valueAt<Int>(1)
        val index = parseResult.valueAt<Int>(2)
        val totalNumEntries = parseResult.valueAt<Int>(3)
        val duration = parseResult.valueAt<Int>(4)
        val timestamp = combinedDateTime(
            date = parseResult.valueAt<LocalDate>(6),
            time = parseResult.valueAt<LocalDateTime>(5)
        )

        return ParseResult.Value(
            ParsedScreen.MyDataTbrDataScreen(
                index = index,
                totalNumEntries = totalNumEntries,
                timestamp = timestamp,
                percentage = percentage,
                durationInMinutes = duration
            )
        )
    }
}

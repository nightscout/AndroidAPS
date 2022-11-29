package info.nightscout.comboctl.parser

/**
 * Two-dimensional binary pattern for searching in display frames.
 *
 * This stores pixels of a pattern as a boolean array. These pixels are
 * immutable and used for parsing display frames coming from the Combo.
 *
 * The pattern is not directly constructed out of a boolean array, since
 * that is impractical. Rather, it is constructed out of an array of strings.
 * This array is the "template" for the pattern, and its items are the "rows".
 * A whitespace character is interpreted as the boolean value "false", any
 * other character as "true". This makes it much easier to hardcode a pattern
 * template in a human-readable form. All template rows must have the exact
 * same length (at least 1 character), since patterns are rectangular. The
 * width property is derived from the length of the rows, while the height
 * equals the number of rows.
 *
 * The pixels BooleanArray contains the actual pixels, which are stored in
 * row-major order. That is: Given coordinates x and y (both starting at 0),
 * then the corresponding index in the array is (x + y * width).
 *
 * Pixels whose value is "true" are considered to be "set", while pixels with
 * the value "false" are considered to be "cleared". The number of set pixels
 * is available via the numSetPixels property. This amount is used when
 * resolving pattern match overlaps to decide if one of the overlapping matches
 * "wins" and the other has to be ignored.
 *
 * @param templateRows The string rows that make up the template.
 * @property width Width of the pattern, in pixels.
 * @property height Height of the pattern, in pixels.
 * @property pixels Boolean array housing the pixels.
 * @property numSetPixels Number of pixels in the array that are set
 *           (= whose value is true).
 */
class Pattern(templateRows: Array<String>) {
    val width: Int
    val height: Int
    val pixels: BooleanArray
    val numSetPixels: Int

    init {
        // Sanity checks. The pattern must have at least one row,
        // and rows must not be empty.
        height = templateRows.size
        if (height < 1)
            throw IllegalArgumentException("Could not generate pattern; no template rows available)")

        width = templateRows[0].length
        if (height < 1)
            throw IllegalArgumentException("Could not generate pattern; empty template row detected")

        // Initialize the pixels array and count the number of pixels.
        // The latter will be needed during pattern matching in case
        // matched patterns overlap in the display frame.

        pixels = BooleanArray(width * height) { false }

        var tempNumSetPixels = 0

        templateRows.forEachIndexed { y, row ->
            // Sanity check in case the pattern is malformed and
            // this row is of different length than the others.
            if (row.length != width)
                throw IllegalArgumentException(
                    "Not all rows are of equal length; row #0: $width row #$y: ${row.length}"
                )

            // Fill the pixel array with pixels from the template rows.
            // These contain whitespace for clear pixels and something
            // else (typically a solid block character) for set pixels.
            for (x in 0 until width) {
                val pixel = row[x] != ' '
                pixels[x + y * width] = pixel
                if (pixel)
                    tempNumSetPixels++
            }
        }

        numSetPixels = tempNumSetPixels
    }
}

/**
 * Available small symbol glyphs.
 */
enum class SmallSymbol {
    CLOCK,
    LOCK_CLOSED,
    LOCK_OPENED,
    CHECK,
    LOW_BATTERY,
    NO_BATTERY,
    WARNING,
    DIVIDE,
    RESERVOIR_LOW,
    RESERVOIR_EMPTY,
    CALENDAR,
    SEPARATOR,
    ARROW,
    UNITS_PER_HOUR,
    BOLUS,
    MULTIWAVE_BOLUS,
    SPEAKER,
    ERROR,
    DOT,
    UP,
    DOWN,
    SUM,
    BRACKET_RIGHT,
    BRACKET_LEFT,
    EXTENDED_BOLUS,
    PERCENT,
    BASAL,
    MINUS,
    WARRANTY,
}

/**
 * Available large symbol glyphs.
 */
enum class LargeSymbol {
    CLOCK,
    CALENDAR,
    DOT,
    SEPARATOR,
    WARNING,
    PERCENT,
    UNITS_PER_HOUR,
    BASAL_SET,
    RESERVOIR_FULL,
    RESERVOIR_LOW,
    RESERVOIR_EMPTY,
    ARROW,
    STOP,
    CALENDAR_AND_CLOCK,
    TBR,
    BOLUS,
    MULTIWAVE_BOLUS,
    MULTIWAVE_BOLUS_IMMEDIATE,
    EXTENDED_BOLUS,
    BLUETOOTH_SETTINGS,
    THERAPY_SETTINGS,
    PUMP_SETTINGS,
    MENU_SETTINGS,
    BASAL,
    MY_DATA,
    REMINDER_SETTINGS,
    CHECK,
    ERROR
}

/**
 * Class specifying a glyph.
 *
 * A "glyph" is a character, digit, or symbol for which a pattern exists that
 * can be search for in a Combo display frame. Glyphs can be "small" or "large"
 * (this primarily refers to the glyph's height). During pattern matching,
 * if matches overlap, and one match is for a small glyph and the other is for
 * a large glyph, the large one "wins", and the small match is ignored.
 *
 * By using the sealed class and its subclasses, it becomes possible to add
 * context to the hard-coded patterns below. When a pattern matches a subregion
 * in a frame, the corresponding Glyph subclass informs about what the discovered
 * subregion stands for.
 *
 * @property isLarge true if this is a "large" glyph.
 */
sealed class Glyph(val isLarge: Boolean) {
    data class SmallDigit(val digit: Int) : Glyph(false)
    data class SmallCharacter(val character: Char) : Glyph(false)
    data class SmallSymbol(val symbol: info.nightscout.comboctl.parser.SmallSymbol) : Glyph(false)
    data class LargeDigit(val digit: Int) : Glyph(true)
    data class LargeCharacter(val character: Char) : Glyph(true)
    data class LargeSymbol(val symbol: info.nightscout.comboctl.parser.LargeSymbol) : Glyph(true)
}

/**
 * Map of hard-coded patterns, each associated with a glyph specifying what the pattern stands for.
 */
val glyphPatterns = mapOf<Glyph, Pattern>(
    Glyph.LargeSymbol(LargeSymbol.CLOCK) to Pattern(arrayOf(
        "              ",
        "    █████     ",
        "  ██     ██   ",
        " █    █    █  ",
        " █    █    ██ ",
        "█     █     █ ",
        "█     █     ██",
        "█     ████  ██",
        "█           ██",
        "█           ██",
        " █         ███",
        " █         ██ ",
        "  ██     ████ ",
        "   █████████  ",
        "     █████    "
    )),
    Glyph.LargeSymbol(LargeSymbol.CALENDAR) to Pattern(arrayOf(
        "              ",
        "█████████████ ",
        "█           ██",
        "██████████████",
        "█ █ █ █ █ █ ██",
        "██████████████",
        "█ █ █ █ █ █ ██",
        "██████████████",
        "█ █ █ █ █ █ ██",
        "██████████████",
        "█ █ █ █ ██████",
        "██████████████",
        " █████████████",
        "              ",
        "              "
    )),
    Glyph.LargeSymbol(LargeSymbol.DOT) to Pattern(arrayOf(
        "     ",
        "     ",
        "     ",
        "     ",
        "     ",
        "     ",
        "     ",
        "     ",
        "     ",
        "     ",
        "     ",
        "     ",
        " ███ ",
        " ███ ",
        " ███ "
    )),
    Glyph.LargeSymbol(LargeSymbol.SEPARATOR) to Pattern(arrayOf(
        "     ",
        "     ",
        "     ",
        "     ",
        "     ",
        " ███ ",
        " ███ ",
        " ███ ",
        "     ",
        "     ",
        " ███ ",
        " ███ ",
        " ███ ",
        "     ",
        "     "
    )),
    Glyph.LargeSymbol(LargeSymbol.WARNING) to Pattern(arrayOf(
        "       ██       ",
        "      ████      ",
        "      █  █      ",
        "     ██  ██     ",
        "     █    █     ",
        "    ██ ██ ██    ",
        "    █  ██  █    ",
        "   ██  ██  ██   ",
        "   █   ██   █   ",
        "  ██   ██   ██  ",
        "  █          █  ",
        " ██    ██    ██ ",
        " █            █ ",
        "████████████████",
        " ███████████████"
    )),
    Glyph.LargeSymbol(LargeSymbol.PERCENT) to Pattern(arrayOf(
        " ██    ██",
        "████  ██ ",
        "████  ██ ",
        " ██  ██  ",
        "     ██  ",
        "    ██   ",
        "    ██   ",
        "   ██    ",
        "   ██    ",
        "  ██     ",
        "  ██  ██ ",
        " ██  ████",
        " ██  ████",
        "██    ██ ",
        "         "
    )),
    Glyph.LargeSymbol(LargeSymbol.UNITS_PER_HOUR) to Pattern(arrayOf(
        "██  ██    ██ ██    ",
        "██  ██    ██ ██    ",
        "██  ██    ██ ██    ",
        "██  ██   ██  ██    ",
        "██  ██   ██  █████ ",
        "██  ██   ██  ███ ██",
        "██  ██  ██   ██  ██",
        "██  ██  ██   ██  ██",
        "██  ██  ██   ██  ██",
        "██  ██ ██    ██  ██",
        "██  ██ ██    ██  ██",
        " ████  ██    ██  ██"
    )),
    Glyph.LargeSymbol(LargeSymbol.BASAL_SET) to Pattern(arrayOf(
        "                 ",
        "     ███████     ",
        "     ███████     ",
        "     ██ █ ██     ",
        "     ███ ████████",
        "     ██ █ ███████",
        "████████ █ █ █ ██",
        "███████ █ █ █ ███",
        "██ █ █ █ █ █ █ ██",
        "███ █ █ █ █ █ ███",
        "██ █ █ █ █ █ █ ██",
        "███ █ █ █ █ █ ███",
        "██ █ █ █ █ █ █ ██",
        "███ █ █ █ █ █ ███",
        "██ █ █ █ █ █ █ ██"
    )),
    Glyph.LargeSymbol(LargeSymbol.RESERVOIR_FULL) to Pattern(arrayOf(
        "                        ",
        "████████████████████    ",
        "████████████████████    ",
        "████████████████████ ███",
        "██████████████████████ █",
        "██████████████████████ █",
        "██████████████████████ █",
        "██████████████████████ █",
        "████████████████████ ███",
        "████████████████████    ",
        "████████████████████    ",
        "                        "
    )),
    Glyph.LargeSymbol(LargeSymbol.RESERVOIR_LOW) to Pattern(arrayOf(
        "                        ",
        "████████████████████    ",
        "█      █  █  █  ████    ",
        "█      █  █  █  ████ ███",
        "█               ██████ █",
        "█               ██████ █",
        "█               ██████ █",
        "█               ██████ █",
        "█               ████ ███",
        "█               ████    ",
        "████████████████████    ",
        "                        "
    )),
    Glyph.LargeSymbol(LargeSymbol.RESERVOIR_EMPTY) to Pattern(arrayOf(
        "                        ",
        "████████████████████    ",
        "█      █  █  █  █  █    ",
        "█      █  █  █  █  █ ███",
        "█                  ███ █",
        "█                    █ █",
        "█                    █ █",
        "█                  ███ █",
        "█                  █ ███",
        "█                  █    ",
        "████████████████████    ",
        "                        "
    )),
    Glyph.LargeSymbol(LargeSymbol.ARROW) to Pattern(arrayOf(
        "                ",
        "        ██      ",
        "        ███     ",
        "        ████    ",
        "        █████   ",
        "        ██████  ",
        "███████████████ ",
        "████████████████",
        "████████████████",
        "███████████████ ",
        "        ██████  ",
        "        █████   ",
        "        ████    ",
        "        ███     ",
        "        ██      "
    )),
    Glyph.LargeSymbol(LargeSymbol.EXTENDED_BOLUS) to Pattern(arrayOf(
        "                ",
        "█████████████   ",
        "█████████████   ",
        "██         ██   ",
        "██         ██   ",
        "██         ██   ",
        "██         ██   ",
        "██         ██   ",
        "██         ██   ",
        "██         ██   ",
        "██         ██   ",
        "██         ██   ",
        "██         ██   ",
        "██         █████",
        "██         █████"
    )),
    Glyph.LargeSymbol(LargeSymbol.MULTIWAVE_BOLUS) to Pattern(arrayOf(
        "                ",
        "██████          ",
        "██████          ",
        "██  ██          ",
        "██  ██          ",
        "██  ██          ",
        "██  ██          ",
        "██  ████████████",
        "██  ████████████",
        "██            ██",
        "██            ██",
        "██            ██",
        "██            ██",
        "██            ██",
        "██            ██"
    )),
    Glyph.LargeSymbol(LargeSymbol.BOLUS) to Pattern(arrayOf(
        "   ██████      ",
        "   ██████      ",
        "   ██  ██      ",
        "   ██  ██      ",
        "   ██  ██      ",
        "   ██  ██      ",
        "   ██  ██      ",
        "   ██  ██      ",
        "   ██  ██      ",
        "   ██  ██      ",
        "   ██  ██      ",
        "   ██  ██      ",
        "   ██  ██      ",
        "█████  ████████",
        "█████  ████████"
    )),
    Glyph.LargeSymbol(LargeSymbol.MULTIWAVE_BOLUS_IMMEDIATE) to Pattern(arrayOf(
        "██████         ",
        "██████         ",
        "██  ██         ",
        "██  ██         ",
        "██  ██         ",
        "██  ██         ",
        "██  ██ ██ ██ ██",
        "██  ██ ██ ██ ██",
        "██             ",
        "██           ██",
        "██           ██",
        "██             ",
        "██           ██",
        "██           ██"
    )),
    Glyph.LargeSymbol(LargeSymbol.STOP) to Pattern(arrayOf(
        "    ████████    ",
        "   ██████████   ",
        "  ████████████  ",
        " ██████████████ ",
        "████████████████",
        "█  █   █   █  ██",
        "█ ███ ██ █ █ █ █",
        "█  ██ ██ █ █  ██",
        "██ ██ ██ █ █ ███",
        "█  ██ ██   █ ███",
        "████████████████",
        " ██████████████ ",
        "  ████████████  ",
        "   ██████████   ",
        "    ████████    "
    )),
    Glyph.LargeSymbol(LargeSymbol.CALENDAR_AND_CLOCK) to Pattern(arrayOf(
        "       █████     ",
        "      █     █    ",
        "██████   █   █   ",
        "█   █    █    █  ",
        "█████    █    █  ",
        "█ █ █    ███  █  ",
        "█████            ",
        "█ █ █     ███████",
        "██████    █     █",
        "█ █ █ █   █    ██",
        "█████████ █   █ █",
        "█ █ █ █ █ ██ █  █",
        "█████████ █ █   █",
        " ████████ ███████"
    )),
    Glyph.LargeSymbol(LargeSymbol.TBR) to Pattern(arrayOf(
        "     ███████        ██    ██",
        "     ███████       ████  ██ ",
        "     ██   ██       ████  ██ ",
        "     ██   ███████   ██  ██  ",
        "     ██   ███████       ██  ",
        "███████   ██   ██      ██   ",
        "███████   ██   ██      ██   ",
        "██   ██   ██   ██     ██    ",
        "██   ██   ██   ██     ██    ",
        "██   ██   ██   ██    ██     ",
        "██   ██   ██   ██    ██  ██ ",
        "██   ██   ██   ██   ██  ████",
        "██   ██   ██   ██   ██  ████",
        "██   ██   ██   ██  ██    ██ "
    )),
    Glyph.LargeSymbol(LargeSymbol.BASAL) to Pattern(arrayOf(
        "                 ",
        "     ███████     ",
        "     ███████     ",
        "     ██   ██     ",
        "     ██   ███████",
        "     ██   ███████",
        "███████   ██   ██",
        "███████   ██   ██",
        "██   ██   ██   ██",
        "██   ██   ██   ██",
        "██   ██   ██   ██",
        "██   ██   ██   ██",
        "██   ██   ██   ██",
        "██   ██   ██   ██",
        "██   ██   ██   ██"
    )),
    Glyph.LargeSymbol(LargeSymbol.PUMP_SETTINGS) to Pattern(arrayOf(
        "███████████       ",
        "███████████       ",
        "████████████      ",
        "██       ███      ",
        "██       ████     ",
        "█████████████     ",
        "██       ██████   ",
        "███████████████ ██",
        "███████████████  █",
        "                ██",
        "           █   █ █",
        "           ██ █  █",
        "           █ █   █",
        "           ███████"
    )),
    Glyph.LargeSymbol(LargeSymbol.PUMP_SETTINGS) to Pattern(arrayOf(
        "███████████       ",
        "███████████       ",
        "████████████      ",
        "██       ███      ",
        "██       ████     ",
        "█████████████     ",
        "██       ██████   ",
        "███████████████ ██",
        "███████████████  █",
        "                ██",
        "           █   █ █",
        "           ██ █  █",
        "           █ █   █",
        "           ███████"
    )),
    Glyph.LargeSymbol(LargeSymbol.THERAPY_SETTINGS) to Pattern(arrayOf(
        "   ████        ",
        "   █  █        ",
        "   █  █        ",
        "████  ████     ",
        "█        █     ",
        "█        █     ",
        "████  ████     ",
        "   █  █        ",
        "   █  █ ███████",
        "   ████ █     █",
        "        █    ██",
        "        █   █ █",
        "        ██ █  █",
        "        █ █   █",
        "        ███████"
    )),
    Glyph.LargeSymbol(LargeSymbol.BLUETOOTH_SETTINGS) to Pattern(arrayOf(
        "  ██████       ",
        " ███ ████      ",
        " ███  ███      ",
        "████ █ ███     ",
        "████ ██ ██     ",
        "██ █ █ ███     ",
        "███   ████     ",
        "████ ██        ",
        "███   █ ███████",
        "██ █ █  █     █",
        "████ ██ █    ██",
        "████ █  █   █ █",
        " ███  █ ██ █  █",
        " ███ ██ █ █   █",
        "  █████ ███████"
    )),
    Glyph.LargeSymbol(LargeSymbol.MENU_SETTINGS) to Pattern(arrayOf(
        "   █████████ ",
        "   █       █ ",
        "   █       █ ",
        "█████████  █ ",
        "█████████  █ ",
        "█████████  █ ",
        "█████████  █ ",
        "█████        ",
        "█████ ███████",
        "█████ █     █",
        "█████ █    ██",
        "█████ █   █ █",
        "█████ ██ █  █",
        "█████ █ █   █",
        "      ███████"
    )),
    Glyph.LargeSymbol(LargeSymbol.MY_DATA) to Pattern(arrayOf(
        "       ████   ",
        "      ██████  ",
        "     ████████ ",
        "     ██    ██ ",
        "            █ ",
        "███████     █ ",
        "█     █    █  ",
        "█ ███ █ ███   ",
        "█     █       ",
        "█ ███ █ ████  ",
        "█     █ █████ ",
        "█     █ ██████",
        "███████ ██████"
    )),
    Glyph.LargeSymbol(LargeSymbol.REMINDER_SETTINGS) to Pattern(arrayOf(
        "      █          ",
        "     █ █         ",
        "     ███         ",
        "    █ █ █        ",
        "   █   █ █       ",
        "   █  █ ██       ",
        "   █   █ █       ",
        "   █  █ █        ",
        "  █    █  ███████",
        "  █   █ █ █     █",
        " █     █  █    ██",
        "█████████ █   █ █",
        "     ███  ██ █  █",
        "      █   █ █   █",
        "          ███████"
    )),
    Glyph.LargeSymbol(LargeSymbol.CHECK) to Pattern(arrayOf(
        "            ███",
        "           ███ ",
        "          ███  ",
        "         ███   ",
        "███     ███    ",
        " ███   ███     ",
        "  ███ ███      ",
        "   █████       ",
        "    ███        ",
        "     █         "
    )),
    Glyph.LargeSymbol(LargeSymbol.ERROR) to Pattern(arrayOf(
        "     █████     ",
        "   █████████   ",
        "  ███████████  ",
        " ███ █████ ███ ",
        " ██   ███   ██ ",
        "████   █   ████",
        "█████     █████",
        "██████   ██████",
        "█████     █████",
        "████   █   ████",
        " ██   ███   ██ ",
        " ███ █████ ███ ",
        "  ███████████  ",
        "   █████████   ",
        "     █████     "
    )),

    Glyph.LargeDigit(0) to Pattern(arrayOf(
        "  ████  ",
        " ██  ██ ",
        "██    ██",
        "██    ██",
        "██    ██",
        "██    ██",
        "██    ██",
        "██    ██",
        "██    ██",
        "██    ██",
        "██    ██",
        "██    ██",
        "██    ██",
        " ██  ██ ",
        "  ████  "
    )),
    Glyph.LargeDigit(1) to Pattern(arrayOf(
        "    ██  ",
        "   ███  ",
        "  ████  ",
        "    ██  ",
        "    ██  ",
        "    ██  ",
        "    ██  ",
        "    ██  ",
        "    ██  ",
        "    ██  ",
        "    ██  ",
        "    ██  ",
        "    ██  ",
        "    ██  ",
        "    ██  "
    )),
    Glyph.LargeDigit(2) to Pattern(arrayOf(
        "  ████  ",
        " ██  ██ ",
        "██    ██",
        "██    ██",
        "      ██",
        "      ██",
        "     ██ ",
        "    ██  ",
        "   ██   ",
        "  ██    ",
        " ██     ",
        "██      ",
        "██      ",
        "██      ",
        "████████"
    )),
    Glyph.LargeDigit(3) to Pattern(arrayOf(
        " █████  ",
        "██   ██ ",
        "      ██",
        "      ██",
        "      ██",
        "     ██ ",
        "   ███  ",
        "     ██ ",
        "      ██",
        "      ██",
        "      ██",
        "      ██",
        "      ██",
        "██   ██ ",
        " █████  "

    )),
    Glyph.LargeDigit(4) to Pattern(arrayOf(
        "     ██ ",
        "    ███ ",
        "    ███ ",
        "   ████ ",
        "   █ ██ ",
        "  ██ ██ ",
        "  █  ██ ",
        " ██  ██ ",
        "██   ██ ",
        "████████",
        "     ██ ",
        "     ██ ",
        "     ██ ",
        "     ██ ",
        "     ██ "

    )),
    Glyph.LargeDigit(5) to Pattern(arrayOf(
        "███████ ",
        "██      ",
        "██      ",
        "██      ",
        "██      ",
        "██████  ",
        "     ██ ",
        "      ██",
        "      ██",
        "      ██",
        "      ██",
        "      ██",
        "      ██",
        "██   ██ ",
        " █████  "
    )),
    Glyph.LargeDigit(6) to Pattern(arrayOf(
        "    ███ ",
        "   ██   ",
        "  ██    ",
        " ██     ",
        " ██     ",
        "██      ",
        "██████  ",
        "███  ██ ",
        "██    ██",
        "██    ██",
        "██    ██",
        "██    ██",
        "██    ██",
        " ██  ██ ",
        "  ████  "
    )),
    Glyph.LargeDigit(7) to Pattern(arrayOf(
        "████████",
        "      ██",
        "      ██",
        "     ██ ",
        "     ██ ",
        "    ██  ",
        "    ██  ",
        "   ██   ",
        "   ██   ",
        "   ██   ",
        "  ██    ",
        "  ██    ",
        "  ██    ",
        "  ██    ",
        "  ██    "
    )),
    Glyph.LargeDigit(8) to Pattern(arrayOf(
        "  ████  ",
        " ██  ██ ",
        "██    ██",
        "██    ██",
        "██    ██",
        " ██  ██ ",
        "  ████  ",
        " ██  ██ ",
        "██    ██",
        "██    ██",
        "██    ██",
        "██    ██",
        "██    ██",
        " ██  ██ ",
        "  ████  "
    )),
    Glyph.LargeDigit(9) to Pattern(arrayOf(
        "  ████  ",
        " ██  ██ ",
        "██    ██",
        "██    ██",
        "██    ██",
        "██    ██",
        " ██  ███",
        "  ██████",
        "      ██",
        "     ██ ",
        "     ██ ",
        "    ██  ",
        "    ██  ",
        "   ██   ",
        " ███    "
    )),

    Glyph.LargeCharacter('E') to Pattern(arrayOf(
        "████████",
        "██      ",
        "██      ",
        "██      ",
        "██      ",
        "██      ",
        "██      ",
        "███████ ",
        "██      ",
        "██      ",
        "██      ",
        "██      ",
        "██      ",
        "██      ",
        "████████"
    )),
    Glyph.LargeCharacter('W') to Pattern(arrayOf(
        "██      ██",
        "██      ██",
        "██      ██",
        "██      ██",
        "██  ██  ██",
        "██  ██  ██",
        "██  ██  ██",
        "██  ██  ██",
        "██  ██  ██",
        "██  ██  ██",
        "██  ██  ██",
        "██ ████ ██",
        "██████████",
        " ███  ███ ",
        "  █    █  "
    )),
    Glyph.LargeCharacter('u') to Pattern(arrayOf(
        "      ",
        "      ",
        "      ",
        "██  ██",
        "██  ██",
        "██  ██",
        "██  ██",
        "██  ██",
        "██  ██",
        "██  ██",
        "██  ██",
        "██  ██",
        "██  ██",
        "██  ██",
        " ████ "
    )),

    Glyph.SmallSymbol(SmallSymbol.CLOCK) to Pattern(arrayOf(
        "  ███  ",
        " █ █ █ ",
        "█  █  █",
        "█  ██ █",
        "█     █",
        " █   █ ",
        "  ███  "
    )),
    Glyph.SmallSymbol(SmallSymbol.UNITS_PER_HOUR) to Pattern(arrayOf(
        "█  █    █ █   ",
        "█  █   █  █   ",
        "█  █   █  █ █ ",
        "█  █  █   ██ █",
        "█  █  █   █  █",
        "█  █ █    █  █",
        " ██  █    █  █"
    )),
    Glyph.SmallSymbol(SmallSymbol.LOCK_CLOSED) to Pattern(arrayOf(
        " ███ ",
        "█   █",
        "█   █",
        "█████",
        "██ ██",
        "██ ██",
        "█████"
    )),
    Glyph.SmallSymbol(SmallSymbol.LOCK_OPENED) to Pattern(arrayOf(
        " ███     ",
        "█   █    ",
        "█   █    ",
        "    █████",
        "    ██ ██",
        "    ██ ██",
        "    █████"
    )),
    Glyph.SmallSymbol(SmallSymbol.CHECK) to Pattern(arrayOf(
        "    █",
        "   ██",
        "█ ██ ",
        "███  ",
        " █   ",
        "     ",
        "     "
    )),
    Glyph.SmallSymbol(SmallSymbol.DIVIDE) to Pattern(arrayOf(
        "     ",
        "    █",
        "   █ ",
        "  █  ",
        " █   ",
        "█    ",
        "     "
    )),
    Glyph.SmallSymbol(SmallSymbol.LOW_BATTERY) to Pattern(arrayOf(
        "██████████ ",
        "█        █ ",
        "███      ██",
        "███       █",
        "███      ██",
        "█        █ ",
        "██████████ "

    )),
    Glyph.SmallSymbol(SmallSymbol.NO_BATTERY) to Pattern(arrayOf(
        "██████████ ",
        "█        █ ",
        "█        ██",
        "█         █",
        "█        ██",
        "█        █ ",
        "██████████ "

    )),
    Glyph.SmallSymbol(SmallSymbol.RESERVOIR_LOW) to Pattern(arrayOf(
        "█████████████    ",
        "█  █  █  █ ██ ███",
        "█  █  █  █ ████ █",
        "█          ████ █",
        "█          ████ █",
        "█          ██ ███",
        "█████████████    "
    )),
    Glyph.SmallSymbol(SmallSymbol.RESERVOIR_EMPTY) to Pattern(arrayOf(
        "█████████████    ",
        "█  █  █  █  █ ███",
        "█  █  █  █  ███ █",
        "█             █ █",
        "█           ███ █",
        "█           █ ███",
        "█████████████    "
    )),
    Glyph.SmallSymbol(SmallSymbol.CALENDAR) to Pattern(arrayOf(
        "███████",
        "█     █",
        "███████",
        "█ █ █ █",
        "███████",
        "█ █ ███",
        "███████"
    )),
    Glyph.SmallSymbol(SmallSymbol.DOT) to Pattern(arrayOf(
        "     ",
        "     ",
        "     ",
        "     ",
        "     ",
        " ██  ",
        " ██  "
    )),
    Glyph.SmallSymbol(SmallSymbol.SEPARATOR) to Pattern(arrayOf(
        "     ",
        " ██  ",
        " ██  ",
        "     ",
        " ██  ",
        " ██  ",
        "     "
    )),
    Glyph.SmallSymbol(SmallSymbol.ARROW) to Pattern(arrayOf(
        "    █   ",
        "    ██  ",
        "███████ ",
        "████████",
        "███████ ",
        "    ██  ",
        "    █   "
    )),
    Glyph.SmallSymbol(SmallSymbol.DOWN) to Pattern(arrayOf(
        "  ███  ",
        "  ███  ",
        "  ███  ",
        "███████",
        " █████ ",
        "  ███  ",
        "   █   "
    )),
    Glyph.SmallSymbol(SmallSymbol.UP) to Pattern(arrayOf(
        "   █   ",
        "  ███  ",
        " █████ ",
        "███████",
        "  ███  ",
        "  ███  ",
        "  ███  "

    )),
    Glyph.SmallSymbol(SmallSymbol.SUM) to Pattern(arrayOf(
        "██████",
        "█    █",
        " █    ",
        "  █   ",
        " █    ",
        "█    █",
        "██████"
    )),
    Glyph.SmallSymbol(SmallSymbol.BOLUS) to Pattern(arrayOf(
        " ███   ",
        " █ █   ",
        " █ █   ",
        " █ █   ",
        " █ █   ",
        " █ █   ",
        "██ ████"
    )),
    Glyph.SmallSymbol(SmallSymbol.MULTIWAVE_BOLUS) to Pattern(arrayOf(
        "███     ",
        "█ █     ",
        "█ █     ",
        "█ ██████",
        "█      █",
        "█      █",
        "█      █"
    )),
    Glyph.SmallSymbol(SmallSymbol.EXTENDED_BOLUS) to Pattern(arrayOf(
        "███████ ",
        "█     █ ",
        "█     █ ",
        "█     █ ",
        "█     █ ",
        "█     █ ",
        "█     ██"
    )),
    Glyph.SmallSymbol(SmallSymbol.SPEAKER) to Pattern(arrayOf(
        "   ██ ",
        "  █ █ ",
        "██  █ ",
        "██  ██",
        "██  █ ",
        "  █ █ ",
        "   ██ "
    )),
    Glyph.SmallSymbol(SmallSymbol.ERROR) to Pattern(arrayOf(
        "  ███  ",
        " █████ ",
        "██ █ ██",
        "███ ███",
        "██ █ ██",
        " █████ ",
        "  ███  "
    )),
    Glyph.SmallSymbol(SmallSymbol.WARNING) to Pattern(arrayOf(
        "   █   ",
        "  ███  ",
        "  █ █  ",
        " █ █ █ ",
        " █   █ ",
        "█  █  █",
        "███████"
    )),
    Glyph.SmallSymbol(SmallSymbol.BRACKET_LEFT) to Pattern(arrayOf(
        "   █ ",
        "  █  ",
        " █   ",
        " █   ",
        " █   ",
        "  █  ",
        "   █ ",
        "     "
    )),
    Glyph.SmallSymbol(SmallSymbol.BRACKET_RIGHT) to Pattern(arrayOf(
        " █   ",
        "  █  ",
        "   █ ",
        "   █ ",
        "   █ ",
        "  █  ",
        " █   ",
        "     "
    )),
    Glyph.SmallSymbol(SmallSymbol.PERCENT) to Pattern(arrayOf(
        "██   ",
        "██  █",
        "   █ ",
        "  █  ",
        " █   ",
        "█  ██",
        "   ██"
    )),
    Glyph.SmallSymbol(SmallSymbol.BASAL) to Pattern(arrayOf(
        "  ████  ",
        "  █  ███",
        "███  █ █",
        "█ █  █ █",
        "█ █  █ █",
        "█ █  █ █",
        "█ █  █ █"
    )),
    Glyph.SmallSymbol(SmallSymbol.MINUS) to Pattern(arrayOf(
        "       ",
        "       ",
        "       ",
        " █████ ",
        "       ",
        "       ",
        "       "
    )),
    Glyph.SmallSymbol(SmallSymbol.WARRANTY) to Pattern(arrayOf(
        " ███ █  ",
        "  ██  █ ",
        " █ █   █",
        "█      █",
        "█   █ █ ",
        " █  ██  ",
        "  █ ███ "
    )),

    Glyph.SmallDigit(0) to Pattern(arrayOf(
        " ███ ",
        "█   █",
        "█  ██",
        "█ █ █",
        "██  █",
        "█   █",
        " ███ "
    )),
    Glyph.SmallDigit(1) to Pattern(arrayOf(
        "  █  ",
        " ██  ",
        "  █  ",
        "  █  ",
        "  █  ",
        "  █  ",
        " ███ "
    )),
    Glyph.SmallDigit(2) to Pattern(arrayOf(
        " ███ ",
        "█   █",
        "    █",
        "   █ ",
        "  █  ",
        " █   ",
        "█████"
    )),
    Glyph.SmallDigit(3) to Pattern(arrayOf(
        "█████",
        "   █ ",
        "  █  ",
        "   █ ",
        "    █",
        "█   █",
        " ███ "
    )),
    Glyph.SmallDigit(4) to Pattern(arrayOf(
        "   █ ",
        "  ██ ",
        " █ █ ",
        "█  █ ",
        "█████",
        "   █ ",
        "   █ "

    )),
    Glyph.SmallDigit(5) to Pattern(arrayOf(
        "█████",
        "█    ",
        "████ ",
        "    █",
        "    █",
        "█   █",
        " ███ "
    )),
    Glyph.SmallDigit(6) to Pattern(arrayOf(
        "  ██ ",
        " █   ",
        "█    ",
        "████ ",
        "█   █",
        "█   █",
        " ███ "
    )),
    Glyph.SmallDigit(7) to Pattern(arrayOf(
        "█████",
        "    █",
        "   █ ",
        "  █  ",
        " █   ",
        " █   ",
        " █   "
    )),
    Glyph.SmallDigit(8) to Pattern(arrayOf(
        " ███ ",
        "█   █",
        "█   █",
        " ███ ",
        "█   █",
        "█   █",
        " ███ "
    )),
    Glyph.SmallDigit(9) to Pattern(arrayOf(
        " ███ ",
        "█   █",
        "█   █",
        " ████",
        "    █",
        "   █ ",
        " ██  "
    )),

    Glyph.SmallCharacter('A') to Pattern(arrayOf(
        "  █  ",
        " █ █ ",
        "█   █",
        "█████",
        "█   █",
        "█   █",
        "█   █"
    )),
    Glyph.SmallCharacter('a') to Pattern(arrayOf(
        " ███ ",
        "    █",
        " ████",
        "█   █",
        " ████"
    )),
    Glyph.SmallCharacter('Ä') to Pattern(arrayOf(
        "█   █",
        " ███ ",
        "█   █",
        "█   █",
        "█████",
        "█   █",
        "█   █"
    )),
    Glyph.SmallCharacter('ă') to Pattern(arrayOf(
        " █ █ ",
        "  █  ",
        "  █  ",
        " █ █ ",
        "█   █",
        "█████",
        "█   █"
    )),
    Glyph.SmallCharacter('Á') to Pattern(arrayOf(
        "   █ ",
        "  █  ",
        " ███ ",
        "█   █",
        "█████",
        "█   █",
        "█   █"
    )),
    Glyph.SmallCharacter('á') to Pattern(arrayOf(
        "   █ ",
        "  █  ",
        "  █  ",
        " █ █ ",
        "█   █",
        "█████",
        "█   █"
    )),
    Glyph.SmallCharacter('ã') to Pattern(arrayOf(
        " █  █",
        "█ ██ ",
        "  █  ",
        " █ █ ",
        "█   █",
        "█████",
        "█   █"
    )),
    Glyph.SmallCharacter('Ą') to Pattern(arrayOf(
        " ███ ",
        "█   █",
        "█████",
        "█   █",
        "█   █",
        "   █ ",
        "    █"
    )),
    Glyph.SmallCharacter('Å') to Pattern(arrayOf(
        "  █  ",
        " █ █ ",
        "  █  ",
        " █ █ ",
        "█   █",
        "█████",
        "█   █"
    )),

    Glyph.SmallCharacter('æ') to Pattern(arrayOf(
        " ████",
        "█ █  ",
        "█ █  ",
        "████ ",
        "█ █  ",
        "█ █  ",
        "█ ███"
    )),

    Glyph.SmallCharacter('B') to Pattern(arrayOf(
        "████ ",
        "█   █",
        "█   █",
        "████ ",
        "█   █",
        "█   █",
        "████ "
    )),
    Glyph.SmallCharacter('C') to Pattern(arrayOf(
        " ███ ",
        "█   █",
        "█    ",
        "█    ",
        "█    ",
        "█   █",
        " ███ "
    )),
    Glyph.SmallCharacter('ć') to Pattern(arrayOf(
        "   █ ",
        "  █  ",
        " ████",
        "█    ",
        "█    ",
        "█    ",
        " ████"
    )),
    Glyph.SmallCharacter('č') to Pattern(arrayOf(
        " █ █ ",
        "  █  ",
        " ████",
        "█    ",
        "█    ",
        "█    ",
        " ████"
    )),
    Glyph.SmallCharacter('Ç') to Pattern(arrayOf(
        " ████",
        "█    ",
        "█    ",
        "█    ",
        " ████",
        "  █  ",
        " ██  "
    )),

    Glyph.SmallCharacter('D') to Pattern(arrayOf(
        "███  ",
        "█  █ ",
        "█   █",
        "█   █",
        "█   █",
        "█  █ ",
        "███  "
    )),
    Glyph.SmallCharacter('E') to Pattern(arrayOf(
        "█████",
        "█    ",
        "█    ",
        "████ ",
        "█    ",
        "█    ",
        "█████"
    )),
    Glyph.SmallCharacter('É') to Pattern(arrayOf(
        "   █ ",
        "  █  ",
        "█████",
        "█    ",
        "████ ",
        "█    ",
        "█████"
    )),
    Glyph.SmallCharacter('Ê') to Pattern(arrayOf(
        "  █  ",
        " █ █ ",
        "█████",
        "█    ",
        "████ ",
        "█    ",
        "█████"
    )),
    Glyph.SmallCharacter('Ě') to Pattern(arrayOf(
        " █ █ ",
        "  █  ",
        "█████",
        "█    ",
        "████ ",
        "█    ",
        "█████"
    )),
    Glyph.SmallCharacter('ę') to Pattern(arrayOf(
        "█████",
        "█    ",
        "████ ",
        "█    ",
        "█████",
        "  █  ",
        "  ██ "
    )),
    Glyph.SmallCharacter('F') to Pattern(arrayOf(
        "█████",
        "█    ",
        "█    ",
        "████ ",
        "█    ",
        "█    ",
        "█    "
    )),
    Glyph.SmallCharacter('G') to Pattern(arrayOf(
        " ███ ",
        "█   █",
        "█    ",
        "█ ███",
        "█   █",
        "█   █",
        " ████"
    )),
    Glyph.SmallCharacter('H') to Pattern(arrayOf(
        "█   █",
        "█   █",
        "█   █",
        "█████",
        "█   █",
        "█   █",
        "█   █"
    )),
    Glyph.SmallCharacter('I') to Pattern(arrayOf(
        " ███ ",
        "  █  ",
        "  █  ",
        "  █  ",
        "  █  ",
        "  █  ",
        " ███ "
    )),
    Glyph.SmallCharacter('i') to Pattern(arrayOf(
        " █ ",
        "   ",
        "██ ",
        " █ ",
        " █ ",
        " █ ",
        "███"
    )),
    Glyph.SmallCharacter('í') to Pattern(arrayOf(
        "  █",
        " █ ",
        "███",
        " █ ",
        " █ ",
        " █ ",
        "███"
    )),
    Glyph.SmallCharacter('İ') to Pattern(arrayOf(
        " █ ",
        "   ",
        "███",
        " █ ",
        " █ ",
        " █ ",
        "███"
    )),

    Glyph.SmallCharacter('J') to Pattern(arrayOf(
        "  ███",
        "   █ ",
        "   █ ",
        "   █ ",
        "   █ ",
        "█  █ ",
        " ██  "
    )),
    Glyph.SmallCharacter('K') to Pattern(arrayOf(
        "█   █",
        "█  █ ",
        "█ █  ",
        "██   ",
        "█ █  ",
        "█  █ ",
        "█   █"
    )),
    Glyph.SmallCharacter('L') to Pattern(arrayOf(
        "█    ",
        "█    ",
        "█    ",
        "█    ",
        "█    ",
        "█    ",
        "█████"
    )),
    Glyph.SmallCharacter('ł') to Pattern(arrayOf(
        " █   ",
        " █   ",
        " █ █ ",
        " ██  ",
        "██   ",
        " █   ",
        " ████"
    )),
    Glyph.SmallCharacter('M') to Pattern(arrayOf(
        "█   █",
        "██ ██",
        "█ █ █",
        "█ █ █",
        "█   █",
        "█   █",
        "█   █"
    )),
    Glyph.SmallCharacter('N') to Pattern(arrayOf(
        "█   █",
        "█   █",
        "██  █",
        "█ █ █",
        "█  ██",
        "█   █",
        "█   █"
    )),
    Glyph.SmallCharacter('Ñ') to Pattern(arrayOf(
        " █  █",
        "█ ██ ",
        "█   █",
        "██  █",
        "█ █ █",
        "█  ██",
        "█   █"
    )),
    Glyph.SmallCharacter('ň') to Pattern(arrayOf(
        " █ █ ",
        "  █  ",
        "█   █",
        "██  █",
        "█ █ █",
        "█  ██",
        "█   █"
    )),
    Glyph.SmallCharacter('ń') to Pattern(arrayOf(
        "   █ ",
        "  █  ",
        "█   █",
        "██  █",
        "█ █ █",
        "█  ██",
        "█   █"
    )),

    Glyph.SmallCharacter('O') to Pattern(arrayOf(
        " ███ ",
        "█   █",
        "█   █",
        "█   █",
        "█   █",
        "█   █",
        " ███ "
    )),
    Glyph.SmallCharacter('Ö') to Pattern(arrayOf(
        "█   █",
        " ███ ",
        "█   █",
        "█   █",
        "█   █",
        "█   █",
        " ███ "
    )),
    Glyph.SmallCharacter('ó') to Pattern(arrayOf(
        "   █ ",
        "  █  ",
        " ███ ",
        "█   █",
        "█   █",
        "█   █",
        " ███ "
    )),
    Glyph.SmallCharacter('ø') to Pattern(arrayOf(
        "     █",
        "  ███ ",
        " █ █ █",
        " █ █ █",
        " █ █ █",
        "  ███ ",
        " █    "
    )),
    Glyph.SmallCharacter('ő') to Pattern(arrayOf(
        " █  █",
        "█  █ ",
        " ███ ",
        "█   █",
        "█   █",
        "█   █",
        " ███ "
    )),

    Glyph.SmallCharacter('P') to Pattern(arrayOf(
        "████ ",
        "█   █",
        "█   █",
        "████ ",
        "█    ",
        "█    ",
        "█    "
    )),
    Glyph.SmallCharacter('Q') to Pattern(arrayOf(
        " ███ ",
        "█   █",
        "█   █",
        "█   █",
        "█ █ █",
        "█  █ ",
        " ██ █"
    )),
    Glyph.SmallCharacter('R') to Pattern(arrayOf(
        "████ ",
        "█   █",
        "█   █",
        "████ ",
        "█ █  ",
        "█  █ ",
        "█   █"
    )),
    Glyph.SmallCharacter('S') to Pattern(arrayOf(
        " ████",
        "█    ",
        "█    ",
        " ███ ",
        "    █",
        "    █",
        "████ "
    )),
    Glyph.SmallCharacter('ś') to Pattern(arrayOf(
        "   █ ",
        "  █  ",
        " ████",
        "█    ",
        " ███ ",
        "    █",
        "████ "
    )),
    Glyph.SmallCharacter('š') to Pattern(arrayOf(
        " █ █ ",
        "  █  ",
        " ████",
        "█    ",
        " ███ ",
        "    █",
        "████ "
    )),

    Glyph.SmallCharacter('T') to Pattern(arrayOf(
        "█████",
        "  █  ",
        "  █  ",
        "  █  ",
        "  █  ",
        "  █  ",
        "  █  "
    )),
    Glyph.SmallCharacter('U') to Pattern(arrayOf(
        "█   █",
        "█   █",
        "█   █",
        "█   █",
        "█   █",
        "█   █",
        " ███ "
    )),
    Glyph.SmallCharacter('u') to Pattern(arrayOf(
        "█   █",
        "█   █",
        "█   █",
        "█  ██",
        " ██ █",
        "     "
    )),
    Glyph.SmallCharacter('Ü') to Pattern(arrayOf(
        "█   █",
        "     ",
        "█   █",
        "█   █",
        "█   █",
        "█   █",
        " ███ "
    )),
    Glyph.SmallCharacter('ú') to Pattern(arrayOf(
        "   █ ",
        "  █  ",
        "█   █",
        "█   █",
        "█   █",
        "█   █",
        " ███ "
    )),
    Glyph.SmallCharacter('ů') to Pattern(arrayOf(
        "  █  ",
        " █ █ ",
        "█ █ █",
        "█   █",
        "█   █",
        "█   █",
        " ███ "
    )),
    Glyph.SmallCharacter('V') to Pattern(arrayOf(
        "█   █",
        "█   █",
        "█   █",
        "█   █",
        "█   █",
        " █ █ ",
        "  █  "
    )),
    Glyph.SmallCharacter('W') to Pattern(arrayOf(
        "█   █",
        "█   █",
        "█   █",
        "█ █ █",
        "█ █ █",
        "█ █ █",
        " █ █ "
    )),
    Glyph.SmallCharacter('X') to Pattern(arrayOf(
        "█   █",
        "█   █",
        " █ █ ",
        "  █  ",
        " █ █ ",
        "█   █",
        "█   █"
    )),
    Glyph.SmallCharacter('Y') to Pattern(arrayOf(
        "█   █",
        "█   █",
        "█   █",
        " █ █ ",
        "  █  ",
        "  █  ",
        "  █  "
    )),
    Glyph.SmallCharacter('ý') to Pattern(arrayOf(
        "   █ ",
        "█ █ █",
        "█   █",
        " █ █ ",
        "  █  ",
        "  █  ",
        "  █  "
    )),
    Glyph.SmallCharacter('Z') to Pattern(arrayOf(
        "█████",
        "    █",
        "   █ ",
        "  █  ",
        " █   ",
        "█    ",
        "█████"
    )),
    Glyph.SmallCharacter('ź') to Pattern(arrayOf(
        "  █  ",
        "█████",
        "    █",
        "  ██ ",
        " █   ",
        "█    ",
        "█████"
    )),
    Glyph.SmallCharacter('ž') to Pattern(arrayOf(
        " █ █ ",
        "  █  ",
        "█████",
        "   █ ",
        "  █  ",
        " █   ",
        "█████"
    )),

    Glyph.SmallCharacter('б') to Pattern(arrayOf(
        "█████",
        "█    ",
        "█    ",
        "████ ",
        "█   █",
        "█   █",
        "████ "
    )),
    Glyph.SmallCharacter('ъ') to Pattern(arrayOf(
        "██  ",
        " █  ",
        " █  ",
        " ██ ",
        " █ █",
        " █ █",
        " ██ "
    )),
    Glyph.SmallCharacter('м') to Pattern(arrayOf(
        "█   █",
        "██ ██",
        "█ █ █",
        "█   █",
        "█   █",
        "█   █",
        "█   █"
    )),
    Glyph.SmallCharacter('л') to Pattern(arrayOf(
        " ████",
        " █  █",
        " █  █",
        " █  █",
        " █  █",
        " █  █",
        "██  █"
    )),
    Glyph.SmallCharacter('ю') to Pattern(arrayOf(
        "█  █ ",
        "█ █ █",
        "█ █ █",
        "███ █",
        "█ █ █",
        "█ █ █",
        "█  █ "
    )),
    Glyph.SmallCharacter('а') to Pattern(arrayOf(
        "  █  ",
        " █ █ ",
        "█   █",
        "█   █",
        "█████",
        "█   █",
        "█   █"
    )),
    Glyph.SmallCharacter('п') to Pattern(arrayOf(
        "█████",
        "█   █",
        "█   █",
        "█   █",
        "█   █",
        "█   █",
        "█   █"
    )),
    Glyph.SmallCharacter('я') to Pattern(arrayOf(
        " ████",
        "█   █",
        "█   █",
        " ████",
        "  █ █",
        " █  █",
        "█   █"
    )),
    Glyph.SmallCharacter('й') to Pattern(arrayOf(
        " █ █ ",
        "  █  ",
        "█   █",
        "█  ██",
        "█ █ █",
        "██  █",
        "█   █"
    )),
    Glyph.SmallCharacter('Г') to Pattern(arrayOf(
        "█████",
        "█    ",
        "█    ",
        "█    ",
        "█    ",
        "█    ",
        "█    "
    )),
    Glyph.SmallCharacter('д') to Pattern(arrayOf(
        "  ██ ",
        " █ █ ",
        " █ █ ",
        "█  █ ",
        "█  █ ",
        "█████",
        "█   █"
    )),
    Glyph.SmallCharacter('ь') to Pattern(arrayOf(
        "█   ",
        "█   ",
        "█   ",
        "███ ",
        "█  █",
        "█  █",
        "███ "
    )),
    Glyph.SmallCharacter('ж') to Pattern(arrayOf(
        "█ █ █",
        "█ █ █",
        " ███ ",
        " ███ ",
        "█ █ █",
        "█ █ █",
        "█ █ █"
    )),
    Glyph.SmallCharacter('ы') to Pattern(arrayOf(
        "█   █",
        "█   █",
        "█   █",
        "██  █",
        "█ █ █",
        "█ █ █",
        "██  █"
    )),
    Glyph.SmallCharacter('у') to Pattern(arrayOf(
        "█   █",
        "█   █",
        "█   █",
        " ███ ",
        "  █  ",
        " █   ",
        "█    "
    )),
    Glyph.SmallCharacter('ч') to Pattern(arrayOf(
        "█   █",
        "█   █",
        "█   █",
        "█  ██",
        " ██ █",
        "    █",
        "    █"
    )),
    Glyph.SmallCharacter('з') to Pattern(arrayOf(
        "  ███ ",
        " █   █",
        "     █",
        "   ██ ",
        "     █",
        " █   █",
        "  ███ "
    )),
    Glyph.SmallCharacter('ц') to Pattern(arrayOf(
        "█  █ ",
        "█  █ ",
        "█  █ ",
        "█  █ ",
        "█  █ ",
        "█████",
        "    █"
    )),
    Glyph.SmallCharacter('и') to Pattern(arrayOf(
        "█   █",
        "█  ██",
        "█ █ █",
        "█ █ █",
        "█ █ █",
        "██  █",
        "█   █"
    )),

    Glyph.SmallCharacter('Σ') to Pattern(arrayOf(
        "█████",
        "█    ",
        " █   ",
        "  █  ",
        " █   ",
        "█    ",
        "█████"
    )),
    Glyph.SmallCharacter('Δ') to Pattern(arrayOf(
        "  █  ",
        "  █  ",
        " █ █ ",
        " █ █ ",
        "█   █",
        "█   █",
        "█████"
    )),
    Glyph.SmallCharacter('Φ') to Pattern(arrayOf(
        "  █  ",
        " ███ ",
        "█ █ █",
        "█ █ █",
        "█ █ █",
        " ███ ",
        "  █  "
    )),
    Glyph.SmallCharacter('Λ') to Pattern(arrayOf(
        "  █  ",
        " █ █ ",
        " █ █ ",
        "█   █",
        "█   █",
        "█   █",
        "█   █"
    )),
    Glyph.SmallCharacter('Ω') to Pattern(arrayOf(
        " ███ ",
        "█   █",
        "█   █",
        "█   █",
        "█   █",
        " █ █ ",
        "██ ██"
    )),
    Glyph.SmallCharacter('υ') to Pattern(arrayOf(
        "█   █",
        "█   █",
        "█   █",
        " ███ ",
        "  █  ",
        "  █  ",
        "  █  "
    )),
    Glyph.SmallCharacter('Θ') to Pattern(arrayOf(
        " ███ ",
        "█   █",
        "█   █",
        "█ █ █",
        "█   █",
        "█   █",
        " ███ "
    ))
)

package app.aaps.core.ui.compose

/**
 * Reads the hour field out of a Unicode date format pattern - the kind `NSDateFormatter.dateFormat`
 * returns on Apple, and `SimpleDateFormat.toPattern()` on the JVM.
 *
 * Looking for the AM/PM letter instead is the obvious way to do this and it is wrong. Traditional
 * Chinese asks for `Bh:mm`, where `B` is the flexible day period - 上午 and 下午 - so the pattern is a
 * 12 hour one with no `a` anywhere in it. A user with the "24-Hour Time" switch off would have been
 * shown a 24 hour picker.
 *
 * The hour field itself is the reliable signal, and it is fixed by the Unicode standard rather than
 * by any locale: `h` and `K` count to twelve, `H` and `k` count to twenty four.
 *
 * @return true or false when the pattern names an hour, null when it names none.
 */
internal fun usesTwelveHourClock(pattern: String): Boolean? {
    var inQuote = false
    var index = 0
    while (index < pattern.length) {
        val letter = pattern[index]
        if (letter == '\'') {
            // Two in a row are one literal apostrophe, not the start and end of an empty quote.
            if (index + 1 < pattern.length && pattern[index + 1] == '\'') index++ else inQuote = !inQuote
            index++
            continue
        }
        if (!inQuote) when (letter) {
            'h', 'K' -> return true
            'H', 'k' -> return false
        }
        index++
    }
    return null
}

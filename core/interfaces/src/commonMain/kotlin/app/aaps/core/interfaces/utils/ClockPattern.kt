package app.aaps.core.interfaces.utils

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
 * ## Why this is here rather than in `:core:ui`
 *
 * Two different things ask the question and they must not disagree: the Compose theme, which decides
 * whether a time picker offers a 12 or 24 hour dial, and `DateFormatPlatform`, which decides whether
 * `DateUtil` prints `HH:mm` or `h:mm a`. They were separate implementations, and the iOS pair
 * actually did disagree - the theme parsed the hour field while `IosDateFormatPlatform` used the
 * AM/PM heuristic above, so on a `zh-Hant` phone with the switch off the app printed 24 hour times
 * next to a 12 hour picker. The JVM had a third copy, correct but written out again.
 *
 * `:core:interfaces` is the lowest module both `:core:ui` and `:shared:impl` already depend on, so
 * putting it here needs no new dependency and leaves one implementation to test.
 *
 * @return true or false when the pattern names an hour, null when it names none. A caller that has
 *   to answer anyway should read null as "not 12 hour": for a medical log an ambiguous `7:30` is
 *   worse than an unfamiliar `19:30`.
 */
fun usesTwelveHourClock(pattern: String): Boolean? {
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

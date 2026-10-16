package app.aaps.wear.complications.cwf

/**
 * What is worth saying about a frame that has just been served, or null when there is nothing.
 *
 * The line this replaces printed one entry per second, which is what made it useful and what made it
 * unusable. Useful, because reading the depicted seconds in order is how the two worst faults of this
 * topic were proved fixed on a real watch: hands that stepped backwards, and frames that stopped
 * arriving. Unusable, because a watch face writing to the log every second drowns everything else in
 * it - and the log is shared with the rest of AAPS.
 *
 * So the series is checked instead of printed. A frame that simply follows the one before says
 * nothing at all; the log stays empty for as long as the clock behaves. Only a break speaks, and a
 * break is exactly what we would want to know about.
 *
 * @param previousSecond the second depicted by the frame served before this one, 0 if none
 * @param second the second depicted by the frame just served
 */
fun servedFrameWarning(previousSecond: Long, second: Long): String? = when {
    previousSecond == 0L                -> null
    second == previousSecond + 1_000    -> null
    // Time only moves one way. This was seen on a Galaxy Watch 4 as the second and minute hands
    // stepping back, and it is guarded against in two places - if it ever shows again, it should say
    // so rather than wait for someone to notice on their wrist.
    second < previousSecond             -> "a frame went back from $previousSecond to $second"
    // The system stops asking under load: during an app install the second hand was seen moving at
    // 5, 2, 3, 2, 20 and 20 seconds. Nothing of ours can conjure a request, but the gap is worth
    // knowing when reading a log after the fact.
    else                                -> "a gap of ${(second - previousSecond) / 1_000} s before $second"
}

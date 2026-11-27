package app.aaps.core.data.model

/**
 * A glucose trend.
 *
 * [text] is the wire name, used by Nightscout and by the phone-to-watch protocol. Do not translate it.
 *
 * [symbol] is NOT a display string, despite looking like one. Three values have no arrow character
 * and carry a placeholder instead: [NONE] is `"??"`, and both [TRIPLE_UP] and [TRIPLE_DOWN] are
 * `"X"`. Showing it raw puts that placeholder in front of the user, and a screen reader cannot say
 * an arrow character usefully either. For the UI use the `directionToIcon()` and
 * `directionToDescription()` extensions in `:core:ui` instead.
 *
 * The one place [symbol] is still right is the watch protocol: `DataHandlerMobile` sends it and the
 * watch matches on it to pick a drawable, so those exact strings, placeholders included, are part of
 * the wire format.
 */
enum class TrendArrow(val text: String, val symbol: String) {
    NONE("NONE", "??"),
    TRIPLE_UP("TripleUp", "X"),
    DOUBLE_UP("DoubleUp", "\u21c8"),
    SINGLE_UP("SingleUp", "\u2191"),
    FORTY_FIVE_UP("FortyFiveUp", "\u2197"),
    FLAT("Flat", "\u2192"),
    FORTY_FIVE_DOWN("FortyFiveDown", "\u2198"),
    SINGLE_DOWN("SingleDown", "\u2193"),
    DOUBLE_DOWN("DoubleDown", "\u21ca"),
    TRIPLE_DOWN("TripleDown", "X")
    ;

    companion object {

        fun fromString(direction: String?) =
            entries.firstOrNull { it.text == direction } ?: NONE

        /**
         * The arrow for a glucose change of [mgdlPerMinute].
         *
         * One set of limits for every trend that AAPS works out itself: the trend on the main screen
         * (`TrendCalculator`) and the arrow of a reading whose source gives none. A rise of more than
         * 40 mg/dL a minute is not a real change, so it has no arrow.
         */
        fun fromSlope(mgdlPerMinute: Double): TrendArrow = when {
            mgdlPerMinute <= -3.5 -> DOUBLE_DOWN
            mgdlPerMinute <= -2   -> SINGLE_DOWN
            mgdlPerMinute <= -1   -> FORTY_FIVE_DOWN
            mgdlPerMinute <= 1    -> FLAT
            mgdlPerMinute <= 2    -> FORTY_FIVE_UP
            mgdlPerMinute <= 3.5  -> SINGLE_UP
            mgdlPerMinute <= 40   -> DOUBLE_UP
            else                  -> NONE
        }
    }
}

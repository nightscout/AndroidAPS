package app.aaps.core.data.model

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
    }
}

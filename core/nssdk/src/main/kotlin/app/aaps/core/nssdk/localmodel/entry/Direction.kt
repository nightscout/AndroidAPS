package app.aaps.core.nssdk.localmodel.entry

enum class Direction(val nsName: String, val txtIcon: String) {
    TRIPLE_DOWN("TripleDown", "\u290B"), // ⤋
    DOUBLE_DOWN("DoubleDown", "\u21ca"), // ⇊
    SINGLE_DOWN("SingleDown", "\u2193"), // ↓
    FORTY_FIVE_DOWN("FortyFiveDown", "\u2198"), // ↘
    FLAT("Flat", "\u2192"), // →
    FORTY_FIVE_UP("FortyFiveUp", "\u2197"), // ↗
    SINGLE_UP("SingleUp", "\u2191"), // ↑
    DOUBLE_UP("DoubleUp", "\u21c8"), // ⇈
    TRIPLE_UP("TripleUp", "\u290A"), // ⤊
    NONE("NONE", "⇼"),
    INVALID("", "-");

    companion object {

        fun fromString(text: String?) = Direction.values().firstOrNull { it.nsName == text } ?: NONE
    }
}

/*

Nightscout:
   NONE: '⇼'
    , TripleUp: '⤊'             \u290A
    , DoubleUp: '⇈'
    , SingleUp: '↑'
    , FortyFiveUp: '↗'
    , Flat: '→'
    , FortyFiveDown: '↘'
    , SingleDown: '↓'
    , DoubleDown: '⇊'
    , TripleDown: '⤋'           \u290B
    , 'NOT COMPUTABLE': '-'
    , 'RATE OUT OF RANGE': '⇕'  \u21D5

    xDrip:


        if (slope_name.compareTo("DoubleDown") == 0) {
            slope_by_minute = -3.5;
        } else if (slope_name.compareTo("SingleDown") == 0) {
            slope_by_minute = -2;
        } else if (slope_name.compareTo("FortyFiveDown") == 0) {
            slope_by_minute = -1;
        } else if (slope_name.compareTo("Flat") == 0) {
            slope_by_minute = 0;
        } else if (slope_name.compareTo("FortyFiveUp") == 0) {
            slope_by_minute = 2;
        } else if (slope_name.compareTo("SingleUp") == 0) {
            slope_by_minute = 3.5;
        } else if (slope_name.compareTo("DoubleUp") == 0) {


 */

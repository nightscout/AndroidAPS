package app.aaps.core.nssdk.localmodel.entry

enum class Direction(val nsName: String) {
    TRIPLE_DOWN("TripleDown"), // ⤋
    DOUBLE_DOWN("DoubleDown"), // ⇊
    SINGLE_DOWN("SingleDown"), // ↓
    FORTY_FIVE_DOWN("FortyFiveDown"), // ↘
    FLAT("Flat"), // →
    FORTY_FIVE_UP("FortyFiveUp"), // ↗
    SINGLE_UP("SingleUp"), // ↑
    DOUBLE_UP("DoubleUp"), // ⇈
    TRIPLE_UP("TripleUp"), // ⤊
    NONE("NONE"),
    INVALID("");

    companion object {

        fun fromString(text: String?) = entries.firstOrNull { it.nsName == text } ?: NONE
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

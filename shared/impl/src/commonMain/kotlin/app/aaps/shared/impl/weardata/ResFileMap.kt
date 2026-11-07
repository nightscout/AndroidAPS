package app.aaps.shared.impl.weardata

enum class ResFileMap(val fileName: String) {
    UNKNOWN("Unknown"),
    CUSTOM_WATCHFACE("CustomWatchface"),
    BACKGROUND("Background"),
    BACKGROUND_HIGH("BackgroundHigh"),
    BACKGROUND_LOW("BackgroundLow"),
    COVER_CHART("CoverChart"),
    COVER_CHART_HIGH("CoverChartHigh"),
    COVER_CHART_LOW("CoverChartLow"),
    COVER_PLATE("CoverPlate"),
    COVER_PLATE_HIGH("CoverPlateHigh"),
    COVER_PLATE_LOW("CoverPlateLow"),
    HOUR_HAND("HourHand"),
    HOUR_HAND_HIGH("HourHandHigh"),
    HOUR_HAND_LOW("HourHandLow"),
    MINUTE_HAND("MinuteHand"),
    MINUTE_HAND_HIGH("MinuteHandHigh"),
    MINUTE_HAND_LOW("MinuteHandLow"),
    SECOND_HAND("SecondHand"),
    SECOND_HAND_HIGH("SecondHandHigh"),
    SECOND_HAND_LOW("SecondHandLow"),
    ARROW_NONE("ArrowNone"),
    ARROW_DOUBLE_UP("ArrowDoubleUp"),
    ARROW_SINGLE_UP("ArrowSingleUp"),
    ARROW_FORTY_FIVE_UP("Arrow45Up"),
    ARROW_FLAT("ArrowFlat"),
    ARROW_FORTY_FIVE_DOWN("Arrow45Down"),
    ARROW_SINGLE_DOWN("ArrowSingleDown"),
    ARROW_DOUBLE_DOWN("ArrowDoubleDown");

    companion object {

        fun fromFileName(file: String): ResFileMap = entries.firstOrNull { it.fileName == file.substringBeforeLast(".") } ?: UNKNOWN
    }
}

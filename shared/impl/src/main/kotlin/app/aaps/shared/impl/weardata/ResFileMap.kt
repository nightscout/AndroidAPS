package app.aaps.shared.impl.weardata

enum class ResFileMap(val fileName: String) {
    UNKNOWN("Unknown"),
    CUSTOM_WATCHFACE("CustomWatchface"),
    BACKGROUND("Background"),
    BACKGROUND_HIGH("BackgroundHigh"),
    BACKGROUND_LOW("BackgroundLow"),
    BACKGROUND_VERY_HIGH("BackgroundVeryHigh"),
    BACKGROUND_VERY_LOW("BackgroundVeryLow"),
    COVER_CHART("CoverChart"),
    COVER_CHART_HIGH("CoverChartHigh"),
    COVER_CHART_LOW("CoverChartLow"),
    COVER_CHART_VERY_HIGH("CoverChartVeryHigh"),
    COVER_CHART_VERY_LOW("CoverChartVeryLow"),
    COVER_PLATE("CoverPlate"),
    COVER_PLATE_HIGH("CoverPlateHigh"),
    COVER_PLATE_LOW("CoverPlateLow"),
    COVER_PLATE_VERY_HIGH("CoverPlateVeryHigh"),
    COVER_PLATE_VERY_LOW("CoverPlateVeryLow"),
    HOUR_HAND("HourHand"),
    HOUR_HAND_HIGH("HourHandHigh"),
    HOUR_HAND_LOW("HourHandLow"),
    HOUR_HAND_VERY_HIGH("HourHandVeryHigh"),
    HOUR_HAND_VERY_LOW("HourHandVeryLow"),
    MINUTE_HAND("MinuteHand"),
    MINUTE_HAND_HIGH("MinuteHandHigh"),
    MINUTE_HAND_LOW("MinuteHandLow"),
    MINUTE_HAND_VERY_HIGH("MinuteHandVeryHigh"),
    MINUTE_HAND_VERY_LOW("MinuteHandVeryLow"),
    SECOND_HAND("SecondHand"),
    SECOND_HAND_HIGH("SecondHandHigh"),
    SECOND_HAND_LOW("SecondHandLow"),
    SECOND_HAND_VERY_HIGH("SecondHandVeryHigh"),
    SECOND_HAND_VERY_LOW("SecondHandVeryLow"),
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

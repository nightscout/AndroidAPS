package app.aaps.shared.impl.weardata

import androidx.annotation.StringRes
import app.aaps.shared.impl.R

enum class ViewKeys(val key: String, @StringRes val comment: Int) {

    BACKGROUND("background", R.string.cwf_comment_background),
    CHART("chart", R.string.cwf_comment_chart),
    COVER_CHART("cover_chart", R.string.cwf_comment_cover_chart),
    FREETEXT1("freetext1", R.string.cwf_comment_freetext1),
    FREETEXT2("freetext2", R.string.cwf_comment_freetext2),
    FREETEXT3("freetext3", R.string.cwf_comment_freetext3),
    FREETEXT4("freetext4", R.string.cwf_comment_freetext4),
    IOB1("iob1", R.string.cwf_comment_iob1),
    IOB2("iob2", R.string.cwf_comment_iob2),
    COB1("cob1", R.string.cwf_comment_cob1),
    COB2("cob2", R.string.cwf_comment_cob2),
    DELTA("delta", R.string.cwf_comment_delta),
    AVG_DELTA("avg_delta", R.string.cwf_comment_avg_delta),
    UPLOADER_BATTERY("uploader_battery", R.string.cwf_comment_uploader_battery),
    RIG_BATTERY("rig_battery", R.string.cwf_comment_rig_battery),
    BASALRATE("basalRate", R.string.cwf_comment_basalRate),
    BGI("bgi", R.string.cwf_comment_bgi),
    STATUS("status", R.string.cwf_comment_status),
    TIME("time", R.string.cwf_comment_time),
    HOUR("hour", R.string.cwf_comment_hour),
    MINUTE("minute", R.string.cwf_comment_minute),
    SECOND("second", R.string.cwf_comment_second),
    TIMEPERIOD("timePeriod", R.string.cwf_comment_timePeriod),
    DAY_NAME("day_name", R.string.cwf_comment_day_name),
    DAY("day", R.string.cwf_comment_day),
    WEEKNUMBER("week_number", R.string.cwf_comment_week_number),
    MONTH("month", R.string.cwf_comment_month),
    LOOP("loop", R.string.cwf_comment_loop),
    DIRECTION("direction", R.string.cwf_comment_direction),
    TIMESTAMP("timestamp", R.string.cwf_comment_timestamp),
    SGV("sgv", R.string.cwf_comment_sgv),
    COVER_PLATE("cover_plate", R.string.cwf_comment_cover_plate),
    HOUR_HAND("hour_hand", R.string.cwf_comment_hour_hand),
    MINUTE_HAND("minute_hand", R.string.cwf_comment_minute_hand),
    SECOND_HAND("second_hand", R.string.cwf_comment_second_hand)
}


package info.nightscout.pump.common.driver.history

import androidx.annotation.StringRes
import info.nightscout.pump.common.R
import info.nightscout.pump.common.defs.PumpHistoryEntryGroup

interface PumpHistoryDataProvider {

    /**
     * Get Data, specified with PumpHistoryPeriod
     */
    fun getData(period: PumpHistoryPeriod): List<PumpHistoryEntry>

    /**
     * Get Initial Period
     */
    fun getInitialPeriod(): PumpHistoryPeriod

    /**
     * Get InitialData
     */
    fun getInitialData(): List<PumpHistoryEntry>

    /**
     * Get Allowed Pump History Groups (for specific pump)
     */
    fun getAllowedPumpHistoryGroups(): List<PumpHistoryEntryGroup>

    /**
     * Get Spinner Width in pixels (same as specifying 150dp)
     */
    fun getSpinnerWidthInPixels(): Int

    /**
     * Get Translation Text
     */
    fun getText(key: PumpHistoryText): String

    /**
     * For filtering of items
     */
    fun isItemInSelection(itemGroup: PumpHistoryEntryGroup, targetGroup: PumpHistoryEntryGroup): Boolean

}

enum class PumpHistoryPeriod constructor(
    @StringRes var stringId: Int,
    var isHours: Boolean = false
) {

    TODAY(R.string.time_today),
    LAST_HOUR(R.string.time_last_hour, true),
    LAST_3_HOURS(R.string.time_last_3_hours, true),
    LAST_6_HOURS(R.string.time_last_6_hours, true),
    LAST_12_HOURS(R.string.time_last_12_hours, true),
    LAST_2_DAYS(R.string.time_last_2_days),
    LAST_4_DAYS(R.string.time_last_4_days),
    LAST_WEEK(R.string.time_last_week),
    LAST_MONTH(R.string.time_last_month),
    ALL(R.string.history_group_all)

}

enum class PumpHistoryText {

    PUMP_HISTORY,

    // OLD ONES
    SCAN_TITLE,
    SELECTED_PUMP_TITLE,
    REMOVE_TITLE,
    REMOVE_TEXT,
    NO_SELECTED_PUMP,
    PUMP_CONFIGURATION
}

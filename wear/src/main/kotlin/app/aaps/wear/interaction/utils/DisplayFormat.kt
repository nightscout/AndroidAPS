package app.aaps.wear.interaction.utils

import android.content.Context
import app.aaps.core.interfaces.rx.weardata.EventData
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.wear.R
import app.aaps.wear.interaction.utils.Pair.Companion.create
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

@Singleton
class DisplayFormat @Inject internal constructor() {

    companion object {

        const val MAX_FIELD_LEN_LONG = 22 // this is found out empirical, for TYPE_LONG_TEXT
        const val MAX_FIELD_LEN_SHORT = 7 // according to Wear OS docs for TYPE_SHORT_TEXT
        const val MIN_FIELD_LEN_COB = 3 // since carbs are usually 0..99g
        const val MIN_FIELD_LEN_IOB = 3 // IoB can range from like .1U to 99U
    }

    @Inject lateinit var sp: SP
    @Inject lateinit var context: Context

    /**
     * Maximal and minimal lengths of fields/labels shown in complications, in characters
     * For MAX values - above that WearOS and watch faces may start ellipsize (...) contents
     * For MIN values - this is minimal length that can hold legible data
     */

    private fun areComplicationsUnicode() = sp.getBoolean("complication_unicode", true)

    private fun deltaSymbol() = if (areComplicationsUnicode()) "\u0394" else ""

    private fun verticalSeparatorSymbol() = if (areComplicationsUnicode()) "\u205E" else "|"

    fun basalRateSymbol() = if (areComplicationsUnicode()) "\u238D\u2006" else ""

    /**
     * Format time elapsed since a reference timestamp in compact form.
     *
     * Returns human-readable time difference optimized for complication display:
     * - < 1 minute: "0'"
     * - < 1 hour: "N'" (minutes with apostrophe, e.g., "15'")
     * - < 1 day: "Nh" (hours, e.g., "3h")
     * - < 7 days: "Nd" (days, e.g., "2d")
     * - >= 7 days: "Nw" (weeks, e.g., "1w")
     *
     * Used to show BG reading age, last bolus time, etc.
     *
     * @param refTime Timestamp in milliseconds to calculate age from
     * @return Compact time difference string (e.g., "5'" or "2h")
     */
    fun shortTimeSince(refTime: Long): String {
        val deltaTimeMs = WearUtil.msSince(refTime)
        return if (deltaTimeMs < Constants.MINUTE_IN_MS) {
            "0'"
        } else if (deltaTimeMs < Constants.HOUR_IN_MS) {
            val minutes = (deltaTimeMs / Constants.MINUTE_IN_MS).toInt()
            "$minutes'"
        } else if (deltaTimeMs < Constants.DAY_IN_MS) {
            val hours = (deltaTimeMs / Constants.HOUR_IN_MS).toInt()
            hours.toString() + context.getString(R.string.hour_short)
        } else {
            val days = (deltaTimeMs / Constants.DAY_IN_MS).toInt()
            if (days < 7) {
                days.toString() + context.getString(R.string.day_short)
            } else {
                val weeks = days / 7
                weeks.toString() + context.getString(R.string.week_short)
            }
        }
    }

    /**
     * Format comprehensive BG info line for LONG_TEXT complications (max 22 chars).
     *
     * Displays full glucose status:
     * - Format: "120↗ Δ+2.5 (15')"
     * - Components: BG value, trend arrow, delta with symbol, age in parentheses
     *
     * Delta precision automatically minimized if line exceeds max length.
     * Delta display controlled by user preference (simple vs detailed).
     *
     * @param singleBg Array of BG data for all datasets
     * @param dataSet Dataset index to format (0-2)
     * @return Formatted glucose line (e.g., "120↗ Δ+2.5 (15')")
     */
    fun longGlucoseLine(singleBg: Array<EventData.SingleBg>, dataSet: Int): String {
        val rawDelta = if (sp.getBoolean(R.string.key_show_detailed_delta, false)) singleBg[dataSet].deltaDetailed else singleBg[dataSet].delta
        return singleBg[dataSet].sgvString + singleBg[dataSet].slopeArrow + " " + deltaSymbol() + SmallestDoubleString(rawDelta).minimise(8) + " (" + shortTimeSince(singleBg[dataSet].timeStamp) + ")"
    }

    /**
     * Format detailed status line with COB, IOB, and basal for LONG_TEXT complications.
     *
     * Displays treatment status with adaptive formatting to fit max 22 characters:
     * 1. Preferred: "15g  ⁞  1.2U  ⁞  ⌍ 0.8U/h" (wide separators, basal symbol)
     * 2. If too long: "15g ⁞ 1.2U ⁞ 0.8U/h" (narrow separators, no basal symbol)
     * 3. If too long: Minimizes IOB precision: "15g ⁞ 1U ⁞ 0.8U/h"
     * 4. If too long: Minimizes COB precision: "15 ⁞ 1U ⁞ 0.8U/h"
     * 5. If still too long: Removes separators: "15 1U 0.8U/h"
     *
     * Separator symbol adapts based on Unicode preference (⁞ vs |).
     *
     * Uses SmallestDoubleString to intelligently reduce precision while maintaining
     * minimum field lengths for clinical relevance (IOB≥3, COB≥3).
     *
     * @param status Array of status data for all datasets
     * @param dataSet Dataset index to format (0-2)
     * @return Formatted status line fitting LONG_TEXT limit (≤22 chars)
     */
    fun longDetailsLine(status: Array<EventData.Status>, dataSet: Int): String {
        val sepLong = "  " + verticalSeparatorSymbol() + "  "
        val sepShort = " " + verticalSeparatorSymbol() + " "
        val sepShortLen = sepShort.length
        val sepMin = " "
        var line = status[dataSet].cob + sepLong + status[dataSet].iobSum + sepLong + basalRateSymbol() + status[dataSet].currentBasal
        if (line.length <= MAX_FIELD_LEN_LONG) {
            return line
        }
        line = status[dataSet].cob + sepShort + status[dataSet].iobSum + sepShort + status[dataSet].currentBasal
        if (line.length <= MAX_FIELD_LEN_LONG) {
            return line
        }
        var remainingMax = MAX_FIELD_LEN_LONG - (status[dataSet].cob.length + status[dataSet].currentBasal.length + sepShortLen * 2)
        val smallestIoB = SmallestDoubleString(status[dataSet].iobSum, SmallestDoubleString.Units.USE).minimise(max(MIN_FIELD_LEN_IOB, remainingMax))
        line = status[dataSet].cob + sepShort + smallestIoB + sepShort + status[dataSet].currentBasal
        if (line.length <= MAX_FIELD_LEN_LONG) {
            return line
        }
        remainingMax = MAX_FIELD_LEN_LONG - (smallestIoB.length + status[dataSet].currentBasal.length + sepShortLen * 2)
        val simplifiedCob = SmallestDoubleString(status[dataSet].cob, SmallestDoubleString.Units.USE).minimise(max(MIN_FIELD_LEN_COB, remainingMax))
        line = simplifiedCob + sepShort + smallestIoB + sepShort + status[dataSet].currentBasal
        if (line.length <= MAX_FIELD_LEN_LONG) {
            return line
        }
        line = simplifiedCob + sepMin + smallestIoB + sepMin + status[dataSet].currentBasal
        return line
    }

    /**
     * Format detailed IOB display with bolus/basal breakdown.
     *
     * Parses IOB detail string "(bolus|basal)" into two display lines:
     * - Line 1: Total IOB minimized to fit SHORT_TEXT (≤7 chars)
     * - Line 2: Breakdown "bolus basal" (e.g., "1.5 -0.3")
     *
     * If detail format invalid or components missing, uses "--" placeholders.
     * Precision automatically minimized to fit while maintaining minimum 3 chars for IOB.
     *
     * Used by complications that support two-line IOB display.
     *
     * @param status Array of status data for all datasets
     * @param dataSet Dataset index to format (0-2)
     * @return Pair of strings (total IOB, bolus/basal breakdown)
     */
    fun detailedIob(status: Array<EventData.Status>, dataSet: Int): Pair<String, String> {
        val iob1 = SmallestDoubleString(status[dataSet].iobSum, SmallestDoubleString.Units.USE).minimise(MAX_FIELD_LEN_SHORT)
        var iob2 = ""
        if (status[dataSet].iobDetail.contains("|")) {
            val iobs = status[dataSet].iobDetail.replace("(", "").replace(")", "").split("|").toTypedArray()
            var iobBolus = SmallestDoubleString(iobs[0]).minimise(MIN_FIELD_LEN_IOB)
            if (iobBolus.trim().isEmpty()) {
                iobBolus = "--"
            }
            var iobBasal = SmallestDoubleString(iobs[1]).minimise(MAX_FIELD_LEN_SHORT - 1 - max(MIN_FIELD_LEN_IOB, iobBolus.length))
            if (iobBasal.trim().isEmpty()) {
                iobBasal = "--"
            }
            iob2 = "$iobBolus $iobBasal"
        }
        return create(iob1, iob2)
    }

    /**
     * Format detailed COB display with absorption info.
     *
     * Parses COB data into two display lines:
     * - Line 1: Current COB minimized to fit SHORT_TEXT (≤7 chars)
     * - Line 2: Extra absorption info if available (e.g., absorption rate)
     *
     * Uses SmallestDoubleString to extract and format additional COB details
     * from the status data if present.
     *
     * Used by complications that support two-line COB display.
     *
     * @param status Array of status data for all datasets
     * @param dataSet Dataset index to format (0-2)
     * @return Pair of strings (current COB, extra absorption info)
     */
    fun detailedCob(status: Array<EventData.Status>, dataSet: Int): Pair<String, String> {
        val cobMini = SmallestDoubleString(status[dataSet].cob, SmallestDoubleString.Units.USE)
        var cob2 = ""
        if (cobMini.extra.isNotEmpty()) {
            cob2 = cobMini.extra + cobMini.units
        }
        val cob1 = cobMini.minimise(MAX_FIELD_LEN_SHORT)
        return create(cob1, cob2)
    }
}

package app.aaps.wear.interaction.utils

import android.content.Context
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.wear.R
import app.aaps.wear.data.RawDisplayData
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
    @Inject lateinit var wearUtil: WearUtil
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

    fun shortTimeSince(refTime: Long): String {
        val deltaTimeMs = wearUtil.msSince(refTime)
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

    fun shortTrend(raw: RawDisplayData): String {
        var minutes = "--"
        val rawDelta = if (sp.getBoolean(R.string.key_show_detailed_delta, false)) raw.singleBg.deltaDetailed else raw.singleBg.delta
        if (raw.singleBg.timeStamp > 0) {
            minutes = shortTimeSince(raw.singleBg.timeStamp)
        }
        if (minutes.length + rawDelta.length + deltaSymbol().length + 1 <= MAX_FIELD_LEN_SHORT) {
            return minutes + " " + deltaSymbol() + rawDelta
        }

        // that only optimizes obvious things like 0 before . or at end, + at beginning
        val delta = SmallestDoubleString(rawDelta).minimise(MAX_FIELD_LEN_SHORT - 1)
        if (minutes.length + delta.length + deltaSymbol().length + 1 <= MAX_FIELD_LEN_SHORT) {
            return minutes + " " + deltaSymbol() + delta
        }
        val shortDelta = SmallestDoubleString(rawDelta).minimise(MAX_FIELD_LEN_SHORT - (1 + minutes.length))
        return "$minutes $shortDelta"
    }

    fun longGlucoseLine(raw: RawDisplayData): String {
        val rawDelta = if (sp.getBoolean(R.string.key_show_detailed_delta, false)) raw.singleBg.deltaDetailed else raw.singleBg.delta
        return raw.singleBg.sgvString + raw.singleBg.slopeArrow + " " + deltaSymbol() + SmallestDoubleString(rawDelta).minimise(8) + " (" + shortTimeSince(raw.singleBg.timeStamp) + ")"
    }

    fun longDetailsLine(raw: RawDisplayData): String {
        val sepLong = "  " + verticalSeparatorSymbol() + "  "
        val sepShort = " " + verticalSeparatorSymbol() + " "
        val sepShortLen = sepShort.length
        val sepMin = " "
        var line = raw.status.cob + sepLong + raw.status.iobSum + sepLong + basalRateSymbol() + raw.status.currentBasal
        if (line.length <= MAX_FIELD_LEN_LONG) {
            return line
        }
        line = raw.status.cob + sepShort + raw.status.iobSum + sepShort + raw.status.currentBasal
        if (line.length <= MAX_FIELD_LEN_LONG) {
            return line
        }
        var remainingMax = MAX_FIELD_LEN_LONG - (raw.status.cob.length + raw.status.currentBasal.length + sepShortLen * 2)
        val smallestIoB = SmallestDoubleString(raw.status.iobSum, SmallestDoubleString.Units.USE).minimise(max(MIN_FIELD_LEN_IOB, remainingMax))
        line = raw.status.cob + sepShort + smallestIoB + sepShort + raw.status.currentBasal
        if (line.length <= MAX_FIELD_LEN_LONG) {
            return line
        }
        remainingMax = MAX_FIELD_LEN_LONG - (smallestIoB.length + raw.status.currentBasal.length + sepShortLen * 2)
        val simplifiedCob = SmallestDoubleString(raw.status.cob, SmallestDoubleString.Units.USE).minimise(max(MIN_FIELD_LEN_COB, remainingMax))
        line = simplifiedCob + sepShort + smallestIoB + sepShort + raw.status.currentBasal
        if (line.length <= MAX_FIELD_LEN_LONG) {
            return line
        }
        line = simplifiedCob + sepMin + smallestIoB + sepMin + raw.status.currentBasal
        return line
    }

    fun detailedIob(raw: RawDisplayData): Pair<String, String> {
        val iob1 = SmallestDoubleString(raw.status.iobSum, SmallestDoubleString.Units.USE).minimise(MAX_FIELD_LEN_SHORT)
        var iob2 = ""
        if (raw.status.iobDetail.contains("|")) {
            val iobs = raw.status.iobDetail.replace("(", "").replace(")", "").split("|").toTypedArray()
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

    fun detailedCob(raw: RawDisplayData): Pair<String, String> {
        val cobMini = SmallestDoubleString(raw.status.cob, SmallestDoubleString.Units.USE)
        var cob2 = ""
        if (cobMini.extra.isNotEmpty()) {
            cob2 = cobMini.extra + cobMini.units
        }
        val cob1 = cobMini.minimise(MAX_FIELD_LEN_SHORT)
        return create(cob1, cob2)
    }
}

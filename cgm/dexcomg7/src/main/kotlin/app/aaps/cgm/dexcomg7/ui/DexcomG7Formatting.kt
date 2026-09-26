package app.aaps.cgm.dexcomg7.ui

import app.aaps.cgm.dexcomg7.R
import app.aaps.cgm.dexcomg7.data.G7SensorRecord
import app.aaps.cgm.dexcomg7.protocol.G7Lifecycle
import app.aaps.cgm.dexcomg7.protocol.G7Trend
import app.aaps.cgm.dexcomg7.session.G7Session
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil

/** Text for the screens. */
object DexcomG7Formatting {

    /** "3 days 4 h", "5 h 10 min", "12 min". Negative durations show as 0 min. */
    fun duration(rh: ResourceHelper, milliseconds: Long): String {
        val minutes = maxOf(0L, milliseconds / 60_000)
        val days = minutes / (24 * 60)
        val hours = (minutes / 60) % 24
        val mins = minutes % 60
        return when {
            days > 0  -> rh.gs(R.string.dexcom_g7_duration_days_hours, days, hours)
            hours > 0 -> rh.gs(R.string.dexcom_g7_duration_hours_minutes, hours, mins)
            else      -> rh.gs(R.string.dexcom_g7_duration_minutes, mins)
        }
    }

    fun refusal(rh: ResourceHelper, kind: String): String = rh.gs(
        when (kind) {
            G7Session.REFUSAL_SLOT_TAKEN -> R.string.dexcom_g7_refused_slot_taken
            G7Session.REFUSAL_KEY        -> R.string.dexcom_g7_refused_key
            G7Session.REFUSAL_WRONG_CODE -> R.string.dexcom_g7_refused_wrong_code
            else                         -> R.string.dexcom_g7_refused_other
        }
    )

    fun previous(rh: ResourceHelper, dateUtil: DateUtil, record: G7SensorRecord): String {
        val name = record.sensorName ?: "?"
        val days = record.sessionLengthSeconds?.let { " " + rh.gs(R.string.dexcom_g7_days, G7Lifecycle.lifetimeMs(it) / (24 * 60 * 60 * 1000L)) } ?: ""
        val started = record.activatedAt?.let { dateUtil.dateAndTimeString(it) } ?: "?"
        return rh.gs(R.string.dexcom_g7_previous_sensor_text, name + days, started, dateUtil.dateAndTimeString(record.endedAt))
    }

    fun arrow(trend: G7Trend?): String = when (trend) {
        G7Trend.DOUBLE_DOWN     -> "⇊"
        G7Trend.SINGLE_DOWN     -> "↓"
        G7Trend.FORTY_FIVE_DOWN -> "↘"
        G7Trend.FLAT            -> "→"
        G7Trend.FORTY_FIVE_UP   -> "↗"
        G7Trend.SINGLE_UP       -> "↑"
        G7Trend.DOUBLE_UP       -> "⇈"
        null                    -> ""
    }
}

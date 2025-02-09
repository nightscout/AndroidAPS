package app.aaps.pump.insight.keys

import app.aaps.core.keys.interfaces.LongNonPreferenceKey

enum class InsightLongNonKey(
    override val key: String,
    override val defaultValue: Long
) : LongNonPreferenceKey {
    LastBolusTimestamp("insight_last_bolus_timestamp", 0L),
}


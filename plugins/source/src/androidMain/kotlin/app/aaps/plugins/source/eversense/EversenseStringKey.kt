package app.aaps.plugins.source.eversense

import app.aaps.core.keys.interfaces.StringPreferenceKey
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.plugins.source.R

/** Preserve PR #191's keys, string storage, mmol/L values and defaults. */
enum class EversenseStringKey(
    override val key: String,
    override val defaultValue: String,
    titleRes: Int
) : StringPreferenceKey {
    TargetHigh("eversense_target_high", "7.8", R.string.eversense_target_high),
    TargetLow("eversense_target_low", "4.4", R.string.eversense_target_low),
    AlertHigh("eversense_alert_high", "11.1", R.string.eversense_alert_high),
    AlertLow("eversense_alert_low", "3.9", R.string.eversense_alert_low);

    override val title = TextRef.AndroidRes(titleRes)
    override val summary = TextRef.AndroidRes(R.string.eversense_mmol_unit)
    override val isPassword = false
    override val isPin = false
}

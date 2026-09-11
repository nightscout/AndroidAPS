package app.aaps.pump.insight.keys

import app.aaps.core.keys.interfaces.BooleanPreferenceKey
import app.aaps.core.keys.interfaces.IntPreferenceKey
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.pump.insight.R

enum class InsightIntKey(
    override val key: String,
    override val defaultValue: Int,
    private val titleResId: Int,
    override val min: Int = Int.MIN_VALUE,
    override val max: Int = Int.MAX_VALUE,
) : IntPreferenceKey {

    MinRecoveryDuration("insight_min_recovery_duration", 5, titleResId = R.string.min_recovery_duration),
    MaxRecoveryDuration("insight_max_recovery_duration", 20, titleResId = R.string.max_recovery_duration),
    DisconnectDelay("insight_disconnect_delay", 5, titleResId = R.string.disconnect_delay),
    ;

    override val title: TextRef = TextRef.AndroidRes(titleResId)
}

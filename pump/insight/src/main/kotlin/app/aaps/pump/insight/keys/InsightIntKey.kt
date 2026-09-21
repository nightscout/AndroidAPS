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

    // Bounds match the limits the driver already applies when it reads these values. Without them the
    // preference screen offered the whole Int range and then silently clamped what the user typed.
    // The driver keeps clamping on read, because a value stored before these bounds existed can still
    // be out of range.
    MinRecoveryDuration("insight_min_recovery_duration", 5, titleResId = R.string.min_recovery_duration, min = 0, max = 20),
    MaxRecoveryDuration("insight_max_recovery_duration", 20, titleResId = R.string.max_recovery_duration, min = 0, max = 20),
    DisconnectDelay("insight_disconnect_delay", 5, titleResId = R.string.disconnect_delay, min = 0, max = 15),
    ;

    override val title: TextRef = TextRef.AndroidRes(titleResId)
}

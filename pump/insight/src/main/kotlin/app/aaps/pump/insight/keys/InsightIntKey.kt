package app.aaps.pump.insight.keys

import app.aaps.core.keys.interfaces.BooleanPreferenceKey
import app.aaps.core.keys.interfaces.IntPreferenceKey
import app.aaps.pump.insight.R

enum class InsightIntKey(
    override val key: String,
    override val defaultValue: Int,
    override val titleResId: Int = 0,
    override val min: Int = Int.MIN_VALUE,
    override val max: Int = Int.MAX_VALUE,
    override val calculatedDefaultValue: Boolean = false,
    override val engineeringModeOnly: Boolean = false,
    override val defaultedBySM: Boolean = false,
    override val showInApsMode: Boolean = true,
    override val showInNsClientMode: Boolean = true,
    override val showInPumpControlMode: Boolean = true,
    override val dependency: BooleanPreferenceKey? = null,
    override val negativeDependency: BooleanPreferenceKey? = null,
    override val hideParentScreenIfHidden: Boolean = false,
    override val exportable: Boolean = true
) : IntPreferenceKey {

    MinRecoveryDuration("insight_min_recovery_duration", 5, titleResId = R.string.min_recovery_duration),
    MaxRecoveryDuration("insight_max_recovery_duration", 20, titleResId = R.string.max_recovery_duration),
    DisconnectDelay("insight_disconnect_delay", 5, titleResId = R.string.disconnect_delay),
}

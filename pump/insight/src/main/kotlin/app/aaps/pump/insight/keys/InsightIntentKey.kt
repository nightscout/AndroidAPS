package app.aaps.pump.insight.keys

import app.aaps.core.keys.interfaces.BooleanPreferenceKey
import app.aaps.core.keys.interfaces.IntentPreferenceKey
import app.aaps.pump.insight.R

enum class InsightIntentKey(
    override val key: String,
    override val titleResId: Int = 0,
    override val defaultedBySM: Boolean = false,
    override val showInApsMode: Boolean = true,
    override val showInNsClientMode: Boolean = true,
    override val showInPumpControlMode: Boolean = true,
    override val dependency: BooleanPreferenceKey? = null,
    override val negativeDependency: BooleanPreferenceKey? = null,
    override val hideParentScreenIfHidden: Boolean = false,
    override val exportable: Boolean = false
) : IntentPreferenceKey {

    InsightPairing(key = "insight_pairing", titleResId = R.string.insight_pairing)
}

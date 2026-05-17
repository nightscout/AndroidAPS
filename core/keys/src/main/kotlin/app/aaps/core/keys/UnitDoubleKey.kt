package app.aaps.core.keys

import app.aaps.core.keys.interfaces.BooleanPreferenceKey
import app.aaps.core.keys.interfaces.UnitDoublePreferenceKey

enum class UnitDoubleKey(
    override val key: String,
    override val defaultValue: Double,
    override val minMgdl: Int,
    override val maxMgdl: Int,
    override val titleResId: Int,
    override val summaryResId: Int? = null,
    override val preferenceType: PreferenceType = PreferenceType.TEXT_FIELD,
    override val defaultedBySM: Boolean = false,
    override val showInApsMode: Boolean = true,
    override val showInNsClientMode: Boolean = true,
    override val showInPumpControlMode: Boolean = true,
    override val dependency: BooleanPreferenceKey? = null,
    override val negativeDependency: BooleanPreferenceKey? = null,
    override val hideParentScreenIfHidden: Boolean = false,
    override val exportable: Boolean = true
) : UnitDoublePreferenceKey {

    OverviewLowMark(key = "low_mark", defaultValue = 72.0, minMgdl = 25, maxMgdl = 160, titleResId = R.string.pref_title_low_mark, showInNsClientMode = false, hideParentScreenIfHidden = true),
    OverviewHighMark(key = "high_mark", defaultValue = 180.0, minMgdl = 90, maxMgdl = 250, titleResId = R.string.pref_title_high_mark, showInNsClientMode = false),
    ApsLgsThreshold(
        key = "lgsThreshold",
        defaultValue = 65.0,
        minMgdl = 60,
        maxMgdl = 100,
        titleResId = R.string.pref_title_lgs_threshold,
        summaryResId = R.string.lgs_threshold_summary,
        defaultedBySM = true,
        dependency = BooleanKey.ApsUseDynamicSensitivity
    )
}
package app.aaps.core.keys

enum class UnitDoubleKey(
    override val key: String,
    override val defaultValue: Double,
    override val minMgdl: Int,
    override val maxMgdl: Int,
    override val defaultedBySM: Boolean = false,
    override val showInApsMode: Boolean = true,
    override val showInNsClientMode: Boolean = true,
    override val showInPumpControlMode: Boolean = true,
    override val dependency: BooleanPreferenceKey? = null,
    override val negativeDependency: BooleanPreferenceKey? = null,
    override val hideParentScreenIfHidden: Boolean = false
) : UnitDoublePreferenceKey {

    OverviewEatingSoonTarget("eatingsoon_target", 90.0, 72, 160, defaultedBySM = true),
    OverviewActivityTarget("activity_target", 140.0, 108, 180, defaultedBySM = true),
    OverviewHypoTarget("hypo_target", 160.0, 108, 180, defaultedBySM = true),
    OverviewLowMark("low_mark", 72.0, 25, 160, showInNsClientMode = false, hideParentScreenIfHidden = true),
    OverviewHighMark("high_mark", 180.0, 90, 250, showInNsClientMode = false),
    ApsLgsThreshold("lgsThreshold", 65.0, 60, 100, defaultedBySM = true, dependency = BooleanKey.ApsUseDynamicSensitivity)
}
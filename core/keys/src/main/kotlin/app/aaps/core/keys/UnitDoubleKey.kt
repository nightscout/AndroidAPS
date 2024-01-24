package app.aaps.core.keys

enum class UnitDoubleKey(
    override val key: Int,
    val defaultValue: Double,
    val minMgdl: Int,
    val maxMgdl: Int,
    override val defaultedBySM: Boolean = false,
    override val showInApsMode: Boolean = true,
    override val showInNsClientMode: Boolean = true,
    override val showInPumpControlMode: Boolean = true,
    override val dependency: Int = 0,
    override val negativeDependency: Int = 0,
    override val hideParentScreenIfHidden: Boolean = false
) : PreferenceKey {

    OverviewEatingSoonTarget(R.string.key_eating_soon_target, 90.0, 72, 160, defaultedBySM = true),
    OverviewActivityTarget(R.string.key_activity_target, 140.0, 108, 180, defaultedBySM = true),
    OverviewHypoTarget(R.string.key_hypo_target, 160.0, 108, 180, defaultedBySM = true),
    OverviewLowMark(R.string.key_low_mark, 72.0, 25, 160, showInNsClientMode = false, hideParentScreenIfHidden = true),
    OverviewHighMark(R.string.key_high_mark, 180.0, 90, 250, showInNsClientMode = false),
    ApsLgsThreshold(R.string.key_dynamic_isf_lgs_threshold, 65.0, 65, 120, defaultedBySM = true, dependency = R.string.key_use_dynamic_sensitivity),
}
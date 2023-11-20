package app.aaps.core.keys

enum class IntKey(
    override val key: Int,
    val defaultValue: Int,
    val min: Int,
    val max: Int,
    override val defaultedBySM: Boolean = false,
    val showInApsMode: Boolean = true,
    val showInNsClientMode: Boolean = true,
    val showInPumpControlMode: Boolean = true,
    val hideParentScreenIfHidden: Boolean = false // PreferenceScreen is final so we cannot extend and modify behavior
) : PreferenceKey {

    OverviewCarbsButtonIncrement1(R.string.key_carbs_button_increment_1, 5, -50, 50, defaultedBySM = true),
    OverviewCarbsButtonIncrement2(R.string.key_carbs_button_increment_2, 10, -50, 50, defaultedBySM = true),
    OverviewCarbsButtonIncrement3(R.string.key_carbs_button_increment_3, 20, -50, 50, defaultedBySM = true),
    OverviewEatingSoonDuration(R.string.key_eating_soon_duration, 45, 15, 120, defaultedBySM = true, hideParentScreenIfHidden = true),
    OverviewActivityDuration(R.string.key_activity_duration, 90, 15, 600, defaultedBySM = true),
    OverviewHypoDuration(R.string.key_hypo_duration, 60, 15, 180, defaultedBySM = true)
}
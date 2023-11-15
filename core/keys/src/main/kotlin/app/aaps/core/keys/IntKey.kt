package app.aaps.core.keys

enum class IntKey(override val key: Int, val defaultValue: Int, override val affectedBySM: Boolean) : PreferenceKey {
    OverviewCarbsButtonIncrement1(R.string.key_carbs_button_increment_1, 5, affectedBySM = true),
    OverviewCarbsButtonIncrement2(R.string.key_carbs_button_increment_1, 10, affectedBySM = true),
    OverviewCarbsButtonIncrement3(R.string.key_carbs_button_increment_1, 20, affectedBySM = true),
    OverviewEatingSoonDuration(R.string.key_eating_soon_duration, 45, affectedBySM = true),
    OverviewActivityDuration(R.string.key_activity_duration, 90, affectedBySM = true),
    OverviewHypoDuration(R.string.key_hypo_duration, 60, affectedBySM = true)
}
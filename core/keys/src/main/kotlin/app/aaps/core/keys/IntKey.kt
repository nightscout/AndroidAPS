package app.aaps.core.keys

enum class IntKey(override val key: Int, val defaultValue: Int, override val defaultedBySM: Boolean) : PreferenceKey {
    OverviewCarbsButtonIncrement1(R.string.key_carbs_button_increment_1, 5, defaultedBySM = true),
    OverviewCarbsButtonIncrement2(R.string.key_carbs_button_increment_1, 10, defaultedBySM = true),
    OverviewCarbsButtonIncrement3(R.string.key_carbs_button_increment_1, 20, defaultedBySM = true),
    OverviewEatingSoonDuration(R.string.key_eating_soon_duration, 45, defaultedBySM = true),
    OverviewActivityDuration(R.string.key_activity_duration, 90, defaultedBySM = true),
    OverviewHypoDuration(R.string.key_hypo_duration, 60, defaultedBySM = true)
}
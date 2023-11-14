package app.aaps.core.keys

enum class IntKeys(override val key: Int, val defaultValue: Int) : Keys {
    OverviewCarbsButtonIncrement1(R.string.key_carbs_button_increment_1, 5),
    OverviewCarbsButtonIncrement2(R.string.key_carbs_button_increment_1, 10),
    OverviewCarbsButtonIncrement3(R.string.key_carbs_button_increment_1, 20),
    OverviewEatingSoonDuration(R.string.key_eating_soon_duration, 45),
    OverviewActivityDuration(R.string.key_activity_duration, 90),
    OverviewHypoDuration(R.string.key_hypo_duration, 60)
}
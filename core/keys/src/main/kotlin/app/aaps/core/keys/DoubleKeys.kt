package app.aaps.core.keys

enum class DoubleKeys(override val key: Int, val defaultValue: Double) : Keys {
    OverviewInsulinButtonIncrement1(R.string.key_insulin_button_increment_1, 0.5),
    OverviewInsulinButtonIncrement2(R.string.key_insulin_button_increment_2, 1.0),
    OverviewInsulinButtonIncrement3(R.string.key_insulin_button_increment_3, 2.0),
    OverviewEatingSoonTarget(R.string.key_eating_soon_target, 90.0),
    OverviewActivityTarget(R.string.key_activity_target, 140.0),
    OverviewHypoTarget(R.string.key_hypo_target, 160.0),
    ActionsFillButton1(R.string.key_fill_button_1, 0.3),
    ActionsFillButton2(R.string.key_fill_button_2, 0.0),
    ActionsFillButton3(R.string.key_fill_button_3, 0.0)
}
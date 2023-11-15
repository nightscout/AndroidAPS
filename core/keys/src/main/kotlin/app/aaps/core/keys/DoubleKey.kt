package app.aaps.core.keys

enum class DoubleKey(override val key: Int, val defaultValue: Double, override val affectedBySM: Boolean, val unitDependent: Boolean = false) : PreferenceKey {
    OverviewInsulinButtonIncrement1(R.string.key_insulin_button_increment_1, 0.5, affectedBySM = true),
    OverviewInsulinButtonIncrement2(R.string.key_insulin_button_increment_2, 1.0, affectedBySM = true),
    OverviewInsulinButtonIncrement3(R.string.key_insulin_button_increment_3, 2.0, affectedBySM = true),
    OverviewEatingSoonTarget(R.string.key_eating_soon_target, 90.0, affectedBySM = true, unitDependent = true),
    OverviewActivityTarget(R.string.key_activity_target, 140.0, affectedBySM = true, unitDependent = true),
    OverviewHypoTarget(R.string.key_hypo_target, 160.0, affectedBySM = true, unitDependent = true),
    OverviewLowMark(R.string.key_low_mark, 72.0, affectedBySM = false, unitDependent = true),
    OverviewHighMark(R.string.key_high_mark, 180.0, affectedBySM = false, unitDependent = true),
    ActionsFillButton1(R.string.key_fill_button_1, 0.3, affectedBySM = true),
    ActionsFillButton2(R.string.key_fill_button_2, 0.0, affectedBySM = true),
    ActionsFillButton3(R.string.key_fill_button_3, 0.0, affectedBySM = true)
}
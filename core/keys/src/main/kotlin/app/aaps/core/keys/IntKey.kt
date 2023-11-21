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
    val hideParentScreenIfHidden: Boolean = false, // PreferenceScreen is final so we cannot extend and modify behavior
    val engineeringModeOnly: Boolean = false
) : PreferenceKey {

    OverviewCarbsButtonIncrement1(R.string.key_carbs_button_increment_1, 5, -50, 50, defaultedBySM = true),
    OverviewCarbsButtonIncrement2(R.string.key_carbs_button_increment_2, 10, -50, 50, defaultedBySM = true),
    OverviewCarbsButtonIncrement3(R.string.key_carbs_button_increment_3, 20, -50, 50, defaultedBySM = true),
    OverviewEatingSoonDuration(R.string.key_eating_soon_duration, 45, 15, 120, defaultedBySM = true, hideParentScreenIfHidden = true),
    OverviewActivityDuration(R.string.key_activity_duration, 90, 15, 600, defaultedBySM = true),
    OverviewHypoDuration(R.string.key_hypo_duration, 60, 15, 180, defaultedBySM = true),
    OverviewCageWarning(R.string.key_statuslights_cage_warning, 48, 24, 240, defaultedBySM = true),
    OverviewCageCritical(R.string.key_statuslights_cage_critical, 72, 24, 240, defaultedBySM = true),
    OverviewIageWarning(R.string.key_statuslights_iage_warning, 72, 24, 240, defaultedBySM = true),
    OverviewIageCritical(R.string.key_statuslights_iage_critical, 144, 24, 240, defaultedBySM = true),
    OverviewSageWarning(R.string.key_statuslights_sage_warning, 216, 24, 720, defaultedBySM = true),
    OverviewSageCritical(R.string.key_statuslights_sage_critical, 240, 24, 720, defaultedBySM = true),
    OverviewSbatWarning(R.string.key_statuslights_sbat_warning, 25, 0, 100, defaultedBySM = true),
    OverviewSbatCritical(R.string.key_statuslights_sbat_critical, 5, 0, 100, defaultedBySM = true),
    OverviewBageWarning(R.string.key_statuslights_bage_warning, 216, 24, 1000, defaultedBySM = true),
    OverviewBageCritical(R.string.key_statuslights_bage_critical, 240, 24, 1000, defaultedBySM = true),
    OverviewResWarning(R.string.key_statuslights_res_warning, 80, 0, 300, defaultedBySM = true),
    OverviewResCritical(R.string.key_statuslights_res_critical, 10, 0, 300, defaultedBySM = true),
    OverviewBattWarning(R.string.key_statuslights_bat_warning, 51, 0, 100, defaultedBySM = true),
    OverviewBattCritical(R.string.key_statuslights_bat_critical, 26, 0, 100, defaultedBySM = true),
    OverviewBolusPercentage(R.string.key_boluswizard_percentage, 100, 10, 100),
    OverviewResetBolusPercentageTime(R.string.key_reset_boluswizard_percentage_time, 16, 6, 120, engineeringModeOnly = true),
}
package app.aaps.core.keys

enum class DoubleKey(
    override val key: Int,
    val defaultValue: Double,
    val min: Double,
    val max: Double,
    override val defaultedBySM: Boolean = false,
    val showInApsMode: Boolean = true,
    val showInNsClientMode: Boolean = true,
    val showInPumpControlMode: Boolean = true,
    val hideParentScreenIfHidden: Boolean = false // PreferenceScreen is final so we cannot extend and modify behavior
) : PreferenceKey {

    OverviewInsulinButtonIncrement1(R.string.key_insulin_button_increment_1, 0.5, -5.0, 5.0, defaultedBySM = true),
    OverviewInsulinButtonIncrement2(R.string.key_insulin_button_increment_2, 1.0, -5.0, 5.0, defaultedBySM = true),
    OverviewInsulinButtonIncrement3(R.string.key_insulin_button_increment_3, 2.0, -5.0, 5.0, defaultedBySM = true),
    ActionsFillButton1(R.string.key_fill_button_1, 0.3, 0.05, 20.0, defaultedBySM = true),
    ActionsFillButton2(R.string.key_fill_button_2, 0.0, 0.05, 20.0, defaultedBySM = true),
    ActionsFillButton3(R.string.key_fill_button_3, 0.0, 0.05, 20.0, defaultedBySM = true)
}
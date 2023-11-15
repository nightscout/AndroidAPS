package app.aaps.core.keys

enum class BooleanKey(
    override val key: Int,
    val defaultValue: Boolean,
    override val defaultedBySM: Boolean = false,
    val showInApsMode: Boolean = true,
    val showInNsClientMode: Boolean = true,
    val showInPumpControlMode: Boolean = true,
    val hideParentScreenIfHidden: Boolean = false     // PreferenceScreen is final so we cannot extend and modify behavior
) : PreferenceKey {

    GeneralSimpleMode(R.string.key_simple_mode, true),
    GeneralSetupWizardProcessed(R.string.key_setupwizard_processed, false),
    OverviewKeepScreenOn(R.string.key_keep_screen_on, false, defaultedBySM = true),
    OverviewShowTreatmentButton(R.string.key_show_treatment_button, false, defaultedBySM = true, hideParentScreenIfHidden = true),
    OverviewShowWizardButton(R.string.key_show_wizard_button, true, defaultedBySM = true),
    OverviewShowInsulinButton(R.string.key_show_insulin_button, true, defaultedBySM = true),
    OverviewShowCarbsButton(R.string.key_show_carbs_button, true, defaultedBySM = true),
    OverviewShowCgmButton(R.string.key_show_cgm_button, false, defaultedBySM = true, showInNsClientMode = false),
    OverviewShowCalibrationButton(R.string.key_show_calibration_button, false, defaultedBySM = true, showInNsClientMode = false),
}
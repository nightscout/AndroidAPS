package app.aaps.core.keys

enum class BooleanKey(override val key: Int, val defaultValue: Boolean, override val affectedBySM: Boolean) : PreferenceKey {
    GeneralSimpleMode(R.string.key_simple_mode, true, affectedBySM = false),
    GeneralSetupWizardProcessed(R.string.key_setupwizard_processed, false, affectedBySM = false),
    OverviewKeepScreenOn(R.string.key_keep_screen_on, false, affectedBySM = true),
    OverviewShowTreatmentButton(R.string.key_show_treatment_button, false, affectedBySM = true),
    OverviewShowWizardButton(R.string.key_show_wizard_button, true, affectedBySM = true),
    OverviewShowInsulinButton(R.string.key_show_insulin_button, true, affectedBySM = true),
}
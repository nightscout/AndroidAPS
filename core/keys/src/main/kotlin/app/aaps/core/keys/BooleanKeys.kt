package app.aaps.core.keys

enum class BooleanKeys(override val key: Int, val defaultValue: Boolean) : Keys {
    GeneralSimpleMode(R.string.key_simple_mode, true),
    GeneralSetupWizardProcessed(R.string.key_setupwizard_processed, false),
    OverviewKeepScreenOn(R.string.key_keep_screen_on, false),
    OverviewShowTreatmentButton(R.string.key_show_treatment_button, false),
    OverviewShowWizardButton(R.string.key_show_wizard_button, true),
    OverviewShowInsulinButton(R.string.key_show_insulin_button, true),
}
package app.aaps.pump.omnipod.common.ui.wizard.compose

/**
 * Wizard steps for Omnipod pod activation and deactivation.
 * Used by both Eros and Dash.
 */
enum class OmnipodWizardStep {

    // Activation steps
    START_POD_ACTIVATION,
    SELECT_INSULIN,
    INITIALIZE_POD,
    ATTACH_POD,
    SITE_LOCATION,
    INSERT_CANNULA,
    POD_ACTIVATED,

    // Deactivation steps
    START_POD_DEACTIVATION,
    DEACTIVATE_POD,
    POD_DEACTIVATED,
    POD_DISCARDED
}

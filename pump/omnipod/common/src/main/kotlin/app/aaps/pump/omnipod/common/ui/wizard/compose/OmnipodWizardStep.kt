package app.aaps.pump.omnipod.common.ui.wizard.compose

/**
 * Wizard steps for Omnipod pod activation and deactivation.
 * Used by both Eros and Dash.
 */
enum class OmnipodWizardStep {

    // Pre-activation gate (shown only when no profile switch exists yet)
    PROFILE_GATE,

    // Activation steps
    START_POD_ACTIVATION,
    SELECT_INSULIN,
    INITIALIZE_POD,
    SITE_LOCATION,
    ATTACH_POD,
    INSERT_CANNULA,
    POD_ACTIVATED,

    // Deactivation steps
    START_POD_DEACTIVATION,
    DEACTIVATE_POD,
    POD_DEACTIVATED,
    POD_DISCARDED
}

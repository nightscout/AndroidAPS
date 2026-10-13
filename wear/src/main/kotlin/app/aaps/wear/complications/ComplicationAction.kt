package app.aaps.wear.complications

/**
 * Defines actions that can be triggered when a user taps a complication.
 *
 * Complications display diabetes-related data on watchfaces. When tapped, they
 * can trigger various actions handled by [ComplicationTapActivity]. Each action
 * opens a different screen or dialog to allow quick access to common functions.
 *
 * Complication providers specify their tap action via [ModernBaseComplicationProviderService.getComplicationAction].
 */
enum class ComplicationAction {
    /**
     * No action - complication does not respond to taps.
     * Used for display-only complications like wallpapers.
     */
    NONE,

    /**
     * Opens the main AAPS menu.
     * Provides access to all wear app functions.
     */
    MENU,

    /**
     * Opens the bolus wizard calculator.
     * Allows quick insulin dose calculation and delivery.
     */
    WIZARD,

    /**
     * Opens the quick bolus entry dialog.
     * Faster than wizard for simple corrections.
     */
    BOLUS,

    /**
     * Opens the eCarbs (extended carbs) entry dialog.
     * Used for slow-absorbing carbs or meal prebolusing.
     */
    E_CARB,

    /**
     * Opens the detailed status screen.
     * Shows comprehensive diabetes metrics and loop status.
     */
    STATUS,

    /**
     * Opens warning dialog about watch-phone sync issues.
     * Displayed when data hasn't updated from phone recently.
     */
    WARNING_SYNC,

    /**
     * Opens warning dialog about outdated sensor data.
     * Displayed when glucose readings are stale from sensor/uploader.
     */
    WARNING_OLD
}
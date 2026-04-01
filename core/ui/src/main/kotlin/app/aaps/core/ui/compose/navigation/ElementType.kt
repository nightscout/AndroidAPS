package app.aaps.core.ui.compose.navigation

import app.aaps.core.interfaces.protection.ProtectionCheck

/**
 * Unified visual identity for UI elements.
 * Each value carries icon, label, description, and color — accessed via extension functions
 * in ElementTypeStyle.kt.
 *
 * @param category Logical grouping for configuration screens and bottom sheets
 * @param searchable Whether this element appears in global search results
 * @param protection Protection level required for navigation
 */
enum class ElementType(
    val category: ElementCategory = ElementCategory.INTERNAL,
    val searchable: Boolean = false,
    val protection: ProtectionCheck.Protection = ProtectionCheck.Protection.NONE
) {

    // Treatment dialogs
    INSULIN(category = ElementCategory.TREATMENT, searchable = true, protection = ProtectionCheck.Protection.BOLUS),
    CARBS(category = ElementCategory.TREATMENT, searchable = true, protection = ProtectionCheck.Protection.BOLUS),
    BOLUS_WIZARD(category = ElementCategory.TREATMENT, searchable = true, protection = ProtectionCheck.Protection.BOLUS),
    QUICK_WIZARD(protection = ProtectionCheck.Protection.BOLUS),
    TREATMENT(category = ElementCategory.TREATMENT, searchable = true, protection = ProtectionCheck.Protection.BOLUS),

    // CGM
    CGM_XDRIP(category = ElementCategory.CGM, searchable = true),
    CGM_DEX(category = ElementCategory.CGM),
    CALIBRATION(category = ElementCategory.CGM, searchable = true),

    // Profile & Targets — minimum level is BOLUS; screen mode (PLAY/EDIT) determined by granted auth level
    PROFILE_MANAGEMENT(category = ElementCategory.MANAGEMENT, searchable = true, protection = ProtectionCheck.Protection.BOLUS),
    TEMP_TARGET_MANAGEMENT(category = ElementCategory.MANAGEMENT, searchable = true, protection = ProtectionCheck.Protection.BOLUS),
    INSULIN_MANAGEMENT(category = ElementCategory.MANAGEMENT, searchable = true, protection = ProtectionCheck.Protection.BOLUS),
    QUICK_WIZARD_MANAGEMENT(category = ElementCategory.MANAGEMENT, searchable = true, protection = ProtectionCheck.Protection.BOLUS),

    // Careportal
    BG_CHECK(category = ElementCategory.CAREPORTAL, searchable = true),
    NOTE(category = ElementCategory.CAREPORTAL, searchable = true),
    EXERCISE(category = ElementCategory.CAREPORTAL, searchable = true),
    QUESTION(category = ElementCategory.CAREPORTAL, searchable = true),
    ANNOUNCEMENT(category = ElementCategory.CAREPORTAL, searchable = true),

    // Device maintenance
    SENSOR_INSERT(category = ElementCategory.DEVICE, searchable = true),
    BATTERY_CHANGE(category = ElementCategory.DEVICE, searchable = true),
    CANNULA_CHANGE(category = ElementCategory.DEVICE, protection = ProtectionCheck.Protection.BOLUS),
    FILL(category = ElementCategory.DEVICE, searchable = true, protection = ProtectionCheck.Protection.BOLUS),
    SITE_ROTATION(category = ElementCategory.DEVICE, searchable = true),

    // Basal
    TEMP_BASAL(category = ElementCategory.BASAL, searchable = true, protection = ProtectionCheck.Protection.BOLUS),
    EXTENDED_BOLUS(category = ElementCategory.BASAL, searchable = true, protection = ProtectionCheck.Protection.BOLUS),

    // System
    AUTOMATION(category = ElementCategory.SYSTEM),
    PUMP(category = ElementCategory.SYSTEM),
    SETTINGS(category = ElementCategory.SYSTEM, protection = ProtectionCheck.Protection.PREFERENCES),
    QUICK_LAUNCH_CONFIG(category = ElementCategory.SYSTEM, searchable = true),

    // Navigation screens
    TREATMENTS(category = ElementCategory.NAVIGATION, searchable = true),
    STATISTICS(category = ElementCategory.NAVIGATION, searchable = true),
    TDD_CYCLE_PATTERN(category = ElementCategory.NAVIGATION, searchable = true),
    PROFILE_HELPER(category = ElementCategory.NAVIGATION, searchable = true),
    HISTORY_BROWSER(category = ElementCategory.NAVIGATION, searchable = true),
    SETUP_WIZARD(category = ElementCategory.NAVIGATION, searchable = true, protection = ProtectionCheck.Protection.PREFERENCES),
    MAINTENANCE(category = ElementCategory.NAVIGATION, searchable = true, protection = ProtectionCheck.Protection.PREFERENCES),
    CONFIGURATION(category = ElementCategory.NAVIGATION, searchable = true, protection = ProtectionCheck.Protection.PREFERENCES),
    ABOUT(category = ElementCategory.NAVIGATION, searchable = true),

    // Display indicators
    COB,
    SENSITIVITY,

    // Running mode / loop (used by UserEntry)
    RUNNING_MODE(category = ElementCategory.MANAGEMENT, searchable = true, protection = ProtectionCheck.Protection.BOLUS),
    USER_ENTRY,
    LOOP,
    AAPS,

    // App lifecycle
    EXIT;

    companion object {

        /** All searchable elements — use this to auto-populate search results. */
        val searchableEntries: List<ElementType> by lazy {
            entries.filter { it.searchable }
        }
    }
}

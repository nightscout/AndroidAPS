package app.aaps.core.interfaces.navigation

import app.aaps.core.interfaces.protection.ProtectionCheck
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.interfaces.ElementVisibility

/**
 * Unified visual identity for UI elements.
 * Each value carries icon, label, description, and color — accessed via extension functions
 * in ElementTypeStyle.kt.
 *
 * @param category Logical grouping for configuration screens and bottom sheets
 * @param searchable Whether this element appears in global search results
 * @param protection Protection level required for navigation
 * @param visibility Runtime condition gating where this element may surface (e.g. in search)
 */
enum class ElementType(
    val category: ElementCategory = ElementCategory.INTERNAL,
    val searchable: Boolean = false,
    val protection: ProtectionCheck.Protection = ProtectionCheck.Protection.NONE,
    val visibility: ElementVisibility = ElementVisibility.ALWAYS
) {

    // Treatment dialogs — all ride the signed Client-Control channel (RoleBranch/BatchExecutor), so an
    // unpaired client must not surface them: CLIENT_PAIRED hides them until paired (always shown on master).
    INSULIN(category = ElementCategory.TREATMENT, searchable = true, protection = ProtectionCheck.Protection.BOLUS, visibility = ElementVisibility.MASTER_OR_PAIRED_CLIENT),
    CARBS(category = ElementCategory.TREATMENT, searchable = true, protection = ProtectionCheck.Protection.BOLUS, visibility = ElementVisibility.MASTER_OR_PAIRED_CLIENT),
    BOLUS_WIZARD(category = ElementCategory.TREATMENT, searchable = true, protection = ProtectionCheck.Protection.BOLUS, visibility = ElementVisibility.MASTER_OR_PAIRED_CLIENT),
    QUICK_WIZARD(protection = ProtectionCheck.Protection.BOLUS, visibility = ElementVisibility.MASTER_OR_PAIRED_CLIENT),
    TREATMENT(category = ElementCategory.TREATMENT, searchable = true, protection = ProtectionCheck.Protection.BOLUS, visibility = ElementVisibility.MASTER_OR_PAIRED_CLIENT),

    // CGM
    CGM_XDRIP(category = ElementCategory.CGM, searchable = true),
    CGM_DEX(category = ElementCategory.CGM),

    // CALIBRATION is intentionally NOT migrated/gated: it's a CGM-plugin command (activeCalibration.addEntry), not a
    // DB write, and a client has no CGM source to calibrate — so it stays local/master-only (see Track B calibration notes).
    CALIBRATION(category = ElementCategory.CGM, searchable = true),

    // Profile & Targets — minimum level is BOLUS; screen mode (PLAY/EDIT) determined by granted auth level.
    // Mutating editors whose writes ride Client-Control → CLIENT_PAIRED (hidden on an unpaired client).
    PROFILE_MANAGEMENT(category = ElementCategory.MANAGEMENT, searchable = true, protection = ProtectionCheck.Protection.BOLUS, visibility = ElementVisibility.MASTER_OR_PAIRED_CLIENT),
    TEMP_TARGET_MANAGEMENT(category = ElementCategory.MANAGEMENT, searchable = true, protection = ProtectionCheck.Protection.BOLUS, visibility = ElementVisibility.MASTER_OR_PAIRED_CLIENT),
    INSULIN_MANAGEMENT(category = ElementCategory.MANAGEMENT, searchable = true, protection = ProtectionCheck.Protection.BOLUS, visibility = ElementVisibility.MASTER_OR_PAIRED_CLIENT),
    QUICK_WIZARD_MANAGEMENT(category = ElementCategory.MANAGEMENT, searchable = true, protection = ProtectionCheck.Protection.BOLUS, visibility = ElementVisibility.MASTER_OR_PAIRED_CLIENT),
    FOOD_MANAGEMENT(category = ElementCategory.MANAGEMENT, searchable = true, visibility = ElementVisibility.MASTER_OR_PAIRED_CLIENT),

    // Careportal — migrated to the Client-Control batch channel (BatchAction.TherapyEvent via CareDialogViewModel):
    // the master is the sole writer and the event syncs back. Gated MASTER_OR_PAIRED_CLIENT (hidden on an unpaired client).
    BG_CHECK(category = ElementCategory.CAREPORTAL, searchable = true, visibility = ElementVisibility.MASTER_OR_PAIRED_CLIENT),
    NOTE(category = ElementCategory.CAREPORTAL, searchable = true, visibility = ElementVisibility.MASTER_OR_PAIRED_CLIENT),
    EXERCISE(category = ElementCategory.CAREPORTAL, searchable = true, visibility = ElementVisibility.MASTER_OR_PAIRED_CLIENT),
    QUESTION(category = ElementCategory.CAREPORTAL, searchable = true, visibility = ElementVisibility.MASTER_OR_PAIRED_CLIENT),
    ANNOUNCEMENT(category = ElementCategory.CAREPORTAL, searchable = true, visibility = ElementVisibility.MASTER_OR_PAIRED_CLIENT),

    // Device maintenance. SENSOR_INSERT + BATTERY_CHANGE route through CareDialog; CANNULA_CHANGE + FILL both open the
    // FillDialog (site / cartridge); SITE_ROTATION records + edits via the batch channel — all Client-Control-migrated → gated.
    SENSOR_INSERT(category = ElementCategory.DEVICE, searchable = true, visibility = ElementVisibility.MASTER_OR_PAIRED_CLIENT),
    BATTERY_CHANGE(category = ElementCategory.DEVICE, searchable = true, visibility = ElementVisibility.MASTER_OR_PAIRED_CLIENT),
    CANNULA_CHANGE(category = ElementCategory.DEVICE, protection = ProtectionCheck.Protection.BOLUS, visibility = ElementVisibility.MASTER_OR_PAIRED_CLIENT),
    FILL(category = ElementCategory.DEVICE, searchable = true, protection = ProtectionCheck.Protection.BOLUS, visibility = ElementVisibility.MASTER_OR_PAIRED_CLIENT),
    SITE_ROTATION(category = ElementCategory.DEVICE, searchable = true, visibility = ElementVisibility.MASTER_OR_PAIRED_CLIENT),

    // Basal — both ride Client-Control → CLIENT_PAIRED.
    TEMP_BASAL(category = ElementCategory.BASAL, searchable = true, protection = ProtectionCheck.Protection.BOLUS, visibility = ElementVisibility.MASTER_OR_PAIRED_CLIENT),
    EXTENDED_BOLUS(category = ElementCategory.BASAL, searchable = true, protection = ProtectionCheck.Protection.BOLUS, visibility = ElementVisibility.MASTER_OR_PAIRED_CLIENT),

    // System
    AUTOMATION(category = ElementCategory.SYSTEM),
    AUTOMATION_MANAGEMENT(category = ElementCategory.MANAGEMENT, searchable = true, protection = ProtectionCheck.Protection.PREFERENCES, visibility = ElementVisibility.MASTER_OR_PAIRED_CLIENT),

    // Pump management — only where the build drives a real pump: full + pumpcontrol (i.e. !isClient).
    // An aapsclient has no real pump (VirtualPump is hidden). NOT gated on pairing (a paired client still
    // has no pump), so this is plain !isClient, distinct from MASTER_OR_PAIRED_CLIENT.
    PUMP(category = ElementCategory.SYSTEM, visibility = ElementVisibility { !it.isClient }),
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

    // Scenes (situation presets) — activation + management ride Client-Control → CLIENT_PAIRED.
    SCENE(category = ElementCategory.MANAGEMENT, protection = ProtectionCheck.Protection.BOLUS, visibility = ElementVisibility.MASTER_OR_PAIRED_CLIENT),
    SCENE_MANAGEMENT(category = ElementCategory.MANAGEMENT, searchable = true, protection = ProtectionCheck.Protection.PREFERENCES, visibility = ElementVisibility.MASTER_OR_PAIRED_CLIENT),

    // NSCv3 client control — paired devices that can issue signed commands
    // Master only AND NSCv3-WS enabled — keeps search in lock-step with the Manage-sheet button (ManageViewModel).
    AUTHORIZED_CLIENTS(category = ElementCategory.MANAGEMENT, searchable = true, protection = ProtectionCheck.Protection.PREFERENCES, visibility = ElementVisibility { !it.isClient && it.preferences.get(BooleanKey.NsClient3UseWs) }),

    // Client only AND NSCv3-WS enabled (the transport client control rides) — in lock-step with its Manage-sheet button.
    PAIR_WITH_MASTER(category = ElementCategory.MANAGEMENT, searchable = true, protection = ProtectionCheck.Protection.PREFERENCES, visibility = ElementVisibility { it.isClient && it.preferences.get(BooleanKey.NsClient3UseWs) }),

    // Running mode / loop (used by UserEntry) — mode changes ride Client-Control → CLIENT_PAIRED.
    RUNNING_MODE(category = ElementCategory.MANAGEMENT, searchable = true, protection = ProtectionCheck.Protection.BOLUS, visibility = ElementVisibility.MASTER_OR_PAIRED_CLIENT),
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

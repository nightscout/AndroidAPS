package app.aaps.core.interfaces.navigation

import app.aaps.core.interfaces.protection.ProtectionCheck
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.interfaces.ClientRole
import app.aaps.core.keys.interfaces.ElementVisibility
import app.aaps.core.keys.interfaces.VisibilityContext

/**
 * Unified visual identity for UI elements.
 * Each value carries icon, label, description, and color — accessed via extension functions
 * in ElementTypeStyle.kt.
 *
 * @param category Logical grouping for configuration screens and bottom sheets
 * @param searchable Whether this element appears in global search results
 * @param protection Protection level required for navigation
 * @param visibility Runtime condition gating where this element may surface (e.g. in search)
 * @param role The lowest client role that may use this element (see [ClientRole]). On a client whose role is lower it
 *   is not shown, because the master would refuse it. A master is always Admin. Check both with [isAvailable].
 */
enum class ElementType(
    val category: ElementCategory = ElementCategory.INTERNAL,
    val searchable: Boolean = false,
    val protection: ProtectionCheck.Protection = ProtectionCheck.Protection.NONE,
    val visibility: ElementVisibility = ElementVisibility.ALWAYS,
    val role: ClientRole = ClientRole.ReadOnly
) {

    // Treatment dialogs — all ride the signed Client-Control channel (RoleBranch/BatchExecutor), so an
    // unpaired client must not surface them: CLIENT_PAIRED hides them until paired (always shown on master).
    // A meal assistant may give insulin and enter carbs: these five.
    INSULIN(category = ElementCategory.TREATMENT, searchable = true, protection = ProtectionCheck.Protection.BOLUS, visibility = ElementVisibility.MASTER_OR_PAIRED_CLIENT, role = ClientRole.MealAssistant),
    CARBS(category = ElementCategory.TREATMENT, searchable = true, protection = ProtectionCheck.Protection.BOLUS, visibility = ElementVisibility.MASTER_OR_PAIRED_CLIENT, role = ClientRole.MealAssistant),
    BOLUS_WIZARD(category = ElementCategory.TREATMENT, searchable = true, protection = ProtectionCheck.Protection.BOLUS, visibility = ElementVisibility.MASTER_OR_PAIRED_CLIENT, role = ClientRole.MealAssistant),
    QUICK_WIZARD(protection = ProtectionCheck.Protection.BOLUS, visibility = ElementVisibility.MASTER_OR_PAIRED_CLIENT, role = ClientRole.MealAssistant),
    TREATMENT(category = ElementCategory.TREATMENT, searchable = true, protection = ProtectionCheck.Protection.BOLUS, visibility = ElementVisibility.MASTER_OR_PAIRED_CLIENT, role = ClientRole.MealAssistant),

    // CGM
    CGM_XDRIP(category = ElementCategory.CGM, searchable = true),
    CGM_DEX(category = ElementCategory.CGM),

    // On a client a calibration goes to the master as BatchAction.Calibration, which hands it to its CGM plugin.
    CALIBRATION(category = ElementCategory.CGM, searchable = true, role = ClientRole.Caregiver),

    // Profile & Targets — minimum level is BOLUS; screen mode (PLAY/EDIT) determined by granted auth level.
    // Mutating editors whose writes ride Client-Control → CLIENT_PAIRED (hidden on an unpaired client).
    // PROFILE_MANAGEMENT is the exception and stays visible: profiles are state an unpaired client still
    // needs to see and calculate with (it receives them from Nightscout), so the screen opens READ-ONLY
    // there instead of disappearing — same rule as the overview chips and status lights.
    PROFILE_MANAGEMENT(category = ElementCategory.MANAGEMENT, searchable = true, protection = ProtectionCheck.Protection.BOLUS),
    TEMP_TARGET_MANAGEMENT(category = ElementCategory.MANAGEMENT, searchable = true, protection = ProtectionCheck.Protection.BOLUS, visibility = ElementVisibility.MASTER_OR_PAIRED_CLIENT, role = ClientRole.Caregiver),
    INSULIN_MANAGEMENT(category = ElementCategory.MANAGEMENT, searchable = true, protection = ProtectionCheck.Protection.BOLUS, visibility = ElementVisibility.MASTER_OR_PAIRED_CLIENT, role = ClientRole.Caregiver),
    QUICK_WIZARD_MANAGEMENT(category = ElementCategory.MANAGEMENT, searchable = true, protection = ProtectionCheck.Protection.BOLUS, visibility = ElementVisibility.MASTER_OR_PAIRED_CLIENT, role = ClientRole.Caregiver),
    FOOD_MANAGEMENT(category = ElementCategory.MANAGEMENT, searchable = true, visibility = ElementVisibility.MASTER_OR_PAIRED_CLIENT, role = ClientRole.Caregiver),

    // Careportal — migrated to the Client-Control batch channel (BatchAction.TherapyEvent via CareDialogViewModel):
    // the master is the sole writer and the event syncs back. Gated MASTER_OR_PAIRED_CLIENT (hidden on an unpaired client).
    BG_CHECK(category = ElementCategory.CAREPORTAL, searchable = true, visibility = ElementVisibility.MASTER_OR_PAIRED_CLIENT, role = ClientRole.Caregiver),
    NOTE(category = ElementCategory.CAREPORTAL, searchable = true, visibility = ElementVisibility.MASTER_OR_PAIRED_CLIENT, role = ClientRole.Caregiver),
    EXERCISE(category = ElementCategory.CAREPORTAL, searchable = true, visibility = ElementVisibility.MASTER_OR_PAIRED_CLIENT, role = ClientRole.Caregiver),
    QUESTION(category = ElementCategory.CAREPORTAL, searchable = true, visibility = ElementVisibility.MASTER_OR_PAIRED_CLIENT, role = ClientRole.Caregiver),
    ANNOUNCEMENT(category = ElementCategory.CAREPORTAL, searchable = true, visibility = ElementVisibility.MASTER_OR_PAIRED_CLIENT, role = ClientRole.Caregiver),

    // Device maintenance. SENSOR_INSERT + BATTERY_CHANGE route through CareDialog; CANNULA_CHANGE + FILL both open the
    // FillDialog (site / cartridge); SITE_ROTATION records + edits via the batch channel — all Client-Control-migrated → gated.
    SENSOR_INSERT(category = ElementCategory.DEVICE, searchable = true, visibility = ElementVisibility.MASTER_OR_PAIRED_CLIENT, role = ClientRole.Caregiver),
    BATTERY_CHANGE(category = ElementCategory.DEVICE, searchable = true, visibility = ElementVisibility.MASTER_OR_PAIRED_CLIENT, role = ClientRole.Caregiver),
    CANNULA_CHANGE(category = ElementCategory.DEVICE, protection = ProtectionCheck.Protection.BOLUS, visibility = ElementVisibility.MASTER_OR_PAIRED_CLIENT, role = ClientRole.Caregiver),
    FILL(category = ElementCategory.DEVICE, searchable = true, protection = ProtectionCheck.Protection.BOLUS, visibility = ElementVisibility.MASTER_OR_PAIRED_CLIENT, role = ClientRole.Caregiver),
    SITE_ROTATION(category = ElementCategory.DEVICE, searchable = true, visibility = ElementVisibility.MASTER_OR_PAIRED_CLIENT, role = ClientRole.Caregiver),

    // Basal — both ride Client-Control → CLIENT_PAIRED.
    TEMP_BASAL(category = ElementCategory.BASAL, searchable = true, protection = ProtectionCheck.Protection.BOLUS, visibility = ElementVisibility.MASTER_OR_PAIRED_CLIENT, role = ClientRole.Caregiver),
    EXTENDED_BOLUS(category = ElementCategory.BASAL, searchable = true, protection = ProtectionCheck.Protection.BOLUS, visibility = ElementVisibility.MASTER_OR_PAIRED_CLIENT, role = ClientRole.Caregiver),

    // System
    AUTOMATION(category = ElementCategory.SYSTEM),
    AUTOMATION_MANAGEMENT(category = ElementCategory.MANAGEMENT, searchable = true, protection = ProtectionCheck.Protection.PREFERENCES, visibility = ElementVisibility.MASTER_OR_PAIRED_CLIENT, role = ClientRole.Caregiver),

    // Pump management — only where the build drives a real pump: full + pumpcontrol (i.e. !isClient).
    // An aapsclient has no real pump (VirtualPump is hidden). NOT gated on pairing (a paired client still
    // has no pump), so this is plain !isClient, distinct from MASTER_OR_PAIRED_CLIENT.
    PUMP(category = ElementCategory.SYSTEM, visibility = ElementVisibility { !it.isClient }),
    SETTINGS(category = ElementCategory.SYSTEM, protection = ProtectionCheck.Protection.PREFERENCES),
    QUICK_LAUNCH_CONFIG(category = ElementCategory.SYSTEM, searchable = true),
    NFC(category = ElementCategory.SYSTEM),

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
    SCENE(category = ElementCategory.MANAGEMENT, protection = ProtectionCheck.Protection.BOLUS, visibility = ElementVisibility.MASTER_OR_PAIRED_CLIENT, role = ClientRole.Caregiver),
    SCENE_MANAGEMENT(category = ElementCategory.MANAGEMENT, searchable = true, protection = ProtectionCheck.Protection.PREFERENCES, visibility = ElementVisibility.MASTER_OR_PAIRED_CLIENT, role = ClientRole.Caregiver),

    // NSCv3 client control — paired devices that can issue signed commands
    // Master only AND NSCv3-WS enabled — keeps search in lock-step with the Manage-sheet button (ManageViewModel).
    AUTHORIZED_CLIENTS(category = ElementCategory.MANAGEMENT, searchable = true, protection = ProtectionCheck.Protection.PREFERENCES, visibility = ElementVisibility { !it.isClient && it.preferences.get(BooleanKey.NsClient3UseWs) }),

    // Client only AND NSCv3-WS enabled (the transport client control rides) — in lock-step with its Manage-sheet button.
    PAIR_WITH_MASTER(category = ElementCategory.MANAGEMENT, searchable = true, protection = ProtectionCheck.Protection.PREFERENCES, visibility = ElementVisibility { it.isClient && it.preferences.get(BooleanKey.NsClient3UseWs) }),

    // Running mode / loop (used by UserEntry) — mode changes ride Client-Control → CLIENT_PAIRED.
    RUNNING_MODE(category = ElementCategory.MANAGEMENT, searchable = true, protection = ProtectionCheck.Protection.BOLUS, visibility = ElementVisibility.MASTER_OR_PAIRED_CLIENT, role = ClientRole.Caregiver),
    USER_ENTRY,
    LOOP,
    AAPS,

    // App lifecycle
    EXIT;

    /**
     * Whether this element may be shown in [ctx]: its [visibility] rule holds, and this phone's role allows its [role].
     * Use this rather than `visibility.isVisible(ctx)`, so the role can never be left out.
     */
    fun isAvailable(ctx: VisibilityContext): Boolean = visibility.isVisible(ctx) && ctx.clientRole.allows(role)

    companion object {

        /** All searchable elements — use this to auto-populate search results. */
        val searchableEntries: List<ElementType> by lazy {
            entries.filter { it.searchable }
        }
    }
}

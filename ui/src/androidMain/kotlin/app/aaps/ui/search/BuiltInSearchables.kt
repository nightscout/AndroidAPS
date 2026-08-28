package app.aaps.ui.search

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TipsAndUpdates
import androidx.compose.material.icons.outlined.Palette
import app.aaps.ui.UiStrings
import app.aaps.core.interfaces.InterfacesStrings
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.insulin.InsulinManager
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.DoubleKey
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.UnitDoubleKey
import app.aaps.core.keys.interfaces.withChangeGuard
import app.aaps.core.ui.compose.icons.IcBolus
import app.aaps.core.ui.compose.icons.IcCalculator
import app.aaps.core.ui.compose.icons.IcCannulaChange
import app.aaps.core.ui.compose.icons.IcCarbs
import app.aaps.core.ui.compose.icons.IcCgmInsert
import app.aaps.core.ui.compose.icons.IcPluginAutomation
import app.aaps.core.ui.compose.icons.IcPluginMaintenance
import app.aaps.core.ui.compose.icons.IcPumpBattery
import app.aaps.core.ui.compose.icons.IcPumpCartridge
import app.aaps.core.ui.compose.icons.IcSiteRotation
import app.aaps.core.ui.compose.icons.Pump
import app.aaps.core.ui.compose.preference.PreferenceSubScreenDef
import app.aaps.core.ui.search.SearchableItem
import app.aaps.core.ui.search.SearchableProvider
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Single source of truth for built-in (non-plugin) preference screens.
 * These are used by both AllPreferencesScreen for display and SearchIndexBuilder for search.
 *
 * To add a new built-in screen:
 * 1. Define it as a property in this class
 * 2. Add it to the appropriate list returned by getSearchableItems()
 * 3. Use the property in AllPreferencesScreen instead of inline definition
 */
@ContributesIntoSet(AppScope::class, binding = binding<SearchableProvider>())
@SingleIn(AppScope::class)
class BuiltInSearchables @Inject constructor(
    private val rh: ResourceHelper,
    private val insulinManager: InsulinManager,
    private val profileFunction: ProfileFunction,
    private val config: Config
) : SearchableProvider {

    // Decides whether concentration-related preferences are worth surfacing in search, so an unknown
    // running insulin simply contributes nothing — the configured list still speaks for itself.
    private fun hasNonU100Insulin(): Boolean =
        insulinManager.insulins.any { it.concentration != 1.0 } || profileFunction.runningICfg.value?.concentration?.let { it != 1.0 } == true

    /**
     * General preferences (units, language, simple mode, patient name, skin, dark mode)
     */
    val general: PreferenceSubScreenDef
        get() = PreferenceSubScreenDef(
            key = "general",
            title = CoreUiStrings.configbuilder_general,
            items = listOf(
                StringKey.GeneralUnits,
                StringKey.GeneralLanguage,
                BooleanKey.GeneralSimpleMode.withChangeGuard { newValue ->
                    if (newValue && hasNonU100Insulin())
                        rh.gs(CoreUiStrings.simple_mode_blocked_by_concentration)
                    else null
                },
                BooleanKey.GeneralInsulinConcentration.withChangeGuard { newValue ->
                    if (!newValue && hasNonU100Insulin())
                        rh.gs(CoreUiStrings.concentration_disable_blocked)
                    else null
                },
                BooleanKey.OverviewKeepScreenOn,
                StringKey.GeneralPatientName,
            ),
            icon = Icons.Default.Settings
        )

    val appearance = PreferenceSubScreenDef(
        key = "appearance",
        title = CoreUiStrings.appearance,
        items = listOf(

            // Range settings subscreen
            PreferenceSubScreenDef(
                key = "range_settings",
                title = CoreUiStrings.prefs_range_title,
                items = listOf(
                    UnitDoubleKey.OverviewLowMark,
                    UnitDoubleKey.OverviewHighMark
                )
            ),

            BooleanKey.OverviewShowNotesInDialogs,
            StringKey.GeneralDarkMode
        ),
        icon = Icons.Outlined.Palette
    )

    /**
     * Protection preferences (passwords, PINs, timeouts)
     */
    val protection = PreferenceSubScreenDef(
        key = "protection",
        title = CoreUiStrings.protection,
        items = listOf(
            // Master Password
            StringKey.ProtectionMasterPassword,
            // Application Protection
            IntKey.ProtectionTypeApplication,
            StringKey.ProtectionApplicationPassword,
            StringKey.ProtectionApplicationPin,
            // Bolus Protection
            IntKey.ProtectionTypeBolus,
            StringKey.ProtectionBolusPassword,
            StringKey.ProtectionBolusPin,
            // Settings Protection
            IntKey.ProtectionTypeSettings,
            StringKey.ProtectionSettingsPassword,
            StringKey.ProtectionSettingsPin,
            // Protection Timeout
            IntKey.ProtectionTimeout
        ),
        icon = Icons.Default.Key
    )

    /**
     * Pump preferences (BT Watchdog, etc.)
     */
    val pump = PreferenceSubScreenDef(
        key = "pump",
        title = CoreUiStrings.pump,
        items = listOf(
            BooleanKey.PumpBtWatchdog
        ),
        icon = Pump
    )

    /**
     * Maintenance preferences (email recipient, logs amount, data choices, unattended export)
     */
    val maintenance = PreferenceSubScreenDef(
        key = "maintenance_settings",
        title = CoreUiStrings.maintenance,
        items = listOf(
            StringKey.MaintenanceEmail,
            IntKey.MaintenanceLogsAmount,
            PreferenceSubScreenDef(
                key = "data_choice_setting",
                title = CoreUiStrings.data_choices,
                items = listOf(
                    BooleanKey.MaintenanceEnableFabric,
                    StringKey.MaintenanceIdentification
                )
            ),
            PreferenceSubScreenDef(
                key = "unattended_export_setting",
                title = CoreUiStrings.unattended_settings_export,
                items = listOf(
                    BooleanKey.MaintenanceEnableExportSettingsAutomation
                )
            )
        ),
        icon = IcPluginMaintenance
    )

    /**
     * Alerts preferences (missed BG, pump unreachable, etc.)
     */
    val alerts = PreferenceSubScreenDef(
        key = "alerts",
        title = CoreUiStrings.localalertsettings_title,
        items = listOf(
            BooleanKey.AlertMissedBgReading,
            IntKey.AlertsStaleDataThreshold,
            BooleanKey.AlertPumpUnreachable,
            IntKey.AlertsPumpUnreachableThreshold,
            BooleanKey.AlertCarbsRequired,
            BooleanKey.AlertUrgentAsAndroidNotification,
            BooleanKey.AlertIncreaseVolume,
            BooleanKey.AlertOverrideDoNotDisturb
        ),
        icon = Icons.Default.Notifications
    )

    // ========== Dialog Settings (not shown in AllPreferencesScreen, only for search) ==========

    /**
     * Fill/Prime button settings (accessible from Fill dialog)
     */
    val fillButtons = PreferenceSubScreenDef(
        key = "prime_fill_settings",
        title = CoreUiStrings.prime_fill,
        items = listOf(
            DoubleKey.ActionsFillButton1,
            DoubleKey.ActionsFillButton2,
            DoubleKey.ActionsFillButton3
        ),
        icon = IcPumpCartridge
    )

    /**
     * Insulin button increment settings (accessible from Insulin dialog)
     */
    val insulinButtons = PreferenceSubScreenDef(
        key = "insulin_button_settings",
        title = CoreUiStrings.insulin_label,
        items = listOf(
            DoubleKey.OverviewInsulinButtonIncrement1,
            DoubleKey.OverviewInsulinButtonIncrement2,
            DoubleKey.OverviewInsulinButtonIncrement3
        ),
        icon = IcBolus
    )

    /**
     * Carbs button increment settings (accessible from Carbs dialog)
     */
    val carbsButtons = PreferenceSubScreenDef(
        key = "carbs_button_settings",
        title = InterfacesStrings.carbs,
        items = listOf(
            IntKey.OverviewCarbsButtonIncrement1,
            IntKey.OverviewCarbsButtonIncrement2,
            IntKey.OverviewCarbsButtonIncrement3,
            BooleanKey.OverviewUseBolusReminder
        ),
        icon = IcCarbs
    )

    /**
     * Status lights warning/critical thresholds (accessible from Overview)
     */
    // Status-light thresholds, grouped into 4 individually-searchable subscreens. The grouping is the
    // single source of truth: the overview status-light settings bottom sheet renders from these
    // (via [statusLights].items), and search indexes each group below.
    val statusLightsCannula = PreferenceSubScreenDef(
        key = "statuslights_cannula",
        title = CoreUiStrings.cannula,
        items = listOf(IntKey.OverviewCageWarning, IntKey.OverviewCageCritical),
        icon = IcCannulaChange
    )
    val statusLightsInsulin = PreferenceSubScreenDef(
        key = "statuslights_insulin",
        title = CoreUiStrings.insulin_label,
        items = listOf(IntKey.OverviewIageWarning, IntKey.OverviewIageCritical, IntKey.OverviewResWarning, IntKey.OverviewResCritical),
        icon = IcPumpCartridge
    )
    val statusLightsSensor = PreferenceSubScreenDef(
        key = "statuslights_sensor",
        title = CoreUiStrings.sensor_label,
        items = listOf(IntKey.OverviewSageWarning, IntKey.OverviewSageCritical, IntKey.OverviewSbatWarning, IntKey.OverviewSbatCritical),
        icon = IcCgmInsert
    )
    val statusLightsPump = PreferenceSubScreenDef(
        key = "statuslights_pump",
        title = CoreUiStrings.pb_label,
        items = listOf(IntKey.OverviewBageWarning, IntKey.OverviewBageCritical, IntKey.OverviewBattWarning, IntKey.OverviewBattCritical),
        icon = IcPumpBattery
    )

    // Container for the status-light settings bottom sheet — items are the 4 group subscreens above
    // (each searchable individually). The parent itself is not added to search.
    val statusLights = PreferenceSubScreenDef(
        key = "statuslights_overview_advanced",
        title = CoreUiStrings.statuslights,
        items = listOf(statusLightsCannula, statusLightsInsulin, statusLightsSensor, statusLightsPump),
        icon = Icons.Default.TipsAndUpdates
    )

    /**
     * Treatment button visibility settings (accessible from Treatment bottom sheet)
     */
    val treatmentButtons = PreferenceSubScreenDef(
        key = "treatment_button_settings",
        title = CoreUiStrings.treatments,
        items = listOf(
            BooleanKey.OverviewShowCgmButton,
            BooleanKey.OverviewShowCalibrationButton,
            BooleanKey.OverviewShowTreatmentButton,
            BooleanKey.OverviewShowInsulinButton,
            BooleanKey.OverviewShowCarbsButton,
            BooleanKey.OverviewShowWizardButton
        ),
        icon = IcBolus
    )

    /**
     * Wizard settings (accessible from Wizard dialog)
     */
    val wizardSettings = PreferenceSubScreenDef(
        key = "wizard_settings",
        title = CoreUiStrings.boluswizard,
        items = listOf(
            IntKey.OverviewBolusPercentage,
            IntKey.OverviewResetBolusPercentageTime,
            BooleanKey.OverviewUseBolusAdvisor
        ),
        icon = IcCalculator
    )

    /**
     * Site rotation settings (accessible from the Site Rotation management screen's cog wheel). The same def
     * backs the cog-wheel bottom sheet and search, so searching the group — or any single key — scopes here
     * instead of opening the full preferences.
     */
    val siteRotation = PreferenceSubScreenDef(
        key = "site_rotation_settings",
        title = CoreUiStrings.site_rotation,
        items = listOf(
            IntKey.SiteRotationUserProfile,
            BooleanKey.SiteRotationManagePump,
            BooleanKey.SiteRotationManageCgm
        ),
        icon = IcSiteRotation
    )

    /**
     * Automation settings — the standalone Automation feature's preference subscreen (location
     * service provider mode). Automation is no longer a plugin, so it is registered here.
     */
    val automation = PreferenceSubScreenDef(
        key = "automation_settings",
        title = CoreUiStrings.automation,
        items = listOf(
            StringKey.AutomationLocation
        ),
        icon = IcPluginAutomation
    )

    override fun getSearchableItems(): List<SearchableItem> = buildList {
        // Main preference screens (shown in AllPreferencesScreen)
        add(SearchableItem.Category(general))
        add(SearchableItem.Category(appearance))
        add(SearchableItem.Category(protection))
        add(SearchableItem.Category(pump))
        add(SearchableItem.Category(alerts))
        add(SearchableItem.Category(maintenance))
        // Registered for both roles: the location-provider mode is a Bidirectional synced setting, so a
        // client can view/set the master's value. Also makes the standalone automation cog resolve its
        // screen on a client (findScreenDef sources from here).
        add(SearchableItem.Category(automation))
        // Dialog settings (only for search, not in AllPreferencesScreen)
        add(SearchableItem.Category(fillButtons))
        add(SearchableItem.Category(insulinButtons))
        add(SearchableItem.Category(carbsButtons))
        // Status lights as 4 individually-searchable groups (parent `statusLights` is just the
        // bottom-sheet container).
        add(SearchableItem.Category(statusLightsCannula))
        add(SearchableItem.Category(statusLightsInsulin))
        add(SearchableItem.Category(statusLightsSensor))
        add(SearchableItem.Category(statusLightsPump))
        add(SearchableItem.Category(treatmentButtons))
        add(SearchableItem.Category(wizardSettings))
        add(SearchableItem.Category(siteRotation))
    }
}


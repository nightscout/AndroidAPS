package app.aaps.ui.search

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TipsAndUpdates
import androidx.compose.material.icons.outlined.Palette
import app.aaps.core.interfaces.insulin.Insulin
import app.aaps.core.interfaces.insulin.InsulinManager
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.skin.SkinDescriptionProvider
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.DoubleKey
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.UnitDoubleKey
import app.aaps.core.keys.interfaces.withChangeGuard
import app.aaps.core.keys.interfaces.withEntries
import app.aaps.core.ui.compose.icons.IcBolus
import app.aaps.core.ui.compose.icons.IcCalculator
import app.aaps.core.ui.compose.icons.IcCarbs
import app.aaps.core.ui.compose.icons.IcPluginMaintenance
import app.aaps.core.ui.compose.icons.IcPumpCartridge
import app.aaps.core.ui.compose.icons.Pump
import app.aaps.core.ui.compose.preference.PreferenceSubScreenDef
import app.aaps.core.ui.search.SearchableItem
import app.aaps.core.ui.search.SearchableProvider
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for built-in (non-plugin) preference screens.
 * These are used by both AllPreferencesScreen for display and SearchIndexBuilder for search.
 *
 * To add a new built-in screen:
 * 1. Define it as a property in this class
 * 2. Add it to the appropriate list returned by getSearchableItems()
 * 3. Use the property in AllPreferencesScreen instead of inline definition
 */
@Singleton
class BuiltInSearchables @Inject constructor(
    private val skinDescriptionProvider: SkinDescriptionProvider,
    private val rh: ResourceHelper,
    private val insulinManager: InsulinManager,
    private val insulin: Insulin
) : SearchableProvider {

    /**
     * Skin entries computed from SkinDescriptionProvider.
     * Maps skin class name to localized display name.
     */
    private val skinEntries: Map<String, String>
        get() = skinDescriptionProvider.skinDescriptions.associate { (className, descriptionResId) ->
            className to rh.gs(descriptionResId)
        }

    private fun hasNonU100Insulin(): Boolean =
        insulinManager.insulins.any { it.concentration != 1.0 } || insulin.iCfg.concentration != 1.0

    /**
     * General preferences (units, language, simple mode, patient name, skin, dark mode)
     */
    val general: PreferenceSubScreenDef
        get() = PreferenceSubScreenDef(
            key = "general",
            titleResId = app.aaps.core.ui.R.string.configbuilder_general,
            items = listOf(
                StringKey.GeneralUnits,
                StringKey.GeneralLanguage,
                BooleanKey.GeneralSimpleMode.withChangeGuard { newValue ->
                    if (newValue && hasNonU100Insulin())
                        rh.gs(app.aaps.core.ui.R.string.simple_mode_blocked_by_concentration)
                    else null
                },
                BooleanKey.GeneralInsulinConcentration.withChangeGuard { newValue ->
                    if (!newValue && hasNonU100Insulin())
                        rh.gs(app.aaps.core.ui.R.string.concentration_disable_blocked)
                    else null
                },
                BooleanKey.OverviewKeepScreenOn,
                StringKey.GeneralPatientName,
            ),
            icon = Icons.Default.Settings
        )

    val appearance = PreferenceSubScreenDef(
        key = "appearance",
        titleResId = app.aaps.core.ui.R.string.appearance,
        items = listOf(

            // Range settings subscreen
            PreferenceSubScreenDef(
                key = "range_settings",
                titleResId = app.aaps.core.keys.R.string.prefs_range_title,
                items = listOf(
                    UnitDoubleKey.OverviewLowMark,
                    UnitDoubleKey.OverviewHighMark
                )
            ),

            BooleanKey.OverviewShowNotesInDialogs,
            StringKey.GeneralSkin.withEntries(skinEntries),
            StringKey.GeneralDarkMode
        ),
        icon = Icons.Outlined.Palette
    )

    /**
     * Protection preferences (passwords, PINs, timeouts)
     */
    val protection = PreferenceSubScreenDef(
        key = "protection",
        titleResId = app.aaps.core.ui.R.string.protection,
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
        titleResId = app.aaps.core.ui.R.string.pump,
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
        titleResId = app.aaps.core.ui.R.string.maintenance,
        items = listOf(
            StringKey.MaintenanceEmail,
            IntKey.MaintenanceLogsAmount,
            PreferenceSubScreenDef(
                key = "data_choice_setting",
                titleResId = app.aaps.core.ui.R.string.data_choices,
                items = listOf(
                    BooleanKey.MaintenanceEnableFabric,
                    StringKey.MaintenanceIdentification
                )
            ),
            PreferenceSubScreenDef(
                key = "unattended_export_setting",
                titleResId = app.aaps.core.ui.R.string.unattended_settings_export,
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
        titleResId = app.aaps.core.ui.R.string.localalertsettings_title,
        items = listOf(
            BooleanKey.AlertMissedBgReading,
            IntKey.AlertsStaleDataThreshold,
            BooleanKey.AlertPumpUnreachable,
            IntKey.AlertsPumpUnreachableThreshold,
            BooleanKey.AlertCarbsRequired,
            BooleanKey.AlertUrgentAsAndroidNotification,
            BooleanKey.AlertIncreaseVolume
        ),
        icon = Icons.Default.Notifications
    )

    // ========== Dialog Settings (not shown in AllPreferencesScreen, only for search) ==========

    /**
     * Fill/Prime button settings (accessible from Fill dialog)
     */
    val fillButtons = PreferenceSubScreenDef(
        key = "prime_fill_settings",
        titleResId = app.aaps.core.ui.R.string.prime_fill,
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
        titleResId = app.aaps.core.ui.R.string.insulin_label,
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
        titleResId = app.aaps.core.ui.R.string.carbs,
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
    val statusLights = PreferenceSubScreenDef(
        key = "statuslights_overview_advanced",
        titleResId = app.aaps.core.ui.R.string.statuslights,
        items = listOf(
            IntKey.OverviewCageWarning,
            IntKey.OverviewCageCritical,
            IntKey.OverviewIageWarning,
            IntKey.OverviewIageCritical,
            IntKey.OverviewSageWarning,
            IntKey.OverviewSageCritical,
            IntKey.OverviewSbatWarning,
            IntKey.OverviewSbatCritical,
            IntKey.OverviewResWarning,
            IntKey.OverviewResCritical,
            IntKey.OverviewBattWarning,
            IntKey.OverviewBattCritical,
            IntKey.OverviewBageWarning,
            IntKey.OverviewBageCritical
        ),
        icon = Icons.Default.TipsAndUpdates
    )

    /**
     * Treatment button visibility settings (accessible from Treatment bottom sheet)
     */
    val treatmentButtons = PreferenceSubScreenDef(
        key = "treatment_button_settings",
        titleResId = app.aaps.core.ui.R.string.treatments,
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
        titleResId = app.aaps.core.ui.R.string.boluswizard,
        items = listOf(
            IntKey.OverviewBolusPercentage,
            IntKey.OverviewResetBolusPercentageTime,
            BooleanKey.OverviewUseBolusAdvisor
        ),
        icon = IcCalculator
    )

    override fun getSearchableItems(): List<SearchableItem> = listOf(
        // Main preference screens (shown in AllPreferencesScreen)
        SearchableItem.Category(general),
        SearchableItem.Category(appearance),
        SearchableItem.Category(protection),
        SearchableItem.Category(pump),
        SearchableItem.Category(alerts),
        SearchableItem.Category(maintenance),
        // Dialog settings (only for search, not in AllPreferencesScreen)
        SearchableItem.Category(fillButtons),
        SearchableItem.Category(insulinButtons),
        SearchableItem.Category(carbsButtons),
        SearchableItem.Category(statusLights),
        SearchableItem.Category(treatmentButtons),
        SearchableItem.Category(wizardSettings)
    )
}


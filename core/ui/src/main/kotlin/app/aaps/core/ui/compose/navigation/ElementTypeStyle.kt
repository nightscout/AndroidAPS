package app.aaps.core.ui.compose.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import app.aaps.core.ui.R
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.icons.IcActivity
import app.aaps.core.ui.compose.icons.IcAnnouncement
import app.aaps.core.ui.compose.icons.IcAs
import app.aaps.core.ui.compose.icons.IcAutomation
import app.aaps.core.ui.compose.icons.IcBgCheck
import app.aaps.core.ui.compose.icons.IcBolus
import app.aaps.core.ui.compose.icons.IcByoda
import app.aaps.core.ui.compose.icons.IcCalculator
import app.aaps.core.ui.compose.icons.IcCalibration
import app.aaps.core.ui.compose.icons.IcCannulaChange
import app.aaps.core.ui.compose.icons.IcCarbs
import app.aaps.core.ui.compose.icons.IcCgmInsert
import app.aaps.core.ui.compose.icons.IcClinicalNotes
import app.aaps.core.ui.compose.icons.IcExtendedBolus
import app.aaps.core.ui.compose.icons.IcHistory
import app.aaps.core.ui.compose.icons.IcLoopClosed
import app.aaps.core.ui.compose.icons.IcNote
import app.aaps.core.ui.compose.icons.IcPluginConfigBuilder
import app.aaps.core.ui.compose.icons.IcPluginInsulin
import app.aaps.core.ui.compose.icons.IcPluginMaintenance
import app.aaps.core.ui.compose.icons.IcProfile
import app.aaps.core.ui.compose.icons.IcPumpBattery
import app.aaps.core.ui.compose.icons.IcPumpCartridge
import app.aaps.core.ui.compose.icons.IcQuestion
import app.aaps.core.ui.compose.icons.IcPluginFood
import app.aaps.core.ui.compose.icons.IcQuickwizard
import app.aaps.core.ui.compose.icons.IcSetupWizard
import app.aaps.core.ui.compose.icons.IcSiteRotation
import app.aaps.core.ui.compose.icons.IcSmb
import app.aaps.core.ui.compose.icons.IcStats
import app.aaps.core.ui.compose.icons.IcTbrHigh
import app.aaps.core.ui.compose.icons.IcTtHigh
import app.aaps.core.ui.compose.icons.IcUserOptions
import app.aaps.core.ui.compose.icons.IcXDrip
import app.aaps.core.ui.compose.icons.Pump

/**
 * Extension functions providing the visual identity for each [ElementType].
 * Single source of truth for icon, color, label, and description.
 */

@Composable
fun ElementType.color(): Color = when (this) {
    ElementType.INSULIN,
    ElementType.TREATMENT,
    ElementType.FILL                    -> AapsTheme.elementColors.insulin

    ElementType.CARBS                   -> AapsTheme.elementColors.carbs
    ElementType.BOLUS_WIZARD            -> AapsTheme.elementColors.bolusWizard
    ElementType.QUICK_WIZARD,
    ElementType.QUICK_WIZARD_MANAGEMENT -> AapsTheme.elementColors.quickWizard
    ElementType.FOOD_MANAGEMENT         -> AapsTheme.elementColors.carbs

    ElementType.CGM_XDRIP               -> AapsTheme.elementColors.cgmXdrip
    ElementType.CGM_DEX                 -> AapsTheme.elementColors.cgmDex
    ElementType.CALIBRATION             -> AapsTheme.elementColors.calibration
    ElementType.INSULIN_MANAGEMENT      -> AapsTheme.elementColors.insulin

    ElementType.PROFILE_MANAGEMENT      -> AapsTheme.elementColors.profileSwitch

    ElementType.TEMP_TARGET_MANAGEMENT  -> AapsTheme.elementColors.tempTarget

    ElementType.BG_CHECK                -> AapsTheme.elementColors.bgCheck
    ElementType.NOTE                    -> AapsTheme.elementColors.note
    ElementType.EXERCISE                -> AapsTheme.elementColors.exercise
    ElementType.QUESTION                -> AapsTheme.elementColors.question
    ElementType.ANNOUNCEMENT            -> AapsTheme.elementColors.announcement

    ElementType.SENSOR_INSERT,
    ElementType.BATTERY_CHANGE,
    ElementType.CANNULA_CHANGE          -> AapsTheme.elementColors.deviceMaintenance

    ElementType.SITE_ROTATION           -> AapsTheme.elementColors.siteRotation
    ElementType.TEMP_BASAL              -> AapsTheme.elementColors.tempBasal
    ElementType.EXTENDED_BOLUS          -> AapsTheme.elementColors.extendedBolus
    ElementType.AUTOMATION              -> AapsTheme.elementColors.automation
    ElementType.PUMP                    -> AapsTheme.elementColors.pump
    ElementType.SETTINGS,
    ElementType.QUICK_LAUNCH_CONFIG     -> AapsTheme.elementColors.settings

    ElementType.TREATMENTS              -> AapsTheme.elementColors.treatments
    ElementType.STATISTICS,
    ElementType.TDD_CYCLE_PATTERN       -> AapsTheme.elementColors.statistics

    ElementType.PROFILE_HELPER          -> AapsTheme.elementColors.profileSwitch
    ElementType.HISTORY_BROWSER,
    ElementType.SETUP_WIZARD,
    ElementType.MAINTENANCE,
    ElementType.CONFIGURATION           -> AapsTheme.elementColors.navigation

    ElementType.ABOUT                   -> AapsTheme.elementColors.aaps

    ElementType.COB                     -> AapsTheme.elementColors.cob
    ElementType.SENSITIVITY             -> AapsTheme.elementColors.sensitivity
    ElementType.RUNNING_MODE            -> AapsTheme.elementColors.runningMode
    ElementType.USER_ENTRY              -> AapsTheme.elementColors.userEntry
    ElementType.LOOP                    -> AapsTheme.elementColors.loop
    ElementType.AAPS                    -> AapsTheme.elementColors.aaps
    ElementType.EXIT                    -> AapsTheme.elementColors.navigation
}

fun ElementType.icon(): ImageVector = when (this) {
    ElementType.INSULIN                 -> IcBolus
    ElementType.CARBS                   -> IcCarbs
    ElementType.BOLUS_WIZARD            -> IcCalculator
    ElementType.QUICK_WIZARD,
    ElementType.QUICK_WIZARD_MANAGEMENT -> IcQuickwizard
    ElementType.FOOD_MANAGEMENT         -> IcPluginFood

    ElementType.TREATMENT               -> Icons.Default.Add
    ElementType.CGM_XDRIP               -> IcXDrip
    ElementType.CGM_DEX                 -> IcByoda
    ElementType.CALIBRATION             -> IcCalibration
    ElementType.INSULIN_MANAGEMENT      -> IcPluginInsulin

    ElementType.PROFILE_MANAGEMENT      -> IcProfile

    ElementType.TEMP_TARGET_MANAGEMENT  -> IcTtHigh

    ElementType.BG_CHECK                -> IcBgCheck
    ElementType.NOTE                    -> IcNote
    ElementType.EXERCISE                -> IcActivity
    ElementType.QUESTION                -> IcQuestion
    ElementType.ANNOUNCEMENT            -> IcAnnouncement
    ElementType.SENSOR_INSERT           -> IcCgmInsert
    ElementType.BATTERY_CHANGE          -> IcPumpBattery
    ElementType.CANNULA_CHANGE          -> IcCannulaChange
    ElementType.FILL                    -> IcPumpCartridge
    ElementType.SITE_ROTATION           -> IcSiteRotation
    ElementType.TEMP_BASAL              -> IcTbrHigh
    ElementType.EXTENDED_BOLUS          -> IcExtendedBolus
    ElementType.AUTOMATION              -> IcAutomation
    ElementType.PUMP                    -> Pump
    ElementType.SETTINGS                -> Icons.Default.Settings
    ElementType.QUICK_LAUNCH_CONFIG     -> Icons.Default.Settings
    ElementType.TREATMENTS              -> IcClinicalNotes
    ElementType.STATISTICS,
    ElementType.TDD_CYCLE_PATTERN       -> IcStats

    ElementType.PROFILE_HELPER          -> IcProfile
    ElementType.HISTORY_BROWSER         -> IcHistory
    ElementType.SETUP_WIZARD            -> IcSetupWizard
    ElementType.MAINTENANCE             -> IcPluginMaintenance
    ElementType.CONFIGURATION           -> IcPluginConfigBuilder
    ElementType.ABOUT                   -> Icons.Default.Info
    ElementType.COB                     -> IcCarbs
    ElementType.SENSITIVITY             -> IcAs
    ElementType.RUNNING_MODE            -> IcLoopClosed
    ElementType.USER_ENTRY              -> IcUserOptions
    ElementType.LOOP                    -> IcLoopClosed
    ElementType.AAPS                    -> IcSmb
    ElementType.EXIT                    -> Icons.AutoMirrored.Filled.ExitToApp
}

fun ElementType.labelResId(): Int = when (this) {
    ElementType.INSULIN                 -> R.string.overview_insulin_label
    ElementType.CARBS                   -> R.string.carbs
    ElementType.BOLUS_WIZARD            -> R.string.boluswizard
    ElementType.QUICK_WIZARD            -> 0 // dynamic label
    ElementType.QUICK_WIZARD_MANAGEMENT -> R.string.quickwizard_managemnt
    ElementType.FOOD_MANAGEMENT         -> R.string.food_management
    ElementType.TREATMENT               -> R.string.overview_treatment_label
    ElementType.CGM_XDRIP               -> R.string.cgm
    ElementType.CGM_DEX                 -> R.string.cgm
    ElementType.CALIBRATION             -> R.string.calibration
    ElementType.INSULIN_MANAGEMENT      -> R.string.insulin_management
    ElementType.PROFILE_MANAGEMENT      -> R.string.profile_management
    ElementType.TEMP_TARGET_MANAGEMENT  -> R.string.temp_target_management
    ElementType.BG_CHECK                -> R.string.careportal_bgcheck
    ElementType.NOTE                    -> R.string.careportal_note
    ElementType.EXERCISE                -> R.string.careportal_exercise
    ElementType.QUESTION                -> R.string.careportal_question
    ElementType.ANNOUNCEMENT            -> R.string.careportal_announcement
    ElementType.SENSOR_INSERT           -> R.string.cgm_sensor_insert
    ElementType.BATTERY_CHANGE          -> R.string.pump_battery_change
    ElementType.CANNULA_CHANGE          -> R.string.careportal_pump_site_change
    ElementType.FILL                    -> R.string.prime_fill
    ElementType.SITE_ROTATION           -> R.string.site_rotation
    ElementType.TEMP_BASAL              -> R.string.temp_basal
    ElementType.EXTENDED_BOLUS          -> R.string.extended_bolus
    ElementType.AUTOMATION              -> 0 // dynamic label
    ElementType.PUMP                    -> R.string.pump
    ElementType.SETTINGS                -> R.string.settings
    ElementType.QUICK_LAUNCH_CONFIG     -> R.string.quick_launch_configure
    ElementType.TREATMENTS              -> R.string.treatments
    ElementType.STATISTICS              -> R.string.statistics
    ElementType.TDD_CYCLE_PATTERN       -> R.string.tdd_cycle_pattern
    ElementType.PROFILE_HELPER          -> R.string.nav_profile_helper
    ElementType.HISTORY_BROWSER         -> R.string.nav_history_browser
    ElementType.SETUP_WIZARD            -> R.string.nav_setupwizard
    ElementType.MAINTENANCE             -> R.string.maintenance
    ElementType.CONFIGURATION           -> R.string.nav_configuration
    ElementType.ABOUT                   -> R.string.nav_about
    ElementType.COB                     -> R.string.cob
    ElementType.SENSITIVITY             -> R.string.sensitivity
    ElementType.RUNNING_MODE            -> R.string.running_mode
    ElementType.USER_ENTRY              -> R.string.user_entry
    ElementType.LOOP                    -> R.string.loop
    ElementType.AAPS                    -> R.string.aaps
    ElementType.EXIT                    -> R.string.nav_exit
}

fun ElementType.descriptionResId(): Int = when (this) {
    ElementType.INSULIN                 -> R.string.treatment_insulin_desc
    ElementType.CARBS                   -> R.string.treatment_carbs_desc
    ElementType.BOLUS_WIZARD            -> R.string.treatment_calculator_desc
    ElementType.TREATMENT               -> R.string.treatment_desc
    ElementType.INSULIN_MANAGEMENT      -> R.string.manage_insulin_desc
    ElementType.PROFILE_MANAGEMENT      -> R.string.manage_profile_desc
    ElementType.TEMP_TARGET_MANAGEMENT  -> R.string.manage_temp_target_desc
    ElementType.QUICK_WIZARD_MANAGEMENT -> R.string.manage_quickwizard_desc
    ElementType.FOOD_MANAGEMENT         -> R.string.manage_food_desc

    ElementType.TEMP_BASAL              -> R.string.manage_temp_basal_desc
    ElementType.EXTENDED_BOLUS          -> R.string.manage_extended_bolus_desc
    ElementType.SITE_ROTATION           -> R.string.manage_site_rotation_desc
    ElementType.NOTE                    -> R.string.treatment_note_desc
    ElementType.QUESTION                -> R.string.treatment_question_desc
    ElementType.CGM_XDRIP,
    ElementType.CGM_DEX                 -> R.string.treatment_cgm_desc

    ElementType.CALIBRATION             -> R.string.treatment_calibration_desc
    ElementType.BG_CHECK                -> R.string.treatment_bg_check_desc
    ElementType.EXERCISE                -> R.string.treatment_exercise_desc
    ElementType.ANNOUNCEMENT            -> R.string.treatment_announcement_desc
    ElementType.SENSOR_INSERT           -> R.string.treatment_sensor_insert_desc
    ElementType.BATTERY_CHANGE          -> R.string.treatment_battery_change_desc
    ElementType.CANNULA_CHANGE          -> R.string.treatment_cannula_change_desc
    ElementType.FILL                    -> R.string.treatment_fill_desc
    ElementType.TREATMENTS              -> R.string.treatments_desc
    ElementType.STATISTICS              -> R.string.statistics_desc
    ElementType.TDD_CYCLE_PATTERN       -> R.string.tdd_cycle_pattern_desc
    ElementType.PROFILE_HELPER          -> R.string.nav_profile_helper_desc
    ElementType.HISTORY_BROWSER         -> R.string.nav_history_browser_desc
    ElementType.SETUP_WIZARD            -> R.string.nav_setupwizard_desc
    ElementType.MAINTENANCE             -> R.string.description_maintenance
    ElementType.CONFIGURATION           -> R.string.nav_configuration_desc
    ElementType.ABOUT                   -> R.string.nav_about_desc
    ElementType.QUICK_LAUNCH_CONFIG     -> R.string.quick_launch_configure_desc
    ElementType.QUICK_WIZARD,
    ElementType.RUNNING_MODE,
    ElementType.AUTOMATION,
    ElementType.PUMP,
    ElementType.SETTINGS,
    ElementType.COB,
    ElementType.SENSITIVITY,
    ElementType.USER_ENTRY,
    ElementType.LOOP,
    ElementType.AAPS,
    ElementType.EXIT                    -> 0
}

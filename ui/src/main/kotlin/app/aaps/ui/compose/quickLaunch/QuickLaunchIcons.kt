package app.aaps.ui.compose.quickLaunch

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.icons.IcActivity
import app.aaps.core.ui.compose.icons.IcAnnouncement
import app.aaps.core.ui.compose.icons.IcAutomation
import app.aaps.core.ui.compose.icons.IcBgCheck
import app.aaps.core.ui.compose.icons.IcBolus
import app.aaps.core.ui.compose.icons.IcCalculator
import app.aaps.core.ui.compose.icons.IcCalibration
import app.aaps.core.ui.compose.icons.IcCannulaChange
import app.aaps.core.ui.compose.icons.IcCarbs
import app.aaps.core.ui.compose.icons.IcCgmInsert
import app.aaps.core.ui.compose.icons.IcClinicalNotes
import app.aaps.core.ui.compose.icons.IcNote
import app.aaps.core.ui.compose.icons.IcProfile
import app.aaps.core.ui.compose.icons.IcPumpBattery
import app.aaps.core.ui.compose.icons.IcPumpCartridge
import app.aaps.core.ui.compose.icons.IcQuestion
import app.aaps.core.ui.compose.icons.IcQuickwizard
import app.aaps.core.ui.compose.icons.IcSiteRotation
import app.aaps.core.ui.compose.icons.IcTtManual
import app.aaps.core.ui.compose.icons.IcXDrip

/**
 * Resolves the default icon for each static toolbar action.
 * Dynamic actions use a fallback icon since their display is context-dependent.
 */
fun QuickLaunchAction.icon(): ImageVector = when (this) {
    QuickLaunchAction.Insulin              -> IcBolus
    QuickLaunchAction.Carbs                -> IcCarbs
    QuickLaunchAction.Wizard               -> IcCalculator
    QuickLaunchAction.Treatment            -> IcClinicalNotes
    QuickLaunchAction.Cgm                  -> IcXDrip
    QuickLaunchAction.Calibration          -> IcCalibration
    QuickLaunchAction.ProfileSwitch        -> IcProfile
    QuickLaunchAction.BgCheck              -> IcBgCheck
    QuickLaunchAction.Note                 -> IcNote
    QuickLaunchAction.Exercise             -> IcActivity
    QuickLaunchAction.Question             -> IcQuestion
    QuickLaunchAction.Announcement         -> IcAnnouncement
    QuickLaunchAction.SensorInsert         -> IcCgmInsert
    QuickLaunchAction.BatteryChange        -> IcPumpBattery
    QuickLaunchAction.CannulaChange        -> IcCannulaChange
    QuickLaunchAction.Fill                 -> IcPumpCartridge
    QuickLaunchAction.SiteRotation         -> IcSiteRotation
    QuickLaunchAction.QuickLaunchConfig    -> Icons.Default.Settings
    // Dynamic actions — default fallback icons
    is QuickLaunchAction.QuickWizardAction -> IcQuickwizard
    is QuickLaunchAction.AutomationAction  -> IcAutomation
    is QuickLaunchAction.TempTargetPreset  -> IcTtManual
    is QuickLaunchAction.ProfileAction     -> IcProfile
    is QuickLaunchAction.PluginAction      -> Icons.Default.Extension // resolved at runtime in ViewModel
}

/**
 * Returns the string resource ID for the label of each static toolbar action.
 * Dynamic actions return 0 — their label is resolved at runtime.
 */
fun QuickLaunchAction.labelResId(): Int = when (this) {
    QuickLaunchAction.Insulin              -> app.aaps.core.ui.R.string.configbuilder_insulin
    QuickLaunchAction.Carbs                -> app.aaps.core.ui.R.string.carbs
    QuickLaunchAction.Wizard               -> app.aaps.core.ui.R.string.boluswizard
    QuickLaunchAction.Treatment            -> app.aaps.core.ui.R.string.overview_treatment_label
    QuickLaunchAction.Cgm                  -> app.aaps.core.ui.R.string.cgm
    QuickLaunchAction.Calibration          -> app.aaps.core.ui.R.string.calibration
    QuickLaunchAction.ProfileSwitch        -> app.aaps.core.ui.R.string.careportal_profileswitch
    QuickLaunchAction.BgCheck              -> app.aaps.core.ui.R.string.careportal_bgcheck
    QuickLaunchAction.Note                 -> app.aaps.core.ui.R.string.careportal_note
    QuickLaunchAction.Exercise             -> app.aaps.core.ui.R.string.careportal_exercise
    QuickLaunchAction.Question             -> app.aaps.core.ui.R.string.careportal_question
    QuickLaunchAction.Announcement         -> app.aaps.core.ui.R.string.careportal_announcement
    QuickLaunchAction.SensorInsert         -> app.aaps.core.ui.R.string.cgm_sensor_insert
    QuickLaunchAction.BatteryChange        -> app.aaps.core.ui.R.string.pump_battery_change
    QuickLaunchAction.CannulaChange        -> app.aaps.core.ui.R.string.careportal_insulin_cartridge_change
    QuickLaunchAction.Fill                 -> app.aaps.core.ui.R.string.prime_fill
    QuickLaunchAction.SiteRotation         -> app.aaps.core.ui.R.string.site_rotation
    QuickLaunchAction.QuickLaunchConfig    -> app.aaps.core.ui.R.string.settings
    // Dynamic actions — labels resolved at runtime
    is QuickLaunchAction.QuickWizardAction -> 0
    is QuickLaunchAction.AutomationAction  -> 0
    is QuickLaunchAction.TempTargetPreset  -> 0
    is QuickLaunchAction.ProfileAction     -> 0
    is QuickLaunchAction.PluginAction      -> 0
}

/**
 * Returns the description string resource ID for each action.
 * Reuses strings from TreatmentBottomSheet and ManageBottomSheet.
 * Dynamic actions return 0 — their description is resolved at runtime.
 */
fun QuickLaunchAction.descriptionResId(): Int = when (this) {
    QuickLaunchAction.Insulin              -> app.aaps.core.ui.R.string.treatment_insulin_desc
    QuickLaunchAction.Carbs                -> app.aaps.core.ui.R.string.treatment_carbs_desc
    QuickLaunchAction.Wizard               -> app.aaps.core.ui.R.string.treatment_calculator_desc
    QuickLaunchAction.Treatment            -> app.aaps.core.ui.R.string.treatment_desc
    QuickLaunchAction.ProfileSwitch        -> app.aaps.core.ui.R.string.manage_profile_desc
    QuickLaunchAction.SiteRotation         -> app.aaps.core.ui.R.string.manage_site_rotation_desc
    QuickLaunchAction.Fill                 -> 0
    // No descriptions in bottom sheets for these
    QuickLaunchAction.Cgm                  -> 0
    QuickLaunchAction.Calibration          -> 0
    QuickLaunchAction.BgCheck              -> 0
    QuickLaunchAction.Note                 -> 0
    QuickLaunchAction.Exercise             -> 0
    QuickLaunchAction.Question             -> 0
    QuickLaunchAction.Announcement         -> 0
    QuickLaunchAction.SensorInsert         -> 0
    QuickLaunchAction.BatteryChange        -> 0
    QuickLaunchAction.CannulaChange        -> 0
    QuickLaunchAction.QuickLaunchConfig    -> 0
    // Dynamic actions — descriptions resolved at runtime
    is QuickLaunchAction.QuickWizardAction -> 0
    is QuickLaunchAction.AutomationAction  -> 0
    is QuickLaunchAction.TempTargetPreset  -> 0
    is QuickLaunchAction.ProfileAction     -> 0
    is QuickLaunchAction.PluginAction      -> 0
}

/**
 * Returns the semantic tint color for each toolbar action,
 * matching the color scheme used in ManageBottomSheet.
 */
@Composable
fun QuickLaunchAction.tintColor(): Color = when (this) {
    QuickLaunchAction.Insulin              -> AapsTheme.elementColors.insulin
    QuickLaunchAction.Carbs                -> AapsTheme.elementColors.carbs
    QuickLaunchAction.Wizard               -> AapsTheme.elementColors.quickWizard
    QuickLaunchAction.Treatment            -> AapsTheme.elementColors.careportal
    QuickLaunchAction.Cgm                  -> AapsTheme.elementColors.cgmXdrip
    QuickLaunchAction.Calibration          -> AapsTheme.elementColors.calibration
    QuickLaunchAction.ProfileSwitch        -> AapsTheme.elementColors.profileSwitch
    QuickLaunchAction.BgCheck              -> AapsTheme.elementColors.bgCheck
    QuickLaunchAction.Note                 -> AapsTheme.elementColors.careportal
    QuickLaunchAction.Exercise             -> AapsTheme.elementColors.exercise
    QuickLaunchAction.Question             -> AapsTheme.elementColors.careportal
    QuickLaunchAction.Announcement         -> AapsTheme.elementColors.announcement
    QuickLaunchAction.SensorInsert         -> AapsTheme.elementColors.cgmXdrip
    QuickLaunchAction.BatteryChange        -> AapsTheme.elementColors.pump
    QuickLaunchAction.CannulaChange        -> AapsTheme.elementColors.pump
    QuickLaunchAction.Fill                 -> AapsTheme.elementColors.pump
    QuickLaunchAction.SiteRotation         -> MaterialTheme.colorScheme.primary
    QuickLaunchAction.QuickLaunchConfig    -> MaterialTheme.colorScheme.primary
    is QuickLaunchAction.QuickWizardAction -> AapsTheme.elementColors.quickWizard
    is QuickLaunchAction.AutomationAction  -> AapsTheme.elementColors.automation
    is QuickLaunchAction.TempTargetPreset  -> AapsTheme.elementColors.tempTarget
    is QuickLaunchAction.ProfileAction     -> AapsTheme.elementColors.profileSwitch
    is QuickLaunchAction.PluginAction      -> MaterialTheme.colorScheme.primary
}

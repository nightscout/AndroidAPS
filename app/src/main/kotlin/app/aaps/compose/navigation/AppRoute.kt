package app.aaps.compose.navigation

import app.aaps.core.ui.compose.ScreenMode

/**
 * Navigation routes for the Compose-based main activity.
 */
sealed class AppRoute(val route: String) {

    data object Main : AppRoute("main")
    data object Profile : AppRoute("profile?mode={mode}") {

        fun createRoute(mode: ScreenMode = ScreenMode.EDIT) = "profile?mode=${mode.name}"
    }

    data object ProfileEditor : AppRoute("profile_editor/{profileIndex}") {

        fun createRoute(profileIndex: Int) = "profile_editor/$profileIndex"
    }

    data object ProfileActivation : AppRoute("profile_activation/{profileIndex}") {

        fun createRoute(profileIndex: Int) = "profile_activation/$profileIndex"
    }

    data object InsulinManagement : AppRoute("insulin_management?mode={mode}") {

        fun createRoute(mode: ScreenMode = ScreenMode.EDIT) = "insulin_management?mode=${mode.name}"
    }

    data object Treatments : AppRoute("treatments")
    data object TempTargetManagement : AppRoute("temp_target_management?mode={mode}") {

        fun createRoute(mode: ScreenMode = ScreenMode.EDIT) = "temp_target_management?mode=${mode.name}"
    }

    data object QuickWizardManagement : AppRoute("quick_wizard_management?mode={mode}") {

        fun createRoute(mode: ScreenMode = ScreenMode.EDIT) = "quick_wizard_management?mode=${mode.name}"
    }

    data object Stats : AppRoute("stats")
    data object ProfileHelper : AppRoute("profile_helper")
    data object Preferences : AppRoute("preferences")
    data object PluginPreferences : AppRoute("plugin_preferences/{pluginKey}") {

        fun createRoute(pluginKey: String) = "plugin_preferences/$pluginKey"
    }

    data object PreferenceScreen : AppRoute("preference_screen/{screenKey}?highlight={highlightKey}") {

        fun createRoute(screenKey: String, highlightKey: String? = null): String {
            return if (highlightKey != null) {
                "preference_screen/$screenKey?highlight=$highlightKey"
            } else {
                "preference_screen/$screenKey"
            }
        }
    }

    data object RunningMode : AppRoute("running_mode")
    data object CareDialog : AppRoute("care_dialog/{eventTypeOrdinal}") {

        fun createRoute(eventTypeOrdinal: Int) = "care_dialog/$eventTypeOrdinal"
    }

    data object FillDialog : AppRoute("fill_dialog/{preselect}") {

        fun createRoute(preselect: Int) = "fill_dialog/$preselect"
    }

    data object CalibrationDialog : AppRoute("calibration_dialog")
    data object CarbsDialog : AppRoute("carbs_dialog")
    data object InsulinDialog : AppRoute("insulin_dialog")
    data object TreatmentDialog : AppRoute("treatment_dialog")
    data object TempBasalDialog : AppRoute("temp_basal_dialog")
    data object ExtendedBolusDialog : AppRoute("extended_bolus_dialog")
    data object WizardDialog : AppRoute("wizard_dialog?carbs={carbs}&notes={notes}") {

        fun createRoute(carbs: Int? = null, notes: String? = null): String {
            val params = buildList {
                carbs?.let { add("carbs=$it") }
                notes?.let { add("notes=$it") }
            }
            return if (params.isEmpty()) "wizard_dialog"
            else "wizard_dialog?${params.joinToString("&")}"
        }
    }

    data object PluginContent : AppRoute("plugin_content/{pluginIndex}") {

        fun createRoute(pluginIndex: Int) = "plugin_content/$pluginIndex"
    }

    data object QuickLaunchConfig : AppRoute("quick_launch_config")
    data object Configuration : AppRoute("configuration")
    data object ImportSettings : AppRoute("import_settings/{source}") {

        fun createRoute(source: String) = "import_settings/$source"
    }

    data object SiteLocationPicker : AppRoute("siteLocationPicker/{siteTypeOrdinal}") {

        fun createRoute(siteType: app.aaps.core.data.model.TE.Type) = "siteLocationPicker/${siteType.ordinal}"
    }

    data object FoodManagement : AppRoute("food_management")
    data object SiteRotationManagement : AppRoute("siteRotationManagement")
    data object SiteRotationSettings : AppRoute("siteRotationSettings")
}

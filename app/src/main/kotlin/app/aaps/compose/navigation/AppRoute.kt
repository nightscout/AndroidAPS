package app.aaps.compose.navigation

/**
 * Navigation routes for the Compose-based main activity.
 */
sealed class AppRoute(val route: String) {

    data object Main : AppRoute("main")
    data object Profile : AppRoute("profile")
    data object ProfileEditor : AppRoute("profile_editor/{profileIndex}") {

        fun createRoute(profileIndex: Int) = "profile_editor/$profileIndex"
    }

    data object ProfileActivation : AppRoute("profile_activation/{profileIndex}") {

        fun createRoute(profileIndex: Int) = "profile_activation/$profileIndex"
    }

    data object Treatments : AppRoute("treatments")
    data object TempTargetManagement : AppRoute("temp_target_management")
    data object QuickWizardManagement : AppRoute("quick_wizard_management")
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

    data object CarbsDialog : AppRoute("carbs_dialog")
    data object InsulinDialog : AppRoute("insulin_dialog")
    data object TreatmentDialog : AppRoute("treatment_dialog")
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

    data object PumpSetup : AppRoute("pump_setup")
    data object Configuration : AppRoute("configuration")
    data object ImportSettings : AppRoute("import_settings/{source}") {

        fun createRoute(source: String) = "import_settings/$source"
    }
}

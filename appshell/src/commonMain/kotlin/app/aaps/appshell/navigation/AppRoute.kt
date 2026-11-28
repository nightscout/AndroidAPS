package app.aaps.appshell.navigation

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

    /** Editor opened for a brand-new, unpersisted profile draft (committed only when saved valid). */
    data object ProfileEditorNew : AppRoute("profile_editor_new")

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
    data object HistoryBrowser : AppRoute("history_browser")
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
    data object CarbsDialog : AppRoute("carbs_dialog?carbs={carbs}&notes={notes}") {

        /** [carbs] and [notes] fill the dialog, for example from a meal on the food screen. */
        fun createRoute(carbs: Int? = null, notes: String? = null): String {
            val params = buildList {
                carbs?.let { add("carbs=$it") }
                notes?.let { add("notes=${WizardDialog.encodeQueryValue(it)}") }
            }
            return if (params.isEmpty()) "carbs_dialog"
            else "carbs_dialog?${params.joinToString("&")}"
        }
    }
    data object InsulinDialog : AppRoute("insulin_dialog")
    data object TreatmentDialog : AppRoute("treatment_dialog")
    data object TempBasalDialog : AppRoute("temp_basal_dialog")
    data object ExtendedBolusDialog : AppRoute("extended_bolus_dialog")
    data object WizardDialog : AppRoute("wizard_dialog?carbs={carbs}&notes={notes}") {

        fun createRoute(carbs: Int? = null, notes: String? = null): String {
            val params = buildList {
                carbs?.let { add("carbs=$it") }
                // Encoded: a food name or a meal note can hold "&", "?", "#", "%" or "/", which would
                // otherwise cut the note or break the route.
                notes?.let { add("notes=${encodeQueryValue(it)}") }
            }
            return if (params.isEmpty()) "wizard_dialog"
            else "wizard_dialog?${params.joinToString("&")}"
        }

        /** Percent-encodes everything except the RFC 3986 unreserved characters, as UTF-8. */
        internal fun encodeQueryValue(value: String): String = buildString {
            for (byte in value.encodeToByteArray()) {
                val c = (byte.toInt() and 0xFF)
                val ch = c.toChar()
                if (ch in 'A'..'Z' || ch in 'a'..'z' || ch in '0'..'9' || ch == '-' || ch == '.' || ch == '_' || ch == '~') append(ch)
                else {
                    append('%')
                    append(HEX[c shr 4])
                    append(HEX[c and 0x0F])
                }
            }
        }

        private const val HEX = "0123456789ABCDEF"
    }

    data object PluginContent : AppRoute("plugin_content/{pluginIndex}") {

        fun createRoute(pluginIndex: Int) = "plugin_content/$pluginIndex"
    }

    data object AutomationList : AppRoute("automation_list")
    data object SceneList : AppRoute("scene_list?mode={mode}") {

        fun createRoute(mode: ScreenMode = ScreenMode.EDIT) = "scene_list?mode=${mode.name}"
    }

    data object SceneWizard : AppRoute("scene_wizard?sceneId={sceneId}") {

        fun createRoute(sceneId: String? = null): String =
            if (sceneId != null) "scene_wizard?sceneId=$sceneId" else "scene_wizard"
    }

    data object QuickLaunchConfig : AppRoute("quick_launch_config")
    data object Configuration : AppRoute("configuration")
    data object PluginCategory : AppRoute("plugin_category/{typeOrdinal}") {

        fun createRoute(typeOrdinal: Int) = "plugin_category/$typeOrdinal"
    }

    data object ImportSettings : AppRoute("import_settings/{source}") {

        fun createRoute(source: String) = "import_settings/$source"
    }

    data object SiteLocationPicker : AppRoute("siteLocationPicker/{siteTypeOrdinal}") {

        fun createRoute(siteType: app.aaps.core.data.model.TE.Type) = "siteLocationPicker/${siteType.ordinal}"
    }

    data object FoodManagement : AppRoute("food_management")
    data object SiteRotationManagement : AppRoute("siteRotationManagement")
    data object SetupWizard : AppRoute("setup_wizard")
    data object AuthorizedClients : AppRoute("authorized_clients")
    /** From the setup wizard, a pairing made here also finishes the wizard. */
    data object PairWithMaster : AppRoute("pair_with_master?wizard={wizard}") {

        fun createRoute(fromSetupWizard: Boolean = false) = if (fromSetupWizard) "pair_with_master?wizard=true" else "pair_with_master"
    }

    /** From the setup wizard, a code used here also finishes the wizard. */
    data object ScanMasterQr : AppRoute("scan_master_qr?wizard={wizard}") {

        fun createRoute(fromSetupWizard: Boolean = false) = if (fromSetupWizard) "scan_master_qr?wizard=true" else "scan_master_qr"
    }
}

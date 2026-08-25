package app.aaps.compose.navigation

import app.aaps.core.data.model.TE
import app.aaps.core.ui.compose.ScreenMode
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Verifies that every [AppRoute] template and its `createRoute` helper agree, and that optional
 * parameters serialize the way the [androidx.navigation] graph parses them. Caught here rather
 * than at runtime so a typo in a template never silently breaks navigation.
 */
class AppRouteTest {

    @Test
    fun staticRoutes_haveStableTemplates() {
        assertThat(AppRoute.Main.route).isEqualTo("main")
        assertThat(AppRoute.Treatments.route).isEqualTo("treatments")
        assertThat(AppRoute.Stats.route).isEqualTo("stats")
        assertThat(AppRoute.ProfileHelper.route).isEqualTo("profile_helper")
        assertThat(AppRoute.HistoryBrowser.route).isEqualTo("history_browser")
        assertThat(AppRoute.Preferences.route).isEqualTo("preferences")
        assertThat(AppRoute.RunningMode.route).isEqualTo("running_mode")
        assertThat(AppRoute.CalibrationDialog.route).isEqualTo("calibration_dialog")
        assertThat(AppRoute.CarbsDialog.route).isEqualTo("carbs_dialog")
        assertThat(AppRoute.InsulinDialog.route).isEqualTo("insulin_dialog")
        assertThat(AppRoute.TreatmentDialog.route).isEqualTo("treatment_dialog")
        assertThat(AppRoute.TempBasalDialog.route).isEqualTo("temp_basal_dialog")
        assertThat(AppRoute.ExtendedBolusDialog.route).isEqualTo("extended_bolus_dialog")
        assertThat(AppRoute.SceneList.route).isEqualTo("scene_list")
        assertThat(AppRoute.QuickLaunchConfig.route).isEqualTo("quick_launch_config")
        assertThat(AppRoute.Configuration.route).isEqualTo("configuration")
        assertThat(AppRoute.FoodManagement.route).isEqualTo("food_management")
        assertThat(AppRoute.SiteRotationManagement.route).isEqualTo("siteRotationManagement")
        assertThat(AppRoute.SetupWizard.route).isEqualTo("setup_wizard")
    }

    @Test
    fun profile_defaultMode_isEdit() {
        assertThat(AppRoute.Profile.createRoute()).isEqualTo("profile?mode=EDIT")
    }

    @Test
    fun profile_explicitPlayMode() {
        assertThat(AppRoute.Profile.createRoute(ScreenMode.PLAY)).isEqualTo("profile?mode=PLAY")
    }

    @Test
    fun profileEditor_embedsIndex() {
        assertThat(AppRoute.ProfileEditor.createRoute(3)).isEqualTo("profile_editor/3")
    }

    @Test
    fun profileActivation_embedsIndex() {
        assertThat(AppRoute.ProfileActivation.createRoute(0)).isEqualTo("profile_activation/0")
    }

    @Test
    fun insulinManagement_defaultMode_isEdit() {
        assertThat(AppRoute.InsulinManagement.createRoute()).isEqualTo("insulin_management?mode=EDIT")
    }

    @Test
    fun insulinManagement_explicitPlayMode() {
        assertThat(AppRoute.InsulinManagement.createRoute(ScreenMode.PLAY)).isEqualTo("insulin_management?mode=PLAY")
    }

    @Test
    fun tempTargetManagement_defaultMode_isEdit() {
        assertThat(AppRoute.TempTargetManagement.createRoute()).isEqualTo("temp_target_management?mode=EDIT")
    }

    @Test
    fun quickWizardManagement_defaultMode_isEdit() {
        assertThat(AppRoute.QuickWizardManagement.createRoute()).isEqualTo("quick_wizard_management?mode=EDIT")
    }

    @Test
    fun pluginPreferences_embedsKey() {
        assertThat(AppRoute.PluginPreferences.createRoute("my.plugin.key")).isEqualTo("plugin_preferences/my.plugin.key")
    }

    @Test
    fun preferenceScreen_withoutHighlight_omitsQueryParam() {
        assertThat(AppRoute.PreferenceScreen.createRoute("general")).isEqualTo("preference_screen/general")
    }

    @Test
    fun preferenceScreen_withHighlight_includesQueryParam() {
        assertThat(AppRoute.PreferenceScreen.createRoute("general", "darkMode"))
            .isEqualTo("preference_screen/general?highlight=darkMode")
    }

    @Test
    fun preferenceScreen_nullHighlight_omitsQueryParam() {
        assertThat(AppRoute.PreferenceScreen.createRoute("general", null)).isEqualTo("preference_screen/general")
    }

    @Test
    fun careDialog_embedsOrdinal() {
        assertThat(AppRoute.CareDialog.createRoute(TE.Type.EXERCISE.ordinal))
            .isEqualTo("care_dialog/${TE.Type.EXERCISE.ordinal}")
    }

    @Test
    fun fillDialog_embedsPreselect() {
        assertThat(AppRoute.FillDialog.createRoute(2)).isEqualTo("fill_dialog/2")
    }

    @Test
    fun wizardDialog_noArgs_returnsBareTemplate() {
        // When both optionals are omitted the helper drops the query string entirely so
        // navigation matches the base destination instead of an empty `?` URI.
        assertThat(AppRoute.WizardDialog.createRoute()).isEqualTo("wizard_dialog")
    }

    @Test
    fun wizardDialog_carbsOnly() {
        assertThat(AppRoute.WizardDialog.createRoute(carbs = 30)).isEqualTo("wizard_dialog?carbs=30")
    }

    @Test
    fun wizardDialog_notesOnly() {
        assertThat(AppRoute.WizardDialog.createRoute(notes = "lunch")).isEqualTo("wizard_dialog?notes=lunch")
    }

    @Test
    fun wizardDialog_carbsAndNotes_joinedWithAmpersand() {
        assertThat(AppRoute.WizardDialog.createRoute(carbs = 45, notes = "snack"))
            .isEqualTo("wizard_dialog?carbs=45&notes=snack")
    }

    @Test
    fun pluginContent_embedsIndex() {
        assertThat(AppRoute.PluginContent.createRoute(7)).isEqualTo("plugin_content/7")
    }

    @Test
    fun sceneWizard_noId_returnsBareTemplate() {
        assertThat(AppRoute.SceneWizard.createRoute()).isEqualTo("scene_wizard")
    }

    @Test
    fun sceneWizard_withId_includesQueryParam() {
        assertThat(AppRoute.SceneWizard.createRoute("abc-123")).isEqualTo("scene_wizard?sceneId=abc-123")
    }

    @Test
    fun sceneWizard_nullId_returnsBareTemplate() {
        assertThat(AppRoute.SceneWizard.createRoute(null)).isEqualTo("scene_wizard")
    }

    @Test
    fun pluginCategory_embedsOrdinal() {
        assertThat(AppRoute.PluginCategory.createRoute(4)).isEqualTo("plugin_category/4")
    }

    @Test
    fun importSettings_embedsSource() {
        assertThat(AppRoute.ImportSettings.createRoute("local")).isEqualTo("import_settings/local")
    }

    @Test
    fun siteLocationPicker_usesEnumOrdinal() {
        val type = TE.Type.CANNULA_CHANGE
        assertThat(AppRoute.SiteLocationPicker.createRoute(type)).isEqualTo("siteLocationPicker/${type.ordinal}")
    }

    @Test
    fun routeTemplates_andCreateRoute_useSamePathPrefix() {
        // Each parametrized helper must produce a string that, after stripping placeholders,
        // shares the same path prefix as its template. A mismatch means the NavGraph composable
        // registered for `route` will never match what `createRoute` builds.
        fun prefixOf(template: String): String = template.substringBefore('/').substringBefore('?')
        assertThat(prefixOf(AppRoute.ProfileEditor.createRoute(1))).isEqualTo(prefixOf(AppRoute.ProfileEditor.route))
        assertThat(prefixOf(AppRoute.CareDialog.createRoute(0))).isEqualTo(prefixOf(AppRoute.CareDialog.route))
        assertThat(prefixOf(AppRoute.PluginContent.createRoute(0))).isEqualTo(prefixOf(AppRoute.PluginContent.route))
        assertThat(prefixOf(AppRoute.WizardDialog.createRoute())).isEqualTo(prefixOf(AppRoute.WizardDialog.route))
        assertThat(prefixOf(AppRoute.SceneWizard.createRoute())).isEqualTo(prefixOf(AppRoute.SceneWizard.route))
        assertThat(prefixOf(AppRoute.PreferenceScreen.createRoute("k"))).isEqualTo(prefixOf(AppRoute.PreferenceScreen.route))
        assertThat(prefixOf(AppRoute.SiteLocationPicker.createRoute(TE.Type.CANNULA_CHANGE)))
            .isEqualTo(prefixOf(AppRoute.SiteLocationPicker.route))
    }
}

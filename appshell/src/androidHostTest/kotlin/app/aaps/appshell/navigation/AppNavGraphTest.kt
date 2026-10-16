package app.aaps.appshell.navigation

import android.content.Context
import androidx.navigation.NavHostController
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.DialogNavigator
import androidx.navigation.createGraph
import androidx.test.core.app.ApplicationProvider
import app.aaps.core.interfaces.plugin.ActivePlugin
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The navigation graph is assembled once and every drawer entry, search result and deep link resolves
 * against it, so a route registered under the wrong template is a dead menu entry rather than a
 * compile error.
 *
 * Building the graph runs the `composable(...)` registrations without rendering any screen, which is
 * what makes this cheap: the destinations need no view model state, only that the view models exist.
 * Rendering each of the ~39 screens would mean stubbing every one of their state flows, for far less
 * than it costs.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppNavGraphTest {

    private val activePlugin: ActivePlugin = mock<ActivePlugin>().apply {
        whenever(getPluginsList()).thenReturn(ArrayList())
    }

    private fun buildGraph(withOverview: Boolean): Set<String> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val navController = NavHostController(context).apply {
            navigatorProvider.addNavigator(ComposeNavigator())
            navigatorProvider.addNavigator(DialogNavigator())
        }
        val graph = navController.createGraph(startDestination = AppRoute.Treatments.route) {
            appNavGraph(
                navController = navController,
                insulinManagementViewModel = mock(),
                profileManagementViewModel = mock(),
                profileEditorViewModel = mock(),
                profileHelperViewModel = mock(),
                tempTargetManagementViewModel = mock(),
                quickWizardManagementViewModel = mock(),
                runningModeManagementViewModel = mock(),
                importViewModel = mock(),
                configurationViewModel = mock(),
                treatmentsViewModel = mock(),
                statsViewModel = mock(),
                siteRotationManagementViewModel = mock(),
                graphViewModel = mock(),
                chipsViewModel = mock(),
                swDefinition = mock(),
                rxBus = mock(),
                activePlugin = activePlugin,
                pluginPermissions = mock(),
                automationRuntime = mock(),
                preferences = mock(),
                rh = mock(),
                builtInSearchables = mock(),
                configBuilder = mock(),
                prefFileList = mock(),
                persistenceLayer = mock(),
                visibilityContext = mock(),
                onNavigationRequest = { _, _ -> },
                onShowDeliveryError = { _, _ -> },
                withProtection = { _, action -> action() },
                requestEditModeAuthorization = { onGranted -> onGranted() },
                onRefreshPermissions = {},
                onExecuteQuickWizard = {},
                onRequestDirectoryAccess = {},
                onRequestPermission = {},
                overview = if (withOverview) ({ }) else null
            )
        }
        return graph.mapNotNull { it.route }.toSet()
    }

    @Test
    fun everyDrawerDestinationIsRegistered() {
        val routes = buildGraph(withOverview = true)

        assertThat(routes).containsAtLeast(
            AppRoute.Treatments.route,
            AppRoute.Stats.route,
            AppRoute.ProfileHelper.route,
            AppRoute.HistoryBrowser.route,
            AppRoute.Preferences.route,
            AppRoute.RunningMode.route,
            AppRoute.Configuration.route,
            AppRoute.SetupWizard.route
        )
    }

    @Test
    fun everyTreatmentDialogIsRegistered() {
        val routes = buildGraph(withOverview = true)

        assertThat(routes).containsAtLeast(
            AppRoute.CarbsDialog.route,
            AppRoute.InsulinDialog.route,
            AppRoute.TreatmentDialog.route,
            AppRoute.TempBasalDialog.route,
            AppRoute.ExtendedBolusDialog.route,
            AppRoute.CalibrationDialog.route
        )
    }

    @Test
    fun everyManagementScreenIsRegistered() {
        val routes = buildGraph(withOverview = true)

        assertThat(routes).containsAtLeast(
            AppRoute.InsulinManagement.route,
            AppRoute.Profile.route,
            AppRoute.TempTargetManagement.route,
            AppRoute.QuickWizardManagement.route,
            AppRoute.FoodManagement.route,
            AppRoute.SceneList.route,
            AppRoute.AutomationList.route,
            AppRoute.SiteRotationManagement.route
        )
    }

    /**
     * A caller with no overview to hand in should get an unresolved route rather than a blank home
     * screen, which is the documented contract of the nullable slot.
     */
    @Test
    fun theHomeRouteExistsOnlyWhenAnOverviewIsSupplied() {
        assertThat(buildGraph(withOverview = true)).contains(AppRoute.Main.route)

        assertThat(buildGraph(withOverview = false)).doesNotContain(AppRoute.Main.route)
    }

    /** The graph is assembled at all, with a plugin list that contributes no preference screens. */
    @Test
    fun theGraphBuildsWithNoPluginsInstalled() {
        val routes = buildGraph(withOverview = true)

        assertThat(routes).isNotEmpty()
        assertThat(routes).contains(AppRoute.Treatments.route)
    }
}

package app.aaps.desktop.shell

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.navigation.compose.NavHost
import app.aaps.appshell.AapsAppRoot
import app.aaps.appshell.navigation.AppRoute
import app.aaps.appshell.navigation.appNavGraph
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.objects.di.CoreObjectsGraph
import app.aaps.core.ui.compose.icons.IcAaps
import app.aaps.desktop.shell.di.DesktopAppGraph
import app.aaps.desktop.shell.di.DesktopStringOwners
import app.aaps.desktop.shell.di.DesktopViewModelFactory
import app.aaps.ui.compose.insulinManagement.InsulinManagementViewModel
import app.aaps.ui.compose.overview.chips.ChipsViewModel
import app.aaps.ui.compose.overview.graphs.GraphViewModel
import app.aaps.ui.compose.profileManagement.viewmodels.ProfileEditorViewModel
import app.aaps.ui.compose.profileManagement.viewmodels.ProfileHelperViewModel
import app.aaps.ui.compose.profileManagement.viewmodels.ProfileManagementViewModel
import app.aaps.ui.compose.quickWizard.viewmodels.QuickWizardManagementViewModel
import app.aaps.ui.compose.runningMode.RunningModeManagementViewModel
import app.aaps.ui.compose.siteRotationDialog.viewModels.SiteRotationManagementViewModel
import app.aaps.ui.compose.stats.viewmodels.StatsViewModel
import app.aaps.ui.compose.tempTarget.TempTargetManagementViewModel
import app.aaps.ui.compose.treatments.viewmodels.TreatmentsViewModel
import app.aaps.ui.compose.configuration.ConfigurationViewModel
import app.aaps.ui.compose.maintenance.ImportViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.zacsweers.metro.createGraphFactory
import app.aaps.core.ui.compose.LocalMetroViewModelFactory
import app.aaps.core.ui.compose.metroViewModel

/**
 * Starts the desktop app.
 *
 * The graph is built **before** the window opens, on purpose. Metro proves at compile time that a
 * binding exists, not that the object behind it can be constructed - a class that reads a file or
 * opens a database in its initializer fails here, at the first attempt, rather than from somewhere
 * that hides it. A failure is shown in the window as well as thrown, because a stack trace on a
 * terminal nobody is watching is how a broken launch gets mistaken for a working one.
 */
fun main() {
    val startup = runCatching {
        val graph = createGraphFactory<DesktopAppGraph.Factory>().create(CoreObjectsGraph)
        startPlugins(graph)
        graph
    }

    application {
        Window(onCloseRequest = ::exitApplication, title = "AAPS") {
            startup.fold(
                onSuccess = { AapsDesktopApp(it) },
                onFailure = { MaterialTheme { Failed(it) } }
            )
        }
    }
}

/**
 * Registers the plugins and picks the active one in each category, before anything draws.
 *
 * Order matters and both steps are needed. Registering the list is not enough on its own: until the
 * selections are verified, `activePump` has nothing to return and throws "No pump selected" from the
 * first view model that asks. Android does exactly this in `MainApp`, and because `ConfigBuilderImpl`
 * is shared now, the desktop calls the same `initialize()` rather than a copy of what it does.
 *
 * String owners come first: a screen that renders before them shows string names rather than words,
 * and the plugin descriptions built below carry names.
 */
private fun startPlugins(graph: DesktopAppGraph) {
    DesktopStringOwners.registerAll()
    // Sorted by the key each plugin registers itself with, which is the order the plugin list is
    // shown in and the order category defaults are picked in.
    val plugins = graph.contributedPlugins.entries.sortedBy { it.key }.map { it.value }
    graph.pluginStore.plugins = plugins
    graph.configBuilder.initialize()
    graph.logger.debug(LTag.CORE, "Registered ${plugins.size} plugins and verified selections")
}

/**
 * The shared AAPS Compose root, with the shared navigation graph inside it.
 *
 * Opens on the preference screen for the same reason the Apple shell does: the overview is still
 * assembled inline in `ComposeMainActivity` rather than in `appNavGraph`, so there is nothing here to
 * call for it. Preferences is a fair test of everything underneath - it reads and writes real
 * settings through the desktop preference store, and renders real text through the generated string
 * maps.
 *
 * The callbacks below are the wiring a platform supplies, and the ones that are not answered say so
 * in the log rather than doing nothing quietly.
 */
@Composable
private fun AapsDesktopApp(graph: DesktopAppGraph) {
    val logger = graph.logger
    val viewModelFactory = remember(graph) { DesktopViewModelFactory(graph) }

    CompositionLocalProvider(LocalMetroViewModelFactory provides viewModelFactory) {
        AapsAppRoot(
            config = graph.config,
            preferences = graph.preferences,
            dateUtil = graph.dateUtil,
            decimalFormatter = graph.decimalFormatter,
            profileUtil = graph.profileUtil,
            passwordHasher = graph.passwordHasher,
            passwordCheck = graph.passwordCheck,
            protectionCheck = graph.protectionCheck,
            exportPasswordDataStore = graph.exportPasswordDataStore,
            visibilityContext = graph.visibilityContext,
            nsClient = graph.nsClient,
            rxBus = graph.rxBus,
            clientControlActionDispatcher = graph.clientControlActionDispatcher,
            appIcon = { modifier -> Icon(imageVector = IcAaps, contentDescription = null, modifier = modifier) },
            splashLogo = { modifier -> Icon(imageVector = IcAaps, contentDescription = null, modifier = modifier) },
            onNavControllerReady = {},
            onClose = { logger.error(LTag.CORE, "Initialization failed and the window was asked to close") }
        ) { navController ->
            // Resolved here rather than inside the NavHost: appNavGraph is a builder, not a
            // composable, so a view model cannot be asked for from within it.
            val insulinManagement = metroViewModel<InsulinManagementViewModel>()
            val profileManagement = metroViewModel<ProfileManagementViewModel>()
            val profileEditor = metroViewModel<ProfileEditorViewModel>()
            val profileHelper = metroViewModel<ProfileHelperViewModel>()
            val tempTargetManagement = metroViewModel<TempTargetManagementViewModel>()
            val quickWizardManagement = metroViewModel<QuickWizardManagementViewModel>()
            val runningModeManagement = metroViewModel<RunningModeManagementViewModel>()
            val import = metroViewModel<ImportViewModel>()
            val configuration = metroViewModel<ConfigurationViewModel>()
            val treatments = metroViewModel<TreatmentsViewModel>()
            val stats = metroViewModel<StatsViewModel>()
            val siteRotationManagement = metroViewModel<SiteRotationManagementViewModel>()
            // Assisted rather than contributed, so built from their own factories. `fullWindow` is
            // false for the same reason as on Android: this is the app's graph, not the history one.
            val graphs: GraphViewModel = viewModel(
                factory = viewModelFactory {
                    initializer { graph.graphViewModelFactory.create(graph.overviewDataCache, fullWindow = false) }
                }
            )
            val chips: ChipsViewModel = viewModel(
                factory = viewModelFactory {
                    initializer { graph.chipsViewModelFactory.create(graph.overviewDataCache) }
                }
            )

            NavHost(navController = navController, startDestination = AppRoute.Preferences.route) {
                appNavGraph(
                    navController = navController,
                    insulinManagementViewModel = insulinManagement,
                    profileManagementViewModel = profileManagement,
                    profileEditorViewModel = profileEditor,
                    profileHelperViewModel = profileHelper,
                    tempTargetManagementViewModel = tempTargetManagement,
                    quickWizardManagementViewModel = quickWizardManagement,
                    runningModeManagementViewModel = runningModeManagement,
                    importViewModel = import,
                    configurationViewModel = configuration,
                    treatmentsViewModel = treatments,
                    statsViewModel = stats,
                    siteRotationManagementViewModel = siteRotationManagement,
                    graphViewModel = graphs,
                    chipsViewModel = chips,
                    swDefinition = graph.swDefinition,
                    rxBus = graph.rxBus,
                    activePlugin = graph.activePlugin,
                    pluginPermissions = graph.pluginPermissions,
                    automationRuntime = graph.automationRuntime,
                    preferences = graph.preferences,
                    rh = graph.textResolver,
                    builtInSearchables = graph.builtInSearchables,
                    configBuilder = graph.configBuilder,
                    prefFileList = graph.prefsFileInfo,
                    persistenceLayer = graph.persistenceLayer,
                    visibilityContext = graph.visibilityContext,
                    onNavigationRequest = { request, _ -> logger.notWiredYet("navigation request $request") },
                    onShowDeliveryError = { comment, title ->
                        logger.error(LTag.CORE, "Delivery error: ${graph.textResolver.gs(title)} - $comment")
                    },
                    // Protection is enforced: ProtectionHost is placed by AapsAppRoot on every
                    // platform now, so a protected screen really does prompt here.
                    withProtection = { protection, action ->
                        graph.protectionCheck.requestProtection(protection) { result ->
                            if (result == app.aaps.core.interfaces.protection.ProtectionResult.GRANTED) action()
                        }
                    },
                    requestEditModeAuthorization = { onGranted ->
                        logger.notWiredYet("edit mode authorization - granting")
                        onGranted()
                    },
                    onRefreshPermissions = { logger.debug(LTag.CORE, "No runtime permissions to refresh on desktop") },
                    onExecuteQuickWizard = { guid -> logger.notWiredYet("quick wizard $guid") },
                    onRequestDirectoryAccess = { logger.debug(LTag.CORE, "Desktop reads its own folder; no access to request") },
                    onRequestPermission = { group -> logger.notWiredYet("permission request $group") },
                    findScreenDef = { null }
                )
            }
        }
    }
}

/** The graph could not be built. The message is the useful part, so it is shown in full. */
@Composable
private fun Failed(error: Throwable) {
    Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("AAPS desktop - startup failed", style = MaterialTheme.typography.headlineSmall)
        Text(error::class.simpleName ?: "error", style = MaterialTheme.typography.titleMedium)
        Text(error.message ?: "no message", style = MaterialTheme.typography.bodyMedium)
    }
}

/** One line, one shape, so a run makes it obvious which platform callbacks are still placeholders. */
private fun AAPSLogger.notWiredYet(what: String) = error(LTag.CORE, "Not wired up on desktop yet: $what")

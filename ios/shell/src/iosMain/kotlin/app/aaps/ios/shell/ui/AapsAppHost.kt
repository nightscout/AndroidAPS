package app.aaps.ios.shell.ui

import androidx.compose.foundation.Image
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.window.ComposeUIViewController
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import app.aaps.appshell.AapsAppRoot
import app.aaps.appshell.navigation.SHELL_HOME_ROUTE
import app.aaps.appshell.navigation.ShellHomeScreen
import app.aaps.appshell.navigation.appNavGraph
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.IosNotificationDelegate
import app.aaps.core.interfaces.protection.ProtectionCheck
import app.aaps.core.interfaces.protection.ProtectionResult
import app.aaps.core.objects.di.CoreObjectsGraph
import app.aaps.shared.clientbindings.ClientGraphBindings
import app.aaps.core.ui.compose.LocalMetroViewModelFactory
import app.aaps.core.ui.compose.icons.IcAaps
import app.aaps.core.ui.compose.metroViewModel
import app.aaps.ios.shell.IosAppStartup
import app.aaps.ios.shell.PluginStoreRegistry
import app.aaps.ios.shell.di.IosAppGraph
import app.aaps.ios.shell.di.IosViewModelFactory
import app.aaps.ui.compose.configuration.ConfigurationViewModel
import app.aaps.ui.compose.insulinManagement.InsulinManagementViewModel
import app.aaps.ui.compose.maintenance.ImportViewModel
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
import dev.zacsweers.metro.createGraphFactory
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIViewController

/**
 * The real AAPS app, running on iOS - the counterpart of `ComposeMainActivity` in `:app`.
 *
 * [AapsAppRoot] is shared code and owns everything above a screen: the theme, the composition
 * locals, the splash gate and the four hosts (snackbar, dialog, password prompt, client-control
 * modal). This file supplies the two things it cannot: the platform's own graph, and the navigation
 * that fills the space it leaves for `content`.
 *
 * ## What this is not, yet
 *
 * The overview - the home screen with the graph and the treatment buttons - is still assembled
 * inline inside `ComposeMainActivity` rather than in the shared navigation graph, so there is
 * nothing here to call for it. The app therefore opens on the preference screen, which **is** in
 * `appNavGraph` and is a fair test of the layers underneath: it reads and writes real preferences
 * through `NSUserDefaults`.
 *
 * The callbacks below are placeholders and say so in the log when used. They are the wiring a
 * platform is supposed to supply - opening a document picker, asking for a permission - and each
 * needs an iOS answer of its own rather than a shared one.
 */
fun aapsAppViewController(): UIViewController {
    // Built once, outside the composition: a graph rebuilt on each recomposition would hand out new
    // singletons every frame - a new database wrapper, a new preference store.
    val graph = createGraphFactory<IosAppGraph.Factory>().create(CoreObjectsGraph, ClientGraphBindings)
    val viewModelFactory = IosViewModelFactory(graph)
    val logger = graph.logger

    // Before the composition, not beside it: the first view model built reads the active pump, and
    // an empty plugin list there throws from a coroutine and takes the process with it.
    IosAppStartup(logger, PluginStoreRegistry(graph.pluginStore), graph.contributedPlugins).run()

    // Attach to the notification centre. Registering a category only records it now, so that building
    // the graph does not need an app bundle - see `IosNotificationDelegate.install`. It is done here
    // rather than in `IosAppStartup` because only the real app reaches this function: startup is unit
    // tested, and `currentNotificationCenter()` cannot be called from a test binary.
    IosNotificationDelegate.install()

    // Read once, outside the composition, for the same reason as the graph: decoding the icon on
    // every recomposition would be work for nothing.
    val appIcon = loadAppIcon()
    if (appIcon == null) logger.error(LTag.CORE, "The app bundle gave no icon; showing the plain AAPS mark")
    logger.debug(LTag.CORE, "Starting the AAPS Compose root on iOS")

    return ComposeUIViewController {
        // iOS has no ambient application object, so the factory is provided here rather than found.
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
                // The real app icon, so the About dialog and the drawer show what the home screen
                // shows. Icon() would tint a logo to one flat colour, so Image() draws it.
                appIcon = { modifier -> AppIconImage(appIcon, modifier) },
                splashLogo = { modifier -> AppIconImage(appIcon, modifier) },
                onNavControllerReady = {},
                onClose = { logger.error(LTag.CORE, "Initialization failed and iOS cannot close an app") }
            ) { navController ->
                // Application protection, the counterpart of the request `ComposeMainActivity`
                // makes from `onResume`. Without this nothing on iOS ever asks, so `ProtectionHost`
                // has no request to render and a configured app-launch password never appears - the
                // setting looked saved and did nothing, which is how it was found.
                //
                // Asked at launch **and every time the app comes back to the foreground**, which is
                // the case that matters: a phone handed to someone else while AAPS is backgrounded.
                // Android does the same from `onResume`. When protection is set to "No protection"
                // this costs nothing - `requestProtection` grants straight away without a dialog, so
                // the gate only appears for someone who asked for it.
                var unlocked by remember { mutableStateOf(false) }
                var asking by remember { mutableStateOf(false) }
                val requestUnlock = remember {
                    {
                        // Guarded, as Android guards with `isProtectionCheckActive`: `didBecomeActive`
                        // can fire while a prompt is already up, and a second request would stack a
                        // second dialog on the first.
                        if (!asking) {
                            asking = true
                            // Re-locked before asking, or the app stays readable behind the prompt on
                            // the way back from the background.
                            unlocked = false
                            graph.protectionCheck.requestProtection(ProtectionCheck.Protection.APPLICATION) { result ->
                                asking = false
                                unlocked = result == ProtectionResult.GRANTED
                                if (!unlocked) logger.error(LTag.CORE, "Application protection was not granted: $result")
                            }
                        }
                    }
                }

                DisposableEffect(Unit) {
                    requestUnlock()
                    val observer = NSNotificationCenter.defaultCenter.addObserverForName(
                        name = UIApplicationDidBecomeActiveNotification,
                        `object` = null,
                        queue = NSOperationQueue.mainQueue,
                        usingBlock = { requestUnlock() }
                    )
                    onDispose { NSNotificationCenter.defaultCenter.removeObserver(observer) }
                }

                // Nothing is drawn until it is granted. Showing the app behind the prompt would make
                // the protection cosmetic - the screen it is meant to hide would be readable through
                // the dialog, and on iOS it would also be in the app-switcher snapshot.
                if (!unlocked) return@AapsAppRoot

                // Resolved here rather than inside the NavHost: `appNavGraph` is a builder, not a
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
                // Assisted rather than contributed, so it is built from its own factory. `fullWindow`
                // is false here for the same reason as on Android: this is the app's own graph, not
                // the full-screen history one.
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

                // Starts on a list of screens rather than on the settings screen. Settings as the
                // start destination left its back arrow inert - there was nothing behind it - and
                // left every other screen unreachable, since they are all navigated to from the
                // overview that iOS does not have yet. See `ShellHomeScreen` in :appshell.
                NavHost(navController = navController, startDestination = SHELL_HOME_ROUTE) {
                    composable(SHELL_HOME_ROUTE) {
                        ShellHomeScreen(onOpen = { route -> navController.navigate(route) })
                    }
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
                        onNavigationRequest = { request, _ ->
                            logger.notWiredYet("navigation request $request")
                        },
                        onShowDeliveryError = { comment, title ->
                            logger.error(LTag.CORE, "Delivery error: ${graph.textResolver.gs(title)} - $comment")
                        },
                        // Protection is not enforced on iOS yet - see IosProtectionCheck for why the
                        // action runs anyway rather than every protected screen being unreachable.
                        withProtection = { _, action -> action() },
                        requestEditModeAuthorization = { onGranted ->
                            logger.notWiredYet("edit mode authorization - granting")
                            onGranted()
                        },
                        onRefreshPermissions = { logger.notWiredYet("permission refresh") },
                        onExecuteQuickWizard = { guid -> logger.notWiredYet("quick wizard $guid") },
                        onRequestDirectoryAccess = { logger.notWiredYet("directory access - iOS needs a document picker") },
                        onRequestPermission = { group -> logger.notWiredYet("permission request $group") },
                        findScreenDef = { null }
                    )
                }
            }
        }
    }
}

/** One line, one shape, so a run makes it obvious which platform callbacks are still placeholders. */
private fun AAPSLogger.notWiredYet(what: String) = error(LTag.CORE, "Not wired up on iOS yet: $what")

/**
 * The app icon, or the plain AAPS mark when the bundle would not give one up.
 *
 * A fallback rather than nothing on screen: the About dialog and the drawer both have a place for
 * it, and an empty space there reads as a broken screen.
 */
@Composable
private fun AppIconImage(icon: ImageBitmap?, modifier: Modifier) {
    if (icon != null) Image(bitmap = icon, contentDescription = null, modifier = modifier)
    else Icon(imageVector = IcAaps, contentDescription = null, modifier = modifier)
}

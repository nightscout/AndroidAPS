package app.aaps.ios.shell.ui

import androidx.compose.foundation.Image
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.key
import app.aaps.core.keys.StringKey
import kotlinx.coroutines.flow.drop
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
import app.aaps.appshell.navigation.appNavGraph
import app.aaps.ui.search.SearchViewModel
import app.aaps.ui.compose.permissionsSheet.PermissionsViewModel
import app.aaps.ui.compose.maintenance.MaintenanceViewModel
import app.aaps.ui.compose.manageSheet.ManageViewModel
import app.aaps.ui.compose.loopSheet.LoopActionViewModel
import app.aaps.ui.compose.scenesSheet.ScenesViewModel
import app.aaps.ui.compose.treatmentsSheet.TreatmentViewModel
import app.aaps.ui.compose.overview.statusLights.StatusViewModel
import app.aaps.ui.compose.main.MainViewModel
import app.aaps.ui.compose.main.OverviewScreen
import app.aaps.appshell.navigation.ElementNavigator
import app.aaps.appshell.navigation.handleSearchResultClick
import app.aaps.appshell.navigation.handleQuickLaunchAction
import app.aaps.appshell.navigation.handleNotificationAction
import app.aaps.appshell.navigation.AppRoute
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.IosNotificationDelegate
import app.aaps.core.interfaces.protection.ProtectionCheck
import app.aaps.core.interfaces.protection.ProtectionResult
import app.aaps.core.interfaces.resources.TextRefValueRegistry
import app.aaps.core.objects.di.CoreObjectsGraph
import app.aaps.ios.shell.platform.IosLanguage
import app.aaps.plugins.sync.nsclientV3.ws.NsSocketFactory
import app.aaps.shared.clientbindings.ClientGraphBindings
import app.aaps.core.ui.compose.LocalMetroViewModelFactory
import app.aaps.core.ui.compose.icons.IcAaps
import app.aaps.core.ui.compose.metroViewModel
import app.aaps.implementation.lifecycle.IosProtectionLifecycle
import app.aaps.ios.shell.IosAppStartup
import app.aaps.ios.shell.PluginStoreRegistry
import app.aaps.ios.shell.di.IosAppGraph
import app.aaps.shared.clientbindings.ClientViewModelFactory
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
fun aapsAppViewController(nsSocketFactory: NsSocketFactory): UIViewController {
    // Built once, outside the composition: a graph rebuilt on each recomposition would hand out new
    // singletons every frame - a new database wrapper, a new preference store.
    val graph = createGraphFactory<IosAppGraph.Factory>().create(CoreObjectsGraph, ClientGraphBindings, nsSocketFactory)
    val viewModelFactory = ClientViewModelFactory(graph)
    val logger = graph.logger

    // Before the composition, not beside it: the first view model built reads the active pump, and
    // an empty plugin list there throws from a coroutine and takes the process with it.
    IosAppStartup(logger, PluginStoreRegistry(graph.pluginStore, graph.configBuilder), graph.contributedPlugins) { graph.periodicMaintenance.start(graph.appScope) }.run()

    // Attach to the notification centre. Registering a category only records it now, so that building
    // the graph does not need an app bundle - see `IosNotificationDelegate.install`. It is done here
    // rather than in `IosAppStartup` because only the real app reaches this function: startup is unit
    // tested, and `currentNotificationCenter()` cannot be called from a test binary.
    // Before the first screen composes, or it renders English and only corrects itself on the next
    // recomposition. Android gets this from `Resources`; here the registry has to be told.
    IosLanguage.apply(graph.preferences)
    logger.debug(LTag.CORE, "Language: ${TextRefValueRegistry.locale ?: "English"}")

    IosNotificationDelegate.install()

    // Same placement and the same reason: this touches UIKit, so it cannot live in the graph or in
    // `IosAppStartup`. Until this ran, Nightscout sync was blocked forever - see `startBatteryWatch`.
    graph.receiverStatusStore.startBatteryWatch()

    // Clears a granted PIN or biometric session when the app is backgrounded, which is what Android
    // gets from `ProcessLifecycleListener.onPause`. Same placement and the same UIKit reason.
    IosProtectionLifecycle(logger, graph.protectionCheck).start()

    // Read once, outside the composition, for the same reason as the graph: decoding the icon on
    // every recomposition would be work for nothing.
    // From the bundle, like the icon and for the same reason: one framework serves both AAPSClient
    // and AAPSClient2, so each target's own `CFBundleDisplayName` is the only thing that can tell
    // them apart. This was the literal "AAPS" before - the name of the *master*, on a follower.
    val appName = graph.textResolver.gs(graph.config.appName)
    val appIcon = loadAppIcon()
    if (appIcon == null) logger.error(LTag.CORE, "The app bundle gave no icon; showing the plain AAPS mark")
    logger.debug(LTag.CORE, "Starting the AAPS Compose root on iOS")

    // The language setting, applied while the app runs. Android answers this by recreating the
    // activity, which reloads the `Context` its `Resources` resolve against; here the registry is
    // repointed and the composition is rebuilt, which is the same outcome without a restart iOS
    // could not perform anyway.
    return ComposeUIViewController {
        // Rebuilds everything below when a rebuild is asked for - after an import, or a language
        // change. `key` discards the subtree's state, which is why it is keyed on this and nothing
        // else: a scroll position lost on a language change is fine, on every recomposition is not.
        // The language setting, applied while the app runs. Android answers this by recreating the
        // activity, which reloads the `Context` its `Resources` resolve against; here the registry is
        // repointed and the composition rebuilt - the same outcome without a restart iOS could not
        // perform anyway.
        LaunchedEffect(Unit) {
            graph.preferences.observe(StringKey.GeneralLanguage).drop(1).collect {
                IosLanguage.apply(graph.preferences)
                logger.debug(LTag.CORE, "Language changed to ${TextRefValueRegistry.locale ?: "English"}")
                graph.uiRestart.request()
            }
        }
        val restart by graph.uiRestart.signal.collectAsState()
        key(restart) {
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
                val mainViewModel = metroViewModel<MainViewModel>()

                // The shared navigator: every route a tap can lead to is mapped in :appshell, so the
                // only per-platform parts are the four actions below. iOS can honour one of them.
                val navigator = ElementNavigator(
                    navController = navController,
                    mainViewModel = mainViewModel,
                    activePlugin = graph.activePlugin,
                    protectionCheck = graph.protectionCheck,
                    configBuilder = graph.configBuilder,
                    dexcomBoyda = graph.dexcomBoyda,
                    // The Dexcom build is an Android app; an iOS client reads glucose from Nightscout.
                    onOpenCgmApp = { pkg -> logger.notWiredYet("open CGM app $pkg") },
                    // iOS gives an app no way to quit itself, and Apple treats that as a crash.
                    onExit = { logger.notWiredYet("exit from the menu") },
                    // Needs a UIDocumentPicker, which nothing on iOS has yet.
                    onRequestDirectoryAccess = { logger.notWiredYet("directory access") },
                    onOpenUrl = { url -> graph.urlOpener.open(url) }
                )
                val chips: ChipsViewModel = viewModel(
                    factory = viewModelFactory {
                        initializer { graph.chipsViewModelFactory.create(graph.overviewDataCache) }
                    }
                )

                // The overview is the start destination, the same as Android and desktop. Settings
                // used to be, which left its back arrow inert - there was nothing behind it - and
                // left every other screen unreachable, since they are all reached from the overview.
                NavHost(navController = navController, startDestination = AppRoute.Main.route) {
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
                        // Passed so the rules can be read and edited here. The runtime is deliberately
                        // NOT started on a client: `MainApp` calls `automationRuntime.start()`, this shell
                        // does not, and that is the design - a follower edits definitions and the master
                        // runs them. Do not "fix" the missing start() call; it would give a second machine
                        // the power to fire the same rules.
                        automationRuntime = graph.automationRuntime,
                        preferences = graph.preferences,
                        rh = graph.textResolver,
                        builtInSearchables = graph.builtInSearchables,
                        configBuilder = graph.configBuilder,
                        prefFileList = graph.prefsFileInfo,
                        persistenceLayer = graph.persistenceLayer,
                        visibilityContext = graph.visibilityContext,
                        onNavigationRequest = { request, _ -> navigator.handleNavigationRequest(request) },
                        onShowDeliveryError = { comment, title ->
                            logger.error(LTag.CORE, "Delivery error: ${graph.textResolver.gs(title)} - $comment")
                        },
                        withProtection = guardedBy(graph.protectionCheck),
                        // The same check `ComposeMainActivity` makes. It used to grant unconditionally
                        // with a "not wired yet" line, which is a stub that fails open on a protection
                        // check: with settings protection configured, the edit pencil on the
                        // QuickWizard, profile, insulin and temp-target screens opened with no prompt.
                        // Those values feed dosing, and a client's edits are pushed to the master.
                        // `protectionCheck` was already on the graph - `withProtection` above uses it.
                        requestEditModeAuthorization = { onGranted ->
                            graph.protectionCheck.requestAuthorization(ProtectionCheck.Protection.PREFERENCES) { result ->
                                if (result.grantedLevel != null) onGranted()
                            }
                        },
                        onRefreshPermissions = { logger.notWiredYet("permission refresh") },
                        onExecuteQuickWizard = { guid -> logger.notWiredYet("quick wizard $guid") },
                        onRequestDirectoryAccess = { logger.notWiredYet("directory access - iOS needs a document picker") },
                        onRequestPermission = { group -> logger.notWiredYet("permission request $group") },
                        findScreenDef = { null },
                        overview = {
                            OverviewScreen(
                                mainViewModel = mainViewModel,
                                manageViewModel = metroViewModel<ManageViewModel>(),
                                maintenanceViewModel = metroViewModel<MaintenanceViewModel>(),
                                statusViewModel = metroViewModel<StatusViewModel>(),
                                treatmentViewModel = metroViewModel<TreatmentViewModel>(),
                                scenesViewModel = metroViewModel<ScenesViewModel>(),
                                loopActionViewModel = metroViewModel<LoopActionViewModel>(),
                                searchViewModel = metroViewModel<SearchViewModel>(),
                                permissionsViewModel = metroViewModel<PermissionsViewModel>(),
                                graphViewModel = graphs,
                                chipsViewModel = chips,
                                activePlugin = graph.activePlugin,
                                config = graph.config,
                                objectives = graph.objectives,
                                bgQualityCheck = graph.bgQualityCheck,
                                notificationManager = graph.notificationManager,
                                uiInteraction = graph.uiInteraction,
                                builtInSearchables = graph.builtInSearchables,
                                bolusProgressData = graph.bolusProgressData,
                                clientControlActionDispatcher = graph.clientControlActionDispatcher,
                                commandQueue = graph.commandQueue,
                                pumpCommunicationStatus = graph.pumpCommunicationStatus,
                                appName = appName,
                                authorizationFailedMessage = "Authorization failed",
                                onNavigate = { request -> navigator.handleNavigationRequest(request) },
                                onSearchResultClick = { entry -> navigator.handleSearchResultClick(entry) },
                                onNotificationActionClick = { n -> navigator.handleNotificationAction(n.id) },
                                onQuickLaunchActionClick = { action -> navigator.handleQuickLaunchAction(action) },
                                // The destination is already in the shared graph and the view model is
                                // already handed to it above, so this is the same call the other two
                                // shells make. It was a placeholder only while iOS had no importer.
                                onImportSettingsNavigate = { source -> navController.navigate(AppRoute.ImportSettings.createRoute(source.name)) },
                                // Needs a UIDocumentPicker, which nothing on iOS has yet.
                                onDirectoryClick = { logger.notWiredYet("directory picker") },
                                onLaunchBrowser = { url -> graph.urlOpener.open(url) },
                                // An iOS app cannot bring itself to the front; the system decides.
                                onBringToForeground = { logger.notWiredYet("bring to foreground") },
                                // No activity to recreate. A Compose scene is not restarted this way.
                                onRecreateActivity = { logger.notWiredYet("recreate") },
                                onAuthorizationFailed = { logger.error(LTag.CORE, "Authorization failed") },
                                autoShowNotificationSheet = false,
                                onAutoShowConsumed = {}
                            )
                        }
                    )
                }
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

/**
 * Runs an action only when the user clears the protection it sits behind.
 *
 * The same shape as desktop's `Main.kt` and Android's `navigator.guarded`. Named rather than written
 * inline at the call site so it can be tested: this used to be `{ _, action -> action() }`, left over
 * from when iOS had no `ProtectionCheck` of its own, and it ran **every** protected action without
 * asking - bolus entry and settings edits included. That is invisible on a device with protection
 * turned off, which is most of them, so it is worth a test rather than a look.
 *
 * Anything other than [ProtectionResult.GRANTED] - denied, or cancelled by backing out of the prompt
 * - must not run the action.
 */
internal fun guardedBy(protectionCheck: ProtectionCheck): (ProtectionCheck.Protection, () -> Unit) -> Unit =
    { protection, action ->
        protectionCheck.requestProtection(protection) { result ->
            if (result == ProtectionResult.GRANTED) action()
        }
    }

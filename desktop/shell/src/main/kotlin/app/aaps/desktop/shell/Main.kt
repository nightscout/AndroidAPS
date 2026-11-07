package app.aaps.desktop.shell

import androidx.compose.foundation.Image
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.NavHost
import app.aaps.appshell.AapsAppRoot
import app.aaps.appshell.StartupFailedScreen
import app.aaps.appshell.navigation.AppRoute
import app.aaps.appshell.navigation.ElementNavigator
import app.aaps.appshell.navigation.appNavGraph
import app.aaps.appshell.navigation.handleNotificationAction
import app.aaps.appshell.navigation.handleQuickLaunchAction
import app.aaps.appshell.navigation.handleSearchResultClick
import app.aaps.core.interfaces.configuration.ExternalOptions
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.protection.ProtectionCheck
import app.aaps.core.interfaces.resources.LanguageTag
import app.aaps.core.interfaces.resources.TextRefValueRegistry
import app.aaps.core.keys.StringKey
import app.aaps.core.objects.di.CoreObjectsGraph
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.LocalMetroViewModelFactory
import app.aaps.core.ui.compose.metroViewModel
import app.aaps.core.ui.compose.rememberOwnViewModelStoreOwner
import app.aaps.desktop.shell.config.DesktopClientConfig
import app.aaps.desktop.shell.config.GeneratedBuildInfo
import app.aaps.desktop.shell.di.DesktopAppGraph
import app.aaps.desktop.shell.di.GeneratedStringOwners
import app.aaps.desktop.shell.secure.DesktopStorage
import app.aaps.desktop.shell.secure.KeyVaultUnavailableException
import app.aaps.implementation.logging.AAPSLoggerDesktop
import app.aaps.desktop.shell.platform.openFolder
import app.aaps.implementation.maintenance.DesktopFolders
import app.aaps.shared.clientbindings.ClientGraphBindings
import app.aaps.shared.clientbindings.ClientViewModelFactory
import app.aaps.shared.impl.sharedPreferences.StoreUnreadableException
import app.aaps.ui.compose.configuration.ConfigurationViewModel
import app.aaps.ui.compose.insulinManagement.InsulinManagementViewModel
import app.aaps.ui.compose.loopSheet.LoopActionViewModel
import app.aaps.ui.compose.main.MainViewModel
import app.aaps.ui.compose.main.OverviewScreen
import app.aaps.ui.compose.maintenance.ImportViewModel
import app.aaps.ui.compose.maintenance.MaintenanceViewModel
import app.aaps.ui.compose.manageSheet.ManageViewModel
import app.aaps.ui.compose.overview.chips.ChipsViewModel
import app.aaps.ui.compose.overview.graphs.GraphViewModel
import app.aaps.ui.compose.overview.statusLights.StatusViewModel
import app.aaps.ui.compose.permissionsSheet.PermissionsViewModel
import app.aaps.ui.compose.profileManagement.viewmodels.ProfileEditorViewModel
import app.aaps.ui.compose.profileManagement.viewmodels.ProfileHelperViewModel
import app.aaps.ui.compose.profileManagement.viewmodels.ProfileManagementViewModel
import app.aaps.ui.compose.quickWizard.viewmodels.QuickWizardManagementViewModel
import app.aaps.ui.compose.runningMode.RunningModeManagementViewModel
import app.aaps.ui.compose.scenesSheet.ScenesViewModel
import app.aaps.ui.compose.siteRotationDialog.viewModels.SiteRotationManagementViewModel
import app.aaps.ui.compose.stats.viewmodels.StatsViewModel
import app.aaps.ui.compose.tempTarget.TempTargetManagementViewModel
import app.aaps.ui.compose.treatments.viewmodels.TreatmentsViewModel
import app.aaps.ui.compose.treatmentsSheet.TreatmentViewModel
import app.aaps.ui.search.SearchViewModel
import dev.zacsweers.metro.createGraphFactory
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Starts the desktop app.
 *
 * The graph is built **before** the app is shown, on purpose. Metro proves at compile time that a
 * binding exists, not that the object behind it can be constructed - a class that reads a file or
 * opens a database in its initializer fails here, at the first attempt, rather than from somewhere
 * that hides it. A failure is shown in the window as well as thrown, because a stack trace on a
 * terminal nobody is watching is how a broken launch gets mistaken for a working one.
 *
 * The window itself opens first, and the start runs on a thread of its own ([DesktopStartup]): opening
 * the settings store can wait for a key store dialog for as long as nobody answers it, and the window
 * says so.
 */
fun main() {
    // Before everything, including the lock and the logger below, because it decides which data
    // directory all of them use. Each client keeps its own, the way each Android client is a
    // separate app with its own private storage, so two can run at once against two sites.
    DesktopFolders.client = GeneratedBuildInfo.CLIENT
    // The icon and the name follow the kind of build, which the build itself says. Not from the graph,
    // so the window has them before the graph exists, and keeps them when the start fails.
    val buildConfig = DesktopClientConfig()
    val iconPath = appIconResource(buildConfig)
    val appName = buildConfig.appNameText

    // Before the window, because macOS reads the Dock icon once as the app starts. Does nothing on
    // the other platforms, where `Window(icon = ...)` below already covers the taskbar.
    applyDockIcon(iconPath)

    application {
        val startup = remember { DesktopStartup() }
        val appIcon = remember { loadAppIcon(iconPath) }
        Window(
            onCloseRequest = ::exitApplication,
            title = appName,
            // The window and taskbar icon. Without it Compose shows its own default, which is what
            // made the app look like a sample project rather than AAPS.
            icon = appIcon
        ) {
            // Started from inside the window, so the window is on screen before the start can wait for a
            // dialog. The lock first, before the graph, because the graph opens the database. Two copies of
            // the *same* client sharing one database is what is being prevented - a different client has
            // its own directory by the first line of main(), and its own lock inside it, so it is unaffected.
            LaunchedEffect(Unit) { startup.start { claimSingleInstance() } }
            when (val result = startup.result) {
                null -> MaterialTheme { StartupWaitingScreen(startup.openingKeyStore) }

                else -> result.fold(
                    onSuccess = { AapsDesktopApp(it, appIcon, appName) },
                    onFailure = { error ->
                        MaterialTheme {
                            StartupFailedScreen(
                                title = "AAPS desktop - startup failed",
                                explanation = failureExplanation(error),
                                error = error,
                                // A failure of either one lands on this same screen again. Not the lock
                                // again: this process holds it already.
                                onRetry = if (error is KeyVaultUnavailableException) ({ startup.start() }) else null,
                                onStartFresh = if (error is StoreUnreadableException) ({ startup.start { DesktopStorage.startFresh() } }) else null
                            )
                        }
                    }
                )
            }
        }
    }
}

/**
 * The start of the app, on a thread of its own, so the window can show what the start waits for.
 *
 * Opening the settings store can wait for the user without end. The system key store may show a dialog
 * first: the macOS Keychain asks for the login password after an update of the app, and a locked Linux
 * keyring asks to be unlocked. The native call has no time limit and cannot be cancelled. Run on the
 * main thread before the window existed, it left an app with nothing on screen for hours.
 *
 * There is no time limit here, on purpose. The store is sealed with the key in the key store, so no other
 * key can open it, and a second key would compete with the real one. A retry would only start a second
 * read while the first dialog is still open.
 *
 * A plain daemon thread, not a coroutine of the window: the window's coroutines must end before the app
 * can exit, and this one cannot end before the native call returns. Closing the window during the wait
 * would leave a process with no window that still holds the single-instance lock.
 */
private class DesktopStartup {

    /** Null while a start runs. State, because "Try again" and "Start fresh" start the app again in place. */
    var result: Result<DesktopAppGraph>? by mutableStateOf(null)
        private set

    /** True while the key store and the settings store are being opened, which is where a dialog can come. */
    var openingKeyStore: Boolean by mutableStateOf(false)
        private set

    private val running = AtomicBoolean(false)

    /** Runs [first], then the start. Does nothing while a start is running, so a double click starts once. */
    fun start(first: () -> Unit = {}) {
        if (!running.compareAndSet(false, true)) return
        result = null
        thread(name = "aaps-start", isDaemon = true) {
            val outcome = runCatching {
                first()
                openingKeyStore = true
                val storage = try {
                    DesktopStorage.open()
                } finally {
                    openingKeyStore = false
                }
                startApp(storage)
            }
            running.set(false)
            result = outcome
        }
    }
}

/**
 * Builds the graph on top of the opened settings store, and starts the plugins.
 *
 * The store is opened before and on its own, so that a store that cannot be opened stops the start with
 * its own message, which the failure screen can act on, instead of failing somewhere inside the first
 * screen.
 */
private fun startApp(storage: DesktopStorage.Opened): DesktopAppGraph {
    val graph = createGraphFactory<DesktopAppGraph.Factory>().create(CoreObjectsGraph, ClientGraphBindings)
    startPlugins(graph)
    // Said in the app, not only in the log: the user is the one who can fix it, by installing a keyring.
    if (storage.fallbackReason != null)
        graph.notificationManager.post(NotificationId.SECRETS_NOT_PROTECTED, graph.textResolver.gs(CoreUiStrings.secrets_not_protected))
    return graph
}

/**
 * Held for the life of the process, and never released by hand.
 *
 * A [FileLock] is let go when its channel is closed, and a channel with nothing referring to it can
 * be collected - so the lock has to be kept reachable or it would quietly disappear while the app is
 * still running. The operating system drops it when the process ends, which is exactly the behaviour
 * wanted: an instance that is killed, or that crashes, does not leave a stale lock behind that stops
 * the next start.
 */
private var instanceLock: FileLock? = null
private var instanceChannel: FileChannel? = null

/**
 * Refuses to start when another copy of this app is already running.
 *
 * Two instances share one `aaps-desktop.db`, one preference file and one log, and neither knows the
 * other is there. It is easy to end up with: the window can be behind another, and starting from the
 * IDE or from Gradle again looks like a fresh start. It really happened - two were found running at
 * once, more than half an hour apart, and neither had complained.
 *
 * The lock lives beside the database in [DesktopFolders.data], not in the user's `AAPS` folder,
 * because it is the app's own business and nobody should ever see it. Throws rather than exits, so
 * the message reaches the same window a failed startup already uses instead of the process vanishing
 * with nothing shown.
 */
private fun claimSingleInstance() {
    val dataDir = DesktopFolders.data
    if (!dataDir.exists() && !dataDir.mkdirs()) error("Could not create ${dataDir.path}")
    val channel = RandomAccessFile(File(dataDir, "aaps-desktop.lock"), "rw").channel
    // null means somebody else holds it; the exception means this JVM already does, which can only
    // happen if this is called twice. Both mean the same thing to the user.
    val lock = runCatching { channel.tryLock() }.getOrNull()
    // Its own logger, because there is no graph yet and there may never be one on this path.
    // AAPSLoggerDesktop takes a null log config for exactly this moment, and it writes to the same
    // file the started instance writes to - so a refused start leaves a line in the log a user reads
    // rather than only a message in a window they may have closed.
    val logger = AAPSLoggerDesktop()
    if (lock == null) {
        channel.close()
        val message = "AAPSClient is already running. Only one copy can use the database in ${dataDir.path} at a time."
        logger.error(LTag.CORE, message)
        error(message)
    }
    logger.debug(LTag.CORE, "Single instance lock taken in ${dataDir.path}")
    instanceChannel = channel
    instanceLock = lock
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
    GeneratedStringOwners.registerAll()
    // The language to answer in, from the setting or from the machine. State on the registry rather
    // than an argument to every lookup - the same arrangement Android gets from `Resources`, which is
    // why `gs(ref)` needs no locale and no call site changed. Without this the desktop is English
    // whatever the setting says, which is how it behaved before the translations were generated.
    // Through LanguageTag, not straight from the preference: the stored values are not all tags.
    // "dk" is a country code where a language code belongs, and "pt_BR"/"zh_TW"/"zh_CN" use the Java
    // underscore form. Passing those on raw matched no translation, so four of the offered languages
    // showed a fully English app here while Android translated.
    TextRefValueRegistry.locale = languageTag(graph.preferences.get(StringKey.GeneralLanguage))
    graph.logger.debug(LTag.CORE, "Language: ${TextRefValueRegistry.locale}")
    // Which marker files were found, so the answer is in the log rather than only in whether a
    // preference row happens to be drawn. An option that is switched off and a marker file that is
    // looked for in the wrong folder produce exactly the same silence, which is how desktop went a
    // long time reading these from the data directory while Android reads them from AAPS/extra.
    // Created up front so the user finds the folder to put a marker file in (#5250). Nothing else
    // creates it: only Config reads it.
    if (!DesktopFolders.extra.isDirectory && !DesktopFolders.extra.mkdirs())
        graph.logger.error(LTag.CORE, "Cannot create ${DesktopFolders.extra.path}")
    val enabledOptions = ExternalOptions.entries.filter { graph.config.isEnabled(it) }
    graph.logger.debug(
        LTag.CORE,
        "External options in ${DesktopFolders.extra.path}: " +
            if (enabledOptions.isEmpty()) "none" else enabledOptions.joinToString { it.filename }
    )
    // Sorted by the key each plugin registers itself with, which is the order the plugin list is
    // shown in and the order category defaults are picked in.
    val plugins = graph.contributedPlugins.entries.sortedBy { it.key }.map { it.value }
    graph.pluginStore.plugins = plugins
    graph.configBuilder.initialize()
    // The periodic housekeeping Android gets from KeepAliveWorker. The work is shared; only the
    // trigger is not, because there is no WorkManager here.
    graph.periodicMaintenance.start(graph.appScope)
    // A snackbar sent while no window is showing one becomes a system notification instead of being
    // lost. The same shared class Android and iOS start.
    graph.snackbarNotificationFallback.start()
    // The "time to eat" alarm lives in this process; set it again when a timer was running. Master only.
    graph.eatReminder.restore()
    graph.logger.debug(LTag.CORE, "Registered ${plugins.size} plugins and verified selections")
}

/** The language tag for the stored language setting, or the machine's language when it follows the device. */
internal fun languageTag(stored: String): String = LanguageTag.of(stored) ?: Locale.getDefault().toLanguageTag()

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
private fun AapsDesktopApp(graph: DesktopAppGraph, appIcon: Painter, appName: String) {
    val logger = graph.logger
    val viewModelFactory = remember(graph) { ClientViewModelFactory(graph) }

    // The language setting, applied while the app runs. Android answers this by recreating its
    // activity, which reloads the `Context` its `Resources` resolve against. There is no activity
    // here: the registry is repointed and the composition rebuilt, which is the same outcome.
    LaunchedEffect(Unit) {
        graph.preferences.observe(StringKey.GeneralLanguage).drop(1).collect {
            // Through the same fix-up as at start. This used the stored value as it was, so choosing
            // Danish, Brazilian Portuguese or either Chinese while the app ran gave English text until
            // the next start.
            TextRefValueRegistry.locale = languageTag(graph.preferences.get(StringKey.GeneralLanguage))
            logger.debug(LTag.CORE, "Language changed to ${TextRefValueRegistry.locale}")
            graph.uiRestart.request()
        }
    }

    // Clears a granted PIN or biometric session when the window stops being the active one - the
    // desktop counterpart of Android's `ProcessLifecycleListener.onPause`. Nothing did this before,
    // so the authorization outlived leaving the app and coming back to the window reached whatever
    // the user had protected without a prompt. `ProtectionTimeout` defaults to a second, so only a
    // user who raised it was affected, which is why it went unnoticed.
    val windowInfo = LocalWindowInfo.current
    LaunchedEffect(windowInfo) {
        // No `drop(1)`: the first value is either focused, which this ignores, or unfocused, and
        // clearing a session nobody has granted yet does nothing.
        snapshotFlow { windowInfo.isWindowFocused }.filter { !it }.collect {
            logger.debug(LTag.CORE, "Window lost focus, clearing the authorization")
            graph.protectionCheck.resetAuthorization()
        }
    }

    // Rebuilds everything below when one is asked for - after an import applies its settings, or a
    // language change. Keyed on this and nothing else: `key` discards the subtree's state, so a
    // scroll position lost on a language change is acceptable where losing it constantly is not.
    val restart by graph.uiRestart.signal.collectAsState()
    key(restart) {
    CompositionLocalProvider(
        LocalMetroViewModelFactory provides viewModelFactory,
        // New with every rebuild, so the view models are rebuilt too, not only the screens
        LocalViewModelStoreOwner provides rememberOwnViewModelStoreOwner()
    ) {
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
            snackbarHostPresence = graph.snackbarHostPresence,
            clientControlActionDispatcher = graph.clientControlActionDispatcher,
            bolusProgressData = graph.bolusProgressData,
            commandQueue = graph.commandQueue,
            pumpCommunicationStatus = graph.pumpCommunicationStatus,
            // Image rather than Icon: Icon paints its vector in a single tint, which flattens a logo
            // to one colour. Android passes the launcher bitmap here and this is the same file.
            appIcon = { modifier -> Image(appIcon, contentDescription = null, modifier = modifier) },
            splashLogo = { modifier -> Image(appIcon, contentDescription = null, modifier = modifier) },
            onNavControllerReady = {},
            onClose = { logger.error(LTag.CORE, "Initialization failed and the window was asked to close") }
        ) { navController ->
            // Resolved here rather than inside the NavHost: appNavGraph is a builder, not a
            // composable, so a view model cannot be asked for from within it.
            // The overview and the drawer must share one instance, or a menu action would talk to a
            // second copy of the screen it is meant to be driving.
            val mainViewModel = metroViewModel<MainViewModel>()

            // The shared navigator. Everything a tap can lead to is mapped in :appshell; only the
            // four platform actions differ, and a desktop can honour two of them properly.
            val navigator = ElementNavigator(
                navController = navController,
                mainViewModel = mainViewModel,
                activePlugin = graph.activePlugin,
                protectionCheck = graph.protectionCheck,
                configBuilder = graph.configBuilder,
                dexcomBoyda = graph.dexcomBoyda,
                onOpenCgmApp = { pkg -> logger.error(LTag.CORE, "No CGM app to open on desktop: $pkg") },
                onExit = { logger.debug(LTag.CORE, "Exit requested from the menu") },
                onRequestDirectoryAccess = { logger.debug(LTag.CORE, "Desktop reads its own folder; nothing to grant") },
                onOpenUrl = { url -> graph.urlOpener.open(url) }
            )
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

            // The overview is the start destination, the same as Android and iOS. Settings used to
            // be, which left its back arrow inert - there was nothing behind it - and left every
            // other screen unreachable, since they are all reached from the overview.
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
                    // Protection is enforced: ProtectionHost is placed by AapsAppRoot on every
                    // platform now, so a protected screen really does prompt here.
                    withProtection = { protection, action ->
                        graph.protectionCheck.requestProtection(protection) { result ->
                            if (result == app.aaps.core.interfaces.protection.ProtectionResult.GRANTED) action()
                        }
                    },
                    // The same check `ComposeMainActivity` makes - see the note in `AapsAppHost`. It
                    // used to grant unconditionally, so settings protection did not cover the edit
                    // pencil on the management screens.
                    requestEditModeAuthorization = { onGranted ->
                        graph.protectionCheck.requestAuthorization(ProtectionCheck.Protection.PREFERENCES) { result ->
                            if (result.grantedLevel != null) onGranted()
                        }
                    },
                    onRefreshPermissions = { logger.debug(LTag.CORE, "No runtime permissions to refresh on desktop") },
                    onExecuteQuickWizard = { guid -> mainViewModel.executeQuickWizard(guid) },
                    onRequestDirectoryAccess = { logger.debug(LTag.CORE, "Desktop reads its own folder; no access to request") },
                    onRequestPermission = { group -> logger.notWiredYet("permission request $group") },
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
                            onImportSettingsNavigate = { source -> navController.navigate(AppRoute.ImportSettings.createRoute(source.name)) },
                            onDirectoryClick = { openFolder(DesktopFolders.root, logger) },
                            // authBrowser, not urlOpener: the sign in ends at a port this app is
                            // listening on, and DesktopAuthBrowser has the fallback launcher an
                            // ordinary link opener does not. See AuthBrowser.
                            onLaunchBrowser = { url -> graph.authBrowser.show(url) },
                            onBringToForeground = { logger.debug(LTag.CORE, "Desktop window is already in front") },
                            onRecreateActivity = { logger.notWiredYet("window recreate") },
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

/**
 * Words a user can act on, for the two failures of the settings store. Both are normal events rather
 * than bugs. Any other failure gets none: its message is the useful part, and the screen shows it.
 */
private fun failureExplanation(error: Throwable): List<String> = when (error) {
    is StoreUnreadableException     -> listOf(
        "Your settings on this computer cannot be read. They are encrypted with a key kept in the system key store, " +
            "and that key does not open them. This happens when the AAPS folder was copied from another computer or " +
            "another user, when the key store was reset, or when the file is damaged.",
        "Start fresh moves the old file aside (it is kept) and starts with empty settings. " +
            "You then need to set up the connection to the master again."
    )

    is KeyVaultUnavailableException -> listOf(
        "The system key store did not answer. AAPS keeps the key to your settings there. Unlock it - log in to " +
            "the desktop session, or unlock the keyring - and try again."
    )

    else                            -> emptyList()
}

/** One line, one shape, so a run makes it obvious which platform callbacks are still placeholders. */
private fun AAPSLogger.notWiredYet(what: String) = error(LTag.CORE, "Not wired up on desktop yet: $what")

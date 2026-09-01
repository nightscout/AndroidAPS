package app.aaps.ui.compose.main

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.interfaces.bgQualityCheck.BgQualityCheck
import app.aaps.core.interfaces.clientcontrol.ClientControlActionDispatcher
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.constraints.Objectives
import app.aaps.core.interfaces.notifications.AapsNotification
import app.aaps.core.interfaces.notifications.AlarmSound
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.dialogs.OkDialog
import app.aaps.core.ui.compose.navigation.NavigationRequest
import app.aaps.core.ui.compose.pump.PumpCommunicationStatus
import app.aaps.ui.compose.loopSheet.LoopActionViewModel
import app.aaps.ui.compose.maintenance.ImportSource
import app.aaps.ui.compose.maintenance.MaintenanceViewModel
import app.aaps.ui.compose.manageSheet.ManageSheetHost
import app.aaps.ui.compose.manageSheet.ManageViewModel
import app.aaps.ui.compose.overview.chips.ChipsViewModel
import app.aaps.ui.compose.overview.graphs.GraphViewModel
import app.aaps.ui.compose.overview.statusLights.StatusViewModel
import app.aaps.ui.compose.permissionsSheet.PermissionsViewModel
import app.aaps.ui.compose.quickLaunch.QuickLaunchAction
import app.aaps.ui.compose.scenesSheet.ScenesViewModel
import app.aaps.ui.compose.treatmentsSheet.TreatmentViewModel
import app.aaps.ui.search.BuiltInSearchables
import app.aaps.ui.search.SearchIndexEntry
import app.aaps.ui.search.SearchViewModel

/**
 * The overview - the app's home screen - assembled once for every platform.
 *
 * ## Why this exists
 *
 * [MainScreen] draws the overview and has been in commonMain all along, but the hundred and seventy
 * lines that *feed* it lived inline in `ComposeMainActivity`. That is why the shared navigation
 * graph had no home screen to offer: iOS and desktop could reach every settings screen and not the
 * one screen the app is actually about, and both had to open on a scaffolding list instead.
 *
 * Nothing in that assembly was Android. It is plugin state, objectives progress and glucose quality -
 * decisions about what to show, which is exactly the kind of thing worth having in one copy. Moving
 * it here is what lets `appNavGraph` register `AppRoute.Main` for everyone.
 *
 * ## What is still per platform
 *
 * The callbacks at the end, and only those: opening a browser, opening a directory picker, bringing
 * the window to the front, recreating the activity, and quitting after a failed authorization. Each
 * is a sentence of platform code, and each is passed in rather than guessed at.
 *
 * [appName] and [authorizationFailedMessage] are parameters because they live in the app module's
 * own resources - the name differs per flavour - and this module cannot see them.
 */
@Composable
@Suppress("LongParameterList", "LongMethod")
fun OverviewScreen(
    // View models
    mainViewModel: MainViewModel,
    manageViewModel: ManageViewModel,
    maintenanceViewModel: MaintenanceViewModel,
    statusViewModel: StatusViewModel,
    treatmentViewModel: TreatmentViewModel,
    scenesViewModel: ScenesViewModel,
    loopActionViewModel: LoopActionViewModel,
    searchViewModel: SearchViewModel,
    permissionsViewModel: PermissionsViewModel,
    graphViewModel: GraphViewModel,
    chipsViewModel: ChipsViewModel,
    // Dependencies
    activePlugin: ActivePlugin,
    config: Config,
    objectives: Objectives,
    bgQualityCheck: BgQualityCheck,
    notificationManager: NotificationManager,
    uiInteraction: UiInteraction,
    builtInSearchables: BuiltInSearchables,
    bolusProgressData: BolusProgressData,
    clientControlActionDispatcher: ClientControlActionDispatcher,
    commandQueue: CommandQueue,
    pumpCommunicationStatus: PumpCommunicationStatus,
    // Text the app module owns
    appName: String,
    authorizationFailedMessage: String,
    // Navigation, which the platform routes because it holds the controller
    onNavigate: (NavigationRequest) -> Unit,
    onSearchResultClick: (SearchIndexEntry) -> Unit,
    onNotificationActionClick: (AapsNotification) -> Unit,
    onQuickLaunchActionClick: (QuickLaunchAction) -> Unit,
    onImportSettingsNavigate: (ImportSource) -> Unit,
    // Platform actions
    onDirectoryClick: () -> Unit,
    onLaunchBrowser: (String) -> Unit,
    onBringToForeground: () -> Unit,
    onRecreateActivity: () -> Unit,
    onAuthorizationFailed: () -> Unit,
    // Notification sheet, opened from outside when something important arrives
    autoShowNotificationSheet: Boolean,
    onAutoShowConsumed: () -> Unit
) {
    val state by mainViewModel.uiState.collectAsStateWithLifecycle()
    val searchState by searchViewModel.uiState.collectAsStateWithLifecycle()
    val calcProgress by mainViewModel.calcProgressFlow.collectAsStateWithLifecycle()
    val notifications by notificationManager.notifications.collectAsStateWithLifecycle()
    val quickLaunchItems by mainViewModel.quickLaunchItems.collectAsStateWithLifecycle()
    val permState by permissionsViewModel.uiState.collectAsStateWithLifecycle()
    val bolusState by bolusProgressData.state.collectAsStateWithLifecycle()
    val pumpStatusBanner by pumpCommunicationStatus.statusBannerFlow.collectAsStateWithLifecycle()
    val pumpQueueStatus by pumpCommunicationStatus.queueStatusFlow.collectAsStateWithLifecycle()

    // Pump setup button in bottom bar
    val pumpPlugin = activePlugin.activePumpInternal as PluginBase
    val showPumpSetup = (!activePlugin.activePump.isInitialized() || activePlugin.activePump.isSuspended()) &&
        pumpPlugin.hasComposeContent()
    val pumpSetupPlugin = if (showPumpSetup) pumpPlugin else null

    // Objectives progress badge (visible while objectives not all completed, in APS mode)
    val objectivesPlugin = objectives as PluginBase
    val objectivesTotal = objectives.size
    val objectivesDone = objectives.accomplishedCount
    val showObjectivesSetup = config.APS && objectivesTotal > 0 && objectivesDone < objectivesTotal &&
        objectivesPlugin.isEnabled() && objectivesPlugin.hasComposeContent()
    val objectivesSetupPlugin = if (showObjectivesSetup) objectivesPlugin else null
    val objectivesProgressText = if (showObjectivesSetup) "$objectivesDone/$objectivesTotal" else null

    // BG source shortcut: shown when BG quality check reports FLAT or DOUBLED
    val bgQualityState by bgQualityCheck.stateFlow.collectAsStateWithLifecycle()
    val bgSourcePlugin = activePlugin.activeBgSource as PluginBase
    val showBgSetup = (bgQualityState == BgQualityCheck.State.FLAT || bgQualityState == BgQualityCheck.State.DOUBLED) &&
        bgSourcePlugin.hasComposeContent()
    val bgSetupPlugin = if (showBgSetup) bgSourcePlugin else null
    val bgQualityBadgeIcon: ImageVector? = if (showBgSetup) when (bgQualityState) {
        BgQualityCheck.State.DOUBLED -> Icons.Filled.Warning
        BgQualityCheck.State.FLAT    -> Icons.AutoMirrored.Filled.TrendingFlat
        else                         -> null
    } else null
    val bgQualityBadgeTint: Color = when (bgQualityState) {
        BgQualityCheck.State.RECALCULATED                       -> AapsTheme.generalColors.statusWarning
        BgQualityCheck.State.DOUBLED, BgQualityCheck.State.FLAT -> AapsTheme.generalColors.statusCritical
        else                                                    -> Color.Unspecified
    }
    val bgQualityBadgeDescription = if (showBgSetup) bgQualityCheck.stateDescription() else null

    val manageSheetState = ManageSheetHost(
        manageViewModel = manageViewModel,
        isSimpleMode = state.isSimpleMode,
        onNavigate = onNavigate,
        onActionsError = { comment, title ->
            uiInteraction.runAlarm(comment, title, AlarmSound.BOLUS_ERROR)
        },
    )

    // Authorization failed dialog
    if (state.showAuthFailedDialog) {
        OkDialog(
            title = "",
            message = authorizationFailedMessage,
            onDismiss = {
                mainViewModel.setShowAuthFailedDialog(false)
                onAuthorizationFailed()
            }
        )
    }

    MainScreen(
        mainViewModel = mainViewModel,
        uiState = state,
        aboutDialogData = if (state.showAboutDialog) mainViewModel.buildAboutDialogData(appName) else null,
        manageSheetState = manageSheetState,
        manageViewModel = manageViewModel,
        maintenanceViewModel = maintenanceViewModel,
        statusViewModel = statusViewModel,
        treatmentViewModel = treatmentViewModel,
        scenesViewModel = scenesViewModel,
        loopActionViewModel = loopActionViewModel,
        // Search
        searchUiState = searchState,
        onSearchQueryChange = { searchViewModel.onQueryChanged(it) },
        onSearchClear = { searchViewModel.clearQuery() },
        onSearchActiveChange = { active ->
            if (active) searchViewModel.onSearchModeActivated()
            else searchViewModel.onSearchModeDeactivated()
        },
        onSearchResultClick = onSearchResultClick,
        onSearchPluginToggle = { plugin -> searchViewModel.togglePlugin(plugin) },
        onConfirmSearchPluginSwitch = { searchViewModel.confirmPluginSwitch() },
        onDismissSearchPluginSwitch = { searchViewModel.dismissPluginSwitch() },
        onConfirmSearchHardwarePump = { searchViewModel.confirmHardwarePump() },
        onDismissSearchHardwarePump = { searchViewModel.dismissHardwarePump() },
        onMenuClick = { mainViewModel.openDrawer() },
        onNavigate = onNavigate,
        onDrawerClosed = { mainViewModel.closeDrawer() },
        onAboutDialogDismiss = { mainViewModel.setShowAboutDialog(false) },
        onOpenBatteryHelp = { mainViewModel.openBatteryHelp() },
        onMaintenanceSheetDismiss = { mainViewModel.setShowMaintenanceSheet(false) },
        onDirectoryClick = onDirectoryClick,
        onLaunchBrowser = onLaunchBrowser,
        onBringToForeground = onBringToForeground,
        onImportSettingsNavigate = onImportSettingsNavigate,
        onRecreateActivity = onRecreateActivity,
        // Notifications
        notifications = notifications,
        onDismissNotification = { notification -> notificationManager.dismiss(notification.id) },
        onNotificationActionClick = onNotificationActionClick,
        autoShowNotificationSheet = autoShowNotificationSheet,
        onAutoShowConsumed = onAutoShowConsumed,
        pumpSetupPlugin = pumpSetupPlugin,
        bgSetupPlugin = bgSetupPlugin,
        bgQualityBadgeIcon = bgQualityBadgeIcon,
        bgQualityBadgeTint = bgQualityBadgeTint,
        bgQualityBadgeDescription = bgQualityBadgeDescription,
        objectivesSetupPlugin = objectivesSetupPlugin,
        objectivesProgressText = objectivesProgressText,
        permissionsMissing = permState.hasAnyMissing,
        onPermissionsClick = { permissionsViewModel.showSheet() },
        // Toolbar
        quickLaunchItems = quickLaunchItems,
        onQuickLaunchActionClick = onQuickLaunchActionClick,
        calcProgress = calcProgress,
        graphViewModel = graphViewModel,
        chipsViewModel = chipsViewModel,
        statusLightsDef = builtInSearchables.statusLights,
        treatmentButtonsDef = builtInSearchables.treatmentButtons,
        // Pump activity
        bolusState = bolusState,
        pumpStatusText = pumpStatusBanner?.text ?: "",
        queueStatusText = pumpQueueStatus,
        isPumpCommunicating = pumpStatusBanner != null,
        onStopBolus = {
            if (config.AAPSCLIENT) {
                clientControlActionDispatcher.stopBolus()
                bolusProgressData.stopPressed()
            } else {
                commandQueue.cancelAllBoluses(null)
            }
        }
    )
}

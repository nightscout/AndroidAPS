package app.aaps.ui.compose.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.interfaces.notifications.AapsNotification
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.pump.BolusProgressState
import app.aaps.core.ui.compose.LocalDateUtil
import app.aaps.core.ui.compose.LocalSnackbarHostState
import app.aaps.core.ui.compose.dialogs.OkCancelDialog
import app.aaps.core.ui.compose.navigation.ElementType
import app.aaps.core.ui.compose.navigation.NavigationRequest
import app.aaps.core.ui.compose.preference.PreferenceSubScreenDef
import app.aaps.ui.compose.alertDialogs.AboutAlertDialog
import app.aaps.ui.compose.alertDialogs.AboutDialogData
import app.aaps.ui.compose.automationSheet.AutomationBottomSheet
import app.aaps.ui.compose.automationSheet.AutomationViewModel
import app.aaps.ui.compose.maintenance.ImportSource
import app.aaps.ui.compose.maintenance.MaintenanceDialogs
import app.aaps.ui.compose.maintenance.MaintenanceViewModel
import app.aaps.ui.compose.manageSheet.ManageSheetState
import app.aaps.ui.compose.manageSheet.ManageViewModel
import app.aaps.ui.compose.overview.OverviewScreen
import app.aaps.ui.compose.overview.graphs.GraphViewModel
import app.aaps.ui.compose.overview.statusLights.StatusViewModel
import app.aaps.ui.compose.quickLaunch.QuickLaunchAction
import app.aaps.ui.compose.quickLaunch.QuickLaunchToolbar
import app.aaps.ui.compose.quickLaunch.ResolvedQuickLaunchItem
import app.aaps.ui.compose.treatmentsSheet.TreatmentBottomSheet
import app.aaps.ui.compose.treatmentsSheet.TreatmentViewModel
import app.aaps.ui.search.SearchIndexEntry
import app.aaps.ui.search.SearchResults
import app.aaps.ui.search.SearchUiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun MainScreen(
    mainViewModel: MainViewModel,
    uiState: MainUiState,
    aboutDialogData: AboutDialogData?,
    manageSheetState: ManageSheetState,
    manageViewModel: ManageViewModel,
    maintenanceViewModel: MaintenanceViewModel,
    statusViewModel: StatusViewModel,
    treatmentViewModel: TreatmentViewModel,
    automationViewModel: AutomationViewModel,
    loopActionViewModel: app.aaps.ui.compose.loopSheet.LoopActionViewModel,
    // Search
    searchUiState: SearchUiState,
    onSearchQueryChange: (String) -> Unit,
    onSearchClear: () -> Unit,
    onSearchActiveChange: (Boolean) -> Unit,
    onSearchResultClick: (SearchIndexEntry) -> Unit,
    // Menu/navigation
    onMenuClick: () -> Unit,
    onNavigate: (NavigationRequest) -> Unit,
    onDrawerClosed: () -> Unit,
    onAboutDialogDismiss: () -> Unit,
    onMaintenanceSheetDismiss: () -> Unit,
    onDirectoryClick: () -> Unit,
    onLaunchBrowser: (String) -> Unit,
    onBringToForeground: () -> Unit,
    onImportSettingsNavigate: (ImportSource) -> Unit,
    onRecreateActivity: () -> Unit,
    // Notifications
    notifications: List<AapsNotification>,
    onDismissNotification: (AapsNotification) -> Unit,
    onNotificationActionClick: (AapsNotification) -> Unit,
    autoShowNotificationSheet: Boolean,
    onAutoShowConsumed: () -> Unit,
    // Pump setup
    pumpSetupPlugin: PluginBase? = null,
    // BG source shortcut
    bgSetupPlugin: PluginBase? = null,
    bgQualityBadgeIcon: ImageVector? = null,
    bgQualityBadgeTint: Color = Color.Unspecified,
    bgQualityBadgeDescription: String? = null,
    // Objectives progress
    objectivesSetupPlugin: PluginBase? = null,
    objectivesProgressText: String? = null,
    // Permissions
    permissionsMissing: Boolean = false,
    onPermissionsClick: () -> Unit = {},
    // Toolbar
    quickLaunchItems: List<ResolvedQuickLaunchItem> = emptyList(),
    onQuickLaunchActionClick: (QuickLaunchAction) -> Unit = {},
    calcProgress: Int,
    graphViewModel: GraphViewModel,
    statusLightsDef: PreferenceSubScreenDef,
    treatmentButtonsDef: PreferenceSubScreenDef,
    // Pump activity
    bolusState: BolusProgressState? = null,
    pumpStatusText: String = "",
    queueStatusText: String? = null,
    isPumpCommunicating: Boolean = false,
    onStopBolus: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    LocalDateUtil.current
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var showTreatmentSheet by remember { mutableStateOf(false) }
    var showAutomationSheet by remember { mutableStateOf(false) }
    var showLoopActionSheet by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Sync drawer state with ui state
    LaunchedEffect(uiState.isDrawerOpen) {
        if (uiState.isDrawerOpen) {
            drawerState.open()
        } else {
            drawerState.close()
        }
    }

    LaunchedEffect(drawerState.isClosed) {
        if (drawerState.isClosed && uiState.isDrawerOpen) {
            onDrawerClosed()
        }
    }

    CompositionLocalProvider(LocalSnackbarHostState provides snackbarHostState) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                MainDrawer(
                    versionName = mainViewModel.versionName,
                    appIcon = mainViewModel.appIcon,
                    onNavigate = { request ->
                        scope.launch { drawerState.close() }
                        onDrawerClosed()
                        onNavigate(request)
                    },
                    isTreatmentsEnabled = uiState.isProfileLoaded
                )
            },
            gesturesEnabled = true,
            modifier = modifier
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val density = LocalDensity.current
                val previewMode = maxHeight < PREVIEW_MODE_MIN_HEIGHT
                var chromeVisible by remember { mutableStateOf(false) }
                val showChrome = !previewMode || chromeVisible
                val interactionSource = remember { MutableInteractionSource() }

                // Measure actual bar heights for content padding in non-preview mode
                var topBarHeightPx by remember { mutableIntStateOf(0) }
                var bottomBarHeightPx by remember { mutableIntStateOf(0) }

                // Auto-hide chrome after timeout, reset when leaving preview mode
                LaunchedEffect(chromeVisible, previewMode) {
                    if (!previewMode) {
                        chromeVisible = false
                        return@LaunchedEffect
                    }
                    if (chromeVisible) {
                        delay(AUTO_HIDE_DELAY_MS)
                        chromeVisible = false
                    }
                }

                Scaffold(
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                ) { scaffoldPadding ->
                    val hasToolbar = quickLaunchItems.isNotEmpty()

                    // Content padding: in preview mode use only system bars;
                    // in normal mode add measured bar heights
                    val contentPadding = if (previewMode) scaffoldPadding
                    else {
                        val topBarHeight = with(density) { topBarHeightPx.toDp() }
                        val bottomBarHeight = with(density) { bottomBarHeightPx.toDp() }
                        PaddingValues(
                            top = scaffoldPadding.calculateTopPadding() + topBarHeight,
                            bottom = scaffoldPadding.calculateBottomPadding() + bottomBarHeight
                        )
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        // Main content
                        OverviewScreen(
                            profileName = uiState.profileName,
                            isProfileModified = uiState.isProfileModified,
                            profileProgress = uiState.profileProgress,
                            tempTargetText = uiState.tempTargetText,
                            tempTargetState = uiState.tempTargetState,
                            tempTargetProgress = uiState.tempTargetProgress,
                            tempTargetReason = uiState.tempTargetReason,
                            runningMode = uiState.runningMode,
                            runningModeText = uiState.runningModeText,
                            runningModeProgress = uiState.runningModeProgress,
                            tbrState = uiState.tbrState,
                            isSimpleMode = uiState.isSimpleMode,
                            calcProgress = calcProgress,
                            graphViewModel = graphViewModel,
                            manageViewModel = manageViewModel,
                            statusViewModel = statusViewModel,
                            statusLightsDef = statusLightsDef,
                            onNavigate = onNavigate,
                            notifications = notifications,
                            onDismissNotification = onDismissNotification,
                            onNotificationActionClick = onNotificationActionClick,
                            autoShowNotificationSheet = autoShowNotificationSheet,
                            onAutoShowConsumed = onAutoShowConsumed,
                            paddingValues = contentPadding,
                            fabBottomOffset = if (hasToolbar && showChrome) 56.dp else 0.dp,
                            bolusState = bolusState,
                            pumpStatusText = pumpStatusText,
                            queueStatusText = queueStatusText,
                            isPumpCommunicating = isPumpCommunicating,
                            onStopBolus = onStopBolus
                        )

                        // Search results overlay
                        if (searchUiState.isSearchActive) {
                            SearchResults(
                                results = searchUiState.results,
                                wikiResults = searchUiState.wikiResults,
                                isSearching = searchUiState.isSearching,
                                isSearchingWiki = searchUiState.isSearchingWiki,
                                wikiOffline = searchUiState.wikiOffline,
                                onResultClick = onSearchResultClick,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(contentPadding)
                            )
                        }

                        // Version overlay
                        VersionOverlay(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(contentPadding)
                        )

                        // Top bar overlay
                        AnimatedVisibility(
                            visible = showChrome,
                            enter = slideInVertically { -it },
                            exit = slideOutVertically { -it },
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = scaffoldPadding.calculateTopPadding())
                        ) {
                            MainTopBar(
                                searchUiState = searchUiState,
                                onMenuClick = {
                                    scope.launch {
                                        drawerState.open()
                                        onMenuClick()
                                    }
                                },
                                onPreferencesClick = { onNavigate(NavigationRequest.Element(ElementType.SETTINGS)) },
                                onSearchQueryChange = onSearchQueryChange,
                                onSearchClear = onSearchClear,
                                onSearchActiveChange = onSearchActiveChange,
                                modifier = Modifier.onSizeChanged { topBarHeightPx = it.height }
                            )
                        }

                        // Bottom bar overlay
                        AnimatedVisibility(
                            visible = showChrome,
                            enter = slideInVertically { it },
                            exit = slideOutVertically { it },
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = scaffoldPadding.calculateBottomPadding())
                        ) {
                            val loopActionState = loopActionViewModel.uiState.collectAsStateWithLifecycle().value
                            MainNavigationBar(
                                onManageClick = { manageSheetState.show() },
                                onTreatmentClick = {
                                    treatmentViewModel.refreshState()
                                    showTreatmentSheet = true
                                },
                                quickWizardCount = uiState.quickWizardItems.size,
                                onAutomationClick = {
                                    automationViewModel.refreshState()
                                    showAutomationSheet = true
                                },
                                automationCount = automationViewModel.uiState.collectAsStateWithLifecycle().value.items.size,
                                pumpSetupPlugin = pumpSetupPlugin,
                                bgSetupPlugin = bgSetupPlugin,
                                bgQualityBadgeIcon = bgQualityBadgeIcon,
                                bgQualityBadgeTint = bgQualityBadgeTint,
                                bgQualityBadgeDescription = bgQualityBadgeDescription,
                                objectivesSetupPlugin = objectivesSetupPlugin,
                                objectivesProgressText = objectivesProgressText,
                                onNavigate = onNavigate,
                                permissionsMissing = permissionsMissing,
                                onPermissionsClick = onPermissionsClick,
                                loopActionAvailable = loopActionState.actionAvailable,
                                onLoopActionClick = { showLoopActionSheet = true },
                                modifier = Modifier.onSizeChanged { bottomBarHeightPx = it.height }
                            )
                        }

                        // Quick launch toolbar overlay
                        AnimatedVisibility(
                            visible = hasToolbar && showChrome,
                            enter = slideInVertically { it },
                            exit = slideOutVertically { it },
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(
                                    bottom = scaffoldPadding.calculateBottomPadding() +
                                        with(density) { bottomBarHeightPx.toDp() } + 8.dp
                                )
                        ) {
                            QuickLaunchToolbar(
                                items = quickLaunchItems,
                                onActionClick = onQuickLaunchActionClick,
                            )
                        }

                        // Tap overlay to restore chrome in preview mode (only when hidden)
                        if (previewMode && !chromeVisible) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clickable(
                                        interactionSource = interactionSource,
                                        indication = null
                                    ) { chromeVisible = true }
                            )
                        }
                    }
                }
            }
        }

        // Treatment bottom sheet
        if (showTreatmentSheet) {
            val treatmentState by treatmentViewModel.uiState.collectAsStateWithLifecycle()
            TreatmentBottomSheet(
                onDismiss = { showTreatmentSheet = false },
                showCgm = treatmentState.showCgm,
                showCalibration = treatmentState.showCalibration,
                showTreatment = treatmentState.showTreatment,
                showInsulin = treatmentState.showInsulin,
                showCarbs = treatmentState.showCarbs,
                showCalculator = treatmentState.showCalculator,
                isDexcomSource = treatmentState.isDexcomSource,
                showSettingsIcon = treatmentState.showSettingsIcon,
                quickWizardItems = treatmentState.quickWizardItems,
                onNavigate = onNavigate,
                treatmentButtonsDef = treatmentButtonsDef,
            )
        }

        // Automation bottom sheet
        if (showAutomationSheet) {
            val automationState by automationViewModel.uiState.collectAsStateWithLifecycle()
            AutomationBottomSheet(
                onDismiss = { showAutomationSheet = false },
                automationItems = automationState.items,
                onItemClick = { item -> mainViewModel.requestAutomationConfirmation(item.eventId) }
            )
        }

        // Loop accept action bottom sheet
        if (showLoopActionSheet) {
            val loopState by loopActionViewModel.uiState.collectAsStateWithLifecycle()
            app.aaps.ui.compose.loopSheet.LoopActionBottomSheet(
                state = loopState,
                onPerform = { mainViewModel.performLoopAccept() },
                onDismiss = { showLoopActionSheet = false }
            )
        }

        // Shared confirmation dialog (automation actions, TT presets — from toolbar or bottom sheets)
        val actionConfirmation by mainViewModel.actionConfirmation.collectAsStateWithLifecycle()
        actionConfirmation?.let { confirmation ->
            OkCancelDialog(
                title = confirmation.title,
                message = confirmation.message,
                onConfirm = { mainViewModel.executeConfirmableAction(confirmation.onConfirmAction) },
                onDismiss = { mainViewModel.dismissActionConfirmation() }
            )
        }

        // Maintenance dialogs (sheets, confirmations, export chain)
        MaintenanceDialogs(
            maintenanceViewModel = maintenanceViewModel,
            showMaintenanceSheet = uiState.showMaintenanceSheet,
            onMaintenanceSheetDismiss = onMaintenanceSheetDismiss,
            onDirectoryClick = onDirectoryClick,
            onImportSettingsNavigate = onImportSettingsNavigate,
            onRecreateActivity = onRecreateActivity,
            onLaunchBrowser = onLaunchBrowser,
            onBringToForeground = onBringToForeground,
            onSnackbar = { snackbarHostState.showSnackbar(it) }
        )

        // About dialog
        if (uiState.showAboutDialog && aboutDialogData != null) {
            AboutAlertDialog(
                data = aboutDialogData,
                onDismiss = onAboutDialogDismiss
            )
        }
    } // CompositionLocalProvider
}

private val PREVIEW_MODE_MIN_HEIGHT: Dp = 500.dp
private const val AUTO_HIDE_DELAY_MS = 3000L
